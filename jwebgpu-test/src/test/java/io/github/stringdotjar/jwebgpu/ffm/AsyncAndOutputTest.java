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

import org.junit.jupiter.api.Test;

import io.github.stringdotjar.jwebgpu.GpuAdapter;
import io.github.stringdotjar.jwebgpu.GpuAdapterInfo;
import io.github.stringdotjar.jwebgpu.GpuBackendType;
import io.github.stringdotjar.jwebgpu.GpuCallbackMode;
import io.github.stringdotjar.jwebgpu.GpuCompilationInfo;
import io.github.stringdotjar.jwebgpu.GpuDevice;
import io.github.stringdotjar.jwebgpu.GpuErrorFilter;
import io.github.stringdotjar.jwebgpu.GpuErrorType;
import io.github.stringdotjar.jwebgpu.GpuFutureWaitInfo;
import io.github.stringdotjar.jwebgpu.GpuInstance;
import io.github.stringdotjar.jwebgpu.GpuLimits;
import io.github.stringdotjar.jwebgpu.GpuQueue;
import io.github.stringdotjar.jwebgpu.GpuShaderModule;
import io.github.stringdotjar.jwebgpu.GpuShaderModuleDescriptor;
import io.github.stringdotjar.jwebgpu.GpuShaderSourceWGSL;
import io.github.stringdotjar.jwebgpu.GpuStatus;
import io.github.stringdotjar.jwebgpu.GpuSupportedFeatures;
import io.github.stringdotjar.jwebgpu.GpuSupportedInstanceFeatures;
import io.github.stringdotjar.jwebgpu.GpuWaitStatus;
import io.github.stringdotjar.jwebgpu.JWebGpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the asynchronous calls and the functions that fill output structs, against the Null backend.
 *
 * <p>These are the paths the triangle does not touch: waiting on a future, an error scope, a queue fence, shader
 * compilation messages, limits, features and the instance functions.
 */
class AsyncAndOutputTest {

  @Test
  void waitAnyCompletesAFuture() {
    Gpu.require();
    GpuInstance instance = Gpu.instance();
    boolean[] done = new boolean[1];
    long id = instance.requestAdapter(null, GpuCallbackMode.WAIT_ANY_ONLY, (status, adapter, message) -> {
      done[0] = true;
      if (adapter != null) {
        adapter.release();
      }
    });
    GpuFutureWaitInfo[] infos = { new GpuFutureWaitInfo() };
    infos[0].future.id = id;
    GpuWaitStatus status = instance.waitAny(infos, 1, 10_000_000_000L);
    assertEquals(GpuWaitStatus.SUCCESS, status);
    assertTrue(infos[0].completed);
    assertTrue(done[0], "the callback runs inside waitAny");
    instance.release();
  }

  @Test
  void queueFenceErrorScopeAndCompilationInfo() {
    Gpu.require();
    GpuInstance instance = Gpu.instance();
    GpuAdapter adapter = Gpu.adapter(instance, GpuBackendType.NULL);
    GpuDevice device = Gpu.device(instance, adapter);
    GpuQueue queue = device.getQueue();

    boolean[] fence = new boolean[1];
    queue.onSubmittedWorkDone(GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, message) -> fence[0] = true);
    Gpu.await(instance, fence);

    device.pushErrorScope(GpuErrorFilter.VALIDATION);
    GpuShaderSourceWGSL wgsl = new GpuShaderSourceWGSL();
    wgsl.code = "this is not wgsl";
    GpuShaderModuleDescriptor descriptor = new GpuShaderModuleDescriptor();
    descriptor.nextInChain = wgsl;
    GpuShaderModule shader = device.createShaderModule(descriptor);

    GpuCompilationInfo[] info = new GpuCompilationInfo[1];
    boolean[] compiled = new boolean[1];
    shader.getCompilationInfo(GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, result) -> {
      info[0] = result;
      compiled[0] = true;
    });
    Gpu.await(instance, compiled);
    assertNotNull(info[0]);
    assertTrue(info[0].messageCount > 0, "the broken shader has compilation messages");
    assertNotNull(info[0].messages[0].message);

    GpuErrorType[] type = new GpuErrorType[1];
    boolean[] popped = new boolean[1];
    device.popErrorScope(GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, errorType, message) -> {
      type[0] = errorType;
      popped[0] = true;
    });
    Gpu.await(instance, popped);
    assertEquals(GpuErrorType.VALIDATION, type[0]);

    shader.release();
    queue.release();
    device.release();
    adapter.release();
    instance.release();
  }

  @Test
  void limitsFeaturesAndInfoAreReadBack() {
    Gpu.require();
    GpuInstance instance = Gpu.instance();
    GpuAdapter adapter = Gpu.adapter(instance, GpuBackendType.NULL);

    GpuLimits limits = new GpuLimits();
    assertEquals(GpuStatus.SUCCESS, adapter.getLimits(limits));
    assertTrue(limits.maxTextureDimension2D >= 8192, "maxTextureDimension2D is " + limits.maxTextureDimension2D);
    assertTrue(limits.maxBindGroups >= 4);
    assertTrue(limits.maxBufferSize >= 268435456L);

    GpuSupportedFeatures features = new GpuSupportedFeatures();
    adapter.getFeatures(features);
    assertTrue(features.featureCount > 0);
    assertTrue(features.features.length >= features.featureCount);
    assertNotNull(features.features[0]);
    assertEquals(adapter.hasFeature(features.features[0]), true);

    GpuAdapterInfo info = new GpuAdapterInfo();
    assertEquals(GpuStatus.SUCCESS, adapter.getInfo(info));
    assertNotNull(info.vendor);
    assertEquals(GpuBackendType.NULL, info.backendType);
    GpuAdapterInfo again = new GpuAdapterInfo();
    adapter.getInfo(again);
    assertEquals(info.description, again.description);

    GpuSupportedInstanceFeatures instanceFeatures = new GpuSupportedInstanceFeatures();
    JWebGpu.getInstanceFeatures(instanceFeatures);
    assertTrue(instanceFeatures.featureCount >= 1);
    assertNotEquals(0L, JWebGpu.getProcAddress("wgpuCreateInstance"));
    assertEquals(0L, JWebGpu.getProcAddress("wgpuNoSuchFunction"));

    adapter.release();
    instance.release();
  }

  @Test
  void releasedObjectsRejectCallsAndReleaseIsIdempotent() {
    Gpu.require();
    GpuInstance instance = Gpu.instance();
    instance.release();
    instance.release();
    assertTrue(instance.isReleased());
    try {
      instance.processEvents();
      throw new AssertionError("a released object must reject calls");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("released"));
    }
  }
}
