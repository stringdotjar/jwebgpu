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
import org.jetbrains.annotations.Nullable;

/**
 * A named, typed slot in the C ABI: a struct field, a function parameter or a callback parameter.
 *
 * <p>Members are already expanded the way the C header expands them, so a list of members is the exact list a C
 * compiler sees, in order. The {@link #specName()} and {@link #specType()} keep the original specification
 * wording for emitters that need the unexpanded meaning (for example to tell a nullable string from an
 * output string).
 *
 * @param name The C identifier, in {@code camelCase} (for example {@code colorAttachmentCount}).
 * @param specName The member's name in the specification (for example {@code color_attachments}), or the
 *     same as {@code name} for members the header adds by itself.
 * @param type The resolved C type.
 * @param role The part this member plays in the ABI.
 * @param nullable Whether the specification allows the value to be null or absent.
 * @param peer For {@link MemberRole#ARRAY_COUNT} and {@link MemberRole#ARRAY_DATA}, the {@link #name()} of the other
 *     half of the pair; otherwise {@code null}.
 * @param specType The type exactly as the specification writes it (for example {@code nullable_string} or
 *     {@code array<struct.vertex_attribute>}), or {@code null} for members the header adds by itself.
 * @param init The default value for a struct field, or {@code null} for parameters and for fields without one.
 * @param owned For parameters and return values, {@code TRUE} if the specification says ownership is passed,
 *     {@code FALSE} if it says explicitly that it is not, and {@code null} if it says nothing.
 * @param doc The documentation text from the specification, trimmed. Empty if the specification has none.
 */
public record Member(
    @NotNull String name,
    @NotNull String specName,
    @NotNull CType type,
    @NotNull MemberRole role,
    boolean nullable,
    @Nullable String peer,
    @Nullable String specType,
    @Nullable InitValue init,
    @Nullable Boolean owned,
    @NotNull String doc) {

  /**
   * Tells whether this member is the pointer half of an array.
   *
   * @return {@code true} for {@link MemberRole#ARRAY_DATA}.
   */
  public boolean isArrayData() {
    return role == MemberRole.ARRAY_DATA;
  }
}
