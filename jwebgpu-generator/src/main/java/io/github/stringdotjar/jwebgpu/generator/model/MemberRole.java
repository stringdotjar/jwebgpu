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
 * The part a {@link Member} plays in the C ABI.
 *
 * <p>The specification describes an array as one member, but C needs two: a count and a pointer. The resolver
 * expands such members, and the role records which half each expanded member is. Emitters use the roles to
 * collapse the pair back into a single array in the generated Java API.
 */
public enum MemberRole {

  /** A plain value, with nothing special about it. */
  VALUE,
  /** The receiver of a method: the object (or struct) handle that is the first parameter of the C function. */
  SELF,
  /** The {@code size_t} element count that comes before an array pointer. Its {@link Member#peer()} is the pointer. */
  ARRAY_COUNT,
  /** The pointer to the first array element. Its {@link Member#peer()} is the count. */
  ARRAY_DATA,
  /** The {@code nextInChain} pointer at the start of an extensible struct. */
  CHAIN_NEXT,
  /** The embedded {@code WGPUChainedStruct chain} header at the start of an extension struct. */
  CHAIN_HEADER,
  /** A callback info struct, passed by value as the last parameter of an async function or stored in a struct. */
  CALLBACK_INFO,
  /** One of the two opaque {@code userdata} pointers of a callback or callback info struct. */
  USERDATA
}
