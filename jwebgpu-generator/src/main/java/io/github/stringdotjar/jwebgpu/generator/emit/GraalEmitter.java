/*
 * MIT License
 *
 * Copyright (c) 2026 stringdotjar
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.stringdotjar.jwebgpu.generator.emit;

import io.github.stringdotjar.jwebgpu.generator.model.Callback;
import io.github.stringdotjar.jwebgpu.generator.model.GpuFunction;
import io.github.stringdotjar.jwebgpu.generator.model.GpuObject;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Writes the GraalVM native image metadata of the FFM backend.
 *
 * <p>A native image is built ahead of time, so it must be told every foreign call and every callback the program can
 * make. This emitter walks the same descriptors that {@link FfmEmitter} writes into the Java sources (through
 * {@link FfmDescriptors}), which means the metadata cannot fall out of step with the code. It writes two files below
 * {@code META-INF/native-image/<group>/<artifact>/}, where the native image builder looks on the class path:
 *
 * <ul>
 *   <li>{@code reachability-metadata.json}, with one {@code foreign.downcalls} entry for every distinct native function
 *       shape, one {@code foreign.directUpcalls} entry and one {@code foreign.upcalls} entry for every callback, and
 *       the {@code reflection} entries that let {@code Native.upcall(...)} find the callback methods by name;
 *   <li>{@code native-image.properties}, with the builder arguments the backend needs.
 * </ul>
 *
 * <p>Nothing here is hand-maintained. The two layouts that the hand-written {@code Native} class creates (the string
 * view and the callback info) are spelled in {@link FfmLayout}, and
 * {@link FfmDescriptors#checkNativeLayouts(Spec)} makes the build fail if the specification ever changes their shape.
 */
public final class GraalEmitter {

  static final String GROUP = "io.github.stringdotjar";
  static final String ARTIFACT = "jwebgpu-ffm";

  private static final String DIR = "META-INF/native-image/" + GROUP + "/" + ARTIFACT + "/";
  private static final String CALLBACKS = FfmNames.PKG + ".FfmCallbacks";

  private GraalEmitter() {}

  /**
   * Generates the metadata files.
   *
   * @param spec The resolved specification.
   * @return The files, in a stable order. The result is the same every time for the same specification.
   * @throws IllegalStateException If a hand-written layout no longer matches the specification.
   */
  public static List<GeneratedFile> emit(Spec spec) {
    FfmDescriptors.checkNativeLayouts(spec);
    List<GeneratedFile> files = new ArrayList<>();
    files.add(new GeneratedFile(DIR + "reachability-metadata.json", metadata(spec)));
    files.add(new GeneratedFile(DIR + "native-image.properties", properties()));
    return files;
  }

  /**
   * Collects the distinct descriptors of every native function the backend binds.
   *
   * @param spec The resolved specification.
   * @return The descriptors, in the order the functions are declared, without duplicates.
   */
  static Set<FfmDescriptor> downcalls(Spec spec) {
    Set<FfmDescriptor> set = new LinkedHashSet<>();
    for (GpuObject o : spec.objects()) {
      for (GpuFunction f : FfmDescriptors.functions(o)) {
        set.add(FfmDescriptors.of(f));
      }
    }
    for (GpuFunction f : FfmDescriptors.globals(spec)) {
      set.add(FfmDescriptors.of(f));
    }
    for (Struct s : spec.structs()) {
      if (s.freeMembers()) {
        set.add(FfmDescriptors.free(spec, s));
      }
    }
    return set;
  }

  private static String metadata(Spec spec) {
    StringBuilder sb = new StringBuilder(1 << 16);
    sb.append("{\n");
    sb.append("  \"reflection\": [\n");
    sb.append("    {\n");
    sb.append("      \"type\": \"").append(CALLBACKS).append("\",\n");
    sb.append("      \"methods\": [\n");
    for (int i = 0; i < spec.callbacks().size(); i++) {
      Callback cb = spec.callbacks().get(i);
      sb.append("        { \"name\": \"").append(FfmCallbackEmitter.method(cb)).append("\", \"parameterTypes\": [");
      List<FfmLayout> params = FfmDescriptors.of(cb).params();
      for (int j = 0; j < params.size(); j++) {
        sb.append(j == 0 ? "" : ", ").append('"').append(params.get(j).type()).append('"');
      }
      sb.append("] }").append(i + 1 < spec.callbacks().size() ? ",\n" : "\n");
    }
    sb.append("      ]\n");
    sb.append("    }\n");
    sb.append("  ],\n");
    sb.append("  \"foreign\": {\n");
    sb.append("    \"downcalls\": [\n");
    List<FfmDescriptor> down = new ArrayList<>(downcalls(spec));
    for (int i = 0; i < down.size(); i++) {
      signature(sb, "      ", null, null, down.get(i), i + 1 < down.size());
    }
    sb.append("    ],\n");
    sb.append("    \"upcalls\": [\n");
    for (int i = 0; i < spec.callbacks().size(); i++) {
      signature(sb, "      ", null, null, FfmDescriptors.of(spec.callbacks().get(i)), i + 1 < spec.callbacks().size());
    }
    sb.append("    ],\n");
    sb.append("    \"directUpcalls\": [\n");
    for (int i = 0; i < spec.callbacks().size(); i++) {
      Callback cb = spec.callbacks().get(i);
      signature(sb, "      ", CALLBACKS, FfmCallbackEmitter.method(cb), FfmDescriptors.of(cb),
          i + 1 < spec.callbacks().size());
    }
    sb.append("    ]\n");
    sb.append("  }\n");
    sb.append("}\n");
    return sb.toString();
  }

  private static void signature(StringBuilder sb, String indent, String owner, String method, FfmDescriptor d,
      boolean more) {
    sb.append(indent).append("{ ");
    if (owner != null) {
      sb.append("\"class\": \"").append(owner).append("\", \"method\": \"").append(method).append("\", ");
    }
    sb.append("\"returnType\": \"").append(d.ret() == null ? "void" : d.ret().graal()).append("\", ");
    sb.append("\"parameterTypes\": [");
    for (int i = 0; i < d.params().size(); i++) {
      sb.append(i == 0 ? "" : ", ").append('"').append(d.params().get(i).graal()).append('"');
    }
    sb.append("] }").append(more ? ",\n" : "\n");
  }

  private static String properties() {
    return "# Generated by jwebgpu-generator. Do not edit.\n"
        + "#\n"
        + "# The FFM backend creates native method handles and callback stubs, which Java only allows for code that\n"
        + "# has native access. The class path of an application is the unnamed module.\n"
        + "Args = --enable-native-access=ALL-UNNAMED\n";
  }
}
