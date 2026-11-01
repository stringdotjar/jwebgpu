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
package io.github.stringdotjar.jwebgpu.generator.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.stringdotjar.jwebgpu.generator.SpecFiles;
import io.github.stringdotjar.jwebgpu.generator.model.EnumType;
import io.github.stringdotjar.jwebgpu.generator.model.GpuFunction;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Enumeration;
import io.github.stringdotjar.jwebgpu.generator.web.WebMap.Binding;
import io.github.stringdotjar.jwebgpu.generator.web.WebMap.EnumMapping;
import io.github.stringdotjar.jwebgpu.generator.web.WebMap.StructMapping;
import io.github.stringdotjar.jwebgpu.generator.web.WebMap.Support;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the merge of the C model with the browser's WebIDL. */
class WebMapTest {

  private static WebMap map;
  private static WebIdl idl;

  @BeforeAll
  static void merge() {
    idl = SpecFiles.loadIdl();
    map = WebMap.of(SpecFiles.load(), idl);
  }

  @Test
  void theTwoSpecificationsAgree() {
    assertEquals(List.of(), map.problems());
  }

  @Test
  void everyBrowserEnumValueHasACValue() {
    // The merge reports a browser string without a C value as a problem, so this also pins the aliases.
    int checked = 0;
    for (EnumMapping m : map.enums()) {
      if (m.idlName().equals("(synthetic)")) {
        continue;
      }
      Enumeration e = idl.enumType(m.idlName());
      List<String> mapped = new ArrayList<>();
      for (int i = 0; i < m.js().size(); i++) {
        if (m.support().get(i) == Support.JS) {
          mapped.add(m.js().get(i));
        }
      }
      assertEquals(e.values().stream().sorted().toList(), mapped.stream().sorted().toList(), m.idlName());
      checked++;
    }
    assertTrue(checked >= 30, "checked " + checked + " enums");
  }

  @Test
  void mapsEnumValuesByNormalizedName() {
    EnumMapping format = map.enumFor("WGPUTextureFormat");
    assertEquals("bgra8unorm-srgb", jsOf(format, "BGRA8_unorm_srgb"));
    assertEquals("bc1-rgba-unorm", jsOf(format, "BC1_RGBA_unorm"));
    assertEquals("astc-10x5-unorm-srgb", jsOf(format, "ASTC_10x5_unorm_srgb"));
    assertEquals("depth24plus-stencil8", jsOf(format, "depth24_plus_stencil8"));
    assertEquals("rg11b10ufloat", jsOf(format, "RG11_B10_ufloat"));
    assertEquals("unorm10-10-10-2", jsOf(map.enumFor("WGPUVertexFormat"), "unorm10__10__10__2"));
    assertEquals("unorm8x4-bgra", jsOf(map.enumFor("WGPUVertexFormat"), "unorm8x4_B_G_R_A"));
    assertEquals("2d-array", jsOf(map.enumFor("WGPUTextureViewDimension"), "2D_array"));
    assertEquals("ccw", jsOf(map.enumFor("WGPUFrontFace"), "CCW"));
    assertEquals("low-power", jsOf(map.enumFor("WGPUPowerPreference"), "low_power"));
    assertEquals("texture-compression-bc", jsOf(map.enumFor("WGPUFeatureName"), "texture_compression_BC"));
    assertEquals("opaque", jsOf(map.enumFor("WGPUCompositeAlphaMode"), "opaque"));
  }

  @Test
  void theDefaultValueIsLeftOut() {
    assertEquals(Support.OMIT, supportOf(map.enumFor("WGPUTextureFormat"), "undefined"));
    assertEquals(Support.OMIT, supportOf(map.enumFor("WGPULoadOp"), "undefined"));
    assertEquals(Support.OMIT, supportOf(map.enumFor("WGPUCompositeAlphaMode"), "auto"));
  }

  @Test
  void nativeOnlyValuesAreListedAndUnsupported() {
    assertEquals(List.of(
        "buffer_binding_type.binding_not_used",
        "composite_alpha_mode.unpremultiplied",
        "composite_alpha_mode.inherit",
        "device_lost_reason.callback_cancelled",
        "device_lost_reason.failed_creation",
        "sampler_binding_type.binding_not_used",
        "storage_texture_access.binding_not_used",
        "texture_sample_type.binding_not_used"), map.unsupportedValues());
    assertEquals(Support.NONE, supportOf(map.enumFor("WGPUCompositeAlphaMode"), "inherit"));
  }

  @Test
  void enumsWithoutAStringStayNative() {
    for (String name : List.of("WGPUBackendType", "WGPUCallbackMode", "WGPUStatus", "WGPUSType", "WGPUPresentMode",
        "WGPURequestAdapterStatus", "WGPUMapAsyncStatus", "WGPUErrorType")) {
      assertNull(map.enumFor(name), name);
    }
    EnumType filter = SpecFiles.load().enumType("WGPUErrorFilter");
    assertNotNull(map.enumFor(filter.cName()));
  }

  @Test
  void bitFlagNumbersAreIdenticalInBothSpecifications() {
    // The merge compares every browser constant with the C value of the same name. Spot checks make the intent visible.
    assertEquals(0x0020L, idl.namespace("GPUBufferUsage").constants().stream()
        .filter(c -> c.name().equals("VERTEX")).findFirst().orElseThrow().number());
    assertEquals(List.of(), map.problems().stream().filter(p -> p.startsWith("bit flag")).toList());
    int namespaces = 0;
    for (WebIdl.Namespace n : idl.namespaces()) {
      assertTrue(n.name().startsWith("GPU"));
      namespaces++;
    }
    assertEquals(5, namespaces);
  }

  @Test
  void mapsStructMembersByName() {
    StructMapping texture = map.structFor("WGPUTextureDescriptor");
    assertEquals("GPUTextureDescriptor", texture.idlName());
    assertEquals("size", texture.field("size").js());
    assertEquals("mipLevelCount", texture.field("mipLevelCount").js());
    assertEquals("viewFormats", texture.field("viewFormats").js());
    assertNull(texture.field("viewFormatCount"), "counts are folded into the array");

    assertEquals("GPUColorDict", map.structFor("WGPUColor").idlName());
    assertEquals("GPUExtent3DDict", map.structFor("WGPUExtent3D").idlName());
    assertEquals("GPUCanvasConfiguration", map.structFor("WGPUSurfaceConfiguration").idlName());
    assertEquals("GPUProgrammableStage", map.structFor("WGPUComputeState").idlName());
    assertTrue(map.structFor("WGPUTexelCopyBufferInfo").field("layout").flatten());
    assertNull(map.structFor("WGPULimits"));
    assertNull(map.structFor("WGPUAdapterInfo"));
    assertNull(map.structFor("WGPUShaderSourceWGSL"), "extension structs are flattened into their parent");
  }

  @Test
  void membersOfTheSurfaceConfigurationThatTheBrowserLacksAreIgnored() {
    StructMapping c = map.structFor("WGPUSurfaceConfiguration");
    assertNull(c.field("width").js());
    assertNull(c.field("presentMode").js());
    assertEquals("device", c.field("device").js());
  }

  @Test
  void bindsFunctions() {
    assertBinding("wgpuDeviceCreateBuffer", Binding.Kind.METHOD, "GPUDevice", "createBuffer");
    assertBinding("wgpuTextureGetWidth", Binding.Kind.GET, "GPUTexture", null);
    assertBinding("wgpuDeviceGetQueue", Binding.Kind.GET, "GPUDevice", null);
    assertBinding("wgpuBufferSetLabel", Binding.Kind.LABEL, "GPUBuffer", null);
    assertBinding("wgpuRenderPassEncoderSetBindGroup", Binding.Kind.SPECIAL, "GPURenderPassEncoder", "setBindGroup");
    assertBinding("wgpuInstanceRequestAdapter", Binding.Kind.SPECIAL, "GPU", "requestAdapter");
    assertBinding("wgpuQueueSubmit", Binding.Kind.METHOD, "GPUQueue", "submit");
    assertBinding("wgpuCommandEncoderWriteTimestamp", Binding.Kind.NONE, "GPUCommandEncoder", null);
    assertEquals(5, map.bindingFor("wgpuCommandEncoderCopyBufferToBuffer").op().args().size(),
        "the five argument overload is chosen");
    assertEquals(3, map.bindingFor("wgpuCommandEncoderClearBuffer").op().args().size());
  }

  @Test
  void everyFunctionHasABindingAndTheUnboundBrowserOperationsAreKnown() {
    List<String> none = new ArrayList<>();
    for (GpuFunction f : SpecFiles.load().functions()) {
      if (f.kind() == io.github.stringdotjar.jwebgpu.generator.model.FunctionKind.NORMAL
          || f.kind() == io.github.stringdotjar.jwebgpu.generator.model.FunctionKind.GET_PROC_ADDRESS) {
        Binding b = map.bindingFor(f.cName());
        if (b.kind() == Binding.Kind.NONE) {
          none.add(f.cName());
        }
      }
    }
    assertEquals(List.of("wgpuCommandEncoderWriteTimestamp"), none);
    assertEquals(List.of("GPU.getPreferredCanvasFormat", "GPUDevice.importExternalTexture",
        "GPUQueue.copyExternalImageToTexture"), map.unboundOperations());
  }

  private static void assertBinding(String cName, Binding.Kind kind, String iface, String op) {
    Binding b = map.bindingFor(cName);
    assertEquals(kind, b.kind(), cName);
    assertEquals(iface, b.iface(), cName);
    if (op != null) {
      assertEquals(op, b.op().name(), cName);
    }
  }

  private static String jsOf(EnumMapping m, String specName) {
    return m.js().get(indexOf(m, specName));
  }

  private static Support supportOf(EnumMapping m, String specName) {
    return m.support().get(indexOf(m, specName));
  }

  private static int indexOf(EnumMapping m, String specName) {
    for (int i = 0; i < m.c().values().size(); i++) {
      if (m.c().values().get(i).specName().equals(specName)) {
        return i;
      }
    }
    throw new AssertionError("no value " + specName + " in " + m.c().specName());
  }
}
