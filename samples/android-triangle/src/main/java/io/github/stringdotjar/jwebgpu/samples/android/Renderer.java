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
package io.github.stringdotjar.jwebgpu.samples.android;

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
import io.github.stringdotjar.jwebgpu.GpuTextureFormat;
import io.github.stringdotjar.jwebgpu.GpuTextureView;
import io.github.stringdotjar.jwebgpu.GpuVertexAttribute;
import io.github.stringdotjar.jwebgpu.GpuVertexBufferLayout;
import io.github.stringdotjar.jwebgpu.GpuVertexFormat;

/**
 * Draws one triangle into any texture view, creating every object once and reusing it for every frame.
 *
 * <p>The vertices come from a vertex buffer and the color from a uniform buffer, so a frame exercises buffer uploads,
 * a bind group, a render pass and a submit. All the objects of a frame (encoder, pass encoder, command buffer,
 * descriptors) are created in the constructor and refilled by {@link #frame}, which allocates nothing.
 */
final class Renderer {

  private static final String WGSL = """
      @group(0) @binding(0) var<uniform> tint : vec4f;
      @vertex fn vs(@location(0) p : vec2f) -> @builtin(position) vec4f { return vec4f(p, 0.0, 1.0); }
      @fragment fn fs() -> @location(0) vec4f { return tint; }
      """;

  final GpuBuffer vertices;
  final GpuRenderPassEncoder passEncoder;

  private final GpuDevice device;
  private final GpuQueue queue;
  private final GpuShaderModule shader;
  private final GpuRenderPipeline pipeline;
  private final GpuBuffer uniforms;
  private final GpuBindGroupLayout bindLayout;
  private final GpuBindGroup bindGroup;
  private final GpuRenderPassDescriptor pass = new GpuRenderPassDescriptor();
  private final GpuCommandEncoder encoder;
  private final GpuCommandBuffer commands;
  private final GpuCommandBuffer[] submit = new GpuCommandBuffer[1];
  private final float[] triangle = { 0f, 0.7f, -0.7f, -0.7f, 0.7f, -0.7f };
  private final float[] tint = { 1f, 0.2f, 0.1f, 1f };

  /**
   * Creates the pipeline, the buffers and the per-frame objects.
   *
   * @param device The device that owns everything.
   * @param format The format of the views that {@link #frame} draws into.
   * @param first A view of that format, used once to create the per-frame objects.
   */
  Renderer(GpuDevice device, GpuTextureFormat format, GpuTextureView first) {
    this.device = device;
    this.queue = device.getQueue();

    GpuShaderSourceWGSL wgsl = new GpuShaderSourceWGSL();
    wgsl.code = WGSL;
    GpuShaderModuleDescriptor shaderDescriptor = new GpuShaderModuleDescriptor();
    shaderDescriptor.nextInChain = wgsl;
    shader = device.createShaderModule(shaderDescriptor);

    GpuRenderPipelineDescriptor pipelineDescriptor = new GpuRenderPipelineDescriptor();
    pipelineDescriptor.vertex.module = shader;
    pipelineDescriptor.vertex.entryPoint = "vs";
    pipelineDescriptor.vertex.bufferCount = 1;
    GpuVertexBufferLayout layout = pipelineDescriptor.vertex.buffers[0];
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
    target.format = format;
    target.writeMask = GpuColorWriteMask.ALL;
    pipelineDescriptor.fragment = fragment;
    pipeline = device.createRenderPipeline(pipelineDescriptor);

    GpuBufferDescriptor vertexBuffer = new GpuBufferDescriptor();
    vertexBuffer.usage = GpuBufferUsage.VERTEX | GpuBufferUsage.COPY_DST;
    vertexBuffer.size = 64;
    vertices = device.createBuffer(vertexBuffer);
    GpuBufferDescriptor uniformBuffer = new GpuBufferDescriptor();
    uniformBuffer.usage = GpuBufferUsage.UNIFORM | GpuBufferUsage.COPY_DST;
    uniformBuffer.size = 16;
    uniforms = device.createBuffer(uniformBuffer);

    bindLayout = pipeline.getBindGroupLayout(0);
    GpuBindGroupDescriptor bindDescriptor = new GpuBindGroupDescriptor();
    bindDescriptor.layout = bindLayout;
    bindDescriptor.entryCount = 1;
    bindDescriptor.entries[0].binding = 0;
    bindDescriptor.entries[0].buffer = uniforms;
    bindDescriptor.entries[0].size = 16;
    bindGroup = device.createBindGroup(bindDescriptor);

    pass.label = "frame";
    pass.colorAttachmentCount = 1;
    GpuRenderPassColorAttachment color = pass.colorAttachments[0];
    color.loadOp = GpuLoadOp.CLEAR;
    color.storeOp = GpuStoreOp.STORE;
    color.clearValue.b = 0.4;
    color.clearValue.a = 1.0;
    color.view = first;

    // Create the per-frame objects once, so a frame only refills them.
    encoder = device.createCommandEncoder(null);
    passEncoder = encoder.beginRenderPass(pass);
    passEncoder.end();
    commands = encoder.finish(null);
    commands.release();
    passEncoder.release();
    encoder.release();
  }

  /**
   * Records, uploads and submits one frame.
   *
   * @param target The view to draw into, which must have the format given to the constructor.
   * @param shade A number from 0 to 1 that changes the clear color, so successive frames differ.
   */
  void frame(GpuTextureView target, float shade) {
    pass.colorAttachments[0].view = target;
    pass.colorAttachments[0].clearValue.r = shade;
    tint[1] = 0.2f + 0.3f * shade;
    device.createCommandEncoder(null, encoder);
    encoder.beginRenderPass(pass, passEncoder);
    passEncoder.setPipeline(pipeline);
    passEncoder.setBindGroup(0, bindGroup);
    passEncoder.setVertexBuffer(0, vertices, 0, GpuConstants.WHOLE_SIZE);
    passEncoder.draw(3, 1, 0, 0);
    passEncoder.end();
    encoder.finish(null, commands);
    queue.writeBuffer(vertices, 0, triangle, 0, triangle.length);
    queue.writeBuffer(uniforms, 0, tint, 0, tint.length);
    submit[0] = commands;
    queue.submit(submit, 1);
    commands.release();
    passEncoder.release();
    encoder.release();
  }

  /**
   * Opens a pass and calls a cheap method on it, so the cost of a call can be measured without a frame around it.
   *
   * @param target The view to draw into.
   * @param calls How many calls to make.
   * @return The elapsed time in nanoseconds, including the loop.
   */
  long timeViewportCalls(GpuTextureView target, int calls) {
    pass.colorAttachments[0].view = target;
    device.createCommandEncoder(null, encoder);
    encoder.beginRenderPass(pass, passEncoder);
    long start = System.nanoTime();
    for (int i = 0; i < calls; i++) {
      passEncoder.setViewport(0f, 0f, 64f, 64f, 0f, 1f);
    }
    long elapsed = System.nanoTime() - start;
    passEncoder.end();
    encoder.finish(null, commands);
    commands.release();
    passEncoder.release();
    encoder.release();
    return elapsed;
  }

  /**
   * Calls {@code setPipeline} many times inside one pass.
   *
   * @param target The view to draw into.
   * @param calls How many calls to make.
   * @return The elapsed time in nanoseconds, including the loop.
   */
  long timePipelineCalls(GpuTextureView target, int calls) {
    pass.colorAttachments[0].view = target;
    device.createCommandEncoder(null, encoder);
    encoder.beginRenderPass(pass, passEncoder);
    long start = System.nanoTime();
    for (int i = 0; i < calls; i++) {
      passEncoder.setPipeline(pipeline);
    }
    long elapsed = System.nanoTime() - start;
    passEncoder.end();
    encoder.finish(null, commands);
    commands.release();
    passEncoder.release();
    encoder.release();
    return elapsed;
  }

  /**
   * Calls a getter many times.
   *
   * @param calls How many calls to make.
   * @return The elapsed time in nanoseconds, including the loop.
   */
  long timeGetterCalls(int calls) {
    long sum = 0;
    long start = System.nanoTime();
    for (int i = 0; i < calls; i++) {
      sum += vertices.getSize();
    }
    long elapsed = System.nanoTime() - start;
    return sum == 0 ? -1 : elapsed;
  }

  /**
   * Uploads 16 bytes many times.
   *
   * @param calls How many uploads to make.
   * @return The elapsed time in nanoseconds, including the loop.
   */
  long timeUploads(int calls) {
    long start = System.nanoTime();
    for (int i = 0; i < calls; i++) {
      queue.writeBuffer(uniforms, 0, tint, 0, tint.length);
    }
    return System.nanoTime() - start;
  }

  void release() {
    bindGroup.release();
    bindLayout.release();
    uniforms.release();
    vertices.release();
    pipeline.release();
    shader.release();
    queue.release();
  }
}
