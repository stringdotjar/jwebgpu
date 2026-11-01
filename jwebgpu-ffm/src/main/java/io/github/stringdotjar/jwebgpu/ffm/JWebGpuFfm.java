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
package io.github.stringdotjar.jwebgpu.ffm;

import io.github.stringdotjar.jwebgpu.JWebGpu;

import java.nio.file.Path;

/**
 * Installer for the desktop FFM backend.
 *
 * <p>A desktop program calls the installer once at startup. It loads the native WebGPU library (Dawn) and registers
 * the backend with {@link JWebGpu}. After that, {@link JWebGpu#createInstance()} works, and every object comes from
 * this backend.
 *
 * <p>Think of it as plugging the hotel into the front desk: {@code JWebGpu} is the front desk that every guest talks
 * to, and this call connects it to the desktop hotel behind it.
 *
 * <p>Example:
 *
 * <pre>{@code
 * JWebGpuFfm.install();
 * GpuInstance instance = JWebGpu.createInstance();
 * }</pre>
 *
 * <p>The foreign function API is a restricted part of Java, so start the program with
 * {@code --enable-native-access=ALL-UNNAMED} (or put {@code Enable-Native-Access: ALL-UNNAMED} in the manifest of an
 * executable jar). Without it, Java prints a warning on the first native call, and a future Java version will refuse.
 *
 * <p>Which library is loaded: the file given to {@link #install(Path)}, otherwise the file named by the system
 * property {@code jwebgpu.library.path}, otherwise the copy packed into the jar for the current operating system and
 * CPU, which is extracted once into a cache directory.
 */
public final class JWebGpuFfm {

  private static final Object LOCK = new Object();
  private static FfmGpuBackend backend;

  private JWebGpuFfm() {}

  /** Loads the native library and installs the backend, using the library packed into the jar. */
  public static void install() {
    install(null);
  }

  /**
   * Loads the native library from a given file and installs the backend.
   *
   * <p>Calling the installer again with the same library does nothing.
   *
   * @param library The library file, or {@code null} to use the system property or the packed copy.
   * @throws IllegalStateException If the platform is not supported, the library cannot be found, or a different
   *     library or backend is already installed.
   */
  public static void install(Path library) {
    synchronized (LOCK) {
      NativeLoader.load(library);
      if (backend == null) {
        backend = new FfmGpuBackend();
      }
      JWebGpu.install(backend);
    }
  }

  /**
   * Tells whether the native library is loaded.
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
