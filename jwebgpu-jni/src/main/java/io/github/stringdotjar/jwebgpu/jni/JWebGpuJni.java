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
package io.github.stringdotjar.jwebgpu.jni;

import io.github.stringdotjar.jwebgpu.JWebGpu;

/**
 * Installer for the Android JNI backend.
 *
 * <p>An Android program calls the installer once at startup. It loads the native libraries (Dawn and the generated C
 * glue) and registers the backend with {@link JWebGpu}. After that, {@link JWebGpu#createInstance()} works, and every
 * object comes from this backend.
 *
 * <p>Think of it as plugging the hotel into the front desk: {@code JWebGpu} is the front desk that every guest talks
 * to, and this call connects it to the Android hotel behind it.
 *
 * <p>Example:
 *
 * <pre>{@code
 * JWebGpuJni.install();
 * GpuInstance instance = JWebGpu.createInstance();
 * }</pre>
 *
 * <p>The backend supports the 64-bit ABIs arm64-v8a and x86_64 only, because the struct layouts it computes assume
 * 8-byte pointers. The same classes also run on a desktop Java virtual machine, which is how the unit tests exercise
 * them; there the libraries are named with the system properties {@code jwebgpu.library.path} (Dawn) and
 * {@code jwebgpu.jni.library.path} (the glue).
 */
public final class JWebGpuJni {

  private static final Object LOCK = new Object();
  private static JniGpuBackend backend;

  private JWebGpuJni() {}

  /**
   * Loads the native libraries and installs the backend.
   *
   * <p>Calling the installer again does nothing.
   *
   * @throws UnsatisfiedLinkError If a native library cannot be loaded.
   * @throws IllegalStateException If a different backend is already installed.
   */
  public static void install() {
    synchronized (LOCK) {
      Native.load();
      if (backend == null) {
        backend = new JniGpuBackend();
      }
      JWebGpu.install(backend);
    }
  }

  /**
   * Tells whether the native libraries are loaded.
   *
   * @return {@code true} after {@link #install()} succeeded.
   */
  public static boolean isInstalled() {
    return Native.isLoaded();
  }

  /**
   * Frees the scratch memory of the calling thread and starts again with the initial size.
   *
   * <p>Every thread that calls the API owns a block of native scratch memory, which grows when one call needs more
   * than the block holds and never shrinks by itself. After a rare, very large upload (for example a huge texture),
   * call this from the same thread to give the memory back. Do not call it from inside a callback.
   */
  public static void trimScratch() {
    Scratch.trim();
  }
}
