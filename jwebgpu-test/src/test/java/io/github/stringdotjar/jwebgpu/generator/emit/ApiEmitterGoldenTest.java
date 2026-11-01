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
package io.github.stringdotjar.jwebgpu.generator.emit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.stringdotjar.jwebgpu.generator.SpecFiles;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden-file tests of the API emitter.
 *
 * <p>Each test compares the emitted source of a representative type with a snapshot. The set covers every shape the
 * emitter writes: an enum, a bit flag class, an extensible struct with arrays and a nested struct, an extension
 * struct, an object with raw byte overloads, and an asynchronous method with its callback.
 */
class ApiEmitterGoldenTest {

  private static List<GeneratedFile> files;

  @BeforeAll
  static void emit() {
    files = ApiEmitter.emit(SpecFiles.load());
  }

  private static String source(String type) {
    String path = "io/github/stringdotjar/jwebgpu/" + type + ".java";
    for (GeneratedFile f : files) {
      if (f.path().equals(path)) {
        return f.source();
      }
    }
    throw new AssertionError("not generated: " + path);
  }

  @Test
  void textureFormatEnumHead() {
    String text = source("GpuTextureFormat");
    Golden.check("GpuTextureFormat.head.java.txt", Golden.head(text, 110));
    assertTrue(text.contains("public static @Nullable GpuTextureFormat of(int value)"));
  }

  @Test
  void textureUsageBitflags() {
    Golden.check("GpuTextureUsage.java.txt", source("GpuTextureUsage"));
  }

  @Test
  void renderPassDescriptor() {
    Golden.check("GpuRenderPassDescriptor.java.txt", source("GpuRenderPassDescriptor"));
  }

  @Test
  void renderPassColorAttachment() {
    Golden.check("GpuRenderPassColorAttachment.java.txt", source("GpuRenderPassColorAttachment"));
  }

  @Test
  void xlibWindowExtensionStruct() {
    Golden.check("GpuSurfaceSourceXlibWindow.java.txt", source("GpuSurfaceSourceXlibWindow"));
  }

  @Test
  void queueObjectWithBufferOverloads() {
    Golden.check("GpuQueue.java.txt", source("GpuQueue"));
  }

  @Test
  void instanceWithAsyncRequestAdapter() {
    Golden.check("GpuInstance.java.txt", source("GpuInstance"));
  }

  @Test
  void requestAdapterCallback() {
    Golden.check("GpuRequestAdapterCallback.java.txt", source("GpuRequestAdapterCallback"));
  }

  @Test
  void backendInterface() {
    Golden.check("GpuBackend.java.txt", source("GpuBackend"));
  }

  @Test
  void bindGroupSettersHaveAnOverloadWithoutDynamicOffsets() {
    for (String type : new String[] {"GpuRenderPassEncoder", "GpuComputePassEncoder", "GpuRenderBundleEncoder"}) {
      String text = source(type);
      assertTrue(text.contains("public void setBindGroup(int groupIndex, @Nullable GpuBindGroup group) {\n"
          + "    setBindGroup(groupIndex, group, EMPTY_INTS, 0);\n  }"), type);
      assertTrue(text.contains("public abstract void setBindGroup(\n      int groupIndex,"), type);
    }
    assertTrue(source("GpuObject").contains("protected static final int[] EMPTY_INTS = new int[0];"));
  }

  @Test
  void emittingTwiceGivesTheSameFiles() {
    List<GeneratedFile> again = ApiEmitter.emit(SpecFiles.load());
    assertEquals(files, again);
  }

  @Test
  void everyFileHasTheHeaderAndAUniquePath() {
    Set<String> paths = new HashSet<>();
    for (GeneratedFile f : files) {
      assertTrue(paths.add(f.path()), f.path());
      assertTrue(f.source().startsWith("/*\n * MIT License"), f.path());
      assertTrue(f.source().endsWith("}\n"), f.path());
      assertTrue(f.source().chars().allMatch(c -> c < 0x80), f.path() + " is not ASCII");
    }
    assertNotNull(source("GpuObject"));
    assertNotNull(source("GpuChainedStruct"));
    assertNotNull(source("GpuConstants"));
  }
}
