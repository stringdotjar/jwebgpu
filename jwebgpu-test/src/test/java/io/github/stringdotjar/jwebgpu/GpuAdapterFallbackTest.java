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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests {@link GpuAdapterFallback} with a fake instance that answers each attempt from a script. */
class GpuAdapterFallbackTest {

  @Test
  void defaultsTryCoreThenCompatibility() {
    GpuAdapterFallback fallback = new GpuAdapterFallback().addDefaults();
    assertEquals(2, fallback.count());
    assertEquals(GpuFeatureLevel.CORE, fallback.options(0).featureLevel);
    assertEquals(GpuBackendType.UNDEFINED, fallback.options(0).backendType);
    assertEquals(GpuFeatureLevel.COMPATIBILITY, fallback.options(1).featureLevel);
    assertEquals(GpuBackendType.UNDEFINED, fallback.options(1).backendType);
  }

  @Test
  void firstAdapterWins() {
    FakeAdapter adapter = new FakeAdapter(true);
    ScriptedInstance instance = new ScriptedInstance(adapter);
    GpuAdapterFallback fallback = new GpuAdapterFallback().addDefaults();
    Result result = new Result();

    fallback.request(instance, GpuCallbackMode.ALLOW_PROCESS_EVENTS, result);

    assertEquals(1, instance.calls);
    assertSame(adapter, result.adapter);
    assertEquals(GpuRequestAdapterStatus.SUCCESS, result.status);
    assertEquals(0, fallback.attempt());
    assertFalse(fallback.isRunning());
  }

  @Test
  void fallsBackToOpenGlInCompatibilityMode() {
    FakeAdapter gl = new FakeAdapter(false);
    ScriptedInstance instance = new ScriptedInstance(null, null, gl);
    GpuAdapterFallback fallback = new GpuAdapterFallback()
        .add(GpuFeatureLevel.CORE, GpuBackendType.VULKAN)
        .add(GpuFeatureLevel.COMPATIBILITY, GpuBackendType.OPENGLES)
        .add(GpuFeatureLevel.COMPATIBILITY, GpuBackendType.OPENGL);
    Result result = new Result();

    fallback.request(instance, GpuCallbackMode.ALLOW_SPONTANEOUS, result);

    assertEquals(3, instance.calls);
    assertEquals(GpuFeatureLevel.COMPATIBILITY, instance.lastLevel);
    assertEquals(GpuBackendType.OPENGL, instance.lastBackend);
    assertSame(gl, result.adapter);
    assertEquals(2, fallback.attempt());
    assertEquals(1, result.calls);
    assertFalse(GpuAdapterFallback.supportsCore(gl));
  }

  @Test
  void reportsTheLastFailureWhenNothingIsFound() {
    ScriptedInstance instance = new ScriptedInstance(null, null);
    GpuAdapterFallback fallback = new GpuAdapterFallback().addDefaults();
    Result result = new Result();

    fallback.request(instance, GpuCallbackMode.ALLOW_PROCESS_EVENTS, result);

    assertEquals(2, instance.calls);
    assertNull(result.adapter);
    assertEquals(GpuRequestAdapterStatus.UNAVAILABLE, result.status);
    assertEquals("attempt 1", result.message);
    assertEquals(1, result.calls);
  }

  @Test
  void stopsWhenCanceled() {
    ScriptedInstance instance = new ScriptedInstance(null, null);
    instance.cancel = true;
    GpuAdapterFallback fallback = new GpuAdapterFallback().addDefaults();
    Result result = new Result();

    fallback.request(instance, GpuCallbackMode.ALLOW_PROCESS_EVENTS, result);

    assertEquals(1, instance.calls);
    assertEquals(GpuRequestAdapterStatus.CALLBACK_CANCELLED, result.status);
  }

  @Test
  void sharedSettingsReachEveryAttempt() {
    GpuAdapterFallback fallback = new GpuAdapterFallback().addDefaults();
    fallback.setPowerPreference(GpuPowerPreference.HIGH_PERFORMANCE);
    for (int i = 0; i < fallback.count(); i++) {
      assertEquals(GpuPowerPreference.HIGH_PERFORMANCE, fallback.options(i).powerPreference);
    }
  }

  @Test
  void rejectsMisuse() {
    GpuAdapterFallback fallback = new GpuAdapterFallback(1);
    ScriptedInstance instance = new ScriptedInstance();
    assertThrows(IllegalArgumentException.class, () -> new GpuAdapterFallback(0));
    assertThrows(IllegalStateException.class,
        () -> fallback.request(instance, GpuCallbackMode.ALLOW_PROCESS_EVENTS, new Result()));
    fallback.add();
    assertThrows(IllegalStateException.class, fallback::add);
    assertThrows(IllegalArgumentException.class,
        () -> fallback.request(instance, GpuCallbackMode.WAIT_ANY_ONLY, new Result()));
    assertThrows(IndexOutOfBoundsException.class, () -> fallback.options(1));
    fallback.clear();
    assertEquals(0, fallback.count());
  }

  @Test
  void requestingAgainWhileRunningFails() {
    ScriptedInstance instance = new ScriptedInstance();
    instance.deferred = true;
    GpuAdapterFallback fallback = new GpuAdapterFallback().addDefaults();
    fallback.request(instance, GpuCallbackMode.ALLOW_PROCESS_EVENTS, new Result());
    assertTrue(fallback.isRunning());
    assertThrows(IllegalStateException.class,
        () -> fallback.request(instance, GpuCallbackMode.ALLOW_PROCESS_EVENTS, new Result()));
    assertThrows(IllegalStateException.class, fallback::clear);
  }

  /** Records the final result of a fallback request. */
  private static final class Result implements GpuRequestAdapterCallback {

    private int calls;
    private GpuRequestAdapterStatus status;
    private GpuAdapter adapter;
    private String message;

    @Override
    public void onRequestAdapter(
        @Nullable GpuRequestAdapterStatus status, @Nullable GpuAdapter adapter, @Nullable String message) {
      this.calls++;
      this.status = status;
      this.adapter = adapter;
      this.message = message;
    }
  }

  /** An instance that answers attempt n with the n-th scripted adapter, right away. */
  private static final class ScriptedInstance extends Fakes.FakeInstance {

    private final GpuAdapter[] answers;
    private int calls;
    private GpuFeatureLevel lastLevel;
    private GpuBackendType lastBackend;
    private boolean cancel;
    private boolean deferred;

    private ScriptedInstance(GpuAdapter... answers) {
      this.answers = answers;
    }

    @Override
    public long requestAdapter(
        @Nullable GpuRequestAdapterOptions options, GpuCallbackMode mode, GpuRequestAdapterCallback callback) {
      int index = calls++;
      lastLevel = options.featureLevel;
      lastBackend = options.backendType;
      if (deferred) {
        return index;
      }
      if (cancel) {
        callback.onRequestAdapter(GpuRequestAdapterStatus.CALLBACK_CANCELLED, null, "cancelled");
        return index;
      }
      GpuAdapter answer = index < answers.length ? answers[index] : null;
      callback.onRequestAdapter(answer != null ? GpuRequestAdapterStatus.SUCCESS : GpuRequestAdapterStatus.UNAVAILABLE,
          answer, "attempt " + index);
      return index;
    }
  }

  /** An adapter that only answers whether it supports the core feature level. */
  private static final class FakeAdapter extends GpuAdapter {

    private final boolean core;

    private FakeAdapter(boolean core) {
      this.core = core;
    }

    @Override
    public void getFeatures(GpuSupportedFeatures features) {}

    @Override
    public @Nullable GpuStatus getInfo(GpuAdapterInfo info) {
      return GpuStatus.SUCCESS;
    }

    @Override
    public @Nullable GpuStatus getLimits(GpuLimits limits) {
      return GpuStatus.SUCCESS;
    }

    @Override
    public boolean hasFeature(GpuFeatureName feature) {
      return core && feature == GpuFeatureName.CORE_FEATURES_AND_LIMITS;
    }

    @Override
    public long requestDevice(
        @Nullable GpuDeviceDescriptor descriptor, GpuCallbackMode mode, @NotNull GpuRequestDeviceCallback callback) {
      return 0L;
    }

    @Override
    public void release() {}

    @Override
    public void addRef() {}

    @Override
    public boolean isReleased() {
      return false;
    }
  }
}
