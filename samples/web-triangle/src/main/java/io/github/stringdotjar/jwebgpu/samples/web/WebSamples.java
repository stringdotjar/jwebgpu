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
package io.github.stringdotjar.jwebgpu.samples.web;

/**
 * The entry point of the web samples: the triangle, then the feature checks.
 *
 * <p>The same code translates to JavaScript and to WasmGC: nothing in it knows which one it is. Open
 * {@code js.html} or {@code wasm.html} from the {@code webDist} folder, or let {@code browserTest} do it in a headless
 * browser.
 */
public final class WebSamples {

  private WebSamples() {}

  /**
   * Starts the samples.
   *
   * @param args Not used.
   */
  public static void main(String[] args) {
    Report report = new Report();
    Context.open(report, ctx -> new WebTriangle(ctx).run(() -> new WebFeatures(ctx).run(report::finish)));
  }
}
