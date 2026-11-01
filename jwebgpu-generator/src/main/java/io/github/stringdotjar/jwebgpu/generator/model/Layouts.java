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
package io.github.stringdotjar.jwebgpu.generator.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes struct layouts for a {@link Spec}.
 *
 * <p>Backends that write structs into raw memory (the FFM backend, and the C glue of the JNI backend) must agree
 * with the C compiler about where each field sits. This class applies the standard C rules: each field is aligned to
 * its own alignment, and the struct is padded to a multiple of its largest alignment. All sizes assume a 64-bit
 * platform, where pointers and {@code size_t} are 8 bytes.
 */
public final class Layouts {

  private final Spec spec;
  private final Map<String, StructLayout> cache = new HashMap<>();

  /**
   * Creates a layout calculator.
   *
   * @param spec The specification that owns the structs.
   */
  public Layouts(Spec spec) {
    this.spec = spec;
  }

  /**
   * Returns the layout of a struct.
   *
   * @param cName The C name of the struct, such as {@code WGPUBufferDescriptor}.
   * @return The layout.
   * @throws IllegalArgumentException If the struct does not exist, or contains itself by value.
   */
  public StructLayout of(String cName) {
    StructLayout cached = cache.get(cName);
    if (cached != null) {
      return cached;
    }
    Struct struct = spec.struct(cName);
    List<Long> offsets = new ArrayList<>(struct.fields().size());
    long offset = 0;
    int align = 1;
    for (int i = 0; i < struct.fields().size(); i++) {
      CType type = struct.fields().get(i).type();
      long size;
      int fieldAlign;
      if (type.kind() == TypeKind.STRUCT && !type.isPointer()) {
        if (type.name().equals(cName)) {
          throw new IllegalArgumentException(cName + " contains itself by value");
        }
        StructLayout nested = of(type.name());
        size = nested.size();
        fieldAlign = nested.align();
      } else {
        int scalar = type.scalarSize();
        if (scalar < 0) {
          throw new IllegalArgumentException(cName + "." + struct.fields().get(i).name() + " has no size");
        }
        size = scalar;
        fieldAlign = scalar;
      }
      offset = alignUp(offset, fieldAlign);
      offsets.add(offset);
      offset += size;
      align = Math.max(align, fieldAlign);
    }
    StructLayout layout = new StructLayout(cName, alignUp(offset, align), align, List.copyOf(offsets));
    cache.put(cName, layout);
    return layout;
  }

  private static long alignUp(long value, int align) {
    return (value + align - 1) / align * align;
  }
}
