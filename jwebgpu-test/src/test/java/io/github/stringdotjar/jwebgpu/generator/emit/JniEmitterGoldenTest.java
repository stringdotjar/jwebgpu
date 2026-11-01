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
 * Golden-file tests of the JNI backend emitters (the Java classes and the C glue).
 *
 * <p>The set covers the shapes the emitters write: an object with raw data overloads, a marshaller for a struct with
 * nested structs, arrays and chains, the callback dispatchers, the native method declarations and the C functions that
 * call {@code webgpu.h}.
 */
class JniEmitterGoldenTest {

  private static List<GeneratedFile> files;
  private static List<GeneratedFile> androidFiles;
  private static String glue;

  @BeforeAll
  static void emit() {
    files = JniJavaEmitter.emit(SpecFiles.load(), false);
    androidFiles = JniJavaEmitter.emit(SpecFiles.load(), true);
    glue = JniCEmitter.emit(SpecFiles.load()).get(0).source();
  }

  private static String source(List<GeneratedFile> from, String type) {
    String path = "io/github/stringdotjar/jwebgpu/jni/" + type + ".java";
    for (GeneratedFile f : from) {
      if (f.path().equals(path)) {
        return f.source();
      }
    }
    throw new AssertionError("not generated: " + path);
  }

  private static String source(String type) {
    return source(files, type);
  }

  @Test
  void queueObject() {
    Golden.check("JniGpuQueue.java.txt", source("JniGpuQueue"));
  }

  @Test
  void renderPassDescriptorMarshaller() {
    Golden.check("JniGpuRenderPassDescriptorStruct.java.txt", source("JniGpuRenderPassDescriptorStruct"));
  }

  @Test
  void nativeFunctionsAreDeclaredInOneClass() {
    Golden.check("Jni.head.java.txt", Golden.head(source("Jni"), 70));
    String plain = source("Jni");
    assertTrue(plain.contains("static native void wgpuQueueSubmit(long a0, long a1, long a2);"));
    assertFalse(plain.contains("FastNative"), "the plain flavor has no Android annotation");
  }

  @Test
  void theAndroidFlavorMarksCheapCallsAsFastNative() {
    String text = source(androidFiles, "Jni");
    assertTrue(text.contains("import dalvik.annotation.optimization.FastNative;"));
    assertTrue(text.contains("@FastNative\n  static native void wgpuRenderPassEncoderDraw("));
    assertTrue(text.contains("  static native int wgpuInstanceWaitAny("), "waiting calls stay normal native calls");
    assertFalse(text.contains("@FastNative\n  static native int wgpuInstanceWaitAny("));
    assertFalse(text.contains("@FastNative\n  static native void wgpuSurfacePresent("));
  }

  @Test
  void adapterInfoReadsBackAndFreesItsMembers() {
    String text = source("JniGpuAdapterInfoStruct");
    assertTrue(text.contains("static void read(Scratch s, long a, GpuAdapterInfo d)"));
    assertTrue(text.contains("static void init(Scratch s, GpuAdapterInfo d, long a)"));
    assertTrue(text.contains("static void freeMembers(long a)"));
    assertFalse(text.contains("static long put("), "an output struct is never written");
  }

  @Test
  void descriptorsAreOnlyWritten() {
    String text = source("JniGpuBufferDescriptorStruct");
    assertTrue(text.contains("static void write("));
    assertFalse(text.contains("static void read("));
  }

  @Test
  void everyCallbackGetsOneEntryPoint() {
    String text = source("JniCallbacks");
    assertTrue(text.contains("static final long REQUEST_ADAPTER = Jni.callbackAddress("));
    assertTrue(text.contains("CallbackTable.take(userdata1)"));
    assertTrue(text.contains("CallbackTable.get(userdata1)"), "the uncaptured error callback stays registered");
    assertTrue(text.contains("CallbackTable.report(e)"));
  }

  @Test
  void chainsDispatchOnTheTag() {
    String text = source("JniChains");
    assertTrue(text.contains("case 2 -> JniGpuShaderSourceWGSLStruct.put(s, (GpuShaderSourceWGSL) c);"));
  }

  @Test
  void glueCallsTheCFunctionsAfterCastingTheArguments() {
    assertTrue(glue.contains("static void jw_wgpuQueueSubmit(JW_ENV, jlong a0, jlong a1, jlong a2) {\n"
        + "  wgpuQueueSubmit((WGPUQueue) (uintptr_t) a0, (size_t) a1, (WGPUCommandBuffer const *) (uintptr_t) a2);\n}"));
    assertTrue(glue.contains("{\"wgpuQueueSubmit\", \"(JJJ)V\", (void*) jw_wgpuQueueSubmit},"));
    assertTrue(glue.contains("wgpuQueueOnSubmittedWorkDone((WGPUQueue) (uintptr_t) a0, "
        + "*(WGPUQueueWorkDoneCallbackInfo const *) (uintptr_t) a1)"), "callback infos are copied from scratch");
    assertTrue(glue.contains("return (jlong) wgpuQueueOnSubmittedWorkDone("), "a future is returned as its id");
  }

  @Test
  void glueChecksTheLayoutAtCompileTime() {
    assertTrue(glue.contains("_Static_assert(offsetof(WGPURenderPassDescriptor, colorAttachments) == "));
    assertTrue(glue.contains("_Static_assert(sizeof(WGPUStringView) == 16, "));
  }

  @Test
  void glueForwardsCallbacksWithPrimitiveArguments() {
    assertTrue(glue.contains("static void jw_callback_0(WGPUMapAsyncStatus status, WGPUStringView message, "
        + "void * userdata1, void * userdata2) {"));
    assertTrue(glue.contains("(jlong) (uintptr_t) message.data, (jlong) message.length"));
    assertTrue(glue.contains("\"onBufferMap\", \"(IJJJJ)V\""));
    assertTrue(glue.contains("static const WGPUBufferMapCallback g_check_0"), "the shape is checked by the compiler");
  }

  @Test
  void emittingTwiceGivesTheSameFiles() {
    assertEquals(files, JniJavaEmitter.emit(SpecFiles.load(), false));
    assertEquals(glue, JniCEmitter.emit(SpecFiles.load()).get(0).source());
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
    assertTrue(glue.startsWith("/*\n * MIT License"));
    assertTrue(glue.chars().allMatch(c -> c < 0x80), "the glue is not ASCII");
  }
}
