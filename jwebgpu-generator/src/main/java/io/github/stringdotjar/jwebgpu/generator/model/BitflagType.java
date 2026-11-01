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
 * A bit flag type such as {@code WGPUBufferUsage}.
 *
 * <p>In C a bit flag type is a typedef of {@code WGPUFlags}, which is a {@code uint64_t}. A bit flag field is
 * therefore 8 bytes wide, even though few flags use more than 32 bits. Entries without an explicit value get
 * {@code 1 << (index - 1)}, so the entry at index zero (always {@code none}) is zero and the next ones are 1, 2, 4,
 * and so on.
 *
 * @param specName The specification name (for example {@code buffer_usage}).
 * @param cName The C name (for example {@code WGPUBufferUsage}).
 * @param values The values in declaration order.
 * @param doc The documentation text from the specification, trimmed. Empty if there is none.
 */
public record BitflagType(
    @NotNull String specName, @NotNull String cName, @NotNull List<BitflagValue> values, @NotNull String doc) {

  /** Copies the value list so the record cannot be changed through it. */
  public BitflagType {
    values = List.copyOf(values);
  }

  /** The width of every bit flag value in bytes. */
  public static final int SIZE = 8;
}
