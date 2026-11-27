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
 * Requests an adapter by trying a list of adapter options in order, so a program can fall back to compatibility mode
 * (and with it to OpenGL, OpenGL ES or Direct3D 11) when the machine has no adapter for full WebGPU.
 *
 * <p>WebGPU has two feature levels. {@link GpuFeatureLevel#CORE} is full WebGPU and needs a modern graphics API
 * (Vulkan, Metal or Direct3D 12). {@link GpuFeatureLevel#COMPATIBILITY} is a slightly smaller subset that also runs on
 * older ones: OpenGL ES 3.1, desktop OpenGL 4.3 and Direct3D 11. On the desktop and on Android, OpenGL and OpenGL ES
 * adapters exist <em>only</em> in compatibility mode, so a request has to ask for that level to ever get one. In the
 * browser, the backend type is ignored and the browser picks the graphics API itself, but the feature level still
 * works the same way.
 *
 * <p>Think of it as calling a list of restaurants for a table: you call your favorite first, and only when it is full
 * do you call the next one on the list. Each "restaurant" is one {@link GpuRequestAdapterOptions}, and the first one
 * that has a table (an adapter) wins.
 *
 * <p>Example, trying full WebGPU first and then compatibility mode on any graphics API:
 *
 * <pre>{@code
 * GpuAdapterFallback fallback = new GpuAdapterFallback().addDefaults();
 * fallback.request(instance, GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, adapter, message) -> {
 *   if (adapter == null) {
 *     // No attempt found an adapter; the status and message are the ones of the last attempt.
 *     return;
 *   }
 *   boolean core = GpuAdapterFallback.supportsCore(adapter);
 *   // Request the device. In compatibility mode, add GpuFeatureName.CORE_FEATURES_AND_LIMITS to the
 *   // required features only when core is true.
 * });
 * }</pre>
 *
 * <p>Example, preferring OpenGL ES over every other graphics API, and then desktop OpenGL:
 *
 * <pre>{@code
 * GpuAdapterFallback fallback = new GpuAdapterFallback()
 *     .add(GpuFeatureLevel.COMPATIBILITY, GpuBackendType.OPENGLES)
 *     .add(GpuFeatureLevel.COMPATIBILITY, GpuBackendType.OPENGL)
 *     .addDefaults();
 * }</pre>
 *
 * <p>Create the fallback once, at startup. It keeps a fixed number of preallocated options (see
 * {@link #GpuAdapterFallback(int)}), so adding attempts and requesting allocates nothing. One fallback runs one request
 * at a time.
 *
 * <p>The attempts are chained from inside the callback of the previous one, so the mode must let a callback start the
 * next request: {@link GpuCallbackMode#ALLOW_PROCESS_EVENTS} or {@link GpuCallbackMode#ALLOW_SPONTANEOUS}. With
 * {@link GpuCallbackMode#WAIT_ANY_ONLY} the future of the first attempt would be the only one the caller could wait on,
 * so that mode is rejected.
 */
public final class GpuAdapterFallback {

  /** The number of attempts a fallback holds when created with {@link #GpuAdapterFallback()}. */
  public static final int DEFAULT_CAPACITY = 8;

  private final GpuRequestAdapterOptions[] attempts;
  private final GpuRequestAdapterCallback step;
  private int count;
  private int current;
  private @Nullable GpuInstance instance;
  private @Nullable GpuCallbackMode mode;
  private @Nullable GpuRequestAdapterCallback callback;

  private boolean running;

  /** Creates an empty fallback with room for {@link #DEFAULT_CAPACITY} attempts. */
  public GpuAdapterFallback() {
    this(DEFAULT_CAPACITY);
  }

  /**
   * Creates an empty fallback with room for a given number of attempts.
   *
   * @param capacity The largest number of attempts. Every attempt's options are allocated now.
   * @throws IllegalArgumentException If {@code capacity} is less than 1.
   */
  public GpuAdapterFallback(int capacity) {
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity must be at least 1, but is " + capacity);
    }
    attempts = new GpuRequestAdapterOptions[capacity];
    for (int i = 0; i < capacity; i++) {
      attempts[i] = new GpuRequestAdapterOptions();
    }
    step = this::onAttempt;
  }

  /**
   * Tells whether an adapter offers full WebGPU (the core feature level).
   *
   * <p>A core adapter always has {@link GpuFeatureName#CORE_FEATURES_AND_LIMITS}. A compatibility mode adapter has it
   * only when the hardware could run core as well, and then a device that lists the feature in its required features
   * gets the core features and limits.
   *
   * @param adapter The adapter to check.
   * @return {@code true} if the adapter supports the core feature level.
   */
  public static boolean supportsCore(@NotNull GpuAdapter adapter) {
    return adapter.hasFeature(GpuFeatureName.CORE_FEATURES_AND_LIMITS);
  }

  /**
   * Appends one attempt and returns its options, reset to the defaults, for the caller to fill.
   *
   * <p>Use this when an attempt needs more than a feature level and a backend type, for example a power preference or
   * an extension struct.
   *
   * @return The options of the new attempt. They belong to this fallback; keep using them only to refill this attempt.
   * @throws IllegalStateException If the fallback is full or a request is running.
   */
  public @NotNull GpuRequestAdapterOptions add() {
    checkIdle();
    if (count == attempts.length) {
      throw new IllegalStateException("The fallback holds at most " + attempts.length
          + " attempts; create it with a larger capacity.");
    }
    GpuRequestAdapterOptions options = attempts[count++];
    options.reset();
    return options;
  }

  /**
   * Appends one attempt with a feature level and a backend type.
   *
   * @param featureLevel The feature level to ask for.
   * @param backendType The graphics API to ask for, or {@link GpuBackendType#UNDEFINED} for any. The browser ignores it.
   * @return This fallback, for chaining.
   * @throws IllegalStateException If the fallback is full or a request is running.
   */
  public @NotNull GpuAdapterFallback add(@NotNull GpuFeatureLevel featureLevel, @NotNull GpuBackendType backendType) {
    GpuRequestAdapterOptions options = add();
    options.featureLevel = featureLevel;
    options.backendType = backendType;
    return this;
  }

  /**
   * Appends the usual chain: full WebGPU on any graphics API, then compatibility mode on any graphics API.
   *
   * <p>The second attempt is the one that finds OpenGL ES, OpenGL and Direct3D 11 adapters when nothing better exists.
   *
   * @return This fallback, for chaining.
   * @throws IllegalStateException If the fallback has no room for two more attempts or a request is running.
   */
  public @NotNull GpuAdapterFallback addDefaults() {
    add(GpuFeatureLevel.CORE, GpuBackendType.UNDEFINED);
    return add(GpuFeatureLevel.COMPATIBILITY, GpuBackendType.UNDEFINED);
  }

  /**
   * Removes every attempt.
   *
   * @throws IllegalStateException If a request is running.
   */
  public void clear() {
    checkIdle();
    count = 0;
  }

  /**
   * Sets the power preference of every attempt added so far.
   *
   * @param preference The power preference.
   * @throws IllegalStateException If a request is running.
   */
  public void setPowerPreference(@NotNull GpuPowerPreference preference) {
    checkIdle();
    for (int i = 0; i < count; i++) {
      attempts[i].powerPreference = preference;
    }
  }

  /**
   * Sets the surface that every attempt added so far must be able to present to.
   *
   * <p>Pass the window's surface when the program draws to a window, so that no attempt returns an adapter that cannot
   * show anything on it.
   *
   * @param surface The surface, or {@code null} for none.
   * @throws IllegalStateException If a request is running.
   */
  public void setCompatibleSurface(@Nullable GpuSurface surface) {
    checkIdle();
    for (int i = 0; i < count; i++) {
      attempts[i].compatibleSurface = surface;
    }
  }

  /**
   * Starts the request: tries each attempt in order until one returns an adapter.
   *
   * <p>The callback runs once. It gets the first adapter that was found together with that attempt's status and
   * message. If no attempt finds an adapter, it gets {@code null} with the status and message of the last attempt.
   * {@link #attempt()} then tells which attempt produced the result. If an attempt is canceled (for example because
   * the instance was released), the request stops there and the callback gets that status.
   *
   * @param instance The instance to request the adapter from.
   * @param mode When the callbacks may run: {@link GpuCallbackMode#ALLOW_PROCESS_EVENTS} or
   *     {@link GpuCallbackMode#ALLOW_SPONTANEOUS}.
   * @param callback Receives the result.
   * @throws IllegalArgumentException If {@code mode} is {@link GpuCallbackMode#WAIT_ANY_ONLY}.
   * @throws IllegalStateException If the fallback has no attempts or a request is already running.
   */
  public void request(
      @NotNull GpuInstance instance, @NotNull GpuCallbackMode mode, @NotNull GpuRequestAdapterCallback callback) {
    if (mode == GpuCallbackMode.WAIT_ANY_ONLY) {
      throw new IllegalArgumentException("GpuAdapterFallback chains its attempts from callbacks, so it needs "
          + "ALLOW_PROCESS_EVENTS or ALLOW_SPONTANEOUS, not WAIT_ANY_ONLY.");
    }
    checkIdle();
    if (count == 0) {
      throw new IllegalStateException("The fallback has no attempts; call add(...) or addDefaults() first.");
    }
    this.instance = instance;
    this.mode = mode;
    this.callback = callback;
    current = 0;
    running = true;
    try {
      instance.requestAdapter(attempts[0], mode, step);
    } catch (RuntimeException e) {
      finish();
      throw e;
    }
  }

  private void onAttempt(
      @Nullable GpuRequestAdapterStatus status, @Nullable GpuAdapter adapter, @Nullable String message) {
    boolean last = current + 1 >= count;
    if (adapter == null && !last && status != GpuRequestAdapterStatus.CALLBACK_CANCELLED) {
      current++;
      try {
        instance.requestAdapter(attempts[current], mode, step);
      } catch (RuntimeException e) {
        finish();
        throw e;
      }
      return;
    }
    GpuRequestAdapterCallback target = callback;
    finish();
    target.onRequestAdapter(status, adapter, message);
  }

  private void finish() {
    running = false;
    instance = null;
    mode = null;
    callback = null;
  }

  private void checkIdle() {
    if (running) {
      throw new IllegalStateException("An adapter request of this fallback is still running.");
    }
  }

  /**
   * Returns the number of attempts.
   *
   * @return The number of attempts added since the last {@link #clear()}.
   */
  public int count() {
    return count;
  }

  /**
   * Returns the options of one attempt.
   *
   * @param index The index of the attempt, from 0 to {@link #count()} minus 1.
   * @return The options. Changing them changes the attempt.
   * @throws IndexOutOfBoundsException If {@code index} is not a valid attempt.
   */
  public @NotNull GpuRequestAdapterOptions options(int index) {
    if (index < 0 || index >= count) {
      throw new IndexOutOfBoundsException("attempt " + index + " of " + count);
    }
    return attempts[index];
  }

  /**
   * Returns the index of the attempt that is running, or that produced the last result.
   *
   * @return The index of the attempt.
   */
  public int attempt() {
    return current;
  }

  /**
   * Tells whether a request is running.
   *
   * @return {@code true} between {@link #request} and the call of its callback.
   */
  public boolean isRunning() {
    return running;
  }
}
