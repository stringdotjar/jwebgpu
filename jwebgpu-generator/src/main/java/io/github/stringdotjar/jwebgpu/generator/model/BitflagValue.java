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

/**
 * One value of a bit flag type.
 *
 * @param specName The entry name in the specification (for example {@code copy_src}).
 * @param cName The C name (for example {@code WGPUBufferUsage_CopySrc}).
 * @param value The 64-bit value. Treat it as unsigned.
 * @param combination Whether the value is the bitwise OR of other entries (for example
 *     {@code WGPUColorWriteMask_All}) instead of a single bit.
 * @param doc The documentation text from the specification, trimmed. Empty if there is none.
 */
public record BitflagValue(
    @NotNull String specName, @NotNull String cName, long value, boolean combination, @NotNull String doc) {
}
