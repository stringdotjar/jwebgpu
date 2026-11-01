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
package io.github.stringdotjar.jwebgpu.jni.android;

import io.github.stringdotjar.jwebgpu.GpuInstance;
import io.github.stringdotjar.jwebgpu.GpuSurface;
import io.github.stringdotjar.jwebgpu.GpuSurfaceDescriptor;
import io.github.stringdotjar.jwebgpu.GpuSurfaceSourceAndroidNativeWindow;

import android.view.Surface;

/**
 * Turns an Android {@link Surface} into a WebGPU {@link GpuSurface}.
 *
 * <p>Android draws into a native window ({@code ANativeWindow}), and the Java {@link Surface} is the handle to it. This
 * helper asks Android for the native window, wraps it in a WebGPU surface, and remembers both so it can give them back
 * in the right order. Think of it as a picture frame holder: the wall (the window) must outlive the frame (the WebGPU
 * surface), so the frame comes down first and the wall is let go afterwards.
 *
 * <p>Example, inside a {@code SurfaceHolder.Callback}:
 *
 * <pre>{@code
 * public void surfaceCreated(SurfaceHolder holder) {
 *   target = AndroidSurface.create(instance, holder.getSurface());
 *   GpuSurface surface = target.getSurface();
 *   // configure the surface, then render
 * }
 *
 * public void surfaceDestroyed(SurfaceHolder holder) {
 *   target.release();
 * }
 * }</pre>
 *
 * <p>Create the helper when the Android surface is created and release it when the surface is destroyed. The
 * surface cannot be used after {@link #release()}, and a new one must be created when Android creates a new Android
 * surface (for example after the app returns from the background).
 */
public final class AndroidSurface {

  private long window;
  private GpuSurface surface;

  private AndroidSurface(long window, GpuSurface surface) {
    this.window = window;
    this.surface = surface;
  }

  /**
   * Creates a WebGPU surface that presents into an Android surface.
   *
   * @param instance The instance that creates the WebGPU surface. It must come from the JNI backend.
   * @param androidSurface The Android surface, which must be valid.
   * @return The helper that owns the WebGPU surface and the native window reference.
   * @throws IllegalArgumentException If Android returns no native window for the surface.
   */
  public static AndroidSurface create(GpuInstance instance, Surface androidSurface) {
    long window = windowFromSurface(androidSurface);
    if (window == 0L) {
      throw new IllegalArgumentException("The Android surface has no native window (is it valid?)");
    }
    try {
      GpuSurfaceSourceAndroidNativeWindow source = new GpuSurfaceSourceAndroidNativeWindow();
      source.window = window;
      GpuSurfaceDescriptor descriptor = new GpuSurfaceDescriptor();
      descriptor.nextInChain = source;
      return new AndroidSurface(window, instance.createSurface(descriptor));
    } catch (RuntimeException | Error e) {
      releaseWindow(window);
      throw e;
    }
  }

  /**
   * Releases the WebGPU surface first and the native window reference afterwards.
   *
   * <p>Calling it again does nothing.
   */
  public void release() {
    GpuSurface s = surface;
    long w = window;
    surface = null;
    window = 0L;
    if (s != null) {
      s.release();
    }
    if (w != 0L) {
      releaseWindow(w);
    }
  }

  /**
   * Returns the WebGPU surface.
   *
   * @return The surface, or {@code null} after {@link #release()}.
   */
  public GpuSurface getSurface() {
    return surface;
  }

  /**
   * Returns the address of the native window.
   *
   * @return The {@code ANativeWindow} address, or 0 after {@link #release()}.
   */
  public long getWindow() {
    return window;
  }

  private static native long windowFromSurface(Surface surface);

  private static native void releaseWindow(long window);
}
