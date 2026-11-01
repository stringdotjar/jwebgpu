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

import io.github.stringdotjar.jwebgpu.generator.model.GpuObject;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl;
import io.github.stringdotjar.jwebgpu.generator.web.WebMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates the web backend ({@code jwebgpu-teavm}) from the C model and the browser's WebIDL.
 *
 * <p>The emitter is a pure function: the two models go in, and a list of source files comes out. They go into the
 * package {@code io.github.stringdotjar.jwebgpu.teavm} and complete the hand-written part of the module (the installer,
 * the promise bridge, the data upload and the output structs):
 *
 * <ul>
 *   <li>one {@code Js...} interface per interface of the browser's IDL, typed with TeaVM's JavaScript Objects,
 *   <li>one {@code TeaVmGpu...} class per API object, plus {@code TeaVmGpuBackend} for the global functions,
 *   <li>one {@code TeaVmGpu...Struct} marshaller per descriptor that the browser takes as a dictionary,
 *   <li>{@code TeaVmEnums} with the interned strings of the browser's enums, and {@code TeaVmProps} with the property
 *       setters of the marshallers.
 * </ul>
 *
 * <p>Example:
 *
 * <pre>{@code
 * Spec spec = SpecLoader.load(Path.of("spec/webgpu.json"));
 * WebIdl idl = WebIdlParser.load(Path.of("spec/webgpu.idl"));
 * for (GeneratedFile file : TeaVmEmitter.emit(spec, idl)) {
 *   Files.writeString(out.resolve(file.path()), file.source());
 * }
 * }</pre>
 */
public final class TeaVmEmitter {

  private TeaVmEmitter() {}

  /**
   * Generates every source file of the backend.
   *
   * @param spec The resolved C specification.
   * @param idl The parsed browser IDL.
   * @return The files, in a stable order. The result is the same every time for the same specifications.
   * @throws IllegalStateException If the two specifications do not agree (see {@link WebMap#problems()}) or two
   *     generated types would get the same name.
   */
  public static List<GeneratedFile> emit(Spec spec, WebIdl idl) {
    WebMap web = WebMap.of(spec, idl);
    if (!web.problems().isEmpty()) {
      throw new IllegalStateException("The C and browser specifications do not agree: " + web.problems());
    }
    Types types = new Types(spec);
    TeaVmStructEmitter structs = new TeaVmStructEmitter(spec, idl, web);
    TeaVmObjectEmitter objects = new TeaVmObjectEmitter(spec, idl, web, types);

    List<GeneratedFile> files = new ArrayList<>(new TeaVmJsoEmitter(idl).emitAll());
    for (GpuObject o : spec.objects()) {
      files.add(objects.emit(o));
    }
    files.add(objects.emitBackend());
    files.addAll(structs.emitAll());
    files.add(structs.emitProps());
    files.add(new TeaVmEnumEmitter(web).emit());

    Set<String> paths = new HashSet<>();
    for (GeneratedFile f : files) {
      if (!paths.add(f.path())) {
        throw new IllegalStateException("two generated files have the path " + f.path());
      }
    }
    return files;
  }
}
