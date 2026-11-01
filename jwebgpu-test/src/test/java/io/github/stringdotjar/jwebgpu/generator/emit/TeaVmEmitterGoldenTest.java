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
import io.github.stringdotjar.jwebgpu.generator.model.GpuObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden-file tests of the web backend emitter.
 *
 * <p>The set covers one browser interface, one object class with plain, special and raw-data methods, and one
 * descriptor marshaller. Further tests check the properties that matter for the whole output: the files are complete,
 * stable and ASCII, and the per-call code of the marshallers allocates nothing.
 */
class TeaVmEmitterGoldenTest {

  private static List<GeneratedFile> files;

  @BeforeAll
  static void emit() {
    files = TeaVmEmitter.emit(SpecFiles.load(), SpecFiles.loadIdl());
  }

  private static String source(String type) {
    String path = "io/github/stringdotjar/jwebgpu/teavm/" + type + ".java";
    for (GeneratedFile f : files) {
      if (f.path().equals(path)) {
        return f.source();
      }
    }
    throw new AssertionError("not generated: " + path);
  }

  @Test
  void bufferInterface() {
    Golden.check("JsGpuBuffer.java.txt", source("JsGpuBuffer"));
  }

  @Test
  void queueObject() {
    Golden.check("TeaVmGpuQueue.java.txt", source("TeaVmGpuQueue"));
  }

  @Test
  void renderPassDescriptorMarshaller() {
    Golden.check("TeaVmGpuRenderPassDescriptorStruct.java.txt", source("TeaVmGpuRenderPassDescriptorStruct"));
  }

  @Test
  void everyObjectHasAWrapperAndAnInterface() {
    for (GpuObject o : SpecFiles.load().objects()) {
      String name = TeaVmNames.object(o.cName());
      assertTrue(source(name).contains("public final class " + name), name);
    }
    assertTrue(source("JsGpuDevice").contains("JsGpuQueue getQueue();"));
    assertTrue(source("JsGpuDevice").contains("JsGpuBuffer createBuffer(JSObject descriptor);"));
    assertTrue(source("JsGpu").contains("JSPromise<JSObject> requestAdapter(JSObject options);"));
  }

  @Test
  void enumsAreInternedStringTables() {
    String text = source("TeaVmEnums");
    assertTrue(text.contains("case \"bgra8unorm-srgb\" -> GpuTextureFormat.BGRA8_UNORM_SRGB;"));
    assertTrue(text.contains("TeaVmJs.string(\"bgra8unorm-srgb\")"));
    assertTrue(text.contains("static final boolean[] NONE = "));
    assertFalse(text.contains("+ \""), "tables are built once, without string concatenation");
  }

  @Test
  void optionalSizesAreLeftOutOfTheCall() {
    String text = source("TeaVmGpuRenderPassEncoder");
    assertTrue(text.contains("if (size == GpuConstants.WHOLE_SIZE) {"));
  }

  @Test
  void unavailableCallsThrow() {
    String text = source("TeaVmGpuCommandEncoder");
    assertTrue(text.contains("public void writeTimestamp("));
    assertTrue(text.contains("throw TeaVmJs.unsupported("));
  }

  @Test
  void marshallersAllocateNothingPerCall() {
    int checked = 0;
    for (GeneratedFile f : files) {
      if (!f.path().endsWith("Struct.java")) {
        continue;
      }
      String body = f.source().substring(f.source().indexOf("void write("));
      for (String line : body.split("\n")) {
        String t = line.trim();
        boolean lazy = t.startsWith("k = new ") || t.matches("\\w+ = new \\w+Struct\\(\\);");
        boolean fail = t.startsWith("throw ");
        if (t.contains("new ") && !lazy && !fail) {
          throw new AssertionError(f.path() + " allocates per call: " + t);
        }
        assertFalse(t.contains("->"), f.path() + " creates a lambda: " + t);
      }
      checked++;
    }
    assertTrue(checked > 40, "checked " + checked + " marshallers");
  }

  @Test
  void emittingTwiceGivesTheSameFiles() {
    assertEquals(files, TeaVmEmitter.emit(SpecFiles.load(), SpecFiles.loadIdl()));
  }

  @Test
  void everyFileHasTheHeaderAndAUniquePath() {
    Set<String> paths = new HashSet<>();
    for (GeneratedFile f : files) {
      assertTrue(paths.add(f.path()), f.path());
      assertTrue(f.source().startsWith("/*\n * MIT License"), f.path());
      assertTrue(f.source().endsWith("}\n"), f.path());
      assertTrue(f.source().chars().allMatch(c -> c < 0x80), f.path() + " is not ASCII");
      assertFalse(f.source().contains("Class.forName"), f.path());
      assertFalse(f.source().contains("java.lang.reflect"), f.path());
    }
  }
}
