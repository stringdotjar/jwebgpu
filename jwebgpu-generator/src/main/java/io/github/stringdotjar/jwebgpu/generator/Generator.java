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
package io.github.stringdotjar.jwebgpu.generator;

import io.github.stringdotjar.jwebgpu.generator.emit.ApiChecksEmitter;
import io.github.stringdotjar.jwebgpu.generator.emit.ApiEmitter;
import io.github.stringdotjar.jwebgpu.generator.emit.FfmEmitter;
import io.github.stringdotjar.jwebgpu.generator.emit.GeneratedFile;
import io.github.stringdotjar.jwebgpu.generator.emit.GraalEmitter;
import io.github.stringdotjar.jwebgpu.generator.emit.JniCEmitter;
import io.github.stringdotjar.jwebgpu.generator.emit.JniJavaEmitter;
import io.github.stringdotjar.jwebgpu.generator.emit.TeaVmEmitter;
import io.github.stringdotjar.jwebgpu.generator.model.EnumType;
import io.github.stringdotjar.jwebgpu.generator.model.GpuFunction;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;
import io.github.stringdotjar.jwebgpu.generator.model.StructKind;
import io.github.stringdotjar.jwebgpu.generator.parse.JsonException;
import io.github.stringdotjar.jwebgpu.generator.parse.SpecException;
import io.github.stringdotjar.jwebgpu.generator.parse.SpecLoader;
import io.github.stringdotjar.jwebgpu.generator.parse.WebIdlParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Entry point of the build-time code generator.
 *
 * <p>Usage: {@code Generator [--spec path/to/webgpu.json] [--idl path/to/webgpu.idl]
 * [--emit api|ffm|graal|teavm|api-checks|jni-java|jni-java-android|jni-c --out directory]}.
 *
 * <ul>
 *   <li>Without {@code --emit}, the generator loads the specification and prints a summary of what it found.
 *   <li>With {@code --emit api}, it writes the sources of the platform-neutral API into the {@code --out} directory
 *       (see {@link ApiEmitter}). The directory is created if needed, and every file is replaced.
 *   <li>With {@code --emit api-checks}, it writes a plain Java class that checks the generated API against the
 *       specification, for the test source set of a module (see {@link ApiChecksEmitter}).
 *   <li>With {@code --emit ffm}, it writes the sources of the desktop backend the same way (see
 *       {@link FfmEmitter}).
 *   <li>With {@code --emit graal}, it writes the GraalVM native image metadata for the FFM backend (see
 *       {@link GraalEmitter}). These are resource files, not Java sources.
 *   <li>With {@code --emit jni-java}, it writes the Java sources of the JNI backend, and {@code jni-java-android} does
 *       the same with ART's {@code @FastNative} on the cheap native methods (see {@link JniJavaEmitter}).
 *   <li>With {@code --emit jni-c}, it writes the C glue of the JNI backend (see {@link JniCEmitter}).
 *   <li>With {@code --emit teavm}, it writes the sources of the web backend. This also reads the browser's WebIDL
 *       from {@code --idl} (default: {@code webgpu.idl} next to the specification), see {@link TeaVmEmitter}.
 * </ul>
 *
 * <p>Without {@code --spec}, {@code spec/webgpu.json} is read relative to the working directory. A single plain
 * argument is also taken as the path of the specification.
 */
public final class Generator {

  private static final String DEFAULT_SPEC = "spec/webgpu.json";

  private Generator() {}

  /**
   * Runs the generator.
   *
   * @param args The command line arguments, see the class description.
   */
  public static void main(String[] args) {
    String spec = DEFAULT_SPEC;
    String emit = null;
    String out = null;
    String idl = null;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--spec" -> spec = value(args, ++i);
        case "--emit" -> emit = value(args, ++i);
        case "--out" -> out = value(args, ++i);
        case "--idl" -> idl = value(args, ++i);
        default -> spec = args[i];
      }
    }
    Path file = Path.of(spec);
    if (!Files.isRegularFile(file)) {
      System.err.println("Specification not found: " + file.toAbsolutePath());
      System.exit(1);
      return;
    }
    try {
      Spec model = SpecLoader.load(file);
      if (emit == null) {
        System.out.println(summarize(model));
      } else if (emit.equals("api") && out != null) {
        int count = write(ApiEmitter.emit(model), Path.of(out));
        System.out.println("Wrote " + count + " API source files to " + out);
      } else if (emit.equals("api-checks") && out != null) {
        int count = write(ApiChecksEmitter.emit(model), Path.of(out));
        System.out.println("Wrote " + count + " API check source file to " + out);
      } else if (emit.equals("ffm") && out != null) {
        int count = write(FfmEmitter.emit(model), Path.of(out));
        System.out.println("Wrote " + count + " FFM backend source files to " + out);
      } else if (emit.equals("graal") && out != null) {
        int count = write(GraalEmitter.emit(model), Path.of(out));
        System.out.println("Wrote " + count + " native image metadata files to " + out);
      } else if ((emit.equals("jni-java") || emit.equals("jni-java-android")) && out != null) {
        int count = write(JniJavaEmitter.emit(model, emit.equals("jni-java-android")), Path.of(out));
        System.out.println("Wrote " + count + " JNI backend source files to " + out);
      } else if (emit.equals("jni-c") && out != null) {
        int count = write(JniCEmitter.emit(model), Path.of(out));
        System.out.println("Wrote " + count + " JNI glue source file to " + out);
      } else if (emit.equals("teavm") && out != null) {
        Path idlFile = idl != null ? Path.of(idl) : file.resolveSibling("webgpu.idl");
        int count = write(TeaVmEmitter.emit(model, WebIdlParser.load(idlFile)), Path.of(out));
        System.out.println("Wrote " + count + " web backend source files to " + out);
      } else {
        System.err.println("Usage: Generator [--spec file] [--idl file] [--emit api|ffm|graal|teavm|api-checks|jni-java"
            + "|jni-java-android|jni-c --out directory]");
        System.exit(2);
      }
    } catch (JsonException | SpecException e) {
      System.err.println("Cannot read " + file + ": " + e.getMessage());
      System.exit(1);
    } catch (IOException e) {
      System.err.println("Cannot write the generated sources: " + e.getMessage());
      System.exit(1);
    }
  }

  /**
   * Writes generated files below a directory.
   *
   * @param files The files, with paths relative to the directory.
   * @param dir The output directory. Missing parent directories are created.
   * @return The number of files written.
   * @throws IOException If a file cannot be written.
   */
  public static int write(List<GeneratedFile> files, Path dir) throws IOException {
    for (GeneratedFile f : files) {
      Path target = dir.resolve(f.path());
      Files.createDirectories(target.getParent());
      Files.writeString(target, f.source(), StandardCharsets.UTF_8);
    }
    return files.size();
  }

  private static String value(String[] args, int index) {
    if (index >= args.length) {
      System.err.println("Missing value for " + args[index - 1]);
      System.exit(2);
    }
    return args[index];
  }

  /**
   * Describes a specification in a few lines of text.
   *
   * @param spec The resolved specification.
   * @return A summary with the counts of every kind of declaration.
   */
  public static String summarize(Spec spec) {
    int methods = 0;
    int async = 0;
    for (GpuFunction f : spec.functions()) {
      if (f.isMethod()) {
        methods++;
      }
      if (f.isAsync()) {
        async++;
      }
    }
    int[] byKind = new int[StructKind.values().length];
    for (Struct s : spec.structs()) {
      byKind[s.kind().ordinal()]++;
    }
    int enumValues = 0;
    for (EnumType e : spec.enums()) {
      enumValues += e.values().size();
    }
    StringBuilder sb = new StringBuilder();
    sb.append("Specification: ").append(spec.name()).append('\n');
    sb.append("  functions:  ").append(spec.functions().size());
    sb.append(" (").append(spec.functions().size() - methods).append(" global, ").append(methods).append(" methods, ");
    sb.append(async).append(" async)\n");
    sb.append("  objects:    ").append(spec.objects().size()).append('\n');
    sb.append("  structs:    ").append(spec.structs().size()).append(" (");
    StructKind[] kinds = StructKind.values();
    for (int i = 0; i < kinds.length; i++) {
      sb.append(i == 0 ? "" : ", ").append(byKind[i]).append(' ').append(kinds[i].name().toLowerCase(Locale.ROOT));
    }
    sb.append(")\n");
    sb.append("  enums:      ").append(spec.enums().size()).append(" (").append(enumValues).append(" values)\n");
    sb.append("  bitflags:   ").append(spec.bitflags().size()).append('\n');
    sb.append("  callbacks:  ").append(spec.callbacks().size()).append('\n');
    sb.append("  constants:  ").append(spec.constants().size());
    return sb.toString();
  }
}
