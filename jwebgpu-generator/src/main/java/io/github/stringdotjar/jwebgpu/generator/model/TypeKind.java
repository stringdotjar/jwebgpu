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

/**
 * The category of a C type used by the WebGPU API.
 *
 * <p>The category tells an emitter how a value is stored and passed. For example, {@link #BOOL} is a 32-bit integer
 * in C but a {@code boolean} in Java, and {@link #BITFLAG} is a 64-bit integer.
 */
public enum TypeKind {

  /** The C {@code void} type. It only appears behind a pointer, or as a function's missing return value. */
  VOID,
  /** The C {@code char} type. It only appears behind a pointer inside a string view. */
  CHAR,
  /** {@code WGPUBool}, a 32-bit unsigned integer where zero is false. */
  BOOL,
  /** {@code uint16_t}. */
  UINT16,
  /** {@code uint32_t}. */
  UINT32,
  /** {@code uint64_t}. */
  UINT64,
  /** {@code size_t}, which is 8 bytes on every platform jwebgpu supports (64-bit only). */
  USIZE,
  /** {@code int16_t}. */
  INT16,
  /** {@code int32_t}. */
  INT32,
  /** {@code float}. */
  FLOAT32,
  /** {@code double}. */
  FLOAT64,
  /** A C enum. Always 32 bits wide because every enum carries a {@code _Force32} member. */
  ENUM,
  /** A bit flag type such as {@code WGPUBufferUsage}, which is a {@code WGPUFlags} and so a 64-bit integer. */
  BITFLAG,
  /** A struct, passed by value unless the type has a pointer. */
  STRUCT,
  /** An opaque object handle such as {@code WGPUDevice}. The handle itself is a pointer. */
  OBJECT,
  /** A C function pointer type such as {@code WGPURequestAdapterCallback}. */
  FUNCTION_POINTER
}
