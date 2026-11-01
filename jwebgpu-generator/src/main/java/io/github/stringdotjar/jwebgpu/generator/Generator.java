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

import io.github.stringdotjar.jwebgpu.generator.model.EnumType;
import io.github.stringdotjar.jwebgpu.generator.model.GpuFunction;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;
import io.github.stringdotjar.jwebgpu.generator.model.StructKind;
import io.github.stringdotjar.jwebgpu.generator.parse.JsonException;
import io.github.stringdotjar.jwebgpu.generator.parse.SpecException;
import io.github.stringdotjar.jwebgpu.generator.parse.SpecLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Entry point of the build-time code generator.
 *
 * <p>For now the generator only loads the specification and prints a summary of what it found. The emitters that
 * write the API and the backends will be added on top of the same {@link Spec} model.
 *
 * <p>Usage: {@code Generator [path to webgpu.json]}. Without an argument, {@code spec/webgpu.json} is read.
 */
public final class Generator {

  private static final String DEFAULT_SPEC = "spec/webgpu.json";

  private Generator() {}

  /**
   * Loads the specification and prints a summary to standard output.
   *
   * @param args Optionally, the path of {@code webgpu.json}.
   */
  public static void main(String[] args) {
    Path file = Path.of(args.length > 0 ? args[0] : DEFAULT_SPEC);
    if (!Files.isRegularFile(file)) {
      System.err.println("Specification not found: " + file.toAbsolutePath());
      System.exit(1);
      return;
    }
    try {
      System.out.println(summarize(SpecLoader.load(file)));
    } catch (JsonException | SpecException e) {
      System.err.println("Cannot read " + file + ": " + e.getMessage());
      System.exit(1);
    }
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
