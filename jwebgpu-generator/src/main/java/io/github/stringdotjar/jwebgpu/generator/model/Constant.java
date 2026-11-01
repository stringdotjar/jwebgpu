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

import java.math.BigInteger;

/**
 * A named constant, such as {@code WGPU_DEPTH_SLICE_UNDEFINED}.
 *
 * @param specName The specification name (for example {@code depth_slice_undefined}).
 * @param cName The C macro name (for example {@code WGPU_DEPTH_SLICE_UNDEFINED}).
 * @param kind The C type of the value.
 * @param value The raw 64-bit pattern of the value. For {@link ConstantKind#UINT32} only the low 32 bits are
 *     meaningful. Meaningless for {@link ConstantKind#NAN}.
 * @param max Whether the specification wrote the value as the maximum of its type (for example
 *     {@code uint32_max}) instead of a number.
 * @param doc The documentation text from the specification, trimmed. Empty if there is none.
 */
public record Constant(
    @NotNull String specName,
    @NotNull String cName,
    @NotNull ConstantKind kind,
    long value,
    boolean max,
    @NotNull String doc) {

  /**
   * Returns the value as a non-negative integer.
   *
   * @return The unsigned value, truncated to 32 bits for {@link ConstantKind#UINT32}.
   * @throws IllegalStateException If the constant is {@link ConstantKind#NAN}.
   */
  public BigInteger unsigned() {
    return switch (kind) {
      case UINT32 -> BigInteger.valueOf(value & 0xFFFFFFFFL);
      case UINT64, USIZE -> new BigInteger(Long.toUnsignedString(value));
      case NAN -> throw new IllegalStateException(cName + " is not an integer");
    };
  }

  /**
   * Returns the C expression the header uses for the value.
   *
   * @return For example {@code UINT32_MAX}, {@code SIZE_MAX}, {@code NAN} or, for a plain number, a hexadecimal
   *     literal such as {@code 0x10UL}.
   */
  public String cValue() {
    if (kind == ConstantKind.NAN) {
      return "NAN";
    }
    if (max) {
      return switch (kind) {
        case UINT32 -> "UINT32_MAX";
        case UINT64 -> "UINT64_MAX";
        default -> "SIZE_MAX";
      };
    }
    BigInteger v = unsigned();
    return "0x" + v.toString(16) + (v.bitLength() <= 32 ? "UL" : "ULL");
  }
}
