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
package io.github.stringdotjar.jwebgpu.teavm;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;

import io.github.stringdotjar.jwebgpu.GpuAdapter;
import io.github.stringdotjar.jwebgpu.GpuAdapterInfo;
import io.github.stringdotjar.jwebgpu.GpuBindGroup;
import io.github.stringdotjar.jwebgpu.GpuBuffer;
import io.github.stringdotjar.jwebgpu.GpuBufferMapCallback;
import io.github.stringdotjar.jwebgpu.GpuCallbackMode;
import io.github.stringdotjar.jwebgpu.GpuChainedStruct;
import io.github.stringdotjar.jwebgpu.GpuCompilationInfo;
import io.github.stringdotjar.jwebgpu.GpuCompilationInfoCallback;
import io.github.stringdotjar.jwebgpu.GpuCompilationInfoRequestStatus;
import io.github.stringdotjar.jwebgpu.GpuCompositeAlphaMode;
import io.github.stringdotjar.jwebgpu.GpuComputePipelineDescriptor;
import io.github.stringdotjar.jwebgpu.GpuConstants;
import io.github.stringdotjar.jwebgpu.GpuCreateComputePipelineAsyncCallback;
import io.github.stringdotjar.jwebgpu.GpuCreatePipelineAsyncStatus;
import io.github.stringdotjar.jwebgpu.GpuCreateRenderPipelineAsyncCallback;
import io.github.stringdotjar.jwebgpu.GpuDeviceDescriptor;
import io.github.stringdotjar.jwebgpu.GpuDeviceLostCallback;
import io.github.stringdotjar.jwebgpu.GpuDeviceLostReason;
import io.github.stringdotjar.jwebgpu.GpuErrorType;
import io.github.stringdotjar.jwebgpu.GpuExtent3D;
import io.github.stringdotjar.jwebgpu.GpuFeatureName;
import io.github.stringdotjar.jwebgpu.GpuFutureWaitInfo;
import io.github.stringdotjar.jwebgpu.GpuInstance;
import io.github.stringdotjar.jwebgpu.GpuInstanceDescriptor;
import io.github.stringdotjar.jwebgpu.GpuInstanceFeatureName;
import io.github.stringdotjar.jwebgpu.GpuInstanceLimits;
import io.github.stringdotjar.jwebgpu.GpuLimits;
import io.github.stringdotjar.jwebgpu.GpuMapAsyncStatus;
import io.github.stringdotjar.jwebgpu.GpuPopErrorScopeCallback;
import io.github.stringdotjar.jwebgpu.GpuPopErrorScopeStatus;
import io.github.stringdotjar.jwebgpu.GpuPresentMode;
import io.github.stringdotjar.jwebgpu.GpuQueueWorkDoneCallback;
import io.github.stringdotjar.jwebgpu.GpuQueueWorkDoneStatus;
import io.github.stringdotjar.jwebgpu.GpuRenderPipelineDescriptor;
import io.github.stringdotjar.jwebgpu.GpuRequestAdapterCallback;
import io.github.stringdotjar.jwebgpu.GpuRequestAdapterOptions;
import io.github.stringdotjar.jwebgpu.GpuRequestAdapterStatus;
import io.github.stringdotjar.jwebgpu.GpuRequestDeviceCallback;
import io.github.stringdotjar.jwebgpu.GpuRequestDeviceStatus;
import io.github.stringdotjar.jwebgpu.GpuStatus;
import io.github.stringdotjar.jwebgpu.GpuSupportedFeatures;
import io.github.stringdotjar.jwebgpu.GpuSupportedInstanceFeatures;
import io.github.stringdotjar.jwebgpu.GpuSupportedWGSLLanguageFeatures;
import io.github.stringdotjar.jwebgpu.GpuSurface;
import io.github.stringdotjar.jwebgpu.GpuSurfaceCapabilities;
import io.github.stringdotjar.jwebgpu.GpuSurfaceConfiguration;
import io.github.stringdotjar.jwebgpu.GpuSurfaceDescriptor;
import io.github.stringdotjar.jwebgpu.GpuSurfaceGetCurrentTextureStatus;
import io.github.stringdotjar.jwebgpu.GpuSurfaceTexture;
import io.github.stringdotjar.jwebgpu.GpuTexelCopyBufferLayout;
import io.github.stringdotjar.jwebgpu.GpuTexelCopyTextureInfo;
import io.github.stringdotjar.jwebgpu.GpuTextureFormat;
import io.github.stringdotjar.jwebgpu.GpuTextureUsage;
import io.github.stringdotjar.jwebgpu.GpuUncapturedErrorCallback;
import io.github.stringdotjar.jwebgpu.GpuWGSLLanguageFeatureName;
import io.github.stringdotjar.jwebgpu.GpuWaitStatus;

import java.nio.ByteBuffer;

/**
 * The calls of the API whose shape differs too much between the native API and the browser for the generator.
 *
 * <p>The generated classes cover every call that is a plain method or attribute of a browser object. Four groups
 * remain, and each one is written by hand here:
 *
 * <ul>
 *   <li><b>Promises.</b> Requesting an adapter or a device, mapping a buffer, waiting for the queue, popping an error
 *       scope, getting compilation messages and creating a pipeline in the background all return a promise in the
 *       browser. They end in a call of the Java callback (see {@link TeaVmAsync}).
 *   <li><b>Output structs.</b> Limits, features and adapter information are read into the struct the caller passes in
 *       (see {@link TeaVmReaders}).
 *   <li><b>Raw memory.</b> The browser has no pointers: a mapped buffer range is an {@code ArrayBuffer} that is
 *       copied, not shared. {@code getMappedRange} therefore throws, and {@code readMappedRange} and
 *       {@code writeMappedRange} (which every backend has) copy instead. Uploads go through {@link TeaVmData}.
 *   <li><b>The instance and the surface.</b> The instance is {@code navigator.gpu}, and a surface is a canvas context.
 * </ul>
 *
 * <p>Method names are the C names, with the object first, for example {@code adapterRequestDevice}. The generated
 * classes call them.
 */
final class TeaVmSpecial {

  private static final GpuFeatureName[] NONE = new GpuFeatureName[0];
  private static final GpuCompilationInfo COMPILATION_INFO = new GpuCompilationInfo(0);

  private TeaVmSpecial() {}

  @JSBody(script = "return navigator.gpu;")
  private static native JsGpu navigatorGpu();

  @JSBody(params = "canvas", script = "return canvas.getContext('webgpu');")
  private static native JsGpuCanvasContext getContext(JSObject canvas);

  @JSBody(params = { "canvas", "w", "h" }, script = "if (canvas.width !== w) { canvas.width = w; }"
      + " if (canvas.height !== h) { canvas.height = h; }")
  private static native void resize(JSObject canvas, int w, int h);

  @JSBody(params = { "device", "handler" }, script = "device.addEventListener('uncapturederror', handler);")
  private static native void listen(JsGpuDevice device, Handler handler);

  @JSBody(params = "e", script = "var x = e.error; return x instanceof GPUValidationError ? 1"
      + " : x instanceof GPUOutOfMemoryError ? 2 : x instanceof GPUInternalError ? 3 : 4;")
  private static native int uncapturedKind(JSObject e);

  @JSBody(params = "e", script = "return String(e.error.message);")
  private static native String uncapturedMessage(JSObject e);

  @JSBody(params = "e", script = "return e === null || e === undefined ? 0 : e instanceof GPUValidationError ? 1"
      + " : e instanceof GPUOutOfMemoryError ? 2 : e instanceof GPUInternalError ? 3 : 4;")
  private static native int errorKind(JSObject e);

  @JSBody(params = { "dst", "dstOffset", "src", "length" },
      script = "new Uint8Array(dst).set(src.subarray(0, length), dstOffset);")
  private static native void writeInto(JSObject dst, int dstOffset, JSObject src, int length);

  @JSBody(params = { "dst", "dstOffset", "src", "length" },
      script = "dst.set(new Uint8Array(src, 0, length), dstOffset);")
  private static native void readFrom(JSObject dst, int dstOffset, JSObject src, int length);

  static GpuInstance createInstance(GpuInstanceDescriptor descriptor) {
    if (descriptor != null && descriptor.requiredFeatureCount > 0) {
      throw TeaVmJs.unsupported("The browser has no optional instance features.");
    }
    return new TeaVmGpuInstance(navigatorGpu());
  }

  static void getInstanceFeatures(GpuSupportedInstanceFeatures features) {
    features.featureCount = 0;
  }

  static GpuStatus getInstanceLimits(GpuInstanceLimits limits) {
    limits.timedWaitAnyMaxCount = 0L;
    return GpuStatus.SUCCESS;
  }

  static boolean hasInstanceFeature(GpuInstanceFeatureName feature) {
    return false;
  }

  static long getProcAddress(String procName) {
    return 0L;
  }

  static GpuSurface instanceCreateSurface(TeaVmGpuInstance self, GpuSurfaceDescriptor descriptor) {
    return new TeaVmGpuSurface(createContext(self, descriptor));
  }

  static GpuSurface instanceCreateSurface(TeaVmGpuInstance self, GpuSurfaceDescriptor descriptor,
      TeaVmGpuSurface into) {
    into.assign(createContext(self, descriptor));
    return into;
  }

  static void instanceGetWGSLLanguageFeatures(TeaVmGpuInstance self, GpuSupportedWGSLLanguageFeatures features) {
    TeaVmReaders.languageFeatures(TeaVmGpuInstance.js(self).getWgslLanguageFeatures(), features);
  }

  static boolean instanceHasWGSLLanguageFeature(TeaVmGpuInstance self, GpuWGSLLanguageFeatureName feature) {
    return TeaVmReaders.hasLanguageFeature(TeaVmGpuInstance.js(self).getWgslLanguageFeatures(), feature);
  }

  static void instanceProcessEvents(TeaVmGpuInstance self) {
    TeaVmGpuInstance.js(self);
  }

  static long instanceRequestAdapter(TeaVmGpuInstance self, GpuRequestAdapterOptions options, GpuCallbackMode mode,
      GpuRequestAdapterCallback callback) {
    JsGpu gpu = TeaVmGpuInstance.js(self);
    JSObject dict = options == null ? TeaVmJs.UNDEFINED : TeaVmGpuRequestAdapterOptionsStruct.shared().fill(options);
    return new TeaVmAsync.Request() {
      @Override
      void succeeded(JSObject value) {
        if (TeaVmAsync.isNothing(value)) {
          callback.onRequestAdapter(GpuRequestAdapterStatus.UNAVAILABLE, null, "No adapter is available.");
        } else {
          JsGpuAdapter js = TeaVmAsync.cast(value);
          callback.onRequestAdapter(GpuRequestAdapterStatus.SUCCESS, new TeaVmGpuAdapter(js), null);
        }
      }

      @Override
      void failed(JSObject reason) {
        callback.onRequestAdapter(GpuRequestAdapterStatus.ERROR, null, TeaVmAsync.message(reason));
      }
    }.start(gpu.requestAdapter(dict));
  }

  static GpuWaitStatus instanceWaitAny(TeaVmGpuInstance self, GpuFutureWaitInfo[] futures, int futureCount,
      long timeoutNS) {
    TeaVmGpuInstance.js(self);
    if (timeoutNS != 0L) {
      throw TeaVmJs.unsupported("The browser cannot block, so waitAny only polls: pass a timeout of 0 and let the "
          + "callbacks fire from the event loop.");
    }
    boolean any = false;
    for (int i = 0; i < futureCount; i++) {
      boolean done = !TeaVmFutures.isPending(futures[i].future.id);
      futures[i].completed = done;
      any |= done;
    }
    return any ? GpuWaitStatus.SUCCESS : GpuWaitStatus.TIMED_OUT;
  }

  static void adapterGetFeatures(TeaVmGpuAdapter self, GpuSupportedFeatures features) {
    TeaVmReaders.features(TeaVmGpuAdapter.js(self).getFeatures(), features);
  }

  static GpuStatus adapterGetInfo(TeaVmGpuAdapter self, GpuAdapterInfo info) {
    TeaVmReaders.adapterInfo(TeaVmGpuAdapter.js(self).getInfo(), info);
    return GpuStatus.SUCCESS;
  }

  static GpuStatus adapterGetLimits(TeaVmGpuAdapter self, GpuLimits limits) {
    TeaVmReaders.limits(TeaVmGpuAdapter.js(self).getLimits(), limits);
    return GpuStatus.SUCCESS;
  }

  static boolean adapterHasFeature(TeaVmGpuAdapter self, GpuFeatureName feature) {
    return TeaVmReaders.hasFeature(TeaVmGpuAdapter.js(self).getFeatures(), feature);
  }

  static long adapterRequestDevice(TeaVmGpuAdapter self, GpuDeviceDescriptor descriptor, GpuCallbackMode mode,
      GpuRequestDeviceCallback callback) {
    JsGpuAdapter adapter = TeaVmGpuAdapter.js(self);
    JSObject dict = descriptor == null ? TeaVmJs.UNDEFINED : TeaVmGpuDeviceDescriptorStruct.shared().fill(descriptor);
    GpuDeviceLostCallback lost = descriptor == null ? null : descriptor.deviceLostCallback;
    GpuUncapturedErrorCallback errors = descriptor == null ? null : descriptor.uncapturedErrorCallback;
    return new TeaVmAsync.Request() {
      @Override
      void succeeded(JSObject value) {
        JsGpuDevice js = TeaVmAsync.cast(value);
        TeaVmGpuDevice device = new TeaVmGpuDevice(js);
        if (lost != null) {
          watchLost(js, device, lost);
        }
        if (errors != null) {
          listen(js, event -> errors.onUncapturedError(device, errorTypeOf(uncapturedKind(event)),
              uncapturedMessage(event)));
        }
        callback.onRequestDevice(GpuRequestDeviceStatus.SUCCESS, device, null);
      }

      @Override
      void failed(JSObject reason) {
        callback.onRequestDevice(GpuRequestDeviceStatus.ERROR, null, TeaVmAsync.message(reason));
      }
    }.start(adapter.requestDevice(dict));
  }

  static long deviceCreateComputePipelineAsync(TeaVmGpuDevice self, GpuComputePipelineDescriptor descriptor,
      GpuCallbackMode mode, GpuCreateComputePipelineAsyncCallback callback) {
    JsGpuDevice device = TeaVmGpuDevice.js(self);
    JSObject dict = TeaVmGpuComputePipelineDescriptorStruct.shared().fill(descriptor);
    return new TeaVmAsync.Request() {
      @Override
      void succeeded(JSObject value) {
        JsGpuComputePipeline js = TeaVmAsync.cast(value);
        callback.onCreateComputePipelineAsync(GpuCreatePipelineAsyncStatus.SUCCESS, new TeaVmGpuComputePipeline(js),
            null);
      }

      @Override
      void failed(JSObject reason) {
        callback.onCreateComputePipelineAsync(pipelineStatus(reason), null, TeaVmAsync.message(reason));
      }
    }.start(device.createComputePipelineAsync(dict));
  }

  static long deviceCreateRenderPipelineAsync(TeaVmGpuDevice self, GpuRenderPipelineDescriptor descriptor,
      GpuCallbackMode mode, GpuCreateRenderPipelineAsyncCallback callback) {
    JsGpuDevice device = TeaVmGpuDevice.js(self);
    JSObject dict = TeaVmGpuRenderPipelineDescriptorStruct.shared().fill(descriptor);
    return new TeaVmAsync.Request() {
      @Override
      void succeeded(JSObject value) {
        JsGpuRenderPipeline js = TeaVmAsync.cast(value);
        callback.onCreateRenderPipelineAsync(GpuCreatePipelineAsyncStatus.SUCCESS, new TeaVmGpuRenderPipeline(js),
            null);
      }

      @Override
      void failed(JSObject reason) {
        callback.onCreateRenderPipelineAsync(pipelineStatus(reason), null, TeaVmAsync.message(reason));
      }
    }.start(device.createRenderPipelineAsync(dict));
  }

  static GpuStatus deviceGetAdapterInfo(TeaVmGpuDevice self, GpuAdapterInfo info) {
    TeaVmReaders.adapterInfo(TeaVmGpuDevice.js(self).getAdapterInfo(), info);
    return GpuStatus.SUCCESS;
  }

  static void deviceGetFeatures(TeaVmGpuDevice self, GpuSupportedFeatures features) {
    TeaVmReaders.features(TeaVmGpuDevice.js(self).getFeatures(), features);
  }

  static GpuStatus deviceGetLimits(TeaVmGpuDevice self, GpuLimits limits) {
    TeaVmReaders.limits(TeaVmGpuDevice.js(self).getLimits(), limits);
    return GpuStatus.SUCCESS;
  }

  static boolean deviceHasFeature(TeaVmGpuDevice self, GpuFeatureName feature) {
    return TeaVmReaders.hasFeature(TeaVmGpuDevice.js(self).getFeatures(), feature);
  }

  static long deviceGetLostFuture(TeaVmGpuDevice self) {
    return new TeaVmAsync.Request() {
      @Override
      void succeeded(JSObject value) {}

      @Override
      void failed(JSObject reason) {}
    }.start(TeaVmGpuDevice.js(self).getLost());
  }

  static long devicePopErrorScope(TeaVmGpuDevice self, GpuCallbackMode mode, GpuPopErrorScopeCallback callback) {
    JsGpuDevice device = TeaVmGpuDevice.js(self);
    return new TeaVmAsync.Request() {
      @Override
      void succeeded(JSObject value) {
        int kind = errorKind(value);
        callback.onPopErrorScope(GpuPopErrorScopeStatus.SUCCESS, errorTypeOf(kind),
            kind == 0 ? null : TeaVmAsync.message(value));
      }

      @Override
      void failed(JSObject reason) {
        callback.onPopErrorScope(GpuPopErrorScopeStatus.ERROR, GpuErrorType.UNKNOWN, TeaVmAsync.message(reason));
      }
    }.start(device.popErrorScope());
  }

  static ByteBuffer bufferGetConstMappedRange(TeaVmGpuBuffer self, long offset, long size) {
    throw mappedRange();
  }

  static ByteBuffer bufferGetMappedRange(TeaVmGpuBuffer self, long offset, long size) {
    throw mappedRange();
  }

  static long bufferMapAsync(TeaVmGpuBuffer self, long mode, long offset, long size, GpuCallbackMode callbackMode,
      GpuBufferMapCallback callback) {
    JsGpuBuffer buffer = TeaVmGpuBuffer.js(self);
    JSPromise<JSObject> promise;
    if (size == GpuConstants.WHOLE_MAP_SIZE) {
      promise = offset == 0L ? buffer.mapAsync((int) mode) : buffer.mapAsync((int) mode, (double) offset);
    } else {
      promise = buffer.mapAsync((int) mode, (double) offset, (double) size);
    }
    return new TeaVmAsync.Request() {
      @Override
      void succeeded(JSObject value) {
        callback.onBufferMap(GpuMapAsyncStatus.SUCCESS, null);
      }

      @Override
      void failed(JSObject reason) {
        boolean aborted = "AbortError".equals(TeaVmAsync.name(reason));
        callback.onBufferMap(aborted ? GpuMapAsyncStatus.ABORTED : GpuMapAsyncStatus.ERROR,
            TeaVmAsync.message(reason));
      }
    }.start(promise);
  }

  static GpuStatus bufferReadMappedRange(TeaVmGpuBuffer self, long offset, JSObject staged) {
    try {
      JSObject mapped = TeaVmGpuBuffer.js(self).getMappedRange((double) offset, (double) TeaVmData.bytes);
      readFrom(staged, TeaVmData.offset, mapped, TeaVmData.bytes);
      return GpuStatus.SUCCESS;
    } catch (RuntimeException e) {
      return GpuStatus.ERROR;
    }
  }

  static GpuStatus bufferWriteMappedRange(TeaVmGpuBuffer self, long offset, JSObject staged) {
    try {
      JSObject mapped = TeaVmGpuBuffer.js(self).getMappedRange((double) offset, (double) TeaVmData.bytes);
      writeInto(mapped, 0, TeaVmData.sub(staged, TeaVmData.offset, TeaVmData.bytes), TeaVmData.bytes);
      return GpuStatus.SUCCESS;
    } catch (RuntimeException e) {
      return GpuStatus.ERROR;
    }
  }

  static long queueOnSubmittedWorkDone(TeaVmGpuQueue self, GpuCallbackMode mode, GpuQueueWorkDoneCallback callback) {
    JsGpuQueue queue = TeaVmGpuQueue.js(self);
    return new TeaVmAsync.Request() {
      @Override
      void succeeded(JSObject value) {
        callback.onQueueWorkDone(GpuQueueWorkDoneStatus.SUCCESS, null);
      }

      @Override
      void failed(JSObject reason) {
        callback.onQueueWorkDone(GpuQueueWorkDoneStatus.ERROR, TeaVmAsync.message(reason));
      }
    }.start(queue.onSubmittedWorkDone());
  }

  static void queueWriteBuffer(TeaVmGpuQueue self, GpuBuffer buffer, long bufferOffset, JSObject staged) {
    TeaVmGpuQueue.js(self).writeBuffer(TeaVmGpuBuffer.js(buffer), (double) bufferOffset, staged,
        (double) TeaVmData.offset, (double) TeaVmData.bytes);
  }

  static void queueWriteTexture(TeaVmGpuQueue self, GpuTexelCopyTextureInfo destination, JSObject staged,
      GpuTexelCopyBufferLayout dataLayout, GpuExtent3D writeSize) {
    JsGpuQueue queue = TeaVmGpuQueue.js(self);
    JSObject data = TeaVmData.sub(staged, TeaVmData.offset, TeaVmData.bytes);
    queue.writeTexture(TeaVmGpuTexelCopyTextureInfoStruct.shared().fill(destination), data,
        TeaVmGpuTexelCopyBufferLayoutStruct.shared().fill(dataLayout),
        TeaVmGpuExtent3DStruct.shared().fill(writeSize));
  }

  static void computePassEncoderSetBindGroup(TeaVmGpuComputePassEncoder self, int groupIndex, GpuBindGroup group,
      int[] dynamicOffsets, int dynamicOffsetCount) {
    JsGpuComputePassEncoder js = TeaVmGpuComputePassEncoder.js(self);
    JsGpuBindGroup g = TeaVmGpuBindGroup.jsOrNull(group);
    if (dynamicOffsetCount == 0) {
      js.setBindGroup(groupIndex, g);
    } else {
      js.setBindGroup(groupIndex, g, offsets(dynamicOffsets, dynamicOffsetCount));
    }
  }

  static void renderPassEncoderSetBindGroup(TeaVmGpuRenderPassEncoder self, int groupIndex, GpuBindGroup group,
      int[] dynamicOffsets, int dynamicOffsetCount) {
    JsGpuRenderPassEncoder js = TeaVmGpuRenderPassEncoder.js(self);
    JsGpuBindGroup g = TeaVmGpuBindGroup.jsOrNull(group);
    if (dynamicOffsetCount == 0) {
      js.setBindGroup(groupIndex, g);
    } else {
      js.setBindGroup(groupIndex, g, offsets(dynamicOffsets, dynamicOffsetCount));
    }
  }

  static void renderBundleEncoderSetBindGroup(TeaVmGpuRenderBundleEncoder self, int groupIndex, GpuBindGroup group,
      int[] dynamicOffsets, int dynamicOffsetCount) {
    JsGpuRenderBundleEncoder js = TeaVmGpuRenderBundleEncoder.js(self);
    JsGpuBindGroup g = TeaVmGpuBindGroup.jsOrNull(group);
    if (dynamicOffsetCount == 0) {
      js.setBindGroup(groupIndex, g);
    } else {
      js.setBindGroup(groupIndex, g, offsets(dynamicOffsets, dynamicOffsetCount));
    }
  }

  static void computePassEncoderSetImmediates(TeaVmGpuComputePassEncoder self, int offset, JSObject staged) {
    TeaVmGpuComputePassEncoder.js(self).setImmediates(offset, staged, (double) TeaVmData.offset,
        (double) TeaVmData.bytes);
  }

  static void renderPassEncoderSetImmediates(TeaVmGpuRenderPassEncoder self, int offset, JSObject staged) {
    TeaVmGpuRenderPassEncoder.js(self).setImmediates(offset, staged, (double) TeaVmData.offset,
        (double) TeaVmData.bytes);
  }

  static void renderBundleEncoderSetImmediates(TeaVmGpuRenderBundleEncoder self, int offset, JSObject staged) {
    TeaVmGpuRenderBundleEncoder.js(self).setImmediates(offset, staged, (double) TeaVmData.offset,
        (double) TeaVmData.bytes);
  }

  static long shaderModuleGetCompilationInfo(TeaVmGpuShaderModule self, GpuCallbackMode mode,
      GpuCompilationInfoCallback callback) {
    JsGpuShaderModule module = TeaVmGpuShaderModule.js(self);
    return new TeaVmAsync.Request() {
      @Override
      void succeeded(JSObject value) {
        JsGpuCompilationInfo js = TeaVmAsync.cast(value);
        TeaVmReaders.compilationInfo(js, COMPILATION_INFO);
        callback.onCompilationInfo(GpuCompilationInfoRequestStatus.SUCCESS, COMPILATION_INFO);
      }

      @Override
      void failed(JSObject reason) {
        COMPILATION_INFO.reset();
        callback.onCompilationInfo(GpuCompilationInfoRequestStatus.CALLBACK_CANCELLED, COMPILATION_INFO);
      }
    }.start(module.getCompilationInfo());
  }

  static void surfaceConfigure(TeaVmGpuSurface self, GpuSurfaceConfiguration config) {
    JsGpuCanvasContext ctx = TeaVmGpuSurface.js(self);
    if (config.width > 0 && config.height > 0) {
      resize(ctx.getCanvas(), config.width, config.height);
    }
    ctx.configure(TeaVmGpuSurfaceConfigurationStruct.shared().fill(config));
  }

  static GpuStatus surfaceGetCapabilities(TeaVmGpuSurface self, GpuAdapter adapter,
      GpuSurfaceCapabilities capabilities) {
    TeaVmGpuSurface.js(self);
    GpuTextureFormat preferred = TeaVmEnums.fromTextureFormat(navigatorGpu().getPreferredCanvasFormat());
    if (capabilities.formats.length < 2) {
      capabilities.formats = new GpuTextureFormat[2];
    }
    capabilities.formats[0] = preferred;
    capabilities.formats[1] = preferred == GpuTextureFormat.RGBA8_UNORM
        ? GpuTextureFormat.BGRA8_UNORM : GpuTextureFormat.RGBA8_UNORM;
    capabilities.formatCount = 2;
    if (capabilities.presentModes.length < 1) {
      capabilities.presentModes = new GpuPresentMode[1];
    }
    capabilities.presentModes[0] = GpuPresentMode.FIFO;
    capabilities.presentModeCount = 1;
    if (capabilities.alphaModes.length < 2) {
      capabilities.alphaModes = new GpuCompositeAlphaMode[2];
    }
    capabilities.alphaModes[0] = GpuCompositeAlphaMode.OPAQUE;
    capabilities.alphaModes[1] = GpuCompositeAlphaMode.PREMULTIPLIED;
    capabilities.alphaModeCount = 2;
    capabilities.usages = GpuTextureUsage.RENDER_ATTACHMENT | GpuTextureUsage.COPY_SRC | GpuTextureUsage.COPY_DST
        | GpuTextureUsage.TEXTURE_BINDING;
    return GpuStatus.SUCCESS;
  }

  static void surfaceGetCurrentTexture(TeaVmGpuSurface self, GpuSurfaceTexture surfaceTexture) {
    JsGpuTexture js = TeaVmGpuSurface.js(self).getCurrentTexture();
    TeaVmGpuTexture texture;
    if (surfaceTexture.texture instanceof TeaVmGpuTexture existing) {
      existing.assign(js);
      texture = existing;
    } else {
      texture = new TeaVmGpuTexture(js);
      surfaceTexture.texture = texture;
    }
    texture.setExternal(true);
    surfaceTexture.status = GpuSurfaceGetCurrentTextureStatus.SUCCESS_OPTIMAL;
  }

  static GpuStatus surfacePresent(TeaVmGpuSurface self) {
    TeaVmGpuSurface.js(self);
    return GpuStatus.SUCCESS;
  }

  private static JsGpuCanvasContext createContext(TeaVmGpuInstance self, GpuSurfaceDescriptor descriptor) {
    TeaVmGpuInstance.js(self);
    for (GpuChainedStruct c = descriptor.nextInChain; c != null; c = c.next) {
      if (c instanceof TeaVmSurfaceSourceCanvas source) {
        JsGpuCanvasContext ctx = getContext(source.resolve());
        if (TeaVmAsync.isNothing(ctx)) {
          throw new IllegalStateException("The canvas could not give a WebGPU context. It may already have another "
              + "kind of context.");
        }
        return ctx;
      }
    }
    throw new IllegalArgumentException("A surface on the web needs a TeaVmSurfaceSourceCanvas in nextInChain.");
  }

  private static JSObject offsets(int[] values, int count) {
    JSObject list = TeaVmJs.list();
    for (int i = 0; i < count; i++) {
      TeaVmJs.pushUint(list, values[i]);
    }
    return list;
  }

  private static void watchLost(JsGpuDevice js, TeaVmGpuDevice device, GpuDeviceLostCallback callback) {
    new TeaVmAsync.Request() {
      @Override
      void succeeded(JSObject value) {
        JsGpuDeviceLostInfo info = TeaVmAsync.cast(value);
        GpuDeviceLostReason reason = "destroyed".equals(info.getReason())
            ? GpuDeviceLostReason.DESTROYED : GpuDeviceLostReason.UNKNOWN;
        callback.onDeviceLost(device, reason, info.getMessage());
      }

      @Override
      void failed(JSObject reason) {}
    }.start(js.getLost());
  }

  private static GpuErrorType errorTypeOf(int kind) {
    return switch (kind) {
      case 0 -> GpuErrorType.NO_ERROR;
      case 1 -> GpuErrorType.VALIDATION;
      case 2 -> GpuErrorType.OUT_OF_MEMORY;
      case 3 -> GpuErrorType.INTERNAL;
      default -> GpuErrorType.UNKNOWN;
    };
  }

  private static GpuCreatePipelineAsyncStatus pipelineStatus(JSObject reason) {
    return "internal".equals(TeaVmAsync.reason(reason))
        ? GpuCreatePipelineAsyncStatus.INTERNAL_ERROR : GpuCreatePipelineAsyncStatus.VALIDATION_ERROR;
  }

  private static UnsupportedOperationException mappedRange() {
    return TeaVmJs.unsupported("A browser buffer cannot expose its memory as a ByteBuffer: the mapped range is an "
        + "ArrayBuffer that the browser owns. Use readMappedRange() and writeMappedRange(), which copy.");
  }

  /** A function that the browser calls with an event. */
  @JSFunctor
  interface Handler extends JSObject {

    void handle(JSObject event);
  }
}
