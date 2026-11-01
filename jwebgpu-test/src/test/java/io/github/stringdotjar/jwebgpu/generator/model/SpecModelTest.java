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
package io.github.stringdotjar.jwebgpu.generator.model;

import org.junit.jupiter.api.Test;

import io.github.stringdotjar.jwebgpu.generator.SpecFiles;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the resolved model against entries of the real vendored specification. */
class SpecModelTest {

  private final Spec spec = SpecFiles.load();

  private static List<String> names(List<Member> members) {
    List<String> result = new ArrayList<>();
    for (Member m : members) {
      result.add(m.name());
    }
    return result;
  }

  private static List<String> types(List<Member> members) {
    List<String> result = new ArrayList<>();
    for (Member m : members) {
      result.add(m.type().declaration());
    }
    return result;
  }

  @Test
  void specIsTheCoreWebGpuSpecification() {
    assertEquals("webgpu", spec.name());
    assertTrue(spec.copyright().contains("WebGPU-Native developers"));
    assertFalse(spec.doc().isEmpty());
  }

  @Test
  void renderPassColorAttachmentHasTheDeclaredMembersInOrder() {
    Struct s = spec.struct("WGPURenderPassColorAttachment");
    assertEquals(StructKind.EXTENSIBLE, s.kind());
    assertEquals(
        List.of("nextInChain", "view", "depthSlice", "resolveTarget", "loadOp", "storeOp", "clearValue"),
        names(s.fields()));
    assertEquals(
        List.of(
            "WGPUChainedStruct *",
            "WGPUTextureView",
            "uint32_t",
            "WGPUTextureView",
            "WGPULoadOp",
            "WGPUStoreOp",
            "WGPUColor"),
        types(s.fields()));
    assertEquals(MemberRole.CHAIN_NEXT, s.field("nextInChain").role());
    assertTrue(s.field("view").nullable());
    assertTrue(s.field("resolveTarget").nullable());
    assertFalse(s.field("depthSlice").nullable());
    assertEquals(TypeKind.OBJECT, s.field("view").type().kind());
    assertEquals(TypeKind.ENUM, s.field("loadOp").type().kind());
    assertEquals(TypeKind.STRUCT, s.field("clearValue").type().kind());
  }

  @Test
  void renderPassColorAttachmentDefaultsMatchTheInitMacro() {
    Struct s = spec.struct("WGPURenderPassColorAttachment");
    assertEquals(InitValue.Kind.CONSTANT, s.field("depthSlice").init().kind());
    assertEquals("WGPU_DEPTH_SLICE_UNDEFINED", s.field("depthSlice").init().text());
    assertEquals("WGPULoadOp_Undefined", s.field("loadOp").init().text());
    assertEquals("WGPU_COLOR_INIT", s.field("clearValue").init().text());
    assertEquals(InitValue.Kind.NULL_POINTER, s.field("view").init().kind());
  }

  @Test
  void renderPassColorAttachmentLayoutIsTheCLayout() {
    StructLayout layout = spec.layouts().of("WGPURenderPassColorAttachment");
    assertEquals(72, layout.size());
    assertEquals(8, layout.align());
    assertEquals(List.of(0L, 8L, 16L, 24L, 32L, 36L, 40L), layout.offsets());
  }

  @Test
  void arraysExpandIntoACountAndAPointer() {
    Struct s = spec.struct("WGPURenderPassDescriptor");
    Member count = s.field("colorAttachmentCount");
    Member data = s.field("colorAttachments");
    assertEquals(MemberRole.ARRAY_COUNT, count.role());
    assertEquals(MemberRole.ARRAY_DATA, data.role());
    assertEquals("size_t", count.type().declaration());
    assertEquals("WGPURenderPassColorAttachment const *", data.type().declaration());
    assertEquals("colorAttachments", count.peer());
    assertEquals("colorAttachmentCount", data.peer());
    assertTrue(data.isArrayData());
    assertEquals("array<struct.render_pass_color_attachment>", data.specType());
    assertTrue(names(s.fields()).indexOf("colorAttachmentCount") + 1 == names(s.fields()).indexOf("colorAttachments"));
  }

  @Test
  void entriesIsSingularizedToEntry() {
    Struct s = spec.struct("WGPUBindGroupDescriptor");
    assertEquals(MemberRole.ARRAY_COUNT, s.field("entryCount").role());
    assertEquals("WGPUBindGroupEntry const *", s.field("entries").type().declaration());
  }

  @Test
  void stringsAreStringViewsPassedByValue() {
    Struct s = spec.struct("WGPUBufferDescriptor");
    Member label = s.field("label");
    assertEquals("WGPUStringView", label.type().declaration());
    assertEquals("string_with_default_empty", label.specType());
    assertEquals(InitValue.Kind.STRING_VIEW_INIT, label.init().kind());
  }

  @Test
  void stringViewIsAPointerAndALength() {
    Struct s = spec.struct("WGPUStringView");
    assertEquals(StructKind.BUILTIN, s.kind());
    assertEquals(List.of("data", "length"), names(s.fields()));
    assertEquals("char const *", s.field("data").type().declaration());
    assertTrue(s.field("data").nullable());
    assertEquals("size_t", s.field("length").type().declaration());
    StructLayout layout = spec.layouts().of("WGPUStringView");
    assertEquals(16, layout.size());
    assertEquals(List.of(0L, 8L), layout.offsets());
  }

  @Test
  void boolIs32BitAndFlagsAre64Bit() {
    Struct s = spec.struct("WGPUBufferDescriptor");
    assertEquals(TypeKind.BOOL, s.field("mappedAtCreation").type().kind());
    assertEquals(4, s.field("mappedAtCreation").type().scalarSize());
    assertEquals(TypeKind.BITFLAG, s.field("usage").type().kind());
    assertEquals(8, s.field("usage").type().scalarSize());
    assertEquals(BitflagType.SIZE, 8);
    StructLayout layout = spec.layouts().of("WGPUBufferDescriptor");
    assertEquals(List.of(0L, 8L, 24L, 32L, 40L), layout.offsets());
    assertEquals(48, layout.size());
  }

  @Test
  void textureUsageValuesAreSingleBits() {
    BitflagType usage = spec.bitflag("WGPUTextureUsage");
    List<String> got = new ArrayList<>();
    for (BitflagValue v : usage.values()) {
      got.add(v.cName() + "=" + v.value());
    }
    assertEquals(
        List.of(
            "WGPUTextureUsage_None=0",
            "WGPUTextureUsage_CopySrc=1",
            "WGPUTextureUsage_CopyDst=2",
            "WGPUTextureUsage_TextureBinding=4",
            "WGPUTextureUsage_StorageBinding=8",
            "WGPUTextureUsage_RenderAttachment=16",
            "WGPUTextureUsage_TransientAttachment=32"),
        got);
  }

  @Test
  void bitflagCombinationsAreTheOrOfTheirParts() {
    BitflagType mask = spec.bitflag("WGPUColorWriteMask");
    BitflagValue all = mask.values().get(mask.values().size() - 1);
    assertEquals("WGPUColorWriteMask_All", all.cName());
    assertTrue(all.combination());
    assertEquals(15L, all.value());
    assertFalse(mask.values().get(1).combination());
  }

  @Test
  void enumValuesUseTheListIndexAndSkipNullPlaceholders() {
    EnumType mode = spec.enumType("WGPUCallbackMode");
    assertEquals(3, mode.values().size());
    assertEquals("WGPUCallbackMode_WaitAnyOnly", mode.values().get(0).cName());
    assertEquals(1, mode.values().get(0).value());
    assertEquals(3, mode.values().get(2).value());
    assertNull(mode.bySpecName("undefined"));

    EnumType loadOp = spec.enumType("WGPULoadOp");
    assertEquals(0, loadOp.bySpecName("undefined").value());
    assertEquals(EnumType.FORCE32, 0x7FFFFFFF);
  }

  @Test
  void sTypeValuesMatchTheirPositionInTheEnum() {
    EnumType sType = spec.enumType("WGPUSType");
    assertEquals(1, sType.bySpecName("shader_source_SPIRV").value());
    assertEquals(2, sType.bySpecName("shader_source_WGSL").value());
    assertEquals(16, sType.bySpecName("texture_binding_view_dimension").value());
    assertEquals("WGPUSType_ShaderSourceWGSL", sType.bySpecName("shader_source_WGSL").cName());
  }

  @Test
  void extensionsKnowWhatTheyExtendAndViceVersa() {
    Struct wgsl = spec.struct("WGPUShaderSourceWGSL");
    assertEquals(StructKind.EXTENSION, wgsl.kind());
    assertEquals("WGPUSType_ShaderSourceWGSL", wgsl.sType());
    assertEquals(2, wgsl.sTypeValue());
    assertEquals(List.of("WGPUShaderModuleDescriptor"), wgsl.extendsStructs());
    assertEquals(List.of("chain", "code"), names(wgsl.fields()));
    assertEquals(MemberRole.CHAIN_HEADER, wgsl.field("chain").role());
    assertEquals("WGPUChainedStruct", wgsl.field("chain").type().declaration());

    Struct module = spec.struct("WGPUShaderModuleDescriptor");
    assertEquals(List.of("WGPUShaderSourceSPIRV", "WGPUShaderSourceWGSL"), module.extensions());
    assertTrue(module.isExtensible());
    assertFalse(wgsl.isExtensible());
  }

  @Test
  void extensionWithoutMembersIsJustTheChainHeader() {
    Struct s = spec.struct("WGPUExternalTextureBindingLayout");
    assertEquals(List.of("chain"), names(s.fields()));
    assertEquals(16, spec.layouts().of(s.cName()).size());
  }

  @Test
  void chainedStructIsAPointerAndAnEnum() {
    Struct s = spec.struct("WGPUChainedStruct");
    assertEquals(List.of("next", "sType"), names(s.fields()));
    assertEquals("WGPUChainedStruct *", s.field("next").type().declaration());
    assertEquals("WGPUSType", s.field("sType").type().declaration());
  }

  @Test
  void requestAdapterTakesACallbackInfoByValueAndReturnsAFuture() {
    GpuFunction f = spec.function("wgpuInstanceRequestAdapter");
    assertEquals("WGPUInstance", f.owner());
    assertTrue(f.isAsync());
    assertTrue(f.isMethod());
    assertEquals("WGPURequestAdapterCallback", f.callback());
    assertEquals(List.of("instance", "options", "callbackInfo"), names(f.params()));
    assertEquals(
        List.of("WGPUInstance", "WGPURequestAdapterOptions const *", "WGPURequestAdapterCallbackInfo"),
        types(f.params()));
    assertEquals(MemberRole.SELF, f.params().get(0).role());
    assertTrue(f.params().get(1).nullable());
    assertEquals(MemberRole.CALLBACK_INFO, f.params().get(2).role());
    assertEquals("WGPUFuture", f.returns().declaration());
    assertEquals(TypeKind.STRUCT, f.returns().kind());
  }

  @Test
  void requestAdapterCallbackInfoHasTheExpandedLayout() {
    Struct info = spec.struct("WGPURequestAdapterCallbackInfo");
    assertEquals(StructKind.CALLBACK_INFO, info.kind());
    assertEquals(List.of("nextInChain", "mode", "callback", "userdata1", "userdata2"), names(info.fields()));
    assertEquals(
        List.of("WGPUChainedStruct *", "WGPUCallbackMode", "WGPURequestAdapterCallback", "void *", "void *"),
        types(info.fields()));
    assertEquals(TypeKind.FUNCTION_POINTER, info.field("callback").type().kind());
    assertEquals(MemberRole.USERDATA, info.field("userdata1").role());
    StructLayout layout = spec.layouts().of(info.cName());
    assertEquals(List.of(0L, 8L, 16L, 24L, 32L), layout.offsets());
    assertEquals(40, layout.size());
  }

  @Test
  void requestAdapterCallbackEndsWithTheTwoUserdataPointers() {
    Callback cb = spec.callback("WGPURequestAdapterCallback");
    assertEquals("WGPURequestAdapterCallbackInfo", cb.infoName());
    assertEquals(CallbackStyle.CALLBACK_MODE, cb.style());
    assertEquals(List.of("status", "adapter", "message", "userdata1", "userdata2"), names(cb.params()));
    assertEquals(
        List.of("WGPURequestAdapterStatus", "WGPUAdapter", "WGPUStringView", "void *", "void *"), types(cb.params()));
  }

  @Test
  void immediateCallbacksHaveNoModeAndNoFuture() {
    Callback cb = spec.callback("WGPUUncapturedErrorCallback");
    assertEquals(CallbackStyle.IMMEDIATE, cb.style());
    Struct info = spec.struct(cb.infoName());
    assertEquals(List.of("nextInChain", "callback", "userdata1", "userdata2"), names(info.fields()));
    assertEquals("WGPUDevice const *", cb.params().get(0).type().declaration());
    assertEquals(32, spec.layouts().of(info.cName()).size());
  }

  @Test
  void callbackInfoMembersAreEmbeddedByValue() {
    Struct desc = spec.struct("WGPUDeviceDescriptor");
    Member lost = desc.field("deviceLostCallbackInfo");
    assertEquals(MemberRole.CALLBACK_INFO, lost.role());
    assertEquals("WGPUDeviceLostCallbackInfo", lost.type().declaration());
    assertEquals(InitValue.Kind.CALLBACK_INFO_INIT, lost.init().kind());
    assertEquals("WGPU_DEVICE_LOST_CALLBACK_INFO_INIT", lost.init().text());
  }

  @Test
  void everyObjectGetsAddRefAndReleaseAfterItsOwnMethods() {
    assertEquals(23, spec.objects().size());
    for (GpuObject o : spec.objects()) {
      List<GpuFunction> m = o.methods();
      GpuFunction addRef = m.get(m.size() - 2);
      GpuFunction release = m.get(m.size() - 1);
      assertEquals(FunctionKind.ADD_REF, addRef.kind());
      assertEquals(FunctionKind.RELEASE, release.kind());
      assertEquals("wgpu" + o.cName().substring(4) + "AddRef", addRef.cName());
      assertEquals("wgpu" + o.cName().substring(4) + "Release", release.cName());
      assertEquals(List.of(o.cName()), types(addRef.params()));
      assertNull(release.returns());
    }
  }

  @Test
  void methodsAreSortedByName() {
    List<String> names = new ArrayList<>();
    for (GpuFunction f : spec.object("WGPUBuffer").methods()) {
      names.add(f.cName());
    }
    assertEquals("wgpuBufferDestroy", names.get(0));
    assertEquals("wgpuBufferGetConstMappedRange", names.get(1));
    assertEquals("wgpuBufferAddRef", names.get(names.size() - 2));
  }

  @Test
  void freeMembersFunctionsTakeTheStructByValue() {
    GpuFunction f = spec.function("wgpuSurfaceCapabilitiesFreeMembers");
    assertEquals(FunctionKind.FREE_MEMBERS, f.kind());
    assertEquals(List.of("WGPUSurfaceCapabilities"), types(f.params()));
    assertEquals(TypeKind.STRUCT, f.params().get(0).type().kind());
    assertTrue(spec.struct("WGPUSurfaceCapabilities").freeMembers());
    long count = spec.functions().stream().filter(x -> x.kind() == FunctionKind.FREE_MEMBERS).count();
    assertEquals(5, count);
  }

  @Test
  void getProcAddressIsAddedAsAGlobalFunction() {
    GpuFunction f = spec.function("wgpuGetProcAddress");
    assertEquals(FunctionKind.GET_PROC_ADDRESS, f.kind());
    assertNull(f.owner());
    assertEquals("WGPUProc", f.returns().declaration());
    assertEquals(List.of("WGPUStringView"), types(f.params()));
    assertEquals(
        List.of("wgpuCreateInstance", "wgpuGetInstanceFeatures", "wgpuGetInstanceLimits", "wgpuHasInstanceFeature",
            "wgpuGetProcAddress"),
        spec.globalFunctions().stream().map(GpuFunction::cName).toList());
  }

  @Test
  void createInstanceReturnsAnOwnedNullableObject() {
    GpuFunction f = spec.function("wgpuCreateInstance");
    assertEquals("WGPUInstance", f.returns().declaration());
    assertEquals(Boolean.TRUE, f.returnOwned());
    assertTrue(f.params().get(0).nullable());
    assertEquals("WGPUInstanceDescriptor const *", f.params().get(0).type().declaration());
  }

  @Test
  void constantsKeepTheirExactValues() {
    Constant slice = spec.constant("WGPU_DEPTH_SLICE_UNDEFINED");
    assertEquals(ConstantKind.UINT32, slice.kind());
    assertEquals(BigInteger.valueOf(0xFFFFFFFFL), slice.unsigned());
    assertEquals("UINT32_MAX", slice.cValue());

    Constant strlen = spec.constant("WGPU_STRLEN");
    assertEquals(ConstantKind.USIZE, strlen.kind());
    assertEquals(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE), strlen.unsigned());
    assertEquals("SIZE_MAX", strlen.cValue());

    assertEquals(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE), spec.constant("WGPU_WHOLE_SIZE").unsigned());
    assertEquals(ConstantKind.NAN, spec.constant("WGPU_DEPTH_CLEAR_VALUE_UNDEFINED").kind());
    assertThrows(IllegalStateException.class, () -> spec.constant("WGPU_DEPTH_CLEAR_VALUE_UNDEFINED").unsigned());
  }

  @Test
  void futureIsASingleUint64() {
    Struct future = spec.struct("WGPUFuture");
    assertEquals(List.of("id"), names(future.fields()));
    assertEquals("uint64_t", future.field("id").type().declaration());
    assertEquals(8, spec.layouts().of("WGPUFuture").size());
  }

  @Test
  void structsAreDeclaredAfterTheStructsTheyContain() {
    List<String> order = spec.structs().stream().map(Struct::cName).toList();
    for (Struct s : spec.structs()) {
      for (Member m : s.fields()) {
        if (m.type().kind() == TypeKind.STRUCT && !m.type().isPointer()) {
          assertTrue(
              order.indexOf(m.type().name()) < order.indexOf(s.cName()),
              s.cName() + " contains " + m.type().name() + " by value, which must be declared first");
        }
      }
    }
  }

  @Test
  void everyReferencedTypeExists() {
    for (Struct s : spec.structs()) {
      for (Member m : s.fields()) {
        assertTypeExists(s.cName() + "." + m.name(), m.type());
      }
    }
    for (GpuFunction f : spec.functions()) {
      for (Member m : f.params()) {
        assertTypeExists(f.cName() + "(" + m.name() + ")", m.type());
      }
      if (f.returns() != null) {
        assertTypeExists(f.cName() + " return", f.returns());
      }
    }
  }

  private void assertTypeExists(String where, CType type) {
    switch (type.kind()) {
      case ENUM -> assertNotNull(spec.findEnum(type.name()), where);
      case STRUCT -> assertNotNull(spec.findStruct(type.name()), where);
      case OBJECT -> spec.object(type.name());
      case BITFLAG -> spec.bitflag(type.name());
      default -> {
      }
    }
  }

  @Test
  void unknownLookupsFailClearly() {
    assertThrows(IllegalArgumentException.class, () -> spec.struct("WGPUNope"));
    assertNull(spec.findStruct("WGPUNope"));
    assertNull(spec.findFunction("wgpuNope"));
  }

  @Test
  void sizesAreInTheExpectedRange() {
    for (Struct s : spec.structs()) {
      StructLayout l = spec.layouts().of(s.cName());
      assertTrue(l.size() >= 8, s.cName());
      assertEquals(0, l.size() % l.align(), s.cName());
    }
  }

  @Test
  void documentationIsKeptAndTodoIsDropped() {
    assertFalse(spec.function("wgpuInstanceProcessEvents").doc().isEmpty());
    assertEquals("", spec.function("wgpuInstanceRequestAdapter").doc());
  }
}
