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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Compares generated text with the golden files under {@code src/test/resources/golden}.
 *
 * <p>A golden file is a snapshot of what the emitter should produce. When the emitter changes on purpose, run the
 * tests once with {@code -Pjwebgpu.updateGolden=true} to rewrite the snapshots, then review the diff with git.
 */
final class Golden {

  private Golden() {}

  /**
   * Checks generated text against a golden file.
   *
   * @param name The golden file name, such as {@code GpuQueue.java.txt}.
   * @param actual The generated text.
   */
  static void check(String name, String actual) {
    Path file = dir().resolve(name);
    try {
      if (Boolean.getBoolean("jwebgpu.updateGolden")) {
        Files.createDirectories(file.getParent());
        Files.writeString(file, actual, StandardCharsets.UTF_8);
        return;
      }
      if (!Files.isRegularFile(file)) {
        fail("Missing golden file " + file + ". Run with -Pjwebgpu.updateGolden=true to create it.");
      }
      assertEquals(Files.readString(file, StandardCharsets.UTF_8), actual, "golden file " + name);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Returns the first lines of a text, each line ending with a line break.
   *
   * @param text The text.
   * @param lines How many lines to keep.
   * @return The head of the text.
   */
  static String head(String text, int lines) {
    int end = 0;
    for (int i = 0; i < lines; i++) {
      int next = text.indexOf('\n', end);
      if (next < 0) {
        return text;
      }
      end = next + 1;
    }
    return text.substring(0, end);
  }

  private static Path dir() {
    String prop = System.getProperty("jwebgpu.goldenDir");
    return Path.of(prop != null ? prop : "src/test/resources/golden").toAbsolutePath();
  }
}
