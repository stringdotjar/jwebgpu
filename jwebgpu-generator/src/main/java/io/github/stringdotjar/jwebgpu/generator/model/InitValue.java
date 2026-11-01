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
 * The default value of a struct member, exactly as the header's {@code INIT} macro writes it.
 *
 * <p>The header gives every struct a {@code WGPU_..._INIT} macro that fills in the specification's defaults.
 * Generated Java descriptors use the same defaults so that a new descriptor behaves like a C struct that was
 * created with that macro.
 *
 * @param kind What kind of default this is.
 * @param text The C expression, as it appears in the macro (for example {@code WGPU_DEPTH_SLICE_UNDEFINED}).
 * @param ref The C name of the enum value, bit flag value, constant or struct the expression refers to, or
 *     {@code null} when the kind is not a reference.
 */
public record InitValue(@NotNull Kind kind, @NotNull String text, @Nullable String ref) {

  /** The kinds of default values. */
  public enum Kind {

    /** An integer literal such as {@code 0} or {@code 50000000}. */
    NUMBER,
    /** A floating point literal such as {@code 0.f} or {@code 32.f}. */
    FLOAT,
    /** {@code WGPU_TRUE} or {@code WGPU_FALSE}. */
    BOOL,
    /** A named enum value such as {@code WGPULoadOp_Undefined}. */
    ENUM,
    /** The enum has no {@code Undefined} entry, so the default is a zero cast to the enum. */
    ENUM_ZERO,
    /** A named bit flag value such as {@code WGPUTextureUsage_None}. */
    BITFLAG,
    /** A named constant such as {@code WGPU_DEPTH_SLICE_UNDEFINED}. */
    CONSTANT,
    /** A null pointer, null object handle or null array. */
    NULL_POINTER,
    /** The {@code INIT} macro of a nested struct. */
    STRUCT_INIT,
    /** A nested struct that is initialized to all zero bytes. */
    STRUCT_ZERO,
    /** {@code WGPU_STRING_VIEW_INIT}, which is a null string view. */
    STRING_VIEW_INIT,
    /** The {@code INIT} macro of a callback info struct. */
    CALLBACK_INFO_INIT
  }
}
