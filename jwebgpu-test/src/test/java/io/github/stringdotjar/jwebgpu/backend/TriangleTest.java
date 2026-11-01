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
import io.github.stringdotjar.jwebgpu.GpuExtent3D;
import io.github.stringdotjar.jwebgpu.GpuFragmentState;
import io.github.stringdotjar.jwebgpu.GpuInstance;
import io.github.stringdotjar.jwebgpu.GpuLoadOp;
import io.github.stringdotjar.jwebgpu.GpuMapAsyncStatus;
import io.github.stringdotjar.jwebgpu.GpuMapMode;
import io.github.stringdotjar.jwebgpu.GpuPrimitiveTopology;
import io.github.stringdotjar.jwebgpu.GpuQueue;
import io.github.stringdotjar.jwebgpu.GpuRenderPassColorAttachment;
import io.github.stringdotjar.jwebgpu.GpuRenderPassDescriptor;
import io.github.stringdotjar.jwebgpu.GpuRenderPassEncoder;
import io.github.stringdotjar.jwebgpu.GpuRenderPipeline;
import io.github.stringdotjar.jwebgpu.GpuRenderPipelineDescriptor;
import io.github.stringdotjar.jwebgpu.GpuShaderModule;
import io.github.stringdotjar.jwebgpu.GpuShaderModuleDescriptor;
import io.github.stringdotjar.jwebgpu.GpuShaderSourceWGSL;
import io.github.stringdotjar.jwebgpu.GpuStatus;
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

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Draws one triangle into an offscreen texture through the generated API and reads the pixels back.
 *
 * <p>This is the first end-to-end proof that the generated backend works: it covers the instance, the adapter and
 * device requests with callbacks, the descriptors (including a chained WGSL source), the render pass, the copy to a
 * buffer, the asynchronous buffer map and the mapped memory.
 *
 * <p>The same flow runs on Dawn's Null backend (nothing is drawn, so it only has to finish without errors), on the
 * default adapter (a real GPU if there is one) and on a software Vulkan driver when {@code VK_ICD_FILENAMES} points
 * to one.
 */
class TriangleTest {

  private static final int SIZE = 64;
  private static final int ROW = 256;

  private static final String WGSL = """
      @vertex fn vs(@builtin(vertex_index) i : u32) -> @builtin(position) vec4f {
        var p = array<vec2f, 3>(vec2f(0.0, 0.8), vec2f(-0.8, -0.8), vec2f(0.8, -0.8));
        return vec4f(p[i], 0.0, 1.0);
      }
      @fragment fn fs() -> @location(0) vec4f { return vec4f(1.0, 0.0, 0.0, 1.0); }
      """;

  private static int[] center = new int[4];
  private static int[] corner = new int[4];

  @Test
  void drawsOnTheNullBackendWithoutErrors() {
    Gpu.require();
    GpuAdapterInfo info = run(GpuBackendType.NULL);
    assertEquals(GpuBackendType.NULL, info.backendType);
    assertEquals("", Gpu.errors());
  }

  @Test
  void drawsOnTheDefaultAdapter() {
    Gpu.require();
    GpuAdapterInfo info = run(GpuBackendType.UNDEFINED);
    System.out.println("default adapter: " + info.backendType + " / " + info.adapterType + " / " + info.device + " / "
        + info.description);
    assertEquals("", Gpu.errors());
    assertRendered();
  }

  @Test
  void drawsOnSoftwareVulkanWhenConfigured() {
    Gpu.require();
    String icd = System.getenv("VK_ICD_FILENAMES");
    assumeTrue(icd != null && !icd.isEmpty(), "VK_ICD_FILENAMES is not set");
    GpuAdapterInfo info = run(GpuBackendType.VULKAN);
    System.out.println("vulkan adapter: " + info.adapterType + " / " + info.device);
    assertEquals("", Gpu.errors());
    assertRendered();
  }

  private static void assertRendered() {
    assertEquals(255, center[0], "center red");
    assertEquals(0, center[1], "center green");
    assertEquals(0, center[2], "center blue");
    assertEquals(255, center[3], "center alpha");
    assertEquals(0, corner[0], "corner red");
    assertEquals(0, corner[1], "corner green");
    assertEquals(255, corner[2], "corner blue");
    assertEquals(255, corner[3], "corner alpha");
  }

  private static GpuAdapterInfo run(GpuBackendType backend) {
    GpuInstance instance = Gpu.instance();
    GpuAdapter adapter = Gpu.adapter(instance, backend);
    GpuAdapterInfo info = new GpuAdapterInfo();
    assertEquals(GpuStatus.SUCCESS, adapter.getInfo(info));
    GpuDevice device = Gpu.device(instance, adapter);
    GpuQueue queue = device.getQueue();

    GpuTextureDescriptor td = new GpuTextureDescriptor();
    td.label = "target";
    td.usage = GpuTextureUsage.RENDER_ATTACHMENT | GpuTextureUsage.COPY_SRC;
    td.dimension = GpuTextureDimension.D2;
    td.size.width = SIZE;
    td.size.height = SIZE;
    td.size.depthOrArrayLayers = 1;
    td.format = GpuTextureFormat.RGBA8_UNORM;
    GpuTexture texture = device.createTexture(td);
    GpuTextureView view = texture.createView(null);

    GpuShaderSourceWGSL wgsl = new GpuShaderSourceWGSL();
    wgsl.code = WGSL;
    GpuShaderModuleDescriptor smd = new GpuShaderModuleDescriptor();
    smd.label = "triangle shader";
    smd.nextInChain = wgsl;
    GpuShaderModule shader = device.createShaderModule(smd);

    GpuRenderPipelineDescriptor rpd = new GpuRenderPipelineDescriptor();
    rpd.vertex.module = shader;
    rpd.vertex.entryPoint = "vs";
    rpd.primitive.topology = GpuPrimitiveTopology.TRIANGLE_LIST;
    GpuFragmentState fragment = new GpuFragmentState();
    fragment.module = shader;
    fragment.entryPoint = "fs";
    fragment.targetCount = 1;
    GpuColorTargetState target = fragment.targets[0];
    target.format = GpuTextureFormat.RGBA8_UNORM;
    target.writeMask = GpuColorWriteMask.ALL;
    rpd.fragment = fragment;
    GpuRenderPipeline pipeline = device.createRenderPipeline(rpd);

    GpuBufferDescriptor bd = new GpuBufferDescriptor();
    bd.label = "readback";
    bd.usage = GpuBufferUsage.MAP_READ | GpuBufferUsage.COPY_DST;
    bd.size = (long) ROW * SIZE;
    GpuBuffer buffer = device.createBuffer(bd);
    assertNotNull(buffer);

    GpuCommandEncoder encoder = device.createCommandEncoder(null);
    GpuRenderPassDescriptor pass = new GpuRenderPassDescriptor();
    pass.colorAttachmentCount = 1;
    GpuRenderPassColorAttachment color = pass.colorAttachments[0];
    color.view = view;
    color.loadOp = GpuLoadOp.CLEAR;
    color.storeOp = GpuStoreOp.STORE;
    color.clearValue.b = 1.0;
    color.clearValue.a = 1.0;
    GpuRenderPassEncoder rp = encoder.beginRenderPass(pass);
    rp.setPipeline(pipeline);
    rp.draw(3, 1, 0, 0);
    rp.end();

    GpuTexelCopyTextureInfo src = new GpuTexelCopyTextureInfo();
    src.texture = texture;
    src.aspect = GpuTextureAspect.ALL;
    GpuTexelCopyBufferInfo dst = new GpuTexelCopyBufferInfo();
    dst.buffer = buffer;
    dst.layout.bytesPerRow = ROW;
    dst.layout.rowsPerImage = SIZE;
    GpuExtent3D extent = new GpuExtent3D();
    extent.width = SIZE;
    extent.height = SIZE;
    extent.depthOrArrayLayers = 1;
    encoder.copyTextureToBuffer(src, dst, extent);
    GpuCommandBuffer commands = encoder.finish(null);
    queue.submit(new GpuCommandBuffer[] { commands }, 1);

    GpuMapAsyncStatus[] status = new GpuMapAsyncStatus[1];
    boolean[] done = new boolean[1];
    buffer.mapAsync(GpuMapMode.READ, 0, bd.size, GpuCallbackMode.ALLOW_PROCESS_EVENTS, (s, message) -> {
      status[0] = s;
      done[0] = true;
    });
    Gpu.await(instance, done);
    assertEquals(GpuMapAsyncStatus.SUCCESS, status[0]);
    ByteBuffer mapped = buffer.getConstMappedRange(0, bd.size);
    assertNotNull(mapped);
    assertTrue(mapped.isReadOnly());
    pixel(mapped, 32, 32, center);
    pixel(mapped, 1, 1, corner);
    buffer.unmap();

    commands.release();
    rp.release();
    encoder.release();
    buffer.release();
    pipeline.release();
    shader.release();
    view.release();
    texture.release();
    queue.release();
    device.release();
    adapter.release();
    instance.release();
    assertTrue(commands.isReleased());
    assertTrue(device.isReleased());
    return info;
  }

  private static void pixel(ByteBuffer mapped, int x, int y, int[] out) {
    int at = y * ROW + x * 4;
    for (int i = 0; i < 4; i++) {
      out[i] = mapped.get(at + i) & 0xFF;
    }
  }
}
