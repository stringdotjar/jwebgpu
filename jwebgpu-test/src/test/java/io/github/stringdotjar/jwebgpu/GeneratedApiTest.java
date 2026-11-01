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

import io.github.stringdotjar.jwebgpu.generator.SpecFiles;
import io.github.stringdotjar.jwebgpu.generator.model.BitflagType;
import io.github.stringdotjar.jwebgpu.generator.model.BitflagValue;
import io.github.stringdotjar.jwebgpu.generator.model.Constant;
import io.github.stringdotjar.jwebgpu.generator.model.EnumType;
import io.github.stringdotjar.jwebgpu.generator.model.EnumValue;
import io.github.stringdotjar.jwebgpu.generator.model.InitValue;
import io.github.stringdotjar.jwebgpu.generator.model.Member;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;
import io.github.stringdotjar.jwebgpu.generator.model.StructKind;
import io.github.stringdotjar.jwebgpu.generator.model.TypeKind;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the behavior of the generated API classes against the model.
 *
 * <p>The tests are exhaustive: they walk the whole model and check the matching generated class for every enum, bit
 * flag type, constant and struct. They use reflection because the generated classes are many and the checks are
 * the same for each; reflection is only a testing tool here and the API itself uses none.
 */
class GeneratedApiTest {

  private static final String PKG = "io.github.stringdotjar.jwebgpu.";

  private static final Spec SPEC = SpecFiles.load();

  private static String javaName(String cName) {
    return PKG + "Gpu" + cName.substring("WGPU".length());
  }

  private static Class<?> classOf(String cName) throws ClassNotFoundException {
    return Class.forName(javaName(cName));
  }

  @Test
  void enumValuesRoundTripThroughOf() throws Exception {
    for (EnumType type : SPEC.enums()) {
      Class<?> c = classOf(type.cName());
      Object[] constants = c.getEnumConstants();
      assertEquals(type.values().size(), constants.length, type.cName());
      Method of = c.getMethod("of", int.class);
      Field value = c.getField("value");
      Set<Integer> numbers = new HashSet<>();
      for (Object constant : constants) {
        int number = value.getInt(constant);
        numbers.add(number);
        assertSame(constant, of.invoke(null, number), type.cName() + " " + constant);
      }
      for (EnumValue v : type.values()) {
        assertTrue(numbers.contains(v.value()), v.cName());
      }
      assertNull(of.invoke(null, 0x7FFFFFFE), type.cName() + " must not know 0x7FFFFFFE");
      assertNull(of.invoke(null, -12345), type.cName() + " must not know -12345");
    }
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
  void bitflagConstantsMatchTheModel() throws Exception {
    for (BitflagType type : SPEC.bitflags()) {
      Class<?> c = classOf(type.cName());
      assertTrue(Modifier.isFinal(c.getModifiers()), type.cName());
      Set<Long> actual = new HashSet<>();
      for (Field f : c.getFields()) {
        assertEquals(long.class, f.getType(), f.getName());
        assertTrue(Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers()), f.getName());
        actual.add(f.getLong(null));
      }
      Set<Long> expected = new HashSet<>();
      for (BitflagValue v : type.values()) {
        expected.add(v.value());
      }
      assertEquals(expected, actual, type.cName());
    }
    assertEquals(0x10L, GpuTextureUsage.RENDER_ATTACHMENT);
    assertEquals(0x4L, GpuTextureUsage.TEXTURE_BINDING);
    assertEquals(0x8L, GpuBufferUsage.COPY_DST);
    assertEquals(0xFL, GpuColorWriteMask.ALL);
    assertEquals(GpuColorWriteMask.RED | GpuColorWriteMask.GREEN | GpuColorWriteMask.BLUE | GpuColorWriteMask.ALPHA,
        GpuColorWriteMask.ALL);
  }

  @Test
  void constantsHaveTheExactCValues() throws Exception {
    assertEquals(-1L, GpuConstants.WHOLE_SIZE);
    assertEquals(-1L, GpuConstants.STRLEN);
    assertEquals(-1L, GpuConstants.WHOLE_MAP_SIZE);
    assertEquals(-1L, GpuConstants.LIMIT_U64_UNDEFINED);
    assertEquals(0xFFFFFFFF, GpuConstants.DEPTH_SLICE_UNDEFINED);
    assertEquals(0xFFFFFFFF, GpuConstants.LIMIT_U32_UNDEFINED);
    assertTrue(Float.isNaN(GpuConstants.DEPTH_CLEAR_VALUE_UNDEFINED));
    for (Constant c : SPEC.constants()) {
      Field f = GpuConstants.class.getField(c.specName().toUpperCase(Locale.ROOT));
      Object actual = f.get(null);
      switch (c.kind()) {
        case UINT32 -> assertEquals((int) c.value(), actual, c.cName());
        case UINT64, USIZE -> assertEquals(c.value(), actual, c.cName());
        case NAN -> assertTrue(Float.isNaN((Float) actual), c.cName());
      }
    }
  }

  @Test
  void everyStructStartsWithTheDefaultsOfTheHeader() throws Exception {
    int checked = 0;
    for (Struct s : SPEC.structs()) {
      if (s.kind() == StructKind.BUILTIN || s.kind() == StructKind.CALLBACK_INFO) {
        continue;
      }
      Object o = classOf(s.cName()).getConstructor().newInstance();
      checkDefaults(s, o);
      if (s.kind() == StructKind.EXTENSION) {
        assertEquals((int) s.sTypeValue(), ((GpuChainedStruct) o).sType(), s.cName());
      }
      checked++;
    }
    assertTrue(checked > 70, "checked " + checked + " structs");
  }

  @Test
  void resetRestoresTheDefaultsOfEveryStruct() throws Exception {
    for (Struct s : SPEC.structs()) {
      if (s.kind() == StructKind.BUILTIN || s.kind() == StructKind.CALLBACK_INFO) {
        continue;
      }
      Object o = classOf(s.cName()).getConstructor().newInstance();
      scramble(o);
      o.getClass().getMethod("reset").invoke(o);
      checkDefaults(s, o);
    }
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

  private void checkDefaults(Struct s, Object o) throws Exception {
    Class<?> c = o.getClass();
    for (Member m : s.fields()) {
      switch (m.role()) {
        case CHAIN_HEADER, USERDATA, SELF -> {
        }
        case CHAIN_NEXT -> assertNull(c.getField("nextInChain").get(o), s.cName());
        case ARRAY_COUNT -> assertEquals(0, c.getField(m.name()).get(o), s.cName() + "." + m.name());
        case ARRAY_DATA -> assertNotNull(c.getField(m.name()).get(o), s.cName() + "." + m.name());
        case CALLBACK_INFO -> {
          String base = m.name().replace("CallbackInfo", "");
          assertNull(c.getField(base + "Callback").get(o), s.cName() + "." + base);
          try {
            assertNull(c.getField(base + "Mode").get(o), s.cName() + "." + base);
          } catch (NoSuchFieldException e) {
            // Immediate callbacks have no mode.
          }
        }
        case VALUE -> checkValue(s, m, c.getField(m.name()).get(o));
      }
    }
  }

  private void checkValue(Struct s, Member m, Object actual) throws Exception {
    String where = s.cName() + "." + m.name();
    if (m.type().kind() == TypeKind.STRUCT && !m.type().isPointer() && !m.type().name().equals("WGPUStringView")) {
      assertNotNull(actual, where);
      return;
    }
    InitValue init = m.init();
    assertNotNull(init, where);
    switch (init.kind()) {
      case NUMBER -> assertNumber(Long.decode(init.text()), actual, where);
      case FLOAT -> {
        String text = init.text().replaceAll("[fF]$", "");
        assertNumber(Double.parseDouble(text), actual, where);
      }
      case BOOL -> assertEquals(init.text().equals("WGPU_TRUE"), actual, where);
      case ENUM -> {
        assertNotNull(actual, where);
        long expected = -1;
        for (EnumValue v : SPEC.enumType(m.type().name()).values()) {
          if (v.cName().equals(init.ref())) {
            expected = v.value();
          }
        }
        assertEquals((int) expected, actual.getClass().getField("value").getInt(actual), where);
      }
      case ENUM_ZERO, NULL_POINTER, STRING_VIEW_INIT, CALLBACK_INFO_INIT -> {
        if (actual instanceof Long l) {
          assertEquals(0L, l, where);
        } else {
          assertNull(actual, where);
        }
      }
      case BITFLAG -> {
        long expected = -1;
        for (BitflagValue v : SPEC.bitflag(m.type().name()).values()) {
          if (v.cName().equals(init.ref())) {
            expected = v.value();
          }
        }
        assertEquals(expected, actual, where);
      }
      case CONSTANT -> {
        Constant c = SPEC.constant(init.ref());
        switch (c.kind()) {
          case NAN -> assertTrue(Float.isNaN((Float) actual), where);
          case UINT32 -> {
            if (actual instanceof Long l) {
              assertEquals(c.value() & 0xFFFFFFFFL, l, where);
            } else {
              assertEquals((int) c.value(), actual, where);
            }
          }
          default -> assertEquals(c.value(), actual, where);
        }
      }
      case STRUCT_INIT, STRUCT_ZERO -> assertNotNull(actual, where);
    }
  }

  private static void assertNumber(Number expected, Object actual, String where) {
    switch (actual) {
      case Integer i -> assertEquals(expected.intValue(), i.intValue(), where);
      case Long l -> assertEquals(expected.longValue(), l.longValue(), where);
      case Short sh -> assertEquals(expected.shortValue(), sh.shortValue(), where);
      case Float f -> assertEquals(expected.floatValue(), f.floatValue(), where);
      case Double d -> assertEquals(expected.doubleValue(), d.doubleValue(), where);
      default -> throw new AssertionError(where + " has unexpected type " + actual);
    }
  }

  private static void scramble(Object o) throws Exception {
    for (Field f : o.getClass().getFields()) {
      if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) {
        continue;
      }
      Class<?> t = f.getType();
      if (t == int.class) {
        f.setInt(o, 12345);
      } else if (t == long.class) {
        f.setLong(o, 12345L);
      } else if (t == float.class) {
        f.setFloat(o, 12.5f);
      } else if (t == double.class) {
        f.setDouble(o, 12.5);
      } else if (t == boolean.class) {
        f.setBoolean(o, true);
      } else if (t == String.class) {
        f.set(o, "scrambled");
      } else if (t.isEnum()) {
        f.set(o, t.getEnumConstants()[t.getEnumConstants().length - 1]);
      }
    }
  }
}
