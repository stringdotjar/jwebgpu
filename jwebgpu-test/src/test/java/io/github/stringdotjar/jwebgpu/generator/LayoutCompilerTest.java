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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;
import io.github.stringdotjar.jwebgpu.generator.model.StructLayout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Asks a real C compiler for the size, alignment and field offsets of every struct and compares them with the
 * model's layouts.
 *
 * <p>The test writes a small C program that includes the vendored {@code webgpu.h}, compiles it with the system C
 * compiler and runs it. It also proves that every field name in the model exists in the header, because
 * {@code offsetof} fails to compile otherwise. The test is skipped when no C compiler is installed.
 */
class LayoutCompilerTest {

  private static String findCompiler() {
    for (String cc : new String[] { "cc", "gcc", "clang" }) {
      try {
        Process p = new ProcessBuilder(cc, "--version").redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        if (p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0) {
          return cc;
        }
      } catch (IOException | InterruptedException e) {
        // Try the next compiler.
      }
    }
    return null;
  }

  private static String run(Path dir, String... command) throws IOException, InterruptedException {
    Process p = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
    String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(true, p.waitFor(120, TimeUnit.SECONDS), "timed out: " + String.join(" ", command));
    assertEquals(0, p.exitValue(), String.join(" ", command) + "\n" + out);
    return out;
  }

  @Test
  void modelLayoutsMatchTheCCompiler(@TempDir Path dir) throws IOException, InterruptedException {
    String cc = findCompiler();
    assumeTrue(cc != null, "no C compiler found");
    assumeTrue(System.getProperty("os.arch").contains("64"), "layouts are for 64-bit platforms");

    Spec spec = SpecFiles.load();
    StringBuilder c = new StringBuilder("#include <stdio.h>\n#include <stddef.h>\n#include \"webgpu.h\"\n");
    c.append("int main(void) {\n");
    List<String> expected = new ArrayList<>();
    for (Struct s : spec.structs()) {
      StructLayout l = spec.layouts().of(s.cName());
      StringBuilder format = new StringBuilder(s.cName() + " %zu %zu");
      StringBuilder args = new StringBuilder("sizeof(" + s.cName() + "), _Alignof(" + s.cName() + ")");
      StringBuilder line = new StringBuilder(s.cName() + " " + l.size() + " " + l.align());
      for (int i = 0; i < s.fields().size(); i++) {
        format.append(" %zu");
        args.append(", offsetof(").append(s.cName()).append(", ").append(s.fields().get(i).name()).append(')');
        line.append(' ').append(l.offsets().get(i));
      }
      c.append("  printf(\"").append(format).append("\\n\", ").append(args).append(");\n");
      expected.add(line.toString());
    }
    c.append("  return 0;\n}\n");
    Path source = dir.resolve("layout.c");
    Files.writeString(source, c.toString());

    Path exe = dir.resolve("layout");
    run(dir, cc, "-std=c11", "-I", SpecFiles.dir().toString(), "-o", exe.toString(), source.toString(), "-lm");
    String output = run(dir, exe.toString());
    assertEquals(expected, List.of(output.strip().split("\n")));
  }
}
