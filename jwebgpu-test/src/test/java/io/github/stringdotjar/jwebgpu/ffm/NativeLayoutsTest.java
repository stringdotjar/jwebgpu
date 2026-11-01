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

import io.github.stringdotjar.jwebgpu.generator.SpecFiles;
import io.github.stringdotjar.jwebgpu.generator.emit.GeneratedFile;
import io.github.stringdotjar.jwebgpu.generator.emit.GraalEmitter;
import io.github.stringdotjar.jwebgpu.generator.parse.Json;
import io.github.stringdotjar.jwebgpu.generator.parse.JsonValue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that the native image metadata describes the hand-written layouts of {@code Native} exactly, and that the
 * backend jar ships the metadata the generator writes.
 *
 * <p>{@code Native.STRING_VIEW} and {@code Native.CALLBACK_INFO} are the two struct layouts that C functions take by
 * value. The generator spells them in the metadata by itself, so this test fails if the two ever disagree.
 */
class NativeLayoutsTest {

  private static final String PATH = "META-INF/native-image/io.github.stringdotjar/jwebgpu-ffm/";

  private static String generated(String name) {
    for (GeneratedFile f : GraalEmitter.emit(SpecFiles.load())) {
      if (f.path().equals(PATH + name)) {
        return f.source();
      }
    }
    throw new AssertionError(name);
  }

  private static String describe(MemoryLayout layout) {
    if (layout instanceof StructLayout s) {
      List<String> parts = new ArrayList<>();
      for (MemoryLayout m : s.memberLayouts()) {
        parts.add(describe(m));
      }
      return "struct(" + String.join(", ", parts) + ")";
    }
    if (layout instanceof PaddingLayout) {
      return "padding(" + layout.byteSize() + ")";
    }
    return switch (layout.byteSize() + ":" + ((ValueLayout) layout).carrier().getSimpleName()) {
      case "8:long" -> "jlong";
      case "4:int" -> "jint";
      default -> throw new AssertionError(layout);
    };
  }

  private static boolean registered(String section, String layout) {
    JsonValue.Obj root = Json.parse(generated("reachability-metadata.json")).asObj();
    for (JsonValue v : root.obj("foreign").arr(section)) {
      for (JsonValue p : v.asObj().arr("parameterTypes")) {
        if (p.asString().equals(layout)) {
          return true;
        }
      }
    }
    return false;
  }

  @Test
  void stringViewMatchesTheMetadata() {
    assertEquals("struct(jlong, jlong)", describe(Native.STRING_VIEW));
    assertTrue(registered("downcalls", describe(Native.STRING_VIEW)));
    assertTrue(registered("upcalls", describe(Native.STRING_VIEW)));
  }

  @Test
  void callbackInfoMatchesTheMetadata() {
    assertEquals("struct(jlong, jint, padding(4), jlong, jlong, jlong)", describe(Native.CALLBACK_INFO));
    assertTrue(registered("downcalls", describe(Native.CALLBACK_INFO)));
  }

  @Test
  void theJarShipsTheGeneratedFiles() throws IOException {
    for (String name : List.of("reachability-metadata.json", "native-image.properties")) {
      try (InputStream in = Native.class.getClassLoader().getResourceAsStream(PATH + name)) {
        assertNotNull(in, name + " is missing from the jwebgpu-ffm resources");
        assertEquals(generated(name), new String(in.readAllBytes(), StandardCharsets.UTF_8), name);
      }
    }
  }
}
