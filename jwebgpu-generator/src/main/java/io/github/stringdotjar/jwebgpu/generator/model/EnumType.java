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

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A C enum.
 *
 * <p>The header numbers entries the way the specification file says: an entry without an explicit value gets the
 * index of its position in the list, counted from zero. The specification uses {@code null} placeholders in the
 * list to keep zero free for "not set", so the first real entry of many enums is {@code 1}. Every value is then
 * combined with the file's {@code enum_prefix} (zero for the core specification) shifted into the high 16 bits.
 * The header also adds a {@code _Force32 = 0x7FFFFFFF} member to every enum to force a 32-bit representation; that
 * member is not listed in {@link #values()} but {@link #FORCE32} describes it.
 *
 * @param specName The specification name (for example {@code texture_format}).
 * @param cName The C name (for example {@code WGPUTextureFormat}).
 * @param values The values in declaration order, without the {@code _Force32} member.
 * @param doc The documentation text from the specification, trimmed. Empty if there is none.
 */
public record EnumType(
    @NotNull String specName, @NotNull String cName, @NotNull List<EnumValue> values, @NotNull String doc) {

  /** Copies the value list so the record cannot be changed through it. */
  public EnumType {
    values = List.copyOf(values);
  }

  /** The value of the {@code _Force32} member that every C enum ends with. */
  public static final int FORCE32 = 0x7FFFFFFF;

  /**
   * Finds a value by its specification name.
   *
   * @param specName The entry name, such as {@code undefined}.
   * @return The value, or {@code null} if the enum has no such entry.
   */
  public EnumValue bySpecName(String specName) {
    for (int i = 0; i < values.size(); i++) {
      if (values.get(i).specName().equals(specName)) {
        return values.get(i);
      }
    }
    return null;
  }
}
