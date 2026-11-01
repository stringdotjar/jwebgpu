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
 * A C function exported by the WebGPU library.
 *
 * <p>The parameters are the exact C parameters. A method's receiver is its first parameter (role
 * {@link MemberRole#SELF}). An array in the specification is two parameters, and an asynchronous function ends
 * with its callback info struct, passed by value. Such a function returns a {@code WGPUFuture} struct by value
 * instead of the return type the specification lists.
 *
 * @param specName The specification name of the function or method (for example {@code request_adapter}).
 * @param cName The C name (for example {@code wgpuInstanceRequestAdapter}).
 * @param owner The C name of the object or struct the function is a method of (for example
 *     {@code WGPUInstance}), or {@code null} for a global function.
 * @param kind Which rule produced the function.
 * @param params The C parameters in order.
 * @param returns The C return type, or {@code null} for {@code void}.
 * @param returnNullable Whether the specification says the return value may be null.
 * @param returnOwned For a return value, {@code TRUE} if ownership passes to the caller, {@code FALSE} if the
 *     specification says it does not, and {@code null} if it says nothing.
 * @param callback The C name of the callback type of an asynchronous function, or {@code null}.
 * @param doc The documentation text from the specification, trimmed. Empty if there is none.
 * @param returnDoc The documentation of the return value, trimmed. Empty if there is none.
 */
public record GpuFunction(
    @NotNull String specName,
    @NotNull String cName,
    @Nullable String owner,
    @NotNull FunctionKind kind,
    @NotNull List<Member> params,
    @Nullable CType returns,
    boolean returnNullable,
    @Nullable Boolean returnOwned,
    @Nullable String callback,
    @NotNull String doc,
    @NotNull String returnDoc) {

  /** Copies the parameter list so the record cannot be changed through it. */
  public GpuFunction {
    params = List.copyOf(params);
  }

  /**
   * Tells whether the function starts an asynchronous operation.
   *
   * @return {@code true} if the function takes a callback info struct and returns a {@code WGPUFuture}.
   */
  public boolean isAsync() {
    return callback != null;
  }

  /**
   * Tells whether the function is a method of an object or struct.
   *
   * @return {@code true} unless the function is global.
   */
  public boolean isMethod() {
    return owner != null;
  }
}
