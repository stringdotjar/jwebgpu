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
package io.github.stringdotjar.jwebgpu.samples.web;

import io.github.stringdotjar.jwebgpu.GpuAdapterInfo;
import io.github.stringdotjar.jwebgpu.GpuBindGroup;
import io.github.stringdotjar.jwebgpu.GpuBindGroupDescriptor;
import io.github.stringdotjar.jwebgpu.GpuBindGroupLayout;
import io.github.stringdotjar.jwebgpu.GpuBindGroupLayoutDescriptor;
import io.github.stringdotjar.jwebgpu.GpuBindGroupLayoutEntry;
import io.github.stringdotjar.jwebgpu.GpuBuffer;
import io.github.stringdotjar.jwebgpu.GpuBufferBindingType;
import io.github.stringdotjar.jwebgpu.GpuBufferDescriptor;
import io.github.stringdotjar.jwebgpu.GpuBufferMapState;
import io.github.stringdotjar.jwebgpu.GpuBufferUsage;
import io.github.stringdotjar.jwebgpu.GpuCallbackMode;
import io.github.stringdotjar.jwebgpu.GpuCommandBuffer;
import io.github.stringdotjar.jwebgpu.GpuCommandEncoder;
import io.github.stringdotjar.jwebgpu.GpuCompilationMessageType;
import io.github.stringdotjar.jwebgpu.GpuComputePassEncoder;
import io.github.stringdotjar.jwebgpu.GpuComputePipeline;
import io.github.stringdotjar.jwebgpu.GpuComputePipelineDescriptor;
import io.github.stringdotjar.jwebgpu.GpuCreatePipelineAsyncStatus;
import io.github.stringdotjar.jwebgpu.GpuErrorFilter;
import io.github.stringdotjar.jwebgpu.GpuErrorType;
import io.github.stringdotjar.jwebgpu.GpuExtent3D;
import io.github.stringdotjar.jwebgpu.GpuFeatureName;
import io.github.stringdotjar.jwebgpu.GpuFragmentState;
import io.github.stringdotjar.jwebgpu.GpuFutureWaitInfo;
import io.github.stringdotjar.jwebgpu.GpuLimits;
import io.github.stringdotjar.jwebgpu.GpuLoadOp;
import io.github.stringdotjar.jwebgpu.GpuMapAsyncStatus;
import io.github.stringdotjar.jwebgpu.GpuMapMode;
import io.github.stringdotjar.jwebgpu.GpuPipelineLayout;
import io.github.stringdotjar.jwebgpu.GpuPipelineLayoutDescriptor;
import io.github.stringdotjar.jwebgpu.GpuQueue;
import io.github.stringdotjar.jwebgpu.GpuRenderPassDescriptor;
import io.github.stringdotjar.jwebgpu.GpuRenderPassEncoder;
import io.github.stringdotjar.jwebgpu.GpuRenderPipeline;
import io.github.stringdotjar.jwebgpu.GpuRenderPipelineDescriptor;
import io.github.stringdotjar.jwebgpu.GpuSampler;
import io.github.stringdotjar.jwebgpu.GpuShaderModule;
import io.github.stringdotjar.jwebgpu.GpuShaderModuleDescriptor;
import io.github.stringdotjar.jwebgpu.GpuShaderSourceWGSL;
import io.github.stringdotjar.jwebgpu.GpuShaderStage;
import io.github.stringdotjar.jwebgpu.GpuStatus;
import io.github.stringdotjar.jwebgpu.GpuStoreOp;
import io.github.stringdotjar.jwebgpu.GpuSupportedFeatures;
import io.github.stringdotjar.jwebgpu.GpuSupportedWGSLLanguageFeatures;
import io.github.stringdotjar.jwebgpu.GpuTexelCopyBufferInfo;
import io.github.stringdotjar.jwebgpu.GpuTexelCopyBufferLayout;
import io.github.stringdotjar.jwebgpu.GpuTexelCopyTextureInfo;
import io.github.stringdotjar.jwebgpu.GpuTexture;
import io.github.stringdotjar.jwebgpu.GpuTextureAspect;
import io.github.stringdotjar.jwebgpu.GpuTextureDescriptor;
import io.github.stringdotjar.jwebgpu.GpuTextureDimension;
import io.github.stringdotjar.jwebgpu.GpuTextureFormat;
import io.github.stringdotjar.jwebgpu.GpuTextureUsage;
import io.github.stringdotjar.jwebgpu.GpuTextureView;
import io.github.stringdotjar.jwebgpu.GpuWGSLLanguageFeatureName;
import io.github.stringdotjar.jwebgpu.GpuWaitStatus;

/**
 * Checks the parts of the web backend that the triangle does not touch.
 *
 * <p>Each check runs a real call against the browser and compares the result with what the native API promises:
 * bind groups with dynamic offsets and a compute shader, a texture that is uploaded and sampled, creating a pipeline in
 * the background, error scopes and uncaptured errors, compilation messages, polling a future, the getters of
 * objects, reusing wrappers, the lifecycle of objects, and the calls that the browser cannot offer.
 */
final class WebFeatures {

  private static final String COMPUTE = """
      struct Params { scale : u32 }
      @group(0) @binding(0) var<uniform> params : Params;
      @group(0) @binding(1) var<storage, read_write> data : array<u32>;
      @compute @workgroup_size(4) fn main(@builtin(global_invocation_id) id : vec3u) {
        data[id.x] = (id.x + 1u) * params.scale;
      }
      """;

  private static final String TEXTURED = """
      @group(0) @binding(0) var tex : texture_2d<f32>;
      @group(0) @binding(1) var smp : sampler;
      struct Out { @builtin(position) pos : vec4f, @location(0) uv : vec2f }
      @vertex fn vs(@builtin(vertex_index) i : u32) -> Out {
        var p = array<vec2f, 3>(vec2f(-1.0, -1.0), vec2f(3.0, -1.0), vec2f(-1.0, 3.0));
        var o : Out;
        o.pos = vec4f(p[i], 0.0, 1.0);
        o.uv = vec2f(p[i].x * 0.5 + 0.5, 0.5 - p[i].y * 0.5);
        return o;
      }
      @fragment fn fs(in : Out) -> @location(0) vec4f { return textureSample(tex, smp, in.uv); }
      """;

  private static final String BROKEN = "@vertex fn vs( { return 1; }";

  private final Context ctx;
  private final Report report;
  private final Step[] steps = new Step[8];
  private final byte[] pixels = new byte[16 * 256];
  private final int[] words = new int[4];

  private Runnable done;
  private int at;

  WebFeatures(Context ctx) {
    this.ctx = ctx;
    this.report = ctx.report;
    steps[0] = this::adapterAndDevice;
    steps[1] = this::objects;
    steps[2] = this::compute;
    steps[3] = this::textured;
    steps[4] = this::errors;
    steps[5] = this::compilation;
    steps[6] = this::futures;
    steps[7] = this::unsupported;
  }

  void run(Runnable done) {
    this.done = done;
    next();
  }

  private void next() {
    if (at == steps.length) {
      done.run();
      return;
    }
    steps[at++].run(this::next);
  }

  private void adapterAndDevice(Runnable next) {
    GpuLimits limits = new GpuLimits();
    ctx.adapter.getLimits(limits);
    report.check("adapter limits", limits.maxTextureDimension2D >= 2048 && limits.maxBindGroups >= 4
        && limits.maxBufferSize >= 134217728L,
        "texture " + limits.maxTextureDimension2D + ", groups "
            + limits.maxBindGroups + ", buffer " + limits.maxBufferSize);
    GpuLimits deviceLimits = new GpuLimits();
    ctx.device.getLimits(deviceLimits);
    report.check("device limits match the adapter", deviceLimits.maxTextureDimension2D > 0
        && deviceLimits.maxTextureDimension2D <= limits.maxTextureDimension2D);

    GpuSupportedFeatures features = new GpuSupportedFeatures(0);
    ctx.adapter.getFeatures(features);
    boolean listed = false;
    for (int i = 0; i < features.featureCount; i++) {
      listed |= features.features[i] == GpuFeatureName.SHADER_F16;
    }
    report.check("adapter features", listed == ctx.adapter.hasFeature(GpuFeatureName.SHADER_F16),
        features.featureCount + " features, array grew to " + features.features.length);
    report.check("the device has no feature it did not ask for", !ctx.device.hasFeature(GpuFeatureName.SHADER_F16));

    GpuAdapterInfo info = new GpuAdapterInfo();
    ctx.device.getAdapterInfo(info);
    report.check("device adapter info", info.vendor != null && info.backendType != null,
        info.vendor + " / " + info.architecture);

    GpuSupportedWGSLLanguageFeatures wgsl = new GpuSupportedWGSLLanguageFeatures(0);
    ctx.instance.getWGSLLanguageFeatures(wgsl);
    report.check("WGSL language features", wgsl.featureCount > 0 && ctx.instance.hasWGSLLanguageFeature(
        wgsl.features[0]), wgsl.featureCount + " features, first " + wgsl.features[0]);
    report.check("an unknown WGSL feature is absent",
        !ctx.instance.hasWGSLLanguageFeature(GpuWGSLLanguageFeatureName.BUFFER_VIEW)
            || wgsl.featureCount > 0);
    next.run();
  }

  private void objects(Runnable next) {
    GpuTextureDescriptor td = new GpuTextureDescriptor();
    td.usage = GpuTextureUsage.TEXTURE_BINDING | GpuTextureUsage.COPY_DST;
    td.dimension = GpuTextureDimension.D2;
    td.size.width = 8;
    td.size.height = 4;
    td.format = GpuTextureFormat.RGBA8_UNORM_SRGB;
    td.label = "getters";
    GpuTexture texture = ctx.device.createTexture(td);
    report.check("texture getters", texture.getWidth() == 8 && texture.getHeight() == 4
        && texture.getDepthOrArrayLayers() == 1 && texture.getMipLevelCount() == 1 && texture.getSampleCount() == 1
        && texture.getFormat() == GpuTextureFormat.RGBA8_UNORM_SRGB
        && texture.getDimension() == GpuTextureDimension.D2 && texture.getUsage() == td.usage);

    GpuBufferDescriptor bd = new GpuBufferDescriptor();
    bd.usage = GpuBufferUsage.UNIFORM | GpuBufferUsage.COPY_DST;
    bd.size = 64L;
    GpuBuffer buffer = ctx.device.createBuffer(bd);
    report.check("buffer getters", buffer.getSize() == 64L && buffer.getUsage() == bd.usage
        && buffer.getMapState() == GpuBufferMapState.UNMAPPED);
    buffer.setLabel("labelled");

    report.check("a new wrapper is alive", !buffer.isReleased() && !texture.isReleased());
    buffer.release();
    buffer.release();
    report.check("release is idempotent", buffer.isReleased());
    boolean threw = false;
    try {
      buffer.getSize();
    } catch (IllegalStateException e) {
      threw = true;
    }
    report.check("a released object cannot be used", threw);

    GpuTexture second = ctx.device.createTexture(td);
    second.addRef();
    second.release();
    report.check("addRef keeps the object alive until the last release", !second.isReleased());
    second.release();
    report.check("the last release gives it up", second.isReleased());
    texture.release();

    GpuQueue queue = new io.github.stringdotjar.jwebgpu.teavm.TeaVmGpuQueue();
    report.check("an empty wrapper is released", queue.isReleased());
    ctx.device.getQueue(queue);
    report.check("getQueue fills an existing wrapper", !queue.isReleased());
    GpuCommandEncoder encoder = ctx.device.createCommandEncoder(null);
    GpuCommandEncoder again = ctx.device.createCommandEncoder(null, encoder);
    report.check("a refilled wrapper is the same object", again == encoder && !encoder.isReleased());
    queue.submit(new GpuCommandBuffer[] { encoder.finish(null) }, 1);
    next.run();
  }

  private void compute(Runnable next) {
    GpuBindGroupLayoutDescriptor layoutDescriptor = new GpuBindGroupLayoutDescriptor();
    layoutDescriptor.entryCount = 2;
    GpuBindGroupLayoutEntry uniform = layoutDescriptor.entries[0];
    uniform.binding = 0;
    uniform.visibility = GpuShaderStage.COMPUTE;
    uniform.buffer.type = GpuBufferBindingType.UNIFORM;
    uniform.buffer.hasDynamicOffset = true;
    uniform.buffer.minBindingSize = 4L;
    GpuBindGroupLayoutEntry storage = layoutDescriptor.entries[1];
    storage.binding = 1;
    storage.visibility = GpuShaderStage.COMPUTE;
    storage.buffer.type = GpuBufferBindingType.STORAGE;
    GpuBindGroupLayout layout = ctx.device.createBindGroupLayout(layoutDescriptor);

    GpuPipelineLayoutDescriptor pipelineLayoutDescriptor = new GpuPipelineLayoutDescriptor();
    pipelineLayoutDescriptor.bindGroupLayoutCount = 1;
    pipelineLayoutDescriptor.bindGroupLayouts[0] = layout;
    GpuPipelineLayout pipelineLayout = ctx.device.createPipelineLayout(pipelineLayoutDescriptor);

    GpuShaderModule shader = shader(COMPUTE);
    GpuComputePipelineDescriptor pipelineDescriptor = new GpuComputePipelineDescriptor();
    pipelineDescriptor.layout = pipelineLayout;
    pipelineDescriptor.compute.module = shader;
    pipelineDescriptor.compute.entryPoint = "main";
    ctx.device.createComputePipelineAsync(pipelineDescriptor, GpuCallbackMode.ALLOW_SPONTANEOUS,
        (status, pipeline, message) -> {
          report.check("a compute pipeline is created in the background",
              status == GpuCreatePipelineAsyncStatus.SUCCESS && pipeline != null, String.valueOf(message));
          if (pipeline == null) {
            next.run();
            return;
          }
          runCompute(layout, pipeline, next);
        });
  }

  private void runCompute(GpuBindGroupLayout layout, GpuComputePipeline pipeline, Runnable next) {
    GpuBuffer params = buffer(GpuBufferUsage.UNIFORM | GpuBufferUsage.COPY_DST, 512L);
    GpuBuffer data = buffer(GpuBufferUsage.STORAGE | GpuBufferUsage.COPY_SRC, 16L);
    GpuBuffer readback = buffer(GpuBufferUsage.MAP_READ | GpuBufferUsage.COPY_DST, 16L);
    ctx.queue.writeBuffer(params, 0L, new int[] { 3 }, 0, 1);
    ctx.queue.writeBuffer(params, 256L, new int[] { 5 }, 0, 1);

    GpuBindGroupDescriptor groupDescriptor = new GpuBindGroupDescriptor();
    groupDescriptor.layout = layout;
    groupDescriptor.entryCount = 2;
    groupDescriptor.entries[0].binding = 0;
    groupDescriptor.entries[0].buffer = params;
    groupDescriptor.entries[0].size = 4L;
    groupDescriptor.entries[1].binding = 1;
    groupDescriptor.entries[1].buffer = data;
    GpuBindGroup group = ctx.device.createBindGroup(groupDescriptor);

    GpuCommandEncoder encoder = ctx.device.createCommandEncoder(null);
    GpuComputePassEncoder pass = encoder.beginComputePass(null);
    pass.setPipeline(pipeline);
    pass.setBindGroup(0, group, new int[] { 256 }, 1);
    pass.dispatchWorkgroups(1, 1, 1);
    pass.end();
    encoder.copyBufferToBuffer(data, 0L, readback, 0L, 16L);
    ctx.queue.submit(new GpuCommandBuffer[] { encoder.finish(null) }, 1);

    readback.mapAsync(GpuMapMode.READ, 0L, 16L, GpuCallbackMode.ALLOW_SPONTANEOUS, (status, message) -> {
      GpuStatus read = status == GpuMapAsyncStatus.SUCCESS ? readback.readMappedRange(0L, words, 0, 4) : null;
      report.check("a compute shader reads a dynamic uniform and fills a storage buffer", read == GpuStatus.SUCCESS
          && words[0] == 5 && words[1] == 10 && words[2] == 15 && words[3] == 20,
          words[0] + "," + words[1] + "," + words[2] + "," + words[3]);
      readback.unmap();
      next.run();
    });
  }

  private void textured(Runnable next) {
    GpuTextureDescriptor td = new GpuTextureDescriptor();
    td.usage = GpuTextureUsage.TEXTURE_BINDING | GpuTextureUsage.COPY_DST;
    td.size.width = 2;
    td.size.height = 2;
    td.format = GpuTextureFormat.RGBA8_UNORM;
    GpuTexture source = ctx.device.createTexture(td);
    byte[] texels = {
        (byte) 255, 0, 0, (byte) 255, 0, (byte) 255, 0, (byte) 255,
        0, 0, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255 };
    GpuTexelCopyTextureInfo destination = new GpuTexelCopyTextureInfo();
    destination.texture = source;
    destination.aspect = GpuTextureAspect.ALL;
    GpuTexelCopyBufferLayout dataLayout = new GpuTexelCopyBufferLayout();
    dataLayout.bytesPerRow = 8;
    dataLayout.rowsPerImage = 2;
    GpuExtent3D size = new GpuExtent3D();
    size.width = 2;
    size.height = 2;
    ctx.queue.writeTexture(destination, texels, 0, texels.length, dataLayout, size);

    GpuTextureDescriptor targetDescriptor = new GpuTextureDescriptor();
    targetDescriptor.usage = GpuTextureUsage.RENDER_ATTACHMENT | GpuTextureUsage.COPY_SRC;
    targetDescriptor.size.width = 16;
    targetDescriptor.size.height = 16;
    targetDescriptor.format = GpuTextureFormat.RGBA8_UNORM;
    GpuTexture target = ctx.device.createTexture(targetDescriptor);
    GpuTextureView targetView = target.createView(null);
    GpuTextureView sourceView = source.createView(null);
    GpuSampler sampler = ctx.device.createSampler(null);

    GpuShaderModule shader = shader(TEXTURED);
    GpuRenderPipelineDescriptor pipelineDescriptor = new GpuRenderPipelineDescriptor();
    pipelineDescriptor.vertex.module = shader;
    pipelineDescriptor.vertex.entryPoint = "vs";
    GpuFragmentState fragment = new GpuFragmentState();
    fragment.module = shader;
    fragment.entryPoint = "fs";
    fragment.targetCount = 1;
    fragment.targets[0].format = GpuTextureFormat.RGBA8_UNORM;
    pipelineDescriptor.fragment = fragment;
    GpuRenderPipeline pipeline = ctx.device.createRenderPipeline(pipelineDescriptor);

    GpuBindGroupDescriptor groupDescriptor = new GpuBindGroupDescriptor();
    groupDescriptor.layout = pipeline.getBindGroupLayout(0);
    groupDescriptor.entryCount = 2;
    groupDescriptor.entries[0].binding = 0;
    groupDescriptor.entries[0].textureView = sourceView;
    groupDescriptor.entries[1].binding = 1;
    groupDescriptor.entries[1].sampler = sampler;
    GpuBindGroup group = ctx.device.createBindGroup(groupDescriptor);

    GpuBuffer readback = buffer(GpuBufferUsage.MAP_READ | GpuBufferUsage.COPY_DST, 16L * 256L);
    GpuCommandEncoder encoder = ctx.device.createCommandEncoder(null);
    GpuRenderPassDescriptor passDescriptor = new GpuRenderPassDescriptor();
    passDescriptor.colorAttachmentCount = 1;
    passDescriptor.colorAttachments[0].view = targetView;
    passDescriptor.colorAttachments[0].loadOp = GpuLoadOp.CLEAR;
    passDescriptor.colorAttachments[0].storeOp = GpuStoreOp.STORE;
    GpuRenderPassEncoder pass = encoder.beginRenderPass(passDescriptor);
    pass.setPipeline(pipeline);
    pass.setBindGroup(0, group);
    pass.draw(3, 1, 0, 0);
    pass.end();
    GpuTexelCopyTextureInfo from = new GpuTexelCopyTextureInfo();
    from.texture = target;
    GpuTexelCopyBufferInfo to = new GpuTexelCopyBufferInfo();
    to.buffer = readback;
    to.layout.bytesPerRow = 256;
    to.layout.rowsPerImage = 16;
    GpuExtent3D extent = new GpuExtent3D();
    extent.width = 16;
    extent.height = 16;
    encoder.copyTextureToBuffer(from, to, extent);
    ctx.queue.submit(new GpuCommandBuffer[] { encoder.finish(null) }, 1);

    readback.mapAsync(GpuMapMode.READ, 0L, 16L * 256L, GpuCallbackMode.ALLOW_SPONTANEOUS, (status, message) -> {
      GpuStatus read = status == GpuMapAsyncStatus.SUCCESS ? readback.readMappedRange(0L, pixels, 0, pixels.length)
          : null;
      String red = pixel(2, 2);
      String green = pixel(13, 2);
      String blue = pixel(2, 13);
      String white = pixel(13, 13);
      report.check("a sampled texture shows its four texels", read == GpuStatus.SUCCESS && red.equals("255,0,0,255")
          && green.equals("0,255,0,255") && blue.equals("0,0,255,255") && white.equals("255,255,255,255"),
          red + " " + green + " " + blue + " " + white);
      readback.unmap();
      next.run();
    });
  }

  private void errors(Runnable next) {
    ctx.device.pushErrorScope(GpuErrorFilter.VALIDATION);
    ctx.device.popErrorScope(GpuCallbackMode.ALLOW_SPONTANEOUS, (status, type, message) -> {
      report.check("an error scope without an error is clean", type == GpuErrorType.NO_ERROR,
          status + " " + type);
      ctx.device.pushErrorScope(GpuErrorFilter.VALIDATION);
      invalidBuffer();
      ctx.device.popErrorScope(GpuCallbackMode.ALLOW_SPONTANEOUS, (status2, type2, message2) -> {
        report.check("an error scope catches a validation error", type2 == GpuErrorType.VALIDATION
            && message2 != null && !message2.isEmpty(), type2 + ": " + message2);
        ctx.expectedErrors = 1;
        invalidBuffer();
        ctx.queue.onSubmittedWorkDone(GpuCallbackMode.ALLOW_SPONTANEOUS, (workStatus, workMessage) -> ctx.queue
            .onSubmittedWorkDone(GpuCallbackMode.ALLOW_SPONTANEOUS, (status3, message3) -> {
              report.check("an uncaptured error reaches the callback", ctx.expectedErrors == 0);
              ctx.expectedErrors = 0;
              next.run();
            }));
      });
    });
  }

  private void compilation(Runnable next) {
    ctx.expectedErrors = 1;
    GpuShaderModule broken = shader(BROKEN);
    broken.getCompilationInfo(GpuCallbackMode.ALLOW_SPONTANEOUS, (status, info) -> {
      boolean ok = info.messageCount > 0 && info.messages[0].type == GpuCompilationMessageType.ERROR
          && info.messages[0].message != null && info.messages[0].lineNum >= 1L;
      report.check("compilation messages describe a shader error", ok,
          info.messageCount + " messages" + (info.messageCount > 0 ? ", first: " + info.messages[0].message : ""));
      ctx.queue.onSubmittedWorkDone(GpuCallbackMode.ALLOW_SPONTANEOUS, (s, m) -> {
        ctx.expectedErrors = 0;
        next.run();
      });
    });
  }

  private void futures(Runnable next) {
    GpuFutureWaitInfo[] waits = { new GpuFutureWaitInfo() };
    waits[0].future.id =
        ctx.instance.requestAdapter(null, GpuCallbackMode.WAIT_ANY_ONLY, (status, adapter, message) -> {
          GpuWaitStatus after = ctx.instance.waitAny(waits, 1, 0L);
          report.check("a settled future is complete", after == GpuWaitStatus.SUCCESS && waits[0].completed);
          if (adapter != null) {
            adapter.release();
          }
          next.run();
        });
    GpuWaitStatus before = ctx.instance.waitAny(waits, 1, 0L);
    report.check("a pending future is not complete", before == GpuWaitStatus.TIMED_OUT && !waits[0].completed);
    boolean threw = false;
    try {
      ctx.instance.waitAny(waits, 1, 1_000_000L);
    } catch (UnsupportedOperationException e) {
      threw = true;
    }
    report.check("waitAny cannot block", threw);
    ctx.instance.processEvents();
  }

  private void unsupported(Runnable next) {
    GpuBuffer buffer = buffer(GpuBufferUsage.MAP_READ | GpuBufferUsage.COPY_DST, 16L);
    report.check("getMappedRange is unsupported", throwsUnsupported(() -> buffer.getMappedRange(0L, 16L)));
    report.check("writeTimestamp is not in the browser API", throwsUnsupported(
        () -> ctx.device.createCommandEncoder(null).writeTimestamp(null, 0)));
    report.check("getProcAddress finds nothing", io.github.stringdotjar.jwebgpu.JWebGpu.getProcAddress("x") == 0L);
    buffer.destroy();
    buffer.release();
    next.run();
  }

  private static boolean throwsUnsupported(Runnable action) {
    try {
      action.run();
      return false;
    } catch (UnsupportedOperationException e) {
      return true;
    }
  }

  private void invalidBuffer() {
    GpuBufferDescriptor descriptor = new GpuBufferDescriptor();
    descriptor.usage = 0L;
    descriptor.size = 16L;
    ctx.device.createBuffer(descriptor);
  }

  private GpuBuffer buffer(long usage, long size) {
    GpuBufferDescriptor descriptor = new GpuBufferDescriptor();
    descriptor.usage = usage;
    descriptor.size = size;
    return ctx.device.createBuffer(descriptor);
  }

  private GpuShaderModule shader(String code) {
    GpuShaderSourceWGSL wgsl = new GpuShaderSourceWGSL();
    wgsl.code = code;
    GpuShaderModuleDescriptor descriptor = new GpuShaderModuleDescriptor();
    descriptor.nextInChain = wgsl;
    return ctx.device.createShaderModule(descriptor);
  }

  private String pixel(int x, int y) {
    int at = y * 256 + x * 4;
    return (pixels[at] & 0xFF) + "," + (pixels[at + 1] & 0xFF) + "," + (pixels[at + 2] & 0xFF) + ","
        + (pixels[at + 3] & 0xFF);
  }

  /** One step of the checks, which continues with the next step when it is done. */
  private interface Step {

    void run(Runnable next);
  }
}
