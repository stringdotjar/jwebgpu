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

import java.util.List;

/**
 * A C struct, with its fields in the exact order and with the exact types the header declares.
 *
 * @param specName The specification name (for example {@code render_pass_color_attachment}), or the C name
 *     for structs the header adds by itself.
 * @param cName The C name (for example {@code WGPURenderPassColorAttachment}).
 * @param kind How the struct takes part in chaining.
 * @param fields The fields in declaration order, including the chain header and expanded array members.
 * @param extendsStructs For an {@link StructKind#EXTENSION}, the C names of the structs it may be chained onto.
 *     Empty for every other kind.
 * @param extensions For an extensible struct, the C names of the extension structs that may be chained onto it.
 *     Empty for every other kind.
 * @param sType For an extension struct, the C name of its {@code WGPUSType} value (for example
 *     {@code WGPUSType_ShaderSourceWGSL}); otherwise {@code null}.
 * @param sTypeValue The numeric value of {@code sType}, or {@code 0} when there is none.
 * @param freeMembers Whether the specification says the implementation allocates members of this struct, in which
 *     case a {@code ...FreeMembers} function exists.
 * @param doc The documentation text from the specification, trimmed. Empty if there is none.
 */
public record Struct(
    @NotNull String specName,
    @NotNull String cName,
    @NotNull StructKind kind,
    @NotNull List<Member> fields,
    @NotNull List<String> extendsStructs,
    @NotNull List<String> extensions,
    @Nullable String sType,
    long sTypeValue,
    boolean freeMembers,
    @NotNull String doc) {

  /** Copies the lists so the record cannot be changed through them. */
  public Struct {
    fields = List.copyOf(fields);
    extendsStructs = List.copyOf(extendsStructs);
    extensions = List.copyOf(extensions);
  }

  /**
   * Finds a field by its C name.
   *
   * @param name The field name, such as {@code nextInChain}.
   * @return The field.
   * @throws IllegalArgumentException If the struct has no such field.
   */
  public Member field(String name) {
    for (int i = 0; i < fields.size(); i++) {
      if (fields.get(i).name().equals(name)) {
        return fields.get(i);
      }
    }
    throw new IllegalArgumentException(cName + " has no field '" + name + "'");
  }

  /**
   * Tells whether the struct starts with a {@code nextInChain} pointer.
   *
   * @return {@code true} for extensible structs and callback info structs.
   */
  public boolean isExtensible() {
    return kind == StructKind.EXTENSIBLE || kind == StructKind.EXTENSIBLE_CALLBACK_ARG
        || kind == StructKind.CALLBACK_INFO;
  }
}
