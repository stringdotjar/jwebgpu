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
package io.github.stringdotjar.jwebgpu;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Entry point of the jwebgpu API.
 *
 * <p>A platform installs one backend at startup, and this class then asks that backend for a GPU instance. Every
 * other object (adapter, device, buffer, and so on) is created from that instance by the backend itself, so this
 * class is only consulted once, at the very beginning.
 *
 * <p>Think of {@code JWebGpu} as the front desk of a hotel and the backend as the hotel behind it. Guests (your
 * program) always talk to the same front desk, no matter which hotel (desktop, Android or web) it belongs to. The
 * hotel is chosen once, when the doors open ({@link #install(GpuBackend)}), and after that the front desk simply
 * hands you a key ({@link #createInstance()}) to the rooms behind it.
 *
 * <p>Example:
 *
 * <pre>{@code
 * // Once, at startup. Each platform has its own installer:
 * //   JWebGpuFfm.install();    (desktop)
 * //   JWebGpuJni.install();    (Android)
 * //   JWebGpuTeaVm.install();  (web)
 * JWebGpuFfm.install();
 *
 * GpuInstance instance = JWebGpu.createInstance();
 * GpuRequestAdapterCallback onAdapter = (status, adapter, message) -> {
 *   // keep the adapter, then request a device from it
 * };
 * instance.requestAdapter(null, GpuCallbackMode.ALLOW_PROCESS_EVENTS, onAdapter);
 * }</pre>
 *
 * <p>A backend is never found automatically (there is no {@code ServiceLoader} and no reflection), because that
 * does not work on platforms that compile ahead of time. A program that forgets to install one gets an
 * {@link IllegalStateException} with a message that names the installers.
 */
public final class JWebGpu {

  private static final Object LOCK = new Object();
  private static volatile GpuBackend backend;

  private JWebGpu() {}

  /**
   * Registers the backend of this platform.
   *
   * <p>A backend can be installed once. Installing the same instance again does nothing, so an installer may be
   * called from several places. Installing a different backend while one is installed is a mistake and fails.
   *
   * @param backend The backend to install.
   * @throws IllegalStateException If a different backend is already installed.
   */
  public static void install(@NotNull GpuBackend backend) {
    if (backend == null) {
      throw new NullPointerException("backend");
    }
    synchronized (LOCK) {
      GpuBackend current = JWebGpu.backend;
      if (current == backend) {
        return;
      }
      if (current != null) {
        throw new IllegalStateException("A jwebgpu backend is already installed (" + current.name()
            + "), so " + backend.name() + " cannot be installed as well.");
      }
      JWebGpu.backend = backend;
    }
  }

  /**
   * Tells whether a backend is installed.
   *
   * @return {@code true} after {@link #install(GpuBackend)} succeeded.
   */
  public static boolean isInstalled() {
    return backend != null;
  }

  /**
   * Returns the installed backend.
   *
   * @return The backend.
   * @throws IllegalStateException If no backend is installed.
   */
  public static @NotNull GpuBackend backend() {
    GpuBackend current = backend;
    if (current == null) {
      throw new IllegalStateException("No jwebgpu backend is installed. Call JWebGpuFfm.install() (desktop), "
          + "JWebGpuJni.install() (Android), or JWebGpuTeaVm.install() (web) first.");
    }
    return current;
  }

  /**
   * Creates a GPU instance with the default settings.
   *
   * <p>This is the same as {@code createInstance(null)}.
   *
   * @return The new instance. Release it when the program ends.
   * @throws IllegalStateException If no backend is installed.
   */
  public static @NotNull GpuInstance createInstance() {
    return createInstance(null);
  }

  /**
   * Creates a GPU instance, which is the root object of the WebGPU API.
   *
   * @param descriptor The settings of the instance, or {@code null} for the defaults.
   * @return The new instance. Release it when the program ends.
   * @throws IllegalStateException If no backend is installed.
   */
  public static @NotNull GpuInstance createInstance(@Nullable GpuInstanceDescriptor descriptor) {
    return backend().createInstance(descriptor);
  }

  /**
   * Fills in the features that every instance supports.
   *
   * @param features The object to fill.
   * @throws IllegalStateException If no backend is installed.
   */
  public static void getInstanceFeatures(@NotNull GpuSupportedInstanceFeatures features) {
    backend().getInstanceFeatures(features);
  }

  /**
   * Fills in the limits of every instance.
   *
   * @param limits The object to fill.
   * @return The status of the call, or {@code null} if the native library returned an unknown value.
   * @throws IllegalStateException If no backend is installed.
   */
  public static @Nullable GpuStatus getInstanceLimits(@NotNull GpuInstanceLimits limits) {
    return backend().getInstanceLimits(limits);
  }

  /**
   * Tells whether instances support a feature.
   *
   * @param feature The feature to check.
   * @return {@code true} if instances support it.
   * @throws IllegalStateException If no backend is installed.
   */
  public static boolean hasInstanceFeature(@NotNull GpuInstanceFeatureName feature) {
    return backend().hasInstanceFeature(feature);
  }

  /**
   * Returns the address of a native WebGPU function, for code that calls the native library directly.
   *
   * @param procName The name of the function.
   * @return The address, or 0 if there is no such function (or on a backend without native functions).
   * @throws IllegalStateException If no backend is installed.
   */
  public static long getProcAddress(@NotNull String procName) {
    return backend().getProcAddress(procName);
  }

  /**
   * Removes the installed backend again. This exists only for tests that install a fake backend; a real program
   * installs its backend once and keeps it.
   */
  static void uninstall() {
    synchronized (LOCK) {
      backend = null;
    }
  }
}
