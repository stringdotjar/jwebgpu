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
package io.github.stringdotjar.jwebgpu.jni;

import org.junit.jupiter.api.BeforeAll;
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks the generated struct marshallers byte by byte.
 *
 * <p>The scratch memory asks the glue library for the address of its buffer, so these tests need the library from
 * the {@code testJni} Gradle task, and they are skipped when it was not built.
 *
 * <p>Each test writes a Java descriptor into scratch memory and reads the raw bytes back at the offsets of the C
 * layout. The expected offsets come from the layout computer of the generator, which another test checks against a C
 * compiler, so a mistake in either the offsets or the marshalling code shows up here.
 */
class MarshalTest {

  private static final Spec SPEC = SpecFiles.load();

  @BeforeAll
  static void loadLibraries() {
    String glue = System.getProperty(Native.GLUE_PROPERTY);
    assumeTrue(glue != null && !glue.isEmpty(), "the JNI glue library was not built; use the testJni task");
    Native.load();
  }

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
    assertEquals(size("WGPURenderPassDescriptor"), JniGpuRenderPassDescriptorStruct.SIZE);
    assertEquals(size("WGPURenderPassColorAttachment"), JniGpuRenderPassColorAttachmentStruct.SIZE);
    assertEquals(size("WGPUBindGroupDescriptor"), JniGpuBindGroupDescriptorStruct.SIZE);
    assertEquals(size("WGPUBindGroupEntry"), JniGpuBindGroupEntryStruct.SIZE);
    assertEquals(size("WGPUShaderSourceWGSL"), JniGpuShaderSourceWGSLStruct.SIZE);
    assertEquals(size("WGPULimits"), JniGpuLimitsStruct.SIZE);
    assertEquals(size("WGPUDeviceDescriptor"), JniGpuDeviceDescriptorStruct.SIZE);
    assertEquals(off("WGPURenderPassDescriptor", "colorAttachments"),
        JniGpuRenderPassDescriptorStruct.OFF_COLOR_ATTACHMENTS);
    assertEquals(off("WGPURenderPassColorAttachment", "clearValue"),
        JniGpuRenderPassColorAttachmentStruct.OFF_CLEAR_VALUE);
    assertEquals(off("WGPUDeviceDescriptor", "uncapturedErrorCallbackInfo"),
        JniGpuDeviceDescriptorStruct.OFF_UNCAPTURED_ERROR_CALLBACK_INFO);
  }

  @Test
  void renderPassWithTwoAttachmentsAndDepthStencil() {
    GpuRenderPassDescriptor d = new GpuRenderPassDescriptor(2);
    d.label = "pass é世界";
    d.colorAttachmentCount = 2;
    GpuRenderPassColorAttachment a0 = d.colorAttachments[0];
    a0.view = new JniGpuTextureView(0x1000L);
    a0.loadOp = GpuLoadOp.CLEAR;
    a0.storeOp = GpuStoreOp.STORE;
    a0.clearValue.r = 0.25;
    a0.clearValue.g = 0.5;
    a0.clearValue.b = 0.75;
    a0.clearValue.a = 1.0;
    GpuRenderPassColorAttachment a1 = d.colorAttachments[1];
    a1.view = new JniGpuTextureView(0x2000L);
    a1.resolveTarget = new JniGpuTextureView(0x3000L);
    a1.depthSlice = 7;
    a1.loadOp = GpuLoadOp.LOAD;
    a1.storeOp = GpuStoreOp.DISCARD;
    GpuRenderPassDepthStencilAttachment ds = new GpuRenderPassDepthStencilAttachment();
    ds.view = new JniGpuTextureView(0x4000L);
    ds.depthLoadOp = GpuLoadOp.CLEAR;
    ds.depthStoreOp = GpuStoreOp.STORE;
    ds.depthClearValue = 0.5f;
    ds.stencilClearValue = 9;
    ds.depthReadOnly = true;
    d.depthStencilAttachment = ds;

    Scratch s = Scratch.begin();
    long p = JniGpuRenderPassDescriptorStruct.put(s, d);

    assertEquals(0L, s.getLong(p + off("WGPURenderPassDescriptor", "nextInChain")));
    assertEquals("pass é世界", s.stringView(p + off("WGPURenderPassDescriptor", "label")));
    assertEquals(2L, s.getLong(p + off("WGPURenderPassDescriptor", "colorAttachmentCount")));
    long arr = s.getLong(p + off("WGPURenderPassDescriptor", "colorAttachments"));
    assertTrue(arr != 0L);
    long stride = size("WGPURenderPassColorAttachment");
    assertEquals(0x1000L, s.getLong(arr + off("WGPURenderPassColorAttachment", "view")));
    assertEquals(GpuConstants.DEPTH_SLICE_UNDEFINED,
        s.getInt(arr + off("WGPURenderPassColorAttachment", "depthSlice")));
    assertEquals(0L, s.getLong(arr + off("WGPURenderPassColorAttachment", "resolveTarget")));
    assertEquals(GpuLoadOp.CLEAR.value, s.getInt(arr + off("WGPURenderPassColorAttachment", "loadOp")));
    assertEquals(GpuStoreOp.STORE.value, s.getInt(arr + off("WGPURenderPassColorAttachment", "storeOp")));
    long clear = arr + off("WGPURenderPassColorAttachment", "clearValue");
    assertEquals(0.25, s.getDouble(clear));
    assertEquals(0.5, s.getDouble(clear + 8));
    assertEquals(0.75, s.getDouble(clear + 16));
    assertEquals(1.0, s.getDouble(clear + 24));
    long second = arr + stride;
    assertEquals(0x2000L, s.getLong(second + off("WGPURenderPassColorAttachment", "view")));
    assertEquals(0x3000L, s.getLong(second + off("WGPURenderPassColorAttachment", "resolveTarget")));
    assertEquals(7, s.getInt(second + off("WGPURenderPassColorAttachment", "depthSlice")));
    assertEquals(GpuLoadOp.LOAD.value, s.getInt(second + off("WGPURenderPassColorAttachment", "loadOp")));
    assertEquals(GpuStoreOp.DISCARD.value, s.getInt(second + off("WGPURenderPassColorAttachment", "storeOp")));

    long dsp = s.getLong(p + off("WGPURenderPassDescriptor", "depthStencilAttachment"));
    assertTrue(dsp != 0L);
    String dsName = "WGPURenderPassDepthStencilAttachment";
    assertEquals(0x4000L, s.getLong(dsp + off(dsName, "view")));
    assertEquals(0.5f, s.getFloat(dsp + off(dsName, "depthClearValue")));
    assertEquals(9, s.getInt(dsp + off(dsName, "stencilClearValue")));
    assertEquals(1, s.getInt(dsp + off(dsName, "depthReadOnly")));
    assertEquals(0, s.getInt(dsp + off(dsName, "stencilReadOnly")));
    assertEquals(0L, s.getLong(p + off("WGPURenderPassDescriptor", "occlusionQuerySet")));
    assertEquals(0L, s.getLong(p + off("WGPURenderPassDescriptor", "timestampWrites")));
  }

  @Test
  void bindGroupWithEntries() {
    GpuBindGroupDescriptor d = new GpuBindGroupDescriptor();
    d.layout = new JniGpuBindGroupLayout(0x77L);
    d.entryCount = 2;
    d.entries[0].binding = 3;
    d.entries[0].buffer = new JniGpuBuffer(0x100L);
    d.entries[0].offset = 64;
    d.entries[0].size = 256;
    d.entries[1].binding = 4;
    d.entries[1].sampler = new JniGpuSampler(0x200L);
    d.entries[1].textureView = new JniGpuTextureView(0x300L);

    Scratch s = Scratch.begin();
    long p = JniGpuBindGroupDescriptorStruct.put(s, d);
    assertEquals(0x77L, s.getLong(p + off("WGPUBindGroupDescriptor", "layout")));
    assertEquals(2L, s.getLong(p + off("WGPUBindGroupDescriptor", "entryCount")));
    long entries = s.getLong(p + off("WGPUBindGroupDescriptor", "entries"));
    String e = "WGPUBindGroupEntry";
    assertEquals(3, s.getInt(entries + off(e, "binding")));
    assertEquals(0x100L, s.getLong(entries + off(e, "buffer")));
    assertEquals(64L, s.getLong(entries + off(e, "offset")));
    assertEquals(256L, s.getLong(entries + off(e, "size")));
    long second = entries + size(e);
    assertEquals(4, s.getInt(second + off(e, "binding")));
    assertEquals(0L, s.getLong(second + off(e, "buffer")));
    assertEquals(0x200L, s.getLong(second + off(e, "sampler")));
    assertEquals(0x300L, s.getLong(second + off(e, "textureView")));
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
    long p = JniGpuShaderModuleDescriptorStruct.put(s, d);
    assertEquals("shader", s.stringView(p + off("WGPUShaderModuleDescriptor", "label")));
    long first = s.getLong(p + off("WGPUShaderModuleDescriptor", "nextInChain"));
    assertTrue(first != 0L);
    assertEquals(2, s.getInt(first + 8), "the first struct is the WGSL source");
    assertEquals(wgsl.code, s.stringView(first + off("WGPUShaderSourceWGSL", "code")));
    long next = s.getLong(first);
    assertTrue(next != 0L, "the first struct points at the second one");
    assertEquals(1, s.getInt(next + 8), "the second struct is the SPIR-V source");
    assertEquals(3, s.getInt(next + off("WGPUShaderSourceSPIRV", "codeSize")));
    long code = s.getLong(next + off("WGPUShaderSourceSPIRV", "code"));
    assertEquals(0x07230203, s.getInt(code));
    assertEquals(6, s.getInt(code + 8));
    assertEquals(0L, s.getLong(next), "the last struct ends the chain");
  }

  @Test
  void labelsAreEncodedAsUtf8WithoutAllocation() {
    Scratch s = Scratch.begin();
    String[] labels = { "", "plain", "café", "世界", "pile of 💩 emoji", "lone \uD800 high", "lone \uDC00 low" };
    String[] expected = { "", "plain", "café", "世界", "pile of 💩 emoji", "lone ? high", "lone ? low" };
    long view = s.alloc(16);
    for (int i = 0; i < labels.length; i++) {
      s.putView(view, labels[i]);
      long len = s.getLong(view + 8);
      assertEquals(expected[i].getBytes(StandardCharsets.UTF_8).length, len, labels[i]);
      assertEquals(expected[i], s.string(s.getLong(view), len));
      assertEquals(0, s.getByte(s.getLong(view) + len), "the text is also zero terminated");
    }
    s.putView(view, null);
    assertEquals(0L, s.getLong(view));
    assertEquals(-1L, s.getLong(view + 8));
    assertNull(s.stringView(view));
  }

  @Test
  void scratchGrowsWithoutInvalidatingEarlierAddresses() {
    Scratch s = Scratch.begin();
    long small = s.alloc(64);
    s.putLong(small, 0x1234567890L);
    long big = s.alloc(Scratch.INITIAL_SIZE * 4);
    s.putLong(big + Scratch.INITIAL_SIZE * 4 - 8, 42L);
    assertEquals(0x1234567890L, s.getLong(small));
    assertTrue(s.getCapacity() >= Scratch.INITIAL_SIZE * 4);
    Scratch again = Scratch.begin();
    assertEquals(0L, again.getUsed());
    JWebGpuJni.trimScratch();
    assertEquals(Scratch.INITIAL_SIZE, Scratch.begin().getCapacity());
  }

  @Test
  void nestedCallsKeepWhatTheOuterCallWrote() {
    Scratch s = Scratch.begin();
    long outer = s.alloc(32);
    s.putLong(outer, 99L);
    s.enter();
    Scratch inner = Scratch.begin();
    long nested = inner.alloc(32);
    s.putLong(nested, 7L);
    assertTrue(nested >= outer + 32);
    s.leave();
    assertEquals(99L, s.getLong(outer));
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
    long p = JniGpuLimitsStruct.put(s, limits);
    GpuLimits back = new GpuLimits();
    JniGpuLimitsStruct.read(s, p, back);
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
    long p = s.alloc(JniGpuFutureWaitInfoStruct.SIZE);
    JniGpuFutureWaitInfoStruct.write(s, info, p);
    assertEquals(12345L, s.getLong(p + off("WGPUFutureWaitInfo", "future")));
    s.putInt(p + off("WGPUFutureWaitInfo", "completed"), 1);
    JniGpuFutureWaitInfoStruct.read(s, p, info);
    assertTrue(info.completed);
    assertEquals(12345L, info.future.id);
  }

  @Test
  void readsOutsideTheScratchGoThroughTheNativeHelpers() {
    ByteBuffer foreign = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
    foreign.putLong(8, 0x1122334455667788L);
    foreign.putInt(16, 0xCAFEBABE);
    foreign.put(20, (byte) 0x7F);
    foreign.putShort(22, (short) 0x1234);
    byte[] text = "héllo 世界 \uD83D\uDCA9".getBytes(StandardCharsets.UTF_8);
    foreign.put(32, text);
    foreign.put(32 + text.length, (byte) 0);
    long base = Native.address(foreign);
    Scratch s = Scratch.begin();
    assertEquals(0x1122334455667788L, s.getLong(base + 8));
    assertEquals(0xCAFEBABE, s.getInt(base + 16));
    assertEquals((byte) 0x7F, s.getByte(base + 20));
    assertEquals((short) 0x1234, s.getShort(base + 22));
    assertEquals("héllo 世界 \uD83D\uDCA9", s.string(base + 32, -1L));
    assertEquals("hé", s.string(base + 32, 3L));
    assertEquals("", s.string(base + 32, 0L));
    assertNull(s.string(0L, 5L));
  }

  @Test
  void arraysCopyInAndOutOfNativeMemory() {
    Scratch s = Scratch.begin();
    long p = s.alloc(64);
    float[] floats = { 1.5f, -2.25f, 3f, 4f };
    s.putFloats(p, floats, 1, 3);
    assertEquals(-2.25f, s.getFloat(p));
    int[] ints = { 7, 8, 9 };
    s.putInts(p + 16, ints, 0, 3);
    int[] back = new int[5];
    s.getInts(p + 16, back, 1, 3);
    assertEquals(0, back[0]);
    assertEquals(8, back[2]);
    assertEquals(9, back[3]);
    assertEquals(0, back[4]);
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
    JniGpuTextureView view = new JniGpuTextureView();
    assertTrue(view.isReleased());
    view.assign(0x55L);
    assertEquals(0x55L, JniGpuTextureView.addr(view));
    assertEquals(0L, JniGpuTextureView.addrOrNull(null));
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
      JniGpuTextureView.addr(foreign);
      throw new AssertionError("a foreign object must be rejected");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("JNI"));
    }
  }
}
