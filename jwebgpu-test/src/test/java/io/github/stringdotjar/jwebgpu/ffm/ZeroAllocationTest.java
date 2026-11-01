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
import io.github.stringdotjar.jwebgpu.GpuBackendType;
import io.github.stringdotjar.jwebgpu.GpuBindGroup;
import io.github.stringdotjar.jwebgpu.GpuBindGroupDescriptor;
import io.github.stringdotjar.jwebgpu.GpuBindGroupLayout;
import io.github.stringdotjar.jwebgpu.GpuBuffer;
import io.github.stringdotjar.jwebgpu.GpuBufferDescriptor;
import io.github.stringdotjar.jwebgpu.GpuBufferUsage;
import io.github.stringdotjar.jwebgpu.GpuColorTargetState;
import io.github.stringdotjar.jwebgpu.GpuColorWriteMask;
import io.github.stringdotjar.jwebgpu.GpuCommandBuffer;
import io.github.stringdotjar.jwebgpu.GpuCommandEncoder;
import io.github.stringdotjar.jwebgpu.GpuConstants;
import io.github.stringdotjar.jwebgpu.GpuDevice;
import io.github.stringdotjar.jwebgpu.GpuFragmentState;
import io.github.stringdotjar.jwebgpu.GpuInstance;
import io.github.stringdotjar.jwebgpu.GpuLoadOp;
import io.github.stringdotjar.jwebgpu.GpuQueue;
import io.github.stringdotjar.jwebgpu.GpuRenderPassColorAttachment;
import io.github.stringdotjar.jwebgpu.GpuRenderPassDescriptor;
import io.github.stringdotjar.jwebgpu.GpuRenderPassEncoder;
import io.github.stringdotjar.jwebgpu.GpuRenderPipeline;
import io.github.stringdotjar.jwebgpu.GpuRenderPipelineDescriptor;
import io.github.stringdotjar.jwebgpu.GpuShaderModule;
import io.github.stringdotjar.jwebgpu.GpuShaderModuleDescriptor;
import io.github.stringdotjar.jwebgpu.GpuShaderSourceWGSL;
import io.github.stringdotjar.jwebgpu.GpuStoreOp;
import io.github.stringdotjar.jwebgpu.GpuTexture;
import io.github.stringdotjar.jwebgpu.GpuTextureDescriptor;
import io.github.stringdotjar.jwebgpu.GpuTextureDimension;
import io.github.stringdotjar.jwebgpu.GpuTextureFormat;
import io.github.stringdotjar.jwebgpu.GpuTextureUsage;
import io.github.stringdotjar.jwebgpu.GpuTextureView;
import io.github.stringdotjar.jwebgpu.GpuVertexAttribute;
import io.github.stringdotjar.jwebgpu.GpuVertexBufferLayout;
import io.github.stringdotjar.jwebgpu.GpuVertexFormat;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Proves that a frame of typical rendering calls allocates nothing on the Java heap.
 *
 * <p>A frame creates a command encoder and a render pass, records state and a draw, finishes the command buffer,
 * uploads some floats and submits. Every object is created once up front and refilled each frame, which is how a
 * game is meant to use the API. The test runs 10,000 frames after a warm-up and asks the JVM how many bytes this
 * thread allocated, then compares it with a control measurement of an empty loop. It uses Dawn's Null backend, so
 * it runs on machines without a GPU.
 */
class ZeroAllocationTest {

  private static final int FRAMES = 10_000;
  private static final int WARMUP = 30_000;

  private static final String WGSL = """
      @group(0) @binding(0) var<uniform> tint : vec4f;
      @vertex fn vs(@location(0) p : vec2f) -> @builtin(position) vec4f { return vec4f(p, 0.0, 1.0); }
      @fragment fn fs() -> @location(0) vec4f { return tint; }
      """;

  private static com.sun.management.ThreadMXBean bean() {
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    assumeTrue(bean instanceof com.sun.management.ThreadMXBean, "needs the HotSpot thread bean");
    com.sun.management.ThreadMXBean sun = (com.sun.management.ThreadMXBean) bean;
    assumeTrue(sun.isThreadAllocatedMemorySupported(), "thread allocation counters are not supported");
    sun.setThreadAllocatedMemoryEnabled(true);
    return sun;
  }

  @Test
  void aFrameOfRenderingCallsAllocatesNothing() {
    Gpu.require();
    com.sun.management.ThreadMXBean bean = bean();
    GpuInstance instance = Gpu.instance();
    GpuAdapter adapter = Gpu.adapter(instance, GpuBackendType.NULL);
    GpuDevice device = Gpu.device(instance, adapter);
    GpuQueue queue = device.getQueue();

    GpuTextureDescriptor td = new GpuTextureDescriptor();
    td.usage = GpuTextureUsage.RENDER_ATTACHMENT;
    td.dimension = GpuTextureDimension.D2;
    td.size.width = 64;
    td.size.height = 64;
    td.size.depthOrArrayLayers = 1;
    td.format = GpuTextureFormat.RGBA8_UNORM;
    GpuTexture texture = device.createTexture(td);
    GpuTextureView view = texture.createView(null);

    GpuShaderSourceWGSL wgsl = new GpuShaderSourceWGSL();
    wgsl.code = WGSL;
    GpuShaderModuleDescriptor smd = new GpuShaderModuleDescriptor();
    smd.nextInChain = wgsl;
    GpuShaderModule shader = device.createShaderModule(smd);

    GpuRenderPipelineDescriptor rpd = new GpuRenderPipelineDescriptor();
    rpd.vertex.module = shader;
    rpd.vertex.entryPoint = "vs";
    rpd.vertex.bufferCount = 1;
    GpuVertexBufferLayout layout = rpd.vertex.buffers[0];
    layout.arrayStride = 8;
    layout.attributeCount = 1;
    GpuVertexAttribute attribute = layout.attributes[0];
    attribute.format = GpuVertexFormat.FLOAT32X2;
    attribute.shaderLocation = 0;
    GpuFragmentState fragment = new GpuFragmentState();
    fragment.module = shader;
    fragment.entryPoint = "fs";
    fragment.targetCount = 1;
    GpuColorTargetState target = fragment.targets[0];
    target.format = GpuTextureFormat.RGBA8_UNORM;
    target.writeMask = GpuColorWriteMask.ALL;
    rpd.fragment = fragment;
    GpuRenderPipeline pipeline = device.createRenderPipeline(rpd);

    GpuBufferDescriptor vbd = new GpuBufferDescriptor();
    vbd.usage = GpuBufferUsage.VERTEX | GpuBufferUsage.COPY_DST;
    vbd.size = 64;
    GpuBuffer vertices = device.createBuffer(vbd);
    GpuBufferDescriptor ubd = new GpuBufferDescriptor();
    ubd.usage = GpuBufferUsage.UNIFORM | GpuBufferUsage.COPY_DST;
    ubd.size = 16;
    GpuBuffer uniforms = device.createBuffer(ubd);

    GpuBindGroupLayout bindLayout = pipeline.getBindGroupLayout(0);
    GpuBindGroupDescriptor bgd = new GpuBindGroupDescriptor();
    bgd.layout = bindLayout;
    bgd.entryCount = 1;
    bgd.entries[0].binding = 0;
    bgd.entries[0].buffer = uniforms;
    bgd.entries[0].size = 16;
    GpuBindGroup bindGroup = device.createBindGroup(bgd);

    GpuRenderPassDescriptor pass = new GpuRenderPassDescriptor();
    pass.label = "frame";
    pass.colorAttachmentCount = 1;
    GpuRenderPassColorAttachment color = pass.colorAttachments[0];
    color.view = view;
    color.loadOp = GpuLoadOp.CLEAR;
    color.storeOp = GpuStoreOp.STORE;
    color.clearValue.a = 1.0;

    GpuCommandEncoder encoder = device.createCommandEncoder(null);
    GpuRenderPassEncoder passEncoder = encoder.beginRenderPass(pass);
    passEncoder.end();
    GpuCommandBuffer commands = encoder.finish(null);
    GpuCommandBuffer[] submit = new GpuCommandBuffer[1];
    float[] data = {0f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f};
    int[] none = new int[0];
    commands.release();
    passEncoder.release();
    encoder.release();

    for (int i = 0; i < WARMUP; i++) {
      frame(device, queue, pass, pipeline, bindGroup, vertices, data, none, encoder, passEncoder, commands, submit);
    }

    long thread = Thread.currentThread().threadId();
    long baselineStart = bean.getThreadAllocatedBytes(thread);
    long baselineEnd = bean.getThreadAllocatedBytes(thread);
    long baseline = baselineEnd - baselineStart;

    long before = bean.getThreadAllocatedBytes(thread);
    for (int i = 0; i < FRAMES; i++) {
      frame(device, queue, pass, pipeline, bindGroup, vertices, data, none, encoder, passEncoder, commands, submit);
    }
    long after = bean.getThreadAllocatedBytes(thread);

    System.out.println("allocated bytes over " + FRAMES + " frames: " + (after - before - baseline));
    assertEquals(baseline, after - before, "a frame of rendering calls must not allocate");
    assertEquals("", Gpu.errors());

    bindGroup.release();
    bindLayout.release();
    uniforms.release();
    vertices.release();
    pipeline.release();
    shader.release();
    view.release();
    texture.release();
    queue.release();
    device.release();
    adapter.release();
    instance.release();
  }

  @Test
  void theMeasurementNoticesRealAllocations() {
    com.sun.management.ThreadMXBean bean = bean();
    long thread = Thread.currentThread().threadId();
    GpuRenderPassDescriptor[] keep = new GpuRenderPassDescriptor[100];
    long before = bean.getThreadAllocatedBytes(thread);
    for (int i = 0; i < keep.length; i++) {
      keep[i] = new GpuRenderPassDescriptor();
    }
    long after = bean.getThreadAllocatedBytes(thread);
    assertTrue(after - before > 100 * 100, "creating descriptors must show up as allocation");
  }

  private static void frame(GpuDevice device, GpuQueue queue, GpuRenderPassDescriptor pass,
      GpuRenderPipeline pipeline, GpuBindGroup bindGroup, GpuBuffer vertices, float[] data, int[] none,
      GpuCommandEncoder encoder, GpuRenderPassEncoder passEncoder, GpuCommandBuffer commands,
      GpuCommandBuffer[] submit) {
    device.createCommandEncoder(null, encoder);
    encoder.beginRenderPass(pass, passEncoder);
    passEncoder.setPipeline(pipeline);
    passEncoder.setBindGroup(0, bindGroup, none, 0);
    passEncoder.setVertexBuffer(0, vertices, 0, GpuConstants.WHOLE_SIZE);
    passEncoder.draw(3, 1, 0, 0);
    passEncoder.end();
    encoder.finish(null, commands);
    queue.writeBuffer(vertices, 0, data, 0, data.length);
    submit[0] = commands;
    queue.submit(submit, 1);
    commands.release();
    passEncoder.release();
    encoder.release();
  }
}
