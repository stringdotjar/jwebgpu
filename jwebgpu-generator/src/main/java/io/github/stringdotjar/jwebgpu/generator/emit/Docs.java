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

import io.github.stringdotjar.jwebgpu.generator.doc.JavadocText;
import io.github.stringdotjar.jwebgpu.generator.model.BitflagType;
import io.github.stringdotjar.jwebgpu.generator.model.Constant;
import io.github.stringdotjar.jwebgpu.generator.model.EnumType;
import io.github.stringdotjar.jwebgpu.generator.model.GpuFunction;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;

/**
 * Converts specification doc strings into Javadoc text, renaming C identifiers into their Java names.
 *
 * <p>The specification documents some members and many enum values with nothing at all. Callers pass a fallback
 * sentence for those cases so that every public method still has a complete comment, as Checkstyle requires.
 */
final class Docs {

  private final Spec spec;

  Docs(Spec spec) {
    this.spec = spec;
  }

  /**
   * Converts a doc string, with a fallback for when there is no documentation.
   *
   * @param doc The doc string from the specification.
   * @param fallback The text to use when {@code doc} is empty. It must already be Javadoc-safe.
   * @return The Javadoc text, never empty if the fallback is not empty.
   */
  String text(String doc, String fallback) {
    String converted = JavadocText.convert(doc, this::rename);
    return converted.isEmpty() ? fallback : converted;
  }

  /**
   * Converts a doc string and returns an empty string when there is no documentation.
   *
   * @param doc The doc string from the specification.
   * @return The Javadoc text, or an empty string.
   */
  String text(String doc) {
    return JavadocText.convert(doc, this::rename);
  }

  /**
   * Turns a C identifier that follows {@code @ref} into the matching Java name.
   *
   * @param id A C identifier such as {@code WGPUTextureFormat}, {@code WGPUBufferUsage_MapRead},
   *     {@code WGPU_WHOLE_SIZE} or {@code wgpuSurfaceConfigure}.
   * @return The Java name, or the identifier unchanged if nothing matches.
   */
  String rename(String id) {
    if (id.startsWith("WGPU_")) {
      for (Constant c : spec.constants()) {
        if (c.cName().equals(id)) {
          return "GpuConstants." + Names.constant(c.specName());
        }
      }
      return id;
    }
    if (id.equals("WGPUStringView")) {
      return "String";
    }
    if (id.startsWith("WGPU")) {
      int underscore = id.indexOf('_');
      if (underscore > 0) {
        return renameValue(id, id.substring(0, underscore));
      }
      return Names.type(id);
    }
    if (id.startsWith("wgpu")) {
      GpuFunction f = spec.findFunction(id);
      if (f != null) {
        String name = Names.method(f.specName());
        return f.owner() == null ? "JWebGpu." + name : Names.type(f.owner()) + "." + name;
      }
    }
    return id;
  }

  private String renameValue(String id, String typeName) {
    for (EnumType e : spec.enums()) {
      if (e.cName().equals(typeName)) {
        for (int i = 0; i < e.values().size(); i++) {
          if (e.values().get(i).cName().equals(id)) {
            return Names.type(typeName) + "." + Names.constant(e.values().get(i).specName());
          }
        }
      }
    }
    for (BitflagType b : spec.bitflags()) {
      if (b.cName().equals(typeName)) {
        for (int i = 0; i < b.values().size(); i++) {
          if (b.values().get(i).cName().equals(id)) {
            return Names.type(typeName) + "." + Names.constant(b.values().get(i).specName());
          }
        }
      }
    }
    return id;
  }
}
