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
 * How a struct takes part in struct chaining.
 *
 * <p>Chaining is WebGPU's way to extend a descriptor without changing its layout. An extensible struct starts with
 * a {@code nextInChain} pointer. An extension struct starts with an embedded {@code WGPUChainedStruct} header whose
 * {@code sType} says which extension it is, and the caller links extensions into the extensible struct's chain.
 */
public enum StructKind {

  /** A struct that starts with {@code nextInChain} and that callers may extend. */
  EXTENSIBLE,
  /** An extensible struct that the implementation fills in and hands to a callback. */
  EXTENSIBLE_CALLBACK_ARG,
  /** A struct that starts with a {@code WGPUChainedStruct chain} header and extends other structs. */
  EXTENSION,
  /** A plain struct that takes no part in chaining. */
  STANDALONE,
  /** A callback info struct that the header generates for each callback. It starts with {@code nextInChain}. */
  CALLBACK_INFO,
  /** A struct that the header defines by hand: {@code WGPUStringView} and {@code WGPUChainedStruct}. */
  BUILTIN
}
