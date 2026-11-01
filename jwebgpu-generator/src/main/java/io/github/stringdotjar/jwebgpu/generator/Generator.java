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

import io.github.stringdotjar.jwebgpu.generator.emit.ApiEmitter;
import io.github.stringdotjar.jwebgpu.generator.emit.FfmEmitter;
import io.github.stringdotjar.jwebgpu.generator.emit.GeneratedFile;
import io.github.stringdotjar.jwebgpu.generator.model.EnumType;
import io.github.stringdotjar.jwebgpu.generator.model.GpuFunction;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;
import io.github.stringdotjar.jwebgpu.generator.model.StructKind;
import io.github.stringdotjar.jwebgpu.generator.parse.JsonException;
import io.github.stringdotjar.jwebgpu.generator.parse.SpecException;
import io.github.stringdotjar.jwebgpu.generator.parse.SpecLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Entry point of the build-time code generator.
 *
 * <p>Usage: {@code Generator [--spec path/to/webgpu.json] [--emit api|ffm --out directory]}.
 *
 * <ul>
 *   <li>Without {@code --emit}, the generator loads the specification and prints a summary of what it found.
 *   <li>With {@code --emit api}, it writes the sources of the platform-neutral API into the {@code --out} directory
 *       (see {@link ApiEmitter}). The directory is created if needed, and every file is replaced.
 *   <li>With {@code --emit ffm}, it writes the sources of the desktop backend the same way (see
 *       {@link FfmEmitter}).
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
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--spec" -> spec = value(args, ++i);
        case "--emit" -> emit = value(args, ++i);
        case "--out" -> out = value(args, ++i);
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
      } else if (emit.equals("ffm") && out != null) {
        int count = write(FfmEmitter.emit(model), Path.of(out));
        System.out.println("Wrote " + count + " FFM backend source files to " + out);
      } else {
        System.err.println("Usage: Generator [--spec file] [--emit api|ffm --out directory]");
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
