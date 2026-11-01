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

import org.teavm.jso.JSBody;

/**
 * Collects the results of the checks that the sample pages run, and prints them to the browser console.
 *
 * <p>The headless browser test reads the console, so every line here is part of the test protocol: a failed check
 * prints {@code FAIL}, and {@link #finish()} prints {@code RESULT all PASS} (or the number of failures) followed by the
 * {@code JWEBGPU_DONE} line that ends the run.
 */
final class Report {

  private int failures;
  private int checks;

  @JSBody(params = "text", script = "console.log(text);")
  static native void log(String text);

  void check(String name, boolean ok, String detail) {
    checks++;
    if (ok) {
      log("PASS " + name + (detail.isEmpty() ? "" : " (" + detail + ")"));
    } else {
      failures++;
      log("FAIL " + name + (detail.isEmpty() ? "" : ": " + detail));
    }
  }

  void check(String name, boolean ok) {
    check(name, ok, "");
  }

  void fail(String message) {
    failures++;
    log("ERROR " + message);
  }

  void finish() {
    log(failures == 0 ? "RESULT all PASS" : "RESULT " + failures + " FAILED");
    log(checks + " checks");
    log("JWEBGPU_DONE");
  }
}
