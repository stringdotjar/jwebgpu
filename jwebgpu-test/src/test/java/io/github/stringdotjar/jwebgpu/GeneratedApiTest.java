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
package io.github.stringdotjar.jwebgpu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the behavior of the generated API classes against the model.
 *
 * <p>The exhaustive checks (every enum, bit flag type, constant and struct) are generated as plain Java into
 * {@link GeneratedApiChecks} by the generator's {@code api-checks} mode, so they read fields and call {@code of(...)}
 * directly and need no reflection. The tests below call them and add checks of known values by hand.
 */
class GeneratedApiTest {

  @Test
  void enumValuesRoundTripThroughOf() {
    GeneratedApiChecks.enums();
  }

  @Test
  void enumsWithoutAZeroValueMapZeroToNull() {
    assertNull(GpuCallbackMode.of(0));
    assertNull(GpuAdapterType.of(0));
    assertSame(GpuTextureFormat.UNDEFINED, GpuTextureFormat.of(0));
  }

  @Test
  void enumConstantsKeepTheNativeNumbers() {
    assertEquals(22, GpuTextureFormat.RGBA8_UNORM.value);
    assertEquals(27, GpuTextureFormat.BGRA8_UNORM.value);
    assertEquals(1, GpuTextureDimension.D1.value);
    assertEquals(2, GpuTextureDimension.D2.value);
    assertEquals(3, GpuTextureDimension.D3.value);
    assertEquals(3, GpuTextureViewDimension.D2_ARRAY.value);
    assertEquals(6, GpuTextureViewDimension.D3.value);
    assertEquals(2, GpuCallbackMode.ALLOW_PROCESS_EVENTS.value);
    assertSame(GpuTextureFormat.RGBA8_UNORM, GpuTextureFormat.of(22));
    assertNull(GpuTextureFormat.of(0x7FFFFFFE));
  }

  @Test
  void bitflagConstantsMatchTheModel() {
    GeneratedApiChecks.bitflags();
    assertEquals(0x10L, GpuTextureUsage.RENDER_ATTACHMENT);
    assertEquals(0x4L, GpuTextureUsage.TEXTURE_BINDING);
    assertEquals(0x8L, GpuBufferUsage.COPY_DST);
    assertEquals(0xFL, GpuColorWriteMask.ALL);
    assertEquals(GpuColorWriteMask.RED | GpuColorWriteMask.GREEN | GpuColorWriteMask.BLUE | GpuColorWriteMask.ALPHA,
        GpuColorWriteMask.ALL);
  }

  @Test
  void constantsHaveTheExactCValues() {
    assertEquals(-1L, GpuConstants.WHOLE_SIZE);
    assertEquals(-1L, GpuConstants.STRLEN);
    assertEquals(-1L, GpuConstants.WHOLE_MAP_SIZE);
    assertEquals(-1L, GpuConstants.LIMIT_U64_UNDEFINED);
    assertEquals(0xFFFFFFFF, GpuConstants.DEPTH_SLICE_UNDEFINED);
    assertEquals(0xFFFFFFFF, GpuConstants.LIMIT_U32_UNDEFINED);
    assertTrue(Float.isNaN(GpuConstants.DEPTH_CLEAR_VALUE_UNDEFINED));
    GeneratedApiChecks.constants();
  }

  @Test
  void everyStructStartsWithTheDefaultsOfTheHeader() {
    int checked = GeneratedApiChecks.structDefaults();
    assertTrue(checked > 70, "checked " + checked + " structs");
  }

  @Test
  void resetRestoresTheDefaultsOfEveryStruct() {
    int checked = GeneratedApiChecks.resetRestoresDefaults();
    assertTrue(checked > 70, "checked " + checked + " structs");
  }

  @Test
  void resetRestoresNestedStructsAndArrayElements() {
    GpuRenderPassDescriptor d = new GpuRenderPassDescriptor(2);
    d.label = "pass";
    d.colorAttachmentCount = 2;
    d.colorAttachments[1].loadOp = GpuLoadOp.CLEAR;
    d.colorAttachments[1].clearValue.r = 0.5;
    d.colorAttachments[1].depthSlice = 7;
    d.reset();
    assertNull(d.label);
    assertEquals(0, d.colorAttachmentCount);
    assertEquals(2, d.colorAttachments.length);
    assertSame(GpuLoadOp.UNDEFINED, d.colorAttachments[1].loadOp);
    assertEquals(0.0, d.colorAttachments[1].clearValue.r);
    assertEquals(GpuConstants.DEPTH_SLICE_UNDEFINED, d.colorAttachments[1].depthSlice);
  }

  @Test
  void headerDefaultsOfWellKnownStructs() {
    GpuExtent3D extent = new GpuExtent3D();
    assertEquals(0, extent.width);
    assertEquals(1, extent.height);
    assertEquals(1, extent.depthOrArrayLayers);

    GpuMultisampleState ms = new GpuMultisampleState();
    assertEquals(1, ms.count);
    assertEquals(0xFFFFFFFF, ms.mask);
    assertEquals(false, ms.alphaToCoverageEnabled);

    GpuSamplerDescriptor sampler = new GpuSamplerDescriptor();
    assertEquals(32.0f, sampler.lodMaxClamp);
    assertEquals(1, sampler.maxAnisotropy);
    assertSame(GpuAddressMode.CLAMP_TO_EDGE, GpuAddressMode.of(1));

    GpuTextureViewDescriptor view = new GpuTextureViewDescriptor();
    assertEquals(GpuConstants.MIP_LEVEL_COUNT_UNDEFINED, view.mipLevelCount);
    assertEquals(GpuConstants.ARRAY_LAYER_COUNT_UNDEFINED, view.arrayLayerCount);

    GpuLimits limits = new GpuLimits();
    assertEquals(GpuConstants.LIMIT_U32_UNDEFINED, limits.maxTextureDimension2D);

    GpuRenderPassDepthStencilAttachment depth = new GpuRenderPassDepthStencilAttachment();
    assertTrue(Float.isNaN(depth.depthClearValue));
  }

  @Test
  void arraysHaveSensibleDefaultCapacities() {
    GpuRenderPassDescriptor pass = new GpuRenderPassDescriptor();
    assertEquals(1, pass.colorAttachments.length);
    assertNotNull(pass.colorAttachments[0]);
    assertEquals(4, new GpuBindGroupDescriptor().entries.length);
    assertEquals(0, new GpuComputeState().constants.length);

    GpuRenderPipelineDescriptor pipeline = new GpuRenderPipelineDescriptor();
    assertEquals(1, pipeline.vertex.buffers.length);
    assertEquals(4, pipeline.vertex.buffers[0].attributes.length);
    assertNull(pipeline.fragment);

    GpuBindGroupDescriptor big = new GpuBindGroupDescriptor(16);
    assertEquals(16, big.entries.length);
    for (int i = 0; i < 16; i++) {
      assertNotNull(big.entries[i]);
    }
    GpuVertexState state = new GpuVertexState(3, 5);
    assertEquals(3, state.constants.length);
    assertEquals(5, state.buffers.length);
  }

  @Test
  void extensionStructsChainThroughNext() {
    GpuSurfaceSourceXlibWindow source = new GpuSurfaceSourceXlibWindow();
    source.display = 11L;
    source.window = 22L;
    GpuSurfaceDescriptor descriptor = new GpuSurfaceDescriptor();
    descriptor.nextInChain = source;
    assertSame(source, descriptor.nextInChain);
    assertNull(source.next);
    assertEquals(6, source.sType());
    assertEquals(2, new GpuShaderSourceWGSL().sType());
    assertEquals(1, new GpuShaderSourceSPIRV().sType());
  }

  @Test
  void callbackInfoMembersBecomeCallbackAndModeFields() {
    GpuDeviceDescriptor d = new GpuDeviceDescriptor();
    assertNull(d.deviceLostCallback);
    assertNull(d.deviceLostMode);
    assertNull(d.uncapturedErrorCallback);
    d.deviceLostCallback = (device, reason, message) -> {
    };
    d.deviceLostMode = GpuCallbackMode.ALLOW_SPONTANEOUS;
    d.reset();
    assertNull(d.deviceLostCallback);
    assertNull(d.deviceLostMode);
  }

  @Test
  void spirvCodeIsAnIntArray() {
    GpuShaderSourceSPIRV spirv = new GpuShaderSourceSPIRV();
    assertNull(spirv.code);
    spirv.code = new int[] { 0x07230203, 1 };
    spirv.codeSize = 2;
    assertArrayEquals(new int[] { 0x07230203, 1 }, spirv.code);
  }

  @Test
  void bindingLayoutsOfALayoutEntryStartAsNotUsed() {
    GpuBindGroupLayoutEntry e = new GpuBindGroupLayoutEntry();
    assertSame(GpuBufferBindingType.BINDING_NOT_USED, e.buffer.type);
    assertSame(GpuSamplerBindingType.BINDING_NOT_USED, e.sampler.type);
    assertSame(GpuTextureSampleType.BINDING_NOT_USED, e.texture.sampleType);
    assertSame(GpuStorageTextureAccess.BINDING_NOT_USED, e.storageTexture.access);
    assertSame(GpuBufferBindingType.UNDEFINED, new GpuBufferBindingLayout().type, "standalone layouts keep their own defaults");
    e.buffer.type = GpuBufferBindingType.UNIFORM;
    e.reset();
    assertSame(GpuBufferBindingType.BINDING_NOT_USED, e.buffer.type);
  }
}
