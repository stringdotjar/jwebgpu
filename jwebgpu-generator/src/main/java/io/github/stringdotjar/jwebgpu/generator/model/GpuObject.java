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
 * A WebGPU object type such as {@code WGPUDevice}, with its methods.
 *
 * <p>In C an object is an opaque pointer. The methods include the {@code AddRef} and {@code Release} methods that
 * the header generator adds to every object, placed after the methods the specification lists.
 *
 * @param specName The specification name (for example {@code render_pass_encoder}).
 * @param cName The C name of the handle type (for example {@code WGPURenderPassEncoder}).
 * @param methods The methods, sorted by name, followed by {@code AddRef} and {@code Release}.
 * @param doc The documentation text from the specification, trimmed. Empty if there is none.
 */
public record GpuObject(
    @NotNull String specName, @NotNull String cName, @NotNull List<GpuFunction> methods, @NotNull String doc) {

  /** Copies the method list so the record cannot be changed through it. */
  public GpuObject {
    methods = List.copyOf(methods);
  }
}
