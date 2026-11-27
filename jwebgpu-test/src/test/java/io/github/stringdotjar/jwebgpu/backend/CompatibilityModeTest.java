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
package io.github.stringdotjar.jwebgpu.backend;

import org.junit.jupiter.api.Test;

import io.github.stringdotjar.jwebgpu.GpuAdapter;
import io.github.stringdotjar.jwebgpu.GpuAdapterFallback;
import io.github.stringdotjar.jwebgpu.GpuAdapterInfo;
import io.github.stringdotjar.jwebgpu.GpuBackendType;
import io.github.stringdotjar.jwebgpu.GpuBuffer;
import io.github.stringdotjar.jwebgpu.GpuBufferDescriptor;
import io.github.stringdotjar.jwebgpu.GpuBufferUsage;
import io.github.stringdotjar.jwebgpu.GpuCallbackMode;
import io.github.stringdotjar.jwebgpu.GpuDevice;
import io.github.stringdotjar.jwebgpu.GpuFeatureLevel;
import io.github.stringdotjar.jwebgpu.GpuInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Requests compatibility mode adapters from the native library, including the OpenGL and OpenGL ES fallback. */
class CompatibilityModeTest {

  @Test
  void nullBackendWorksInCompatibilityMode() {
    Gpu.require();
    GpuInstance instance = Gpu.instance();
    GpuAdapter adapter = request(instance, new GpuAdapterFallback()
        .add(GpuFeatureLevel.COMPATIBILITY, GpuBackendType.NULL));
    assertNotNull(adapter, "the Null backend offers no compatibility mode adapter");
    GpuDevice device = Gpu.device(instance, adapter);
    useDevice(device);
    device.release();
    adapter.release();
    instance.release();
  }

  @Test
  void fallsBackToOpenGl() {
    Gpu.require();
    GpuInstance instance = Gpu.instance();
    GpuAdapterFallback fallback = new GpuAdapterFallback()
        .add(GpuFeatureLevel.COMPATIBILITY, GpuBackendType.OPENGLES)
        .add(GpuFeatureLevel.COMPATIBILITY, GpuBackendType.OPENGL);
    GpuAdapter adapter = request(instance, fallback);
    // Machines without an EGL driver (and libraries built without the OpenGL backends) have no such adapter.
    assumeTrue(adapter != null, "no OpenGL or OpenGL ES adapter on this machine");

    GpuAdapterInfo info = new GpuAdapterInfo();
    adapter.getInfo(info);
    assertTrue(info.backendType == GpuBackendType.OPENGLES || info.backendType == GpuBackendType.OPENGL,
        "unexpected backend " + info.backendType);
    assertEquals(info.backendType == GpuBackendType.OPENGLES ? 0 : 1, fallback.attempt());
    GpuDevice device = Gpu.device(instance, adapter);
    useDevice(device);
    device.release();
    adapter.release();
    instance.release();
  }

  private static GpuAdapter request(GpuInstance instance, GpuAdapterFallback fallback) {
    GpuAdapter[] result = new GpuAdapter[1];
    boolean[] done = new boolean[1];
    fallback.request(instance, GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, adapter, message) -> {
      result[0] = adapter;
      done[0] = true;
    });
    Gpu.await(instance, done);
    return result[0];
  }

  private static void useDevice(GpuDevice device) {
    GpuBufferDescriptor descriptor = new GpuBufferDescriptor();
    descriptor.size = 256;
    descriptor.usage = GpuBufferUsage.COPY_DST | GpuBufferUsage.UNIFORM;
    GpuBuffer buffer = device.createBuffer(descriptor);
    assertNotNull(buffer);
    buffer.release();
    assertEquals("", Gpu.errors());
  }
}
