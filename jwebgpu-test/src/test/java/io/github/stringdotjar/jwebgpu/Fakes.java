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

/** Minimal fake implementations of the abstract API classes, for tests that need real objects. */
final class Fakes {

  private Fakes() {}

  /** A texture view that remembers whether it was released. */
  static final class FakeTextureView extends GpuTextureView {

    private boolean released;

    @Override
    public void setLabel(String label) {}

    @Override
    public void release() {
      released = true;
    }

    @Override
    public void addRef() {}

    @Override
    public boolean isReleased() {
      return released;
    }
  }

  /** An instance that records the calls the tests care about. */
  static final class FakeInstance extends GpuInstance {

    int processEventsCalls;
    GpuRequestAdapterCallback lastCallback;
    GpuCallbackMode lastMode;
    private boolean released;

    @Override
    public GpuSurface createSurface(GpuSurfaceDescriptor descriptor) {
      return null;
    }

    @Override
    public GpuSurface createSurface(GpuSurfaceDescriptor descriptor, @NotNull GpuSurface into) {
      return into;
    }

    @Override
    public void getWGSLLanguageFeatures(GpuSupportedWGSLLanguageFeatures features) {}

    @Override
    public boolean hasWGSLLanguageFeature(GpuWGSLLanguageFeatureName feature) {
      return false;
    }

    @Override
    public void processEvents() {
      processEventsCalls++;
    }

    @Override
    public long requestAdapter(
        @Nullable GpuRequestAdapterOptions options, GpuCallbackMode mode, GpuRequestAdapterCallback callback) {
      lastMode = mode;
      lastCallback = callback;
      return 42L;
    }

    @Override
    public @Nullable GpuWaitStatus waitAny(GpuFutureWaitInfo[] futures, int futureCount, long timeoutNS) {
      return GpuWaitStatus.SUCCESS;
    }

    @Override
    public void release() {
      released = true;
    }

    @Override
    public void addRef() {}

    @Override
    public boolean isReleased() {
      return released;
    }
  }

  /** A backend that records what the tests ask of it. */
  static final class RecordingBackend implements GpuBackend {

    final String name;
    final FakeInstance instance = new FakeInstance();
    int createInstanceCalls;
    GpuInstanceDescriptor lastDescriptor;
    String lastProcName;

    RecordingBackend(String name) {
      this.name = name;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public GpuInstance createInstance(@Nullable GpuInstanceDescriptor descriptor) {
      createInstanceCalls++;
      lastDescriptor = descriptor;
      return instance;
    }

    @Override
    public void getInstanceFeatures(GpuSupportedInstanceFeatures features) {
      features.featureCount = 0;
    }

    @Override
    public @Nullable GpuStatus getInstanceLimits(GpuInstanceLimits limits) {
      limits.timedWaitAnyMaxCount = 64L;
      return GpuStatus.SUCCESS;
    }

    @Override
    public boolean hasInstanceFeature(GpuInstanceFeatureName feature) {
      return feature == GpuInstanceFeatureName.TIMED_WAIT_ANY;
    }

    @Override
    public long getProcAddress(String procName) {
      lastProcName = procName;
      return 0L;
    }
  }
}
