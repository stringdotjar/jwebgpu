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

import io.github.stringdotjar.jwebgpu.GpuBindGroupDescriptor;
import io.github.stringdotjar.jwebgpu.GpuConstants;
import io.github.stringdotjar.jwebgpu.GpuFutureWaitInfo;
import io.github.stringdotjar.jwebgpu.GpuLimits;
import io.github.stringdotjar.jwebgpu.GpuLoadOp;
import io.github.stringdotjar.jwebgpu.GpuRenderPassColorAttachment;
import io.github.stringdotjar.jwebgpu.GpuRenderPassDepthStencilAttachment;
import io.github.stringdotjar.jwebgpu.GpuRenderPassDescriptor;
import io.github.stringdotjar.jwebgpu.GpuShaderModuleDescriptor;
import io.github.stringdotjar.jwebgpu.GpuShaderSourceSPIRV;
import io.github.stringdotjar.jwebgpu.GpuShaderSourceWGSL;
import io.github.stringdotjar.jwebgpu.GpuStoreOp;
import io.github.stringdotjar.jwebgpu.GpuTextureView;
import io.github.stringdotjar.jwebgpu.generator.SpecFiles;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the generated struct marshallers byte by byte, without the native library.
 *
 * <p>Each test writes a Java descriptor into scratch memory and reads the raw bytes back at the offsets of the C
 * layout. The expected offsets come from the layout computer of the generator, which another test checks against a C
 * compiler, so a mistake in either the offsets or the marshalling code shows up here.
 */
class MarshalTest {

  private static final Spec SPEC = SpecFiles.load();

  private static long off(String struct, String field) {
    Struct s = SPEC.struct(struct);
    for (int i = 0; i < s.fields().size(); i++) {
      if (s.fields().get(i).name().equals(field)) {
        return SPEC.layouts().of(struct).offsets().get(i);
      }
    }
    throw new AssertionError(struct + " has no field " + field);
  }

  private static long size(String struct) {
    return SPEC.layouts().of(struct).size();
  }

  @Test
  void generatedSizesMatchTheCLayout() {
    assertEquals(size("WGPURenderPassDescriptor"), FfmGpuRenderPassDescriptorStruct.SIZE);
    assertEquals(size("WGPURenderPassColorAttachment"), FfmGpuRenderPassColorAttachmentStruct.SIZE);
    assertEquals(size("WGPUBindGroupDescriptor"), FfmGpuBindGroupDescriptorStruct.SIZE);
    assertEquals(size("WGPUBindGroupEntry"), FfmGpuBindGroupEntryStruct.SIZE);
    assertEquals(size("WGPUShaderSourceWGSL"), FfmGpuShaderSourceWGSLStruct.SIZE);
    assertEquals(size("WGPULimits"), FfmGpuLimitsStruct.SIZE);
    assertEquals(size("WGPUDeviceDescriptor"), FfmGpuDeviceDescriptorStruct.SIZE);
    assertEquals(off("WGPURenderPassDescriptor", "colorAttachments"),
        FfmGpuRenderPassDescriptorStruct.OFF_COLOR_ATTACHMENTS);
    assertEquals(off("WGPURenderPassColorAttachment", "clearValue"),
        FfmGpuRenderPassColorAttachmentStruct.OFF_CLEAR_VALUE);
    assertEquals(off("WGPUDeviceDescriptor", "uncapturedErrorCallbackInfo"),
        FfmGpuDeviceDescriptorStruct.OFF_UNCAPTURED_ERROR_CALLBACK_INFO);
  }

  @Test
  void renderPassWithTwoAttachmentsAndDepthStencil() {
    GpuRenderPassDescriptor d = new GpuRenderPassDescriptor(2);
    d.label = "pass é世界";
    d.colorAttachmentCount = 2;
    GpuRenderPassColorAttachment a0 = d.colorAttachments[0];
    a0.view = new FfmGpuTextureView(0x1000L);
    a0.loadOp = GpuLoadOp.CLEAR;
    a0.storeOp = GpuStoreOp.STORE;
    a0.clearValue.r = 0.25;
    a0.clearValue.g = 0.5;
    a0.clearValue.b = 0.75;
    a0.clearValue.a = 1.0;
    GpuRenderPassColorAttachment a1 = d.colorAttachments[1];
    a1.view = new FfmGpuTextureView(0x2000L);
    a1.resolveTarget = new FfmGpuTextureView(0x3000L);
    a1.depthSlice = 7;
    a1.loadOp = GpuLoadOp.LOAD;
    a1.storeOp = GpuStoreOp.DISCARD;
    GpuRenderPassDepthStencilAttachment ds = new GpuRenderPassDepthStencilAttachment();
    ds.view = new FfmGpuTextureView(0x4000L);
    ds.depthLoadOp = GpuLoadOp.CLEAR;
    ds.depthStoreOp = GpuStoreOp.STORE;
    ds.depthClearValue = 0.5f;
    ds.stencilClearValue = 9;
    ds.depthReadOnly = true;
    d.depthStencilAttachment = ds;

    Scratch s = Scratch.begin();
    long p = FfmGpuRenderPassDescriptorStruct.put(s, d);

    assertEquals(0L, Mem.getLong(p + off("WGPURenderPassDescriptor", "nextInChain")));
    assertEquals("pass é世界", Mem.stringView(p + off("WGPURenderPassDescriptor", "label")));
    assertEquals(2L, Mem.getLong(p + off("WGPURenderPassDescriptor", "colorAttachmentCount")));
    long arr = Mem.getLong(p + off("WGPURenderPassDescriptor", "colorAttachments"));
    assertTrue(arr != 0L);
    long stride = size("WGPURenderPassColorAttachment");
    assertEquals(0x1000L, Mem.getLong(arr + off("WGPURenderPassColorAttachment", "view")));
    assertEquals(GpuConstants.DEPTH_SLICE_UNDEFINED,
        Mem.getInt(arr + off("WGPURenderPassColorAttachment", "depthSlice")));
    assertEquals(0L, Mem.getLong(arr + off("WGPURenderPassColorAttachment", "resolveTarget")));
    assertEquals(GpuLoadOp.CLEAR.value, Mem.getInt(arr + off("WGPURenderPassColorAttachment", "loadOp")));
    assertEquals(GpuStoreOp.STORE.value, Mem.getInt(arr + off("WGPURenderPassColorAttachment", "storeOp")));
    long clear = arr + off("WGPURenderPassColorAttachment", "clearValue");
    assertEquals(0.25, Mem.getDouble(clear));
    assertEquals(0.5, Mem.getDouble(clear + 8));
    assertEquals(0.75, Mem.getDouble(clear + 16));
    assertEquals(1.0, Mem.getDouble(clear + 24));
    long second = arr + stride;
    assertEquals(0x2000L, Mem.getLong(second + off("WGPURenderPassColorAttachment", "view")));
    assertEquals(0x3000L, Mem.getLong(second + off("WGPURenderPassColorAttachment", "resolveTarget")));
    assertEquals(7, Mem.getInt(second + off("WGPURenderPassColorAttachment", "depthSlice")));
    assertEquals(GpuLoadOp.LOAD.value, Mem.getInt(second + off("WGPURenderPassColorAttachment", "loadOp")));
    assertEquals(GpuStoreOp.DISCARD.value, Mem.getInt(second + off("WGPURenderPassColorAttachment", "storeOp")));

    long dsp = Mem.getLong(p + off("WGPURenderPassDescriptor", "depthStencilAttachment"));
    assertTrue(dsp != 0L);
    String dsName = "WGPURenderPassDepthStencilAttachment";
    assertEquals(0x4000L, Mem.getLong(dsp + off(dsName, "view")));
    assertEquals(0.5f, Mem.getFloat(dsp + off(dsName, "depthClearValue")));
    assertEquals(9, Mem.getInt(dsp + off(dsName, "stencilClearValue")));
    assertEquals(1, Mem.getInt(dsp + off(dsName, "depthReadOnly")));
    assertEquals(0, Mem.getInt(dsp + off(dsName, "stencilReadOnly")));
    assertEquals(0L, Mem.getLong(p + off("WGPURenderPassDescriptor", "occlusionQuerySet")));
    assertEquals(0L, Mem.getLong(p + off("WGPURenderPassDescriptor", "timestampWrites")));
  }

  @Test
  void bindGroupWithEntries() {
    GpuBindGroupDescriptor d = new GpuBindGroupDescriptor();
    d.layout = new FfmGpuBindGroupLayout(0x77L);
    d.entryCount = 2;
    d.entries[0].binding = 3;
    d.entries[0].buffer = new FfmGpuBuffer(0x100L);
    d.entries[0].offset = 64;
    d.entries[0].size = 256;
    d.entries[1].binding = 4;
    d.entries[1].sampler = new FfmGpuSampler(0x200L);
    d.entries[1].textureView = new FfmGpuTextureView(0x300L);

    Scratch s = Scratch.begin();
    long p = FfmGpuBindGroupDescriptorStruct.put(s, d);
    assertEquals(0x77L, Mem.getLong(p + off("WGPUBindGroupDescriptor", "layout")));
    assertEquals(2L, Mem.getLong(p + off("WGPUBindGroupDescriptor", "entryCount")));
    long entries = Mem.getLong(p + off("WGPUBindGroupDescriptor", "entries"));
    String e = "WGPUBindGroupEntry";
    assertEquals(3, Mem.getInt(entries + off(e, "binding")));
    assertEquals(0x100L, Mem.getLong(entries + off(e, "buffer")));
    assertEquals(64L, Mem.getLong(entries + off(e, "offset")));
    assertEquals(256L, Mem.getLong(entries + off(e, "size")));
    long second = entries + size(e);
    assertEquals(4, Mem.getInt(second + off(e, "binding")));
    assertEquals(0L, Mem.getLong(second + off(e, "buffer")));
    assertEquals(0x200L, Mem.getLong(second + off(e, "sampler")));
    assertEquals(0x300L, Mem.getLong(second + off(e, "textureView")));
  }

  @Test
  void chainedShaderSourcesAreLinkedWithTheirTags() {
    GpuShaderSourceWGSL wgsl = new GpuShaderSourceWGSL();
    wgsl.code = "@fragment fn fs() -> @location(0) vec4f { return vec4f(1.0); } // ü";
    GpuShaderSourceSPIRV spirv = new GpuShaderSourceSPIRV();
    spirv.codeSize = 3;
    spirv.code = new int[] { 0x07230203, 5, 6 };
    wgsl.next = spirv;
    GpuShaderModuleDescriptor d = new GpuShaderModuleDescriptor();
    d.label = "shader";
    d.nextInChain = wgsl;

    Scratch s = Scratch.begin();
    long p = FfmGpuShaderModuleDescriptorStruct.put(s, d);
    assertEquals("shader", Mem.stringView(p + off("WGPUShaderModuleDescriptor", "label")));
    long first = Mem.getLong(p + off("WGPUShaderModuleDescriptor", "nextInChain"));
    assertTrue(first != 0L);
    assertEquals(2, Mem.getInt(first + 8), "the first struct is the WGSL source");
    assertEquals(wgsl.code, Mem.stringView(first + off("WGPUShaderSourceWGSL", "code")));
    long next = Mem.getLong(first);
    assertTrue(next != 0L, "the first struct points at the second one");
    assertEquals(1, Mem.getInt(next + 8), "the second struct is the SPIR-V source");
    assertEquals(3, Mem.getInt(next + off("WGPUShaderSourceSPIRV", "codeSize")));
    long code = Mem.getLong(next + off("WGPUShaderSourceSPIRV", "code"));
    assertEquals(0x07230203, Mem.getInt(code));
    assertEquals(6, Mem.getInt(code + 8));
    assertEquals(0L, Mem.getLong(next), "the last struct ends the chain");
  }

  @Test
  void labelsAreEncodedAsUtf8WithoutAllocation() {
    Scratch s = Scratch.begin();
    String[] labels = { "", "plain", "café", "世界", "pile of 💩 emoji", "lone \uD800 high", "lone \uDC00 low" };
    String[] expected = { "", "plain", "café", "世界", "pile of 💩 emoji", "lone ? high", "lone ? low" };
    long view = s.alloc(16);
    for (int i = 0; i < labels.length; i++) {
      s.putView(view, labels[i]);
      long len = Mem.getLong(view + 8);
      assertEquals(expected[i].getBytes(java.nio.charset.StandardCharsets.UTF_8).length, len, labels[i]);
      assertEquals(expected[i], Mem.string(Mem.getLong(view), len));
      assertEquals(0, Mem.getByte(Mem.getLong(view) + len), "the text is also zero terminated");
    }
    s.putView(view, null);
    assertEquals(0L, Mem.getLong(view));
    assertEquals(-1L, Mem.getLong(view + 8));
    assertNull(Mem.stringView(view));
  }

  @Test
  void scratchGrowsWithoutInvalidatingEarlierAddresses() {
    Scratch s = Scratch.begin();
    long small = s.alloc(64);
    Mem.putLong(small, 0x1234567890L);
    long big = s.alloc(Scratch.INITIAL_SIZE * 4);
    Mem.putLong(big + Scratch.INITIAL_SIZE * 4 - 8, 42L);
    assertEquals(0x1234567890L, Mem.getLong(small));
    assertTrue(s.getCapacity() >= Scratch.INITIAL_SIZE * 4);
    Scratch again = Scratch.begin();
    assertEquals(0L, again.getUsed());
    JWebGpuFfm.trimScratch();
    assertEquals(Scratch.INITIAL_SIZE, Scratch.begin().getCapacity());
  }

  @Test
  void nestedCallsKeepWhatTheOuterCallWrote() {
    Scratch s = Scratch.begin();
    long outer = s.alloc(32);
    Mem.putLong(outer, 99L);
    s.enter();
    Scratch inner = Scratch.begin();
    long nested = inner.alloc(32);
    Mem.putLong(nested, 7L);
    assertTrue(nested >= outer + 32);
    s.leave();
    assertEquals(99L, Mem.getLong(outer));
    assertEquals(outer + 32, s.alloc(0));
  }

  @Test
  void aWrittenStructCanBeReadBack() {
    GpuLimits limits = new GpuLimits();
    limits.maxTextureDimension2D = 8192;
    limits.maxBindGroups = 4;
    limits.maxBufferSize = 1L << 33;
    limits.maxUniformBufferBindingSize = 65536L;
    limits.minUniformBufferOffsetAlignment = 256;
    Scratch s = Scratch.begin();
    long p = FfmGpuLimitsStruct.put(s, limits);
    GpuLimits back = new GpuLimits();
    FfmGpuLimitsStruct.read(p, back);
    assertEquals(8192, back.maxTextureDimension2D);
    assertEquals(4, back.maxBindGroups);
    assertEquals(1L << 33, back.maxBufferSize);
    assertEquals(65536L, back.maxUniformBufferBindingSize);
    assertEquals(256, back.minUniformBufferOffsetAlignment);
    assertEquals(limits.maxColorAttachments, back.maxColorAttachments);
  }

  @Test
  void futureWaitInfoRoundTrips() {
    GpuFutureWaitInfo info = new GpuFutureWaitInfo();
    info.future.id = 12345L;
    Scratch s = Scratch.begin();
    long p = s.alloc(FfmGpuFutureWaitInfoStruct.SIZE);
    FfmGpuFutureWaitInfoStruct.write(s, info, p);
    assertEquals(12345L, Mem.getLong(p + off("WGPUFutureWaitInfo", "future")));
    Mem.putInt(p + off("WGPUFutureWaitInfo", "completed"), 1);
    FfmGpuFutureWaitInfoStruct.read(p, info);
    assertTrue(info.completed);
    assertEquals(12345L, info.future.id);
  }

  @Test
  void callbackSlotsAreReusedAfterOneShotCallbacksFire() {
    Object a = new Object();
    Object b = new Object();
    int slotA = CallbackTable.put(a);
    int slotB = CallbackTable.put(b);
    assertTrue(slotA > 0 && slotB > 0 && slotA != slotB);
    assertEquals(a, CallbackTable.get(slotA));
    assertEquals(a, CallbackTable.take(slotA));
    assertNull(CallbackTable.take(slotA));
    assertNull(CallbackTable.get(slotA));
    assertEquals(slotA, CallbackTable.put(new Object()));
    assertEquals(b, CallbackTable.take(slotB));
    assertNull(CallbackTable.get(0));
    assertNull(CallbackTable.get(100000));
  }

  @Test
  void objectWrappersCheckTheirState() {
    FfmGpuTextureView view = new FfmGpuTextureView();
    assertTrue(view.isReleased());
    view.assign(0x55L);
    assertEquals(0x55L, FfmGpuTextureView.addr(view));
    assertEquals(0L, FfmGpuTextureView.addrOrNull(null));
    assertEquals(0x55L, view.getAddress());
    GpuTextureView foreign = new GpuTextureView() {
      @Override
      public void release() {}

      @Override
      public void addRef() {}

      @Override
      public boolean isReleased() {
        return false;
      }

      @Override
      public void setLabel(String label) {}
    };
    try {
      FfmGpuTextureView.addr(foreign);
      throw new AssertionError("a foreign object must be rejected");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("FFM"));
    }
  }
}
