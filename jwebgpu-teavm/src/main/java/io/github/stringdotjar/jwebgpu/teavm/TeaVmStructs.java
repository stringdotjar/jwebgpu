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

import io.github.stringdotjar.jwebgpu.GpuCompatibilityModeLimits;
import io.github.stringdotjar.jwebgpu.GpuComponentSwizzle;
import io.github.stringdotjar.jwebgpu.GpuConstantEntry;
import io.github.stringdotjar.jwebgpu.GpuConstants;
import io.github.stringdotjar.jwebgpu.GpuLimits;
import io.github.stringdotjar.jwebgpu.GpuPipelineLayout;
import io.github.stringdotjar.jwebgpu.GpuTextureComponentSwizzle;

/**
 * The few descriptor members that the browser spells so differently that the generator leaves them to hand-written
 * code: the limits of a device request, the constants of a shader stage, and the pipeline layout.
 */
final class TeaVmStructs {

  private static final JSObject AUTO = TeaVmJs.string("auto");

  private TeaVmStructs() {}

  @JSBody(params = { "o", "k", "v" }, script = "o[k] = v;")
  private static native void put(JSObject o, String k, double v);

  @JSBody(params = { "o", "v" }, script = "o.requiredLimits = v == null ? undefined : v;")
  private static native void putLimits(JSObject o, JSObject v);

  @JSBody(params = { "o", "v" }, script = "o.constants = v == null ? undefined : v;")
  private static native void putConstants(JSObject o, JSObject v);

  @JSBody(params = { "o", "v" }, script = "o.swizzle = v;")
  private static native void putSwizzle(JSObject o, String v);

  @JSBody(params = { "o", "v" }, script = "o.layout = v;")
  private static native void putLayout(JSObject o, JSObject v);

  /**
   * Writes the {@code swizzle} member of a texture view.
   *
   * <p>The C struct has one value for each of the four channels. The browser takes them as one string such as
   * {@code "rgba"} or {@code "r001"}, where each letter says what the channel reads. A channel that is left undefined
   * reads itself. Texture views with a swizzle are rare, so the four-letter string is created on demand.
   *
   * @param t The object to write into.
   * @param s The swizzle.
   */
  static void swizzle(JSObject t, GpuTextureComponentSwizzle s) {
    char[] letters = { letter(s.r, 'r'), letter(s.g, 'g'), letter(s.b, 'b'), letter(s.a, 'a') };
    putSwizzle(t, new String(letters));
  }

  /**
   * Writes the {@code requiredLimits} member of a device request.
   *
   * <p>The browser takes a plain object whose keys are limit names, and a missing key means "use the default". The C
   * struct marks a limit it does not set with the "undefined" sentinel, so those limits are left out.
   *
   * @param t The object to write into.
   * @param l The limits, or {@code null} for none.
   */
  static void limits(JSObject t, GpuLimits l) {
    if (l == null) {
      putLimits(t, null);
      return;
    }
    JSObject o = TeaVmJs.object();
    u32(o, "maxTextureDimension1D", l.maxTextureDimension1D);
    u32(o, "maxTextureDimension2D", l.maxTextureDimension2D);
    u32(o, "maxTextureDimension3D", l.maxTextureDimension3D);
    u32(o, "maxTextureArrayLayers", l.maxTextureArrayLayers);
    u32(o, "maxBindGroups", l.maxBindGroups);
    u32(o, "maxBindGroupsPlusVertexBuffers", l.maxBindGroupsPlusVertexBuffers);
    u32(o, "maxBindingsPerBindGroup", l.maxBindingsPerBindGroup);
    u32(o, "maxDynamicUniformBuffersPerPipelineLayout", l.maxDynamicUniformBuffersPerPipelineLayout);
    u32(o, "maxDynamicStorageBuffersPerPipelineLayout", l.maxDynamicStorageBuffersPerPipelineLayout);
    u32(o, "maxSampledTexturesPerShaderStage", l.maxSampledTexturesPerShaderStage);
    u32(o, "maxSamplersPerShaderStage", l.maxSamplersPerShaderStage);
    u32(o, "maxStorageBuffersPerShaderStage", l.maxStorageBuffersPerShaderStage);
    u32(o, "maxStorageTexturesPerShaderStage", l.maxStorageTexturesPerShaderStage);
    u32(o, "maxUniformBuffersPerShaderStage", l.maxUniformBuffersPerShaderStage);
    u64(o, "maxUniformBufferBindingSize", l.maxUniformBufferBindingSize);
    u64(o, "maxStorageBufferBindingSize", l.maxStorageBufferBindingSize);
    u32(o, "minUniformBufferOffsetAlignment", l.minUniformBufferOffsetAlignment);
    u32(o, "minStorageBufferOffsetAlignment", l.minStorageBufferOffsetAlignment);
    u32(o, "maxVertexBuffers", l.maxVertexBuffers);
    u64(o, "maxBufferSize", l.maxBufferSize);
    u32(o, "maxVertexAttributes", l.maxVertexAttributes);
    u32(o, "maxVertexBufferArrayStride", l.maxVertexBufferArrayStride);
    u32(o, "maxInterStageShaderVariables", l.maxInterStageShaderVariables);
    u32(o, "maxColorAttachments", l.maxColorAttachments);
    u32(o, "maxColorAttachmentBytesPerSample", l.maxColorAttachmentBytesPerSample);
    u32(o, "maxComputeWorkgroupStorageSize", l.maxComputeWorkgroupStorageSize);
    u32(o, "maxComputeInvocationsPerWorkgroup", l.maxComputeInvocationsPerWorkgroup);
    u32(o, "maxComputeWorkgroupSizeX", l.maxComputeWorkgroupSizeX);
    u32(o, "maxComputeWorkgroupSizeY", l.maxComputeWorkgroupSizeY);
    u32(o, "maxComputeWorkgroupSizeZ", l.maxComputeWorkgroupSizeZ);
    u32(o, "maxComputeWorkgroupsPerDimension", l.maxComputeWorkgroupsPerDimension);
    u32(o, "maxImmediateSize", l.maxImmediateSize);
    if (l.nextInChain instanceof GpuCompatibilityModeLimits c) {
      u32(o, "maxStorageBuffersInVertexStage", c.maxStorageBuffersInVertexStage);
      u32(o, "maxStorageTexturesInVertexStage", c.maxStorageTexturesInVertexStage);
      u32(o, "maxStorageBuffersInFragmentStage", c.maxStorageBuffersInFragmentStage);
      u32(o, "maxStorageTexturesInFragmentStage", c.maxStorageTexturesInFragmentStage);
    }
    putLimits(t, o);
  }

  /**
   * Writes the {@code constants} member of a shader stage.
   *
   * <p>The browser takes a plain object that maps a constant name to its value. Shader constants are rare, so the
   * object is created on demand instead of being cached.
   *
   * @param t The object to write into.
   * @param entries The constants.
   * @param count How many entries to use.
   */
  static void constants(JSObject t, GpuConstantEntry[] entries, int count) {
    if (count == 0) {
      putConstants(t, null);
      return;
    }
    JSObject o = TeaVmJs.object();
    for (int i = 0; i < count; i++) {
      put(o, entries[i].key, entries[i].value);
    }
    putConstants(t, o);
  }

  /**
   * Writes the {@code layout} member of a pipeline.
   *
   * @param t The object to write into.
   * @param layout The pipeline layout, or {@code null} to let the browser derive one from the shaders.
   */
  static void pipelineLayout(JSObject t, GpuPipelineLayout layout) {
    putLayout(t, layout == null ? AUTO : TeaVmGpuPipelineLayout.js(layout));
  }

  private static char letter(GpuComponentSwizzle c, char self) {
    if (c == null) {
      return self;
    }
    return switch (c) {
      case ZERO -> '0';
      case ONE -> '1';
      case R -> 'r';
      case G -> 'g';
      case B -> 'b';
      case A -> 'a';
      default -> self;
    };
  }

  private static void u32(JSObject o, String key, int value) {
    if (value != GpuConstants.LIMIT_U32_UNDEFINED) {
      put(o, key, value & 0xFFFFFFFFL);
    }
  }

  private static void u64(JSObject o, String key, long value) {
    if (value != GpuConstants.LIMIT_U64_UNDEFINED) {
      put(o, key, (double) value);
    }
  }
}
