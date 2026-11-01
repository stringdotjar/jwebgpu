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
package io.github.stringdotjar.jwebgpu.samples;

import io.github.stringdotjar.jwebgpu.GpuAdapter;
import io.github.stringdotjar.jwebgpu.GpuAdapterInfo;
import io.github.stringdotjar.jwebgpu.GpuBackendType;
import io.github.stringdotjar.jwebgpu.GpuBuffer;
import io.github.stringdotjar.jwebgpu.GpuBufferDescriptor;
import io.github.stringdotjar.jwebgpu.GpuBufferUsage;
import io.github.stringdotjar.jwebgpu.GpuCallbackMode;
import io.github.stringdotjar.jwebgpu.GpuColorTargetState;
import io.github.stringdotjar.jwebgpu.GpuColorWriteMask;
import io.github.stringdotjar.jwebgpu.GpuCommandBuffer;
import io.github.stringdotjar.jwebgpu.GpuCommandEncoder;
import io.github.stringdotjar.jwebgpu.GpuDevice;
import io.github.stringdotjar.jwebgpu.GpuDeviceDescriptor;
import io.github.stringdotjar.jwebgpu.GpuExtent3D;
import io.github.stringdotjar.jwebgpu.GpuFragmentState;
import io.github.stringdotjar.jwebgpu.GpuInstance;
import io.github.stringdotjar.jwebgpu.GpuLoadOp;
import io.github.stringdotjar.jwebgpu.GpuMapAsyncStatus;
import io.github.stringdotjar.jwebgpu.GpuMapMode;
import io.github.stringdotjar.jwebgpu.GpuQueue;
import io.github.stringdotjar.jwebgpu.GpuRenderPassColorAttachment;
import io.github.stringdotjar.jwebgpu.GpuRenderPassDescriptor;
import io.github.stringdotjar.jwebgpu.GpuRenderPassEncoder;
import io.github.stringdotjar.jwebgpu.GpuRenderPipeline;
import io.github.stringdotjar.jwebgpu.GpuRenderPipelineDescriptor;
import io.github.stringdotjar.jwebgpu.GpuRequestAdapterOptions;
import io.github.stringdotjar.jwebgpu.GpuShaderModule;
import io.github.stringdotjar.jwebgpu.GpuShaderModuleDescriptor;
import io.github.stringdotjar.jwebgpu.GpuShaderSourceWGSL;
import io.github.stringdotjar.jwebgpu.GpuStoreOp;
import io.github.stringdotjar.jwebgpu.GpuTexelCopyBufferInfo;
import io.github.stringdotjar.jwebgpu.GpuTexelCopyTextureInfo;
import io.github.stringdotjar.jwebgpu.GpuTexture;
import io.github.stringdotjar.jwebgpu.GpuTextureAspect;
import io.github.stringdotjar.jwebgpu.GpuTextureDescriptor;
import io.github.stringdotjar.jwebgpu.GpuTextureDimension;
import io.github.stringdotjar.jwebgpu.GpuTextureFormat;
import io.github.stringdotjar.jwebgpu.GpuTextureUsage;
import io.github.stringdotjar.jwebgpu.GpuTextureView;
import io.github.stringdotjar.jwebgpu.JWebGpu;
import io.github.stringdotjar.jwebgpu.ffm.JWebGpuFfm;

import java.nio.ByteBuffer;
import java.util.function.BooleanSupplier;

/**
 * Draws a red triangle on a blue background into a texture that is never shown, and prints the result as text.
 *
 * <p>There is no window and no swap chain, so the sample runs anywhere WebGPU runs, even on a machine without a
 * display. It walks through the steps every program needs: install the backend, create an instance, ask for an
 * adapter and a device, build a pipeline from a WGSL shader, record and submit a render pass, and read the pixels
 * back through a mapped buffer.
 *
 * <p>Run it with {@code ./gradlew :samples:triangle-offscreen:run}, or build it as a GraalVM native image with
 * {@code ./gradlew :samples:triangle-offscreen:nativeCompile}. Pass {@code null} as the only argument to use Dawn's Null
 * backend, which accepts every call but draws nothing, so it needs no graphics card.
 */
public final class TriangleOffscreen {

  private static final int SIZE = 32;
  private static final int ROW_BYTES = 256;

  private static final String SHADER = """
      @vertex fn vs(@builtin(vertex_index) i : u32) -> @builtin(position) vec4f {
        var p = array<vec2f, 3>(vec2f(0.0, 0.8), vec2f(-0.8, -0.8), vec2f(0.8, -0.8));
        return vec4f(p[i], 0.0, 1.0);
      }
      @fragment fn fs() -> @location(0) vec4f { return vec4f(1.0, 0.0, 0.0, 1.0); }
      """;

  private TriangleOffscreen() {}

  /**
   * Runs the sample.
   *
   * @param args Optionally {@code null}, to use Dawn's Null backend instead of the first real adapter.
   */
  public static void main(String[] args) {
    JWebGpuFfm.install();
    GpuInstance instance = JWebGpu.createInstance();

    GpuAdapter[] adapter = new GpuAdapter[1];
    GpuRequestAdapterOptions options = null;
    if (args.length > 0 && args[0].equals("null")) {
      options = new GpuRequestAdapterOptions();
      options.backendType = GpuBackendType.NULL;
    }
    instance.requestAdapter(options, GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, result, message) -> {
      adapter[0] = result;
      if (result == null) {
        System.err.println("No adapter (" + status + "): " + message);
      }
    });
    waitFor(instance, () -> adapter[0] != null);

    GpuAdapterInfo info = new GpuAdapterInfo();
    adapter[0].getInfo(info);
    System.out.println("Adapter: " + info.device + " (" + info.backendType + ")");

    GpuDeviceDescriptor deviceDescriptor = new GpuDeviceDescriptor();
    deviceDescriptor.uncapturedErrorCallback =
        (device, type, message) -> System.err.println("WebGPU error (" + type + "): " + message);
    GpuDevice[] device = new GpuDevice[1];
    adapter[0].requestDevice(deviceDescriptor, GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, result, message) -> {
      device[0] = result;
      if (result == null) {
        System.err.println("No device (" + status + "): " + message);
      }
    });
    waitFor(instance, () -> device[0] != null);
    GpuQueue queue = device[0].getQueue();

    GpuTextureDescriptor textureDescriptor = new GpuTextureDescriptor();
    textureDescriptor.usage = GpuTextureUsage.RENDER_ATTACHMENT | GpuTextureUsage.COPY_SRC;
    textureDescriptor.dimension = GpuTextureDimension.D2;
    textureDescriptor.size.width = SIZE;
    textureDescriptor.size.height = SIZE;
    textureDescriptor.format = GpuTextureFormat.RGBA8_UNORM;
    GpuTexture texture = device[0].createTexture(textureDescriptor);
    GpuTextureView view = texture.createView(null);

    GpuShaderSourceWGSL wgsl = new GpuShaderSourceWGSL();
    wgsl.code = SHADER;
    GpuShaderModuleDescriptor shaderDescriptor = new GpuShaderModuleDescriptor();
    shaderDescriptor.nextInChain = wgsl;
    GpuShaderModule shader = device[0].createShaderModule(shaderDescriptor);

    GpuRenderPipelineDescriptor pipelineDescriptor = new GpuRenderPipelineDescriptor();
    pipelineDescriptor.vertex.module = shader;
    pipelineDescriptor.vertex.entryPoint = "vs";
    GpuFragmentState fragment = new GpuFragmentState();
    fragment.module = shader;
    fragment.entryPoint = "fs";
    fragment.targetCount = 1;
    GpuColorTargetState target = fragment.targets[0];
    target.format = GpuTextureFormat.RGBA8_UNORM;
    target.writeMask = GpuColorWriteMask.ALL;
    pipelineDescriptor.fragment = fragment;
    GpuRenderPipeline pipeline = device[0].createRenderPipeline(pipelineDescriptor);

    GpuBufferDescriptor bufferDescriptor = new GpuBufferDescriptor();
    bufferDescriptor.usage = GpuBufferUsage.MAP_READ | GpuBufferUsage.COPY_DST;
    bufferDescriptor.size = (long) ROW_BYTES * SIZE;
    GpuBuffer readback = device[0].createBuffer(bufferDescriptor);

    GpuCommandEncoder encoder = device[0].createCommandEncoder(null);
    GpuRenderPassDescriptor pass = new GpuRenderPassDescriptor();
    pass.colorAttachmentCount = 1;
    GpuRenderPassColorAttachment color = pass.colorAttachments[0];
    color.view = view;
    color.loadOp = GpuLoadOp.CLEAR;
    color.storeOp = GpuStoreOp.STORE;
    color.clearValue.b = 1.0;
    color.clearValue.a = 1.0;
    GpuRenderPassEncoder renderPass = encoder.beginRenderPass(pass);
    renderPass.setPipeline(pipeline);
    renderPass.draw(3, 1, 0, 0);
    renderPass.end();

    GpuTexelCopyTextureInfo source = new GpuTexelCopyTextureInfo();
    source.texture = texture;
    source.aspect = GpuTextureAspect.ALL;
    GpuTexelCopyBufferInfo destination = new GpuTexelCopyBufferInfo();
    destination.buffer = readback;
    destination.layout.bytesPerRow = ROW_BYTES;
    destination.layout.rowsPerImage = SIZE;
    GpuExtent3D extent = new GpuExtent3D();
    extent.width = SIZE;
    extent.height = SIZE;
    encoder.copyTextureToBuffer(source, destination, extent);
    GpuCommandBuffer commands = encoder.finish(null);
    queue.submit(new GpuCommandBuffer[] { commands }, 1);

    GpuMapAsyncStatus[] mapped = new GpuMapAsyncStatus[1];
    readback.mapAsync(GpuMapMode.READ, 0, bufferDescriptor.size, GpuCallbackMode.ALLOW_PROCESS_EVENTS,
        (status, message) -> mapped[0] = status);
    waitFor(instance, () -> mapped[0] != null);
    ByteBuffer pixels = readback.getConstMappedRange(0, bufferDescriptor.size);
    print(pixels);
    readback.unmap();

    commands.release();
    renderPass.release();
    encoder.release();
    readback.release();
    pipeline.release();
    shader.release();
    view.release();
    texture.release();
    queue.release();
    device[0].release();
    adapter[0].release();
    instance.release();
  }

  private static void print(ByteBuffer pixels) {
    if (pixels == null) {
      System.out.println("The buffer could not be mapped.");
      return;
    }
    StringBuilder text = new StringBuilder();
    for (int y = 0; y < SIZE; y += 2) {
      for (int x = 0; x < SIZE; x++) {
        int at = y * ROW_BYTES + x * 4;
        boolean red = (pixels.get(at) & 0xFF) > 127;
        boolean blue = (pixels.get(at + 2) & 0xFF) > 127;
        text.append(red ? '#' : blue ? '.' : '?');
      }
      text.append('\n');
    }
    System.out.print(text);
    int center = (SIZE / 2) * ROW_BYTES + (SIZE / 2) * 4;
    System.out.println("Center pixel (r, g, b, a): " + (pixels.get(center) & 0xFF) + ", "
        + (pixels.get(center + 1) & 0xFF) + ", " + (pixels.get(center + 2) & 0xFF) + ", "
        + (pixels.get(center + 3) & 0xFF));
  }

  private static void waitFor(GpuInstance instance, BooleanSupplier done) {
    long deadline = System.nanoTime() + 20_000_000_000L;
    while (!done.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new IllegalStateException("Timed out waiting for the GPU.");
      }
      instance.processEvents();
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted.", e);
      }
    }
  }
}
