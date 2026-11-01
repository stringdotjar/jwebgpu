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
 * A callback function type, together with the name of the info struct that carries it.
 *
 * <p>C callbacks cannot capture state, so the header gives each one two opaque {@code userdata} pointers that the
 * caller stores in the callback info struct and the implementation passes back. The info struct is itself part of
 * the model (see {@link Struct} with {@link StructKind#CALLBACK_INFO}).
 *
 * @param specName The specification name (for example {@code request_adapter}).
 * @param cName The C name of the function pointer type (for example {@code WGPURequestAdapterCallback}).
 * @param infoName The C name of the callback info struct (for example {@code WGPURequestAdapterCallbackInfo}).
 * @param style How the callback is delivered.
 * @param params The C parameters in order, ending with the two {@code userdata} pointers.
 * @param doc The documentation text from the specification, trimmed. Empty if there is none.
 */
public record Callback(
    @NotNull String specName,
    @NotNull String cName,
    @NotNull String infoName,
    @NotNull CallbackStyle style,
    @NotNull List<Member> params,
    @NotNull String doc) {

  /** Copies the parameter list so the record cannot be changed through it. */
  public Callback {
    params = List.copyOf(params);
  }
}
