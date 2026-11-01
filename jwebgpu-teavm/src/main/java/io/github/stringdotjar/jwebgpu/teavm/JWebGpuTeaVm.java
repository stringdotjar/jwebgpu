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

import io.github.stringdotjar.jwebgpu.JWebGpu;

/**
 * Installer for the web backend.
 *
 * <p>A web program calls the installer once at startup to register this backend with the API. The backend uses the
 * browser's own WebGPU implementation ({@code navigator.gpu}), so there is nothing to download or load.
 *
 * <p>Example:
 *
 * <pre>{@code
 * public static void main(String[] args) {
 *   JWebGpuTeaVm.install();
 *   GpuInstance instance = JWebGpu.createInstance();
 *   GpuRequestAdapterCallback onAdapter = (status, adapter, message) -> {
 *     // keep the adapter, then request a device from it
 *   };
 *   instance.requestAdapter(null, GpuCallbackMode.ALLOW_SPONTANEOUS, onAdapter);
 * }
 * }</pre>
 *
 * <p>The same code translates to JavaScript and to WebAssembly (WasmGC); the backend contains nothing that is specific
 * to one of them.
 *
 * <p>The web has no event loop to pump, so {@code processEvents} does nothing: callbacks fire when the browser
 * resolves the underlying promise, which happens after the current task yields. {@code waitAny} cannot block the
 * browser either, so it only polls (timeout 0).
 */
public final class JWebGpuTeaVm {

  private static TeaVmGpuBackend backend;

  private JWebGpuTeaVm() {}

  @JSBody(script = "return typeof navigator !== 'undefined' && navigator !== null && !!navigator.gpu;")
  private static native boolean hasWebGpu();

  /**
   * Tells whether the browser offers WebGPU.
   *
   * @return {@code true} if {@code navigator.gpu} exists. It also needs a secure context (HTTPS or localhost).
   */
  public static boolean isSupported() {
    return hasWebGpu();
  }

  /**
   * Registers the web backend with the API. Calling it again does nothing.
   *
   * @throws IllegalStateException If the browser does not offer WebGPU, or if another backend is already installed.
   */
  public static void install() {
    if (!hasWebGpu()) {
      throw new IllegalStateException("WebGPU is not available in this browser: navigator.gpu does not exist. Use a "
          + "recent browser, and serve the page over HTTPS or from localhost.");
    }
    if (backend == null) {
      backend = new TeaVmGpuBackend();
    }
    JWebGpu.install(backend);
  }
}
