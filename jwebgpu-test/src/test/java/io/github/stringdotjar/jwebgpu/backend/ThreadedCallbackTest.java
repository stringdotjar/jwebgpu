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
import io.github.stringdotjar.jwebgpu.GpuBackendType;
import io.github.stringdotjar.jwebgpu.GpuCallbackMode;
import io.github.stringdotjar.jwebgpu.GpuColorTargetState;
import io.github.stringdotjar.jwebgpu.GpuColorWriteMask;
import io.github.stringdotjar.jwebgpu.GpuCreatePipelineAsyncStatus;
import io.github.stringdotjar.jwebgpu.GpuDevice;
import io.github.stringdotjar.jwebgpu.GpuFragmentState;
import io.github.stringdotjar.jwebgpu.GpuInstance;
import io.github.stringdotjar.jwebgpu.GpuRenderPipeline;
import io.github.stringdotjar.jwebgpu.GpuRenderPipelineDescriptor;
import io.github.stringdotjar.jwebgpu.GpuShaderModule;
import io.github.stringdotjar.jwebgpu.GpuShaderModuleDescriptor;
import io.github.stringdotjar.jwebgpu.GpuShaderSourceWGSL;
import io.github.stringdotjar.jwebgpu.GpuTextureFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Checks callbacks that the native library fires from a thread of its own.
 *
 * <p>With the callback mode {@code ALLOW_SPONTANEOUS}, Dawn may finish an asynchronous request on one of its worker
 * threads, a thread that the Java virtual machine has never seen. The backend must attach such a thread, run the Java
 * callback with its own scratch memory, and detach it again, without crashing and without losing the result. The test
 * does not call {@code processEvents()} while it waits, so a callback can only arrive from another thread. It uses
 * the default adapter (a real GPU or a software Vulkan driver), because the Null backend finishes on the calling
 * thread.
 */
class ThreadedCallbackTest {

  private static final String WGSL = """
      @vertex fn vs(@builtin(vertex_index) i : u32) -> @builtin(position) vec4f {
        var p = array<vec2f, 3>(vec2f(0.0, 0.5), vec2f(-0.5, -0.5), vec2f(0.5, -0.5));
        return vec4f(p[i], 0.0, 1.0);
      }
      @fragment fn fs() -> @location(0) vec4f { return vec4f(1.0, 0.0, 0.0, 1.0); }
      """;

  @Test
  void aPipelineCompiledOnAnotherThreadReportsBack() throws InterruptedException {
    Gpu.require();
    GpuInstance instance = Gpu.instance();
    GpuAdapter adapter = Gpu.adapter(instance, GpuBackendType.UNDEFINED);
    GpuDevice device = Gpu.device(instance, adapter);

    GpuShaderSourceWGSL wgsl = new GpuShaderSourceWGSL();
    wgsl.code = WGSL;
    GpuShaderModuleDescriptor smd = new GpuShaderModuleDescriptor();
    smd.nextInChain = wgsl;
    GpuShaderModule shader = device.createShaderModule(smd);
    GpuRenderPipelineDescriptor rpd = new GpuRenderPipelineDescriptor();
    rpd.vertex.module = shader;
    rpd.vertex.entryPoint = "vs";
    GpuFragmentState fragment = new GpuFragmentState();
    fragment.module = shader;
    fragment.entryPoint = "fs";
    fragment.targetCount = 1;
    GpuColorTargetState target = fragment.targets[0];
    target.format = GpuTextureFormat.RGBA8_UNORM;
    target.writeMask = GpuColorWriteMask.ALL;
    rpd.fragment = fragment;

    Object lock = new Object();
    GpuRenderPipeline[] pipeline = new GpuRenderPipeline[1];
    GpuCreatePipelineAsyncStatus[] status = new GpuCreatePipelineAsyncStatus[1];
    String[] threadName = new String[1];
    device.createRenderPipelineAsync(rpd, GpuCallbackMode.ALLOW_SPONTANEOUS, (s, result, message) -> {
      synchronized (lock) {
        pipeline[0] = result;
        status[0] = s;
        threadName[0] = Thread.currentThread().getName();
        lock.notifyAll();
      }
    });
    long deadline = System.nanoTime() + 20_000_000_000L;
    synchronized (lock) {
      while (status[0] == null && System.nanoTime() < deadline) {
        lock.wait(100);
      }
    }
    assertEquals(GpuCreatePipelineAsyncStatus.SUCCESS, status[0]);
    assertNotNull(pipeline[0]);
    System.out.println("pipeline callback ran on thread '" + threadName[0] + "'");
    assertEquals("", Gpu.errors());

    pipeline[0].release();
    shader.release();
    device.release();
    adapter.release();
    instance.release();
  }
}
