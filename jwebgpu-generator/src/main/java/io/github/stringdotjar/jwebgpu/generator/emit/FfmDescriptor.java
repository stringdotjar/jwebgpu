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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of one native function or callback: what it returns and what it takes.
 *
 * <p>The FFM emitters print it as a {@code FunctionDescriptor} expression, and the native image emitter prints it as a
 * metadata entry, both from the same record (see {@link FfmLayout}).
 *
 * @param ret The return layout, or {@code null} for a function that returns nothing.
 * @param params The parameter layouts in order.
 */
record FfmDescriptor(@Nullable FfmLayout ret, @NotNull List<FfmLayout> params) {

  /** Copies the list so the record cannot be changed through it. */
  FfmDescriptor {
    params = List.copyOf(params);
  }

  /**
   * Returns the Java expression that creates the {@code FunctionDescriptor}.
   *
   * @return For example {@code FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)}.
   */
  @NotNull
  String java() {
    List<String> layouts = new ArrayList<>();
    for (FfmLayout l : params) {
      layouts.add(l.java());
    }
    if (ret == null) {
      return "FunctionDescriptor.ofVoid(" + String.join(", ", layouts) + ")";
    }
    return layouts.isEmpty()
        ? "FunctionDescriptor.of(" + ret.java() + ")"
        : "FunctionDescriptor.of(" + ret.java() + ", " + String.join(", ", layouts) + ")";
  }
}
