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
package io.github.stringdotjar.jwebgpu.generator.emit;

import org.jetbrains.annotations.NotNull;

/**
 * One value type in a foreign function descriptor, written in the two notations that must always agree.
 *
 * <p>The Java source of the FFM backend spells a layout as an expression ({@code ValueLayout.JAVA_LONG}), and the
 * GraalVM native image metadata spells the same layout as a string ({@code jlong}). Both come from this one record,
 * so a descriptor in the metadata can never differ from the descriptor in the code.
 *
 * @param java The Java expression that creates the layout.
 * @param graal The layout in the grammar of GraalVM's {@code reachability-metadata.json}. Fixed-size names such as
 *     {@code jlong} are used, so the text means the same on every platform.
 * @param type The Java type of a method parameter that carries this layout, as a source name that needs no import.
 */
record FfmLayout(@NotNull String java, @NotNull String graal, @NotNull String type) {

  /** A 64-bit integer, which also carries every object handle and pointer. */
  static final FfmLayout LONG = new FfmLayout("ValueLayout.JAVA_LONG", "jlong", "long");

  /** A 32-bit integer. */
  static final FfmLayout INT = new FfmLayout("ValueLayout.JAVA_INT", "jint", "int");

  /** A 16-bit integer. */
  static final FfmLayout SHORT = new FfmLayout("ValueLayout.JAVA_SHORT", "jshort", "short");

  /** A 32-bit float. */
  static final FfmLayout FLOAT = new FfmLayout("ValueLayout.JAVA_FLOAT", "jfloat", "float");

  /** A 64-bit float. */
  static final FfmLayout DOUBLE = new FfmLayout("ValueLayout.JAVA_DOUBLE", "jdouble", "double");

  /** A byte, used inside struct layouts. */
  static final FfmLayout BYTE = new FfmLayout("ValueLayout.JAVA_BYTE", "jbyte", "byte");

  /** A {@code WGPUStringView}, passed by value: a pointer and a length. */
  static final FfmLayout STRING_VIEW =
      new FfmLayout("Native.STRING_VIEW", "struct(jlong, jlong)", "java.lang.foreign.MemorySegment");

  /** A callback info struct, passed by value: the chain pointer, the mode, the callback and two userdata pointers. */
  static final FfmLayout CALLBACK_INFO = new FfmLayout("Native.CALLBACK_INFO",
      "struct(jlong, jint, padding(4), jlong, jlong, jlong)", "java.lang.foreign.MemorySegment");
}
