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
import org.teavm.jso.JSObject;

import io.github.stringdotjar.jwebgpu.GpuAdapterInfo;
import io.github.stringdotjar.jwebgpu.GpuAdapterType;
import io.github.stringdotjar.jwebgpu.GpuBackendType;
import io.github.stringdotjar.jwebgpu.GpuCompatibilityModeLimits;
import io.github.stringdotjar.jwebgpu.GpuCompilationInfo;
import io.github.stringdotjar.jwebgpu.GpuCompilationMessage;
import io.github.stringdotjar.jwebgpu.GpuFeatureName;
import io.github.stringdotjar.jwebgpu.GpuLimits;
import io.github.stringdotjar.jwebgpu.GpuSupportedFeatures;
import io.github.stringdotjar.jwebgpu.GpuSupportedWGSLLanguageFeatures;
import io.github.stringdotjar.jwebgpu.GpuWGSLLanguageFeatureName;

/**
 * Reads results from the browser into the output structs of the API.
 *
 * <p>These are the "get" calls of the native API that fill a struct the caller passes in: adapter information,
 * limits, supported features and compilation messages. The structs are overwritten in place. An array in the struct
 * is only replaced when it is too small, so a call that reuses its struct allocates nothing after the first time.
 */
final class TeaVmReaders {

  private static final GpuFeatureName[] FEATURES = GpuFeatureName.values();
  private static final GpuWGSLLanguageFeatureName[] LANGUAGE_FEATURES = GpuWGSLLanguageFeatureName.values();

  private TeaVmReaders() {}

  @JSBody(params = { "a", "i" }, script = "return a[i];")
  private static native JSObject at(JSObject a, int i);

  @JSBody(params = "a", script = "return a.length;")
  private static native int length(JSObject a);

  static void adapterInfo(JsGpuAdapterInfo js, GpuAdapterInfo out) {
    out.vendor = js.getVendor();
    out.architecture = js.getArchitecture();
    out.device = js.getDevice();
    out.description = js.getDescription();
    out.backendType = GpuBackendType.WEBGPU;
    out.adapterType = js.getIsFallbackAdapter() ? GpuAdapterType.CPU : GpuAdapterType.UNKNOWN;
    out.vendorID = 0;
    out.deviceID = 0;
    out.subgroupMinSize = js.getSubgroupMinSize();
    out.subgroupMaxSize = js.getSubgroupMaxSize();
  }

  static void limits(JsGpuSupportedLimits js, GpuLimits out) {
    out.maxTextureDimension1D = js.getMaxTextureDimension1D();
    out.maxTextureDimension2D = js.getMaxTextureDimension2D();
    out.maxTextureDimension3D = js.getMaxTextureDimension3D();
    out.maxTextureArrayLayers = js.getMaxTextureArrayLayers();
    out.maxBindGroups = js.getMaxBindGroups();
    out.maxBindGroupsPlusVertexBuffers = js.getMaxBindGroupsPlusVertexBuffers();
    out.maxBindingsPerBindGroup = js.getMaxBindingsPerBindGroup();
    out.maxDynamicUniformBuffersPerPipelineLayout = js.getMaxDynamicUniformBuffersPerPipelineLayout();
    out.maxDynamicStorageBuffersPerPipelineLayout = js.getMaxDynamicStorageBuffersPerPipelineLayout();
    out.maxSampledTexturesPerShaderStage = js.getMaxSampledTexturesPerShaderStage();
    out.maxSamplersPerShaderStage = js.getMaxSamplersPerShaderStage();
    out.maxStorageBuffersPerShaderStage = js.getMaxStorageBuffersPerShaderStage();
    out.maxStorageTexturesPerShaderStage = js.getMaxStorageTexturesPerShaderStage();
    out.maxUniformBuffersPerShaderStage = js.getMaxUniformBuffersPerShaderStage();
    out.maxUniformBufferBindingSize = (long) js.getMaxUniformBufferBindingSize();
    out.maxStorageBufferBindingSize = (long) js.getMaxStorageBufferBindingSize();
    out.minUniformBufferOffsetAlignment = js.getMinUniformBufferOffsetAlignment();
    out.minStorageBufferOffsetAlignment = js.getMinStorageBufferOffsetAlignment();
    out.maxVertexBuffers = js.getMaxVertexBuffers();
    out.maxBufferSize = (long) js.getMaxBufferSize();
    out.maxVertexAttributes = js.getMaxVertexAttributes();
    out.maxVertexBufferArrayStride = js.getMaxVertexBufferArrayStride();
    out.maxInterStageShaderVariables = js.getMaxInterStageShaderVariables();
    out.maxColorAttachments = js.getMaxColorAttachments();
    out.maxColorAttachmentBytesPerSample = js.getMaxColorAttachmentBytesPerSample();
    out.maxComputeWorkgroupStorageSize = js.getMaxComputeWorkgroupStorageSize();
    out.maxComputeInvocationsPerWorkgroup = js.getMaxComputeInvocationsPerWorkgroup();
    out.maxComputeWorkgroupSizeX = js.getMaxComputeWorkgroupSizeX();
    out.maxComputeWorkgroupSizeY = js.getMaxComputeWorkgroupSizeY();
    out.maxComputeWorkgroupSizeZ = js.getMaxComputeWorkgroupSizeZ();
    out.maxComputeWorkgroupsPerDimension = js.getMaxComputeWorkgroupsPerDimension();
    out.maxImmediateSize = js.getMaxImmediateSize();
    if (out.nextInChain instanceof GpuCompatibilityModeLimits c) {
      c.maxStorageBuffersInVertexStage = js.getMaxStorageBuffersInVertexStage();
      c.maxStorageTexturesInVertexStage = js.getMaxStorageTexturesInVertexStage();
      c.maxStorageBuffersInFragmentStage = js.getMaxStorageBuffersInFragmentStage();
      c.maxStorageTexturesInFragmentStage = js.getMaxStorageTexturesInFragmentStage();
    }
  }

  static void features(JsGpuSupportedFeatures js, GpuSupportedFeatures out) {
    int n = 0;
    for (int i = 0; i < FEATURES.length; i++) {
      if (TeaVmEnums.hasFeatureName(FEATURES[i]) && js.has(TeaVmEnums.jsFeatureName(FEATURES[i]))) {
        n++;
      }
    }
    if (out.features.length < n) {
      out.features = new GpuFeatureName[n];
    }
    n = 0;
    for (int i = 0; i < FEATURES.length; i++) {
      if (TeaVmEnums.hasFeatureName(FEATURES[i]) && js.has(TeaVmEnums.jsFeatureName(FEATURES[i]))) {
        out.features[n++] = FEATURES[i];
      }
    }
    out.featureCount = n;
  }

  static boolean hasFeature(JsGpuSupportedFeatures js, GpuFeatureName feature) {
    return TeaVmEnums.hasFeatureName(feature) && js.has(TeaVmEnums.jsFeatureName(feature));
  }

  static void languageFeatures(JsWGSLLanguageFeatures js, GpuSupportedWGSLLanguageFeatures out) {
    int n = 0;
    for (int i = 0; i < LANGUAGE_FEATURES.length; i++) {
      if (hasLanguageFeature(js, LANGUAGE_FEATURES[i])) {
        n++;
      }
    }
    if (out.features.length < n) {
      out.features = new GpuWGSLLanguageFeatureName[n];
    }
    n = 0;
    for (int i = 0; i < LANGUAGE_FEATURES.length; i++) {
      if (hasLanguageFeature(js, LANGUAGE_FEATURES[i])) {
        out.features[n++] = LANGUAGE_FEATURES[i];
      }
    }
    out.featureCount = n;
  }

  static boolean hasLanguageFeature(JsWGSLLanguageFeatures js, GpuWGSLLanguageFeatureName feature) {
    return TeaVmEnums.hasWGSLLanguageFeatureName(feature) && js.has(TeaVmEnums.jsWGSLLanguageFeatureName(feature));
  }

  static void compilationInfo(JsGpuCompilationInfo js, GpuCompilationInfo out) {
    JSObject list = js.getMessages();
    int n = length(list);
    if (out.messages.length < n) {
      GpuCompilationMessage[] bigger = new GpuCompilationMessage[n];
      for (int i = 0; i < n; i++) {
        bigger[i] = i < out.messages.length ? out.messages[i] : new GpuCompilationMessage();
      }
      out.messages = bigger;
    }
    for (int i = 0; i < n; i++) {
      JsGpuCompilationMessage m = TeaVmAsync.cast(at(list, i));
      GpuCompilationMessage o = out.messages[i];
      o.message = m.getMessage();
      o.type = TeaVmEnums.fromCompilationMessageType(m.getType());
      o.lineNum = (long) m.getLineNum();
      o.linePos = (long) m.getLinePos();
      o.offset = (long) m.getOffset();
      o.length = (long) m.getLength();
    }
    out.messageCount = n;
  }
}
