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
package io.github.stringdotjar.jwebgpu.generator.parse;

import io.github.stringdotjar.jwebgpu.generator.model.Spec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads the vendored {@code webgpu.json} into a {@link Spec}.
 *
 * <p>Example:
 *
 * <pre>{@code
 * Spec spec = SpecLoader.load(Path.of("spec/webgpu.json"));
 * System.out.println(spec.structs().size() + " structs");
 * }</pre>
 */
public final class SpecLoader {

  private SpecLoader() {}

  /**
   * Reads and resolves a specification file.
   *
   * @param file The path of {@code webgpu.json}.
   * @return The resolved specification.
   * @throws UncheckedIOException If the file cannot be read.
   * @throws JsonException If the file is not valid JSON.
   * @throws SpecException If the specification cannot be resolved.
   */
  public static Spec load(Path file) {
    try {
      return loadString(Files.readString(file, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + file, e);
    }
  }

  /**
   * Parses and resolves specification text.
   *
   * @param json The contents of a {@code webgpu.json} file.
   * @return The resolved specification.
   * @throws JsonException If the text is not valid JSON.
   * @throws SpecException If the specification cannot be resolved.
   */
  public static Spec loadString(String json) {
    return SpecResolver.resolve(Json.parse(json).asObj());
  }
}
