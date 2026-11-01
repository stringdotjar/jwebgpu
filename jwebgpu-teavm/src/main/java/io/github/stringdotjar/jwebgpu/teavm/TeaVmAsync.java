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
package io.github.stringdotjar.jwebgpu.teavm;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;

/**
 * Turns a browser promise into a call of a Java callback.
 *
 * <p>The native API reports asynchronous results through callbacks, while the browser returns promises. A
 * {@link Request} attaches to a promise, registers a future so that {@code waitAny} can poll it, and calls
 * {@link Request#succeeded(JSObject)} or {@link Request#failed(JSObject)} from the browser's event loop. This is why
 * the web backend needs no {@code processEvents}: results arrive on their own once the program yields to the browser.
 *
 * <p>Each request allocates a few small objects. That is fine for what the API uses promises for (startup, pipeline
 * creation, buffer mapping), and none of it happens inside a frame's draw calls.
 */
final class TeaVmAsync {

  private TeaVmAsync() {}

  @JSBody(params = { "p", "ok", "fail" }, script = "p.then(function (v) { ok(v); }, function (e) { fail(e); });")
  private static native void attach(JSObject p, Handler ok, Handler fail);

  @JSBody(params = "v", script = "return v === null || v === undefined;")
  static native boolean isNothing(JSObject v);

  @JSBody(params = "e", script = "return e && e.message !== undefined ? String(e.message) : String(e);")
  static native String message(JSObject e);

  @JSBody(params = "e", script = "return e && e.name !== undefined ? String(e.name) : '';")
  static native String name(JSObject e);

  @JSBody(params = "e", script = "return e && e.reason !== undefined ? String(e.reason) : '';")
  static native String reason(JSObject e);

  @JSBody(params = "v", script = "return v;")
  static native <T extends JSObject> T cast(JSObject v);

  /** A function that the browser calls with one value. */
  @JSFunctor
  interface Handler extends JSObject {

    void handle(JSObject value);
  }

  /** An operation that finishes later. */
  abstract static class Request {

    private long id;
    private final Handler ok = v -> {
      TeaVmFutures.complete(id);
      succeeded(v);
    };
    private final Handler fail = e -> {
      TeaVmFutures.complete(id);
      failed(e);
    };

    abstract void succeeded(JSObject value);

    abstract void failed(JSObject reason);

    /**
     * Starts listening to a promise.
     *
     * @param promise The promise.
     * @return The id of the future that tracks the operation.
     */
    final long start(JSPromise<JSObject> promise) {
      id = TeaVmFutures.begin();
      attach(promise, ok, fail);
      return id;
    }
  }
}
