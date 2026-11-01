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

import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl;
import io.github.stringdotjar.jwebgpu.generator.parse.WebIdlParser;
import io.github.stringdotjar.jwebgpu.generator.parse.SpecLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Locates the vendored specification files for the tests.
 *
 * <p>The build passes the location of the {@code spec/} directory in the {@code jwebgpu.specDir} system property.
 */
public final class SpecFiles {

  private static Spec cached;
  private static WebIdl cachedIdl;

  private SpecFiles() {}

  /**
   * Returns the {@code spec/} directory of the repository.
   *
   * @return The directory.
   */
  public static Path dir() {
    String prop = System.getProperty("jwebgpu.specDir");
    Path dir = Path.of(prop != null ? prop : "../spec").toAbsolutePath().normalize();
    if (!Files.isDirectory(dir)) {
      throw new IllegalStateException("spec directory not found: " + dir);
    }
    return dir;
  }

  /**
   * Loads the vendored {@code webgpu.json}. The result is cached because resolving takes a moment.
   *
   * @return The resolved specification.
   */
  public static synchronized Spec load() {
    if (cached == null) {
      cached = SpecLoader.load(dir().resolve("webgpu.json"));
    }
    return cached;
  }

  /**
   * Parses the vendored {@code webgpu.idl}. The result is cached.
   *
   * @return The browser API model.
   */
  public static synchronized WebIdl loadIdl() {
    if (cachedIdl == null) {
      cachedIdl = WebIdlParser.load(dir().resolve("webgpu.idl"));
    }
    return cachedIdl;
  }

  /**
   * Returns the vendored upstream C header.
   *
   * @return The path of {@code spec/webgpu.h}.
   */
  public static Path upstreamHeader() {
    return dir().resolve("webgpu.h");
  }

  /**
   * Returns Dawn's own generated header, if the developer has it on disk.
   *
   * <p>Dawn's header is not vendored (it is not needed to build). Point the {@code jwebgpu.dawnHeader} system
   * property at a copy to enable the tests that compare it with the model.
   *
   * @return The path, or {@code null} if no readable file is configured.
   */
  public static Path dawnHeader() {
    String prop = System.getProperty("jwebgpu.dawnHeader");
    if (prop == null || prop.isBlank()) {
      return null;
    }
    Path path = Path.of(prop);
    return Files.isRegularFile(path) ? path : null;
  }
}
