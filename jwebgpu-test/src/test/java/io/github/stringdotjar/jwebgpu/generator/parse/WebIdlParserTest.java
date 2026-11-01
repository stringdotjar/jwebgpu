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
package io.github.stringdotjar.jwebgpu.generator.parse;

import io.github.stringdotjar.jwebgpu.generator.SpecFiles;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Argument;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Dictionary;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.DictionaryMember;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Interface;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Operation;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Type;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the WebIDL parser, on small inputs and on the vendored WebGPU IDL. */
class WebIdlParserTest {

  @Test
  void parsesEnumsWithCommentsAndTrailingComma() {
    WebIdl idl = WebIdlParser.parse("""
        // a comment
        enum GPUFilterMode {
            "nearest", /* inline */
            "linear",
        };
        """);
    assertEquals(List.of("nearest", "linear"), idl.enumType("GPUFilterMode").values());
  }

  @Test
  void parsesDictionariesWithInheritanceDefaultsAndRequired() {
    WebIdl idl = WebIdlParser.parse("""
        dictionary Base { USVString label = ""; };
        dictionary Child : Base {
            required unsigned long long size;
            sequence<DOMString> names = [];
            Other nested = {};
            (sequence<double> or ColorDict)? color;
            [Clamp] unsigned short anisotropy = 1;
            double lod = -1.5;
        };
        """);
    Dictionary child = idl.dictionary("Child");
    assertEquals("Base", child.parent());
    assertEquals(6, child.members().size());
    DictionaryMember size = child.members().get(0);
    assertTrue(size.required());
    assertEquals("unsigned long long", size.type().name());
    assertEquals("[]", child.members().get(1).defaultValue());
    assertEquals("{}", child.members().get(2).defaultValue());
    Type color = child.members().get(3).type();
    assertEquals(Type.Kind.UNION, color.kind());
    assertTrue(color.nullable());
    assertEquals("unsigned short", child.members().get(4).type().name());
    assertEquals("-1.5", child.members().get(5).defaultValue());
    assertEquals(List.of("label", "size", "names", "nested", "color", "anisotropy", "lod"),
        idl.allMembers("Child").stream().map(DictionaryMember::name).toList());
  }

  @Test
  void mergesPartialInterfacesAndMixins() {
    WebIdl idl = WebIdlParser.parse("""
        interface mixin Labeled { attribute USVString label; };
        [Exposed=(Window, Worker), SecureContext]
        interface Device : EventTarget {
            readonly attribute Queue queue;
            undefined destroy();
        };
        partial interface Device { Promise<GPUError?> popErrorScope(); };
        Device includes Labeled;
        """);
    Interface device = idl.iface("Device");
    assertEquals("EventTarget", device.parent());
    assertNotNull(device.operation("destroy"));
    assertNotNull(device.operation("popErrorScope"));
    assertEquals(Type.Kind.PROMISE, device.operation("popErrorScope").returns().kind());
    assertTrue(device.attribute("queue").readonly());
    assertFalse(device.attribute("label").readonly());
  }

  @Test
  void parsesOperationsWithOptionalArgumentsOverloadsAndExtendedAttributes() {
    WebIdl idl = WebIdlParser.parse("""
        interface Encoder {
            undefined copy(Buffer a, Buffer b, optional unsigned long long size);
            undefined copy(Buffer a, unsigned long long offset, Buffer b, unsigned long long offset2,
                optional unsigned long long size);
            undefined set(long index, Group? group, [AllowShared] Uint32Array data, unsigned long start);
        };
        """);
    Interface e = idl.iface("Encoder");
    List<Operation> copies = e.overloads("copy");
    assertEquals(2, copies.size());
    assertEquals(2, copies.get(0).requiredCount());
    assertTrue(copies.get(0).args().get(2).optional());
    assertEquals(4, copies.get(1).requiredCount());
    Argument group = e.operation("set").args().get(1);
    assertTrue(group.type().nullable());
    assertEquals("Uint32Array", e.operation("set").args().get(2).type().name());
  }

  @Test
  void resolvesTypedefsAndKeepsNullability() {
    WebIdl idl = WebIdlParser.parse("""
        typedef [EnforceRange] unsigned long GPUSize32;
        typedef (sequence<double> or ColorDict) GPUColor;
        """);
    assertEquals("unsigned long", idl.resolve(Type.named("GPUSize32")).name());
    Type color = idl.resolve(Type.named("GPUColor").asNullable());
    assertEquals(Type.Kind.UNION, color.kind());
    assertTrue(color.nullable());
  }

  @Test
  void parsesNamespacesWithConstants() {
    WebIdl idl = WebIdlParser.parse("""
        [Exposed=(Window, Worker), SecureContext]
        namespace GPUMapMode {
            const GPUFlagsConstant READ  = 0x0001;
            const GPUFlagsConstant WRITE = 0x0002;
        };
        """);
    assertEquals(1L, idl.namespace("GPUMapMode").constants().get(0).number());
    assertEquals(2L, idl.namespace("GPUMapMode").constants().get(1).number());
  }

  @Test
  void parsesSetLikeInterfaces() {
    WebIdl idl = WebIdlParser.parse("interface Features { readonly setlike<DOMString>; };");
    assertTrue(idl.iface("Features").setLike());
  }

  @Test
  void reportsTheLineOfSyntaxErrors() {
    SpecException e = assertThrows(SpecException.class, () -> WebIdlParser.parse("enum A {\n  \"a\"\n  42\n};"));
    assertTrue(e.getMessage().contains("line 3"), e.getMessage());
    assertThrows(SpecException.class, () -> WebIdlParser.parse("interface A {\n"));
    assertThrows(SpecException.class, () -> WebIdlParser.parse("A includes Missing;\ninterface A {};"));
    assertThrows(IllegalArgumentException.class, () -> WebIdlParser.parse("enum A { \"a\" };").iface("A"));
  }

  @Test
  void parsesTheVendoredWebGpuIdl() {
    WebIdl idl = SpecFiles.loadIdl();

    Interface device = idl.iface("GPUDevice");
    assertEquals("EventTarget", device.parent());
    assertNotNull(device.operation("createBuffer"));
    assertNotNull(device.operation("pushErrorScope"), "from a partial interface");
    assertNotNull(device.attribute("label"), "from the GPUObjectBase mixin");
    assertNotNull(device.attribute("lost"));
    assertEquals(Type.Kind.PROMISE, device.operation("createRenderPipelineAsync").returns().kind());

    assertEquals(2, idl.iface("GPUCommandEncoder").overloads("copyBufferToBuffer").size());
    assertEquals(2, idl.iface("GPURenderPassEncoder").overloads("setBindGroup").size(), "from a mixin");
    assertTrue(idl.iface("GPUSupportedFeatures").setLike());

    List<String> names = idl.allMembers("GPURenderPipelineDescriptor").stream().map(DictionaryMember::name).toList();
    assertEquals(List.of("label", "layout", "vertex", "primitive", "depthStencil", "multisample", "fragment"), names);

    assertTrue(idl.enumType("GPUTextureFormat").values().contains("bgra8unorm-srgb"));
    assertEquals(0x10L, constant(idl, "GPUBufferUsage", "INDEX"));
    assertNull(idl.findInterface("GPUNotThere"));
    assertTrue(idl.interfaces().size() > 25);
    assertTrue(idl.dictionaries().size() > 50);
    assertTrue(idl.enums().size() > 30);
  }

  private static long constant(WebIdl idl, String namespace, String name) {
    return idl.namespace(namespace).constants().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow()
        .number();
  }
}
