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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden-file tests of the FFM backend emitter.
 *
 * <p>The set covers the shapes the emitter writes: an object with raw data overloads, a marshaller for a struct with
 * nested structs, arrays and chains, and the callback and chain dispatchers.
 */
class FfmEmitterGoldenTest {

  private static List<GeneratedFile> files;

  @BeforeAll
  static void emit() {
    files = FfmEmitter.emit(SpecFiles.load());
  }

  private static String source(String type) {
    String path = "io/github/stringdotjar/jwebgpu/ffm/" + type + ".java";
    for (GeneratedFile f : files) {
      if (f.path().equals(path)) {
        return f.source();
      }
    }
    throw new AssertionError("not generated: " + path);
  }

  @Test
  void queueObject() {
    Golden.check("FfmGpuQueue.java.txt", source("FfmGpuQueue"));
  }

  @Test
  void renderPassDescriptorMarshaller() {
    Golden.check("FfmGpuRenderPassDescriptorStruct.java.txt", source("FfmGpuRenderPassDescriptorStruct"));
  }

  @Test
  void adapterInfoReadsBackAndFreesItsMembers() {
    String text = source("FfmGpuAdapterInfoStruct");
    assertTrue(text.contains("static void read(long a, GpuAdapterInfo d)"));
    assertTrue(text.contains("static void init(Scratch s, GpuAdapterInfo d, long a)"));
    assertTrue(text.contains("static void freeMembers(long a)"));
    assertFalse(text.contains("static long put("), "an output struct is never written");
  }

  @Test
  void descriptorsAreOnlyWritten() {
    String text = source("FfmGpuBufferDescriptorStruct");
    assertTrue(text.contains("static void write("));
    assertFalse(text.contains("static void read("));
  }

  @Test
  void everyCallbackGetsOneStub() {
    String text = source("FfmCallbacks");
    assertTrue(text.contains("static final long REQUEST_ADAPTER = Native.upcall("));
    assertTrue(text.contains("CallbackTable.take(userdata1)"));
    assertTrue(text.contains("CallbackTable.get(userdata1)"), "the uncaptured error callback stays registered");
    assertTrue(text.contains("CallbackTable.report(e)"));
  }

  @Test
  void chainsDispatchOnTheTag() {
    String text = source("FfmChains");
    assertTrue(text.contains("case 2 -> FfmGpuShaderSourceWGSLStruct.put(s, (GpuShaderSourceWGSL) c);"));
  }

  @Test
  void emittingTwiceGivesTheSameFiles() {
    assertEquals(files, FfmEmitter.emit(SpecFiles.load()));
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
  }
}
