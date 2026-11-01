package io.github.stringdotjar.jwebgpu.generator.emit;

import io.github.stringdotjar.jwebgpu.generator.model.CType;
import io.github.stringdotjar.jwebgpu.generator.model.GpuFunction;
import io.github.stringdotjar.jwebgpu.generator.model.Member;
import io.github.stringdotjar.jwebgpu.generator.model.MemberRole;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates {@code Jni}, the class that declares every native function of the JNI backend.
 *
 * <p>There is one {@code static native} method per C function of {@code webgpu.h}, named exactly like the C function.
 * The parameters are the raw Java types of the call (see {@link JniNames#raw(CType, MemberRole)}): handles and pointers
 * are {@code long}, and a struct that C takes by value is the {@code long} address of the struct in scratch memory.
 * The native side registers them all with one {@code RegisterNatives} table, so the methods need no special names.
 *
 * <p>When Android annotations are requested, the methods that are cheap and never wait are marked
 * {@code @FastNative}, which makes ART skip the thread state change for the call. A call that can wait for the GPU, the
 * display or a compiler (presenting, compiling shaders and pipelines, creating the device) stays a normal native call,
 * because a fast native call that blocks would also stall the garbage collector.
 */
final class JniNativeEmitter {

  private static final String[] FAST_PREFIXES = {
      "wgpuRenderPassEncoder", "wgpuComputePassEncoder", "wgpuRenderBundleEncoder", "wgpuCommandEncoder",
      "wgpuCommandBuffer", "wgpuQueue", "wgpuTexture", "wgpuBuffer", "wgpuBindGroup", "wgpuSampler",
      "wgpuRenderPipeline", "wgpuComputePipeline", "wgpuQuerySet", "wgpuPipelineLayout", "wgpuRenderBundle",
      "wgpuShaderModule"
  };
  private static final String[] FAST_DEVICE = {
      "wgpuDeviceCreateBuffer", "wgpuDeviceCreateTexture", "wgpuDeviceCreateSampler", "wgpuDeviceCreateBindGroup",
      "wgpuDeviceCreateCommandEncoder", "wgpuDeviceGetQueue"
  };

  private final Spec spec;
  private final boolean android;

  JniNativeEmitter(Spec spec, boolean android) {
    this.spec = spec;
    this.android = android;
  }

  /**
   * Tells whether a native function is marked as a fast native call on Android.
   *
   * @param cName The C name of the function.
   * @return {@code true} for calls that are cheap and never wait.
   */
  static boolean isFast(String cName) {
    for (String prefix : FAST_PREFIXES) {
      if (cName.startsWith(prefix)) {
        return !cName.endsWith("MapAsync");
      }
    }
    for (String name : FAST_DEVICE) {
      if (cName.equals(name)) {
        return true;
      }
    }
    return false;
  }

  GeneratedFile emit() {
    SourceWriter w = new SourceWriter(JniNames.PKG);
    if (android) {
      w.imp("dalvik.annotation.optimization.FastNative");
    }
    w.doc("The native functions of the JNI backend, one per C function of {@code webgpu.h}.\n\n"
        + "<p>The C glue library registers all of them at once when it loads. A parameter is a {@code long} for every "
        + "handle and pointer, and a struct that C takes by value (a string view or a callback info) is passed as the "
        + "address of the struct in scratch memory. This class is generated from the specification.");
    w.openType("final class Jni");
    w.line("private Jni() {}");
    w.blank();
    w.doc("Returns the address of the C function that receives one kind of callback.\n\n"
        + "@param index The position of the callback in the specification.\n@return The function address.");
    w.line("static native long callbackAddress(int index);");
    w.blank();
    for (GpuFunction f : spec.functions()) {
      List<String> params = new ArrayList<>();
      for (int i = 0; i < f.params().size(); i++) {
        Member m = f.params().get(i);
        params.add(JniNames.raw(m.type(), m.role()) + " a" + i);
      }
      String ret = f.returns() == null ? "void" : JniNames.raw(f.returns(), MemberRole.VALUE);
      if (android && isFast(f.cName())) {
        w.line("@FastNative");
      }
      w.line("static native " + ret + " " + f.cName() + "(" + String.join(", ", params) + ");");
    }
    w.close();
    return new GeneratedFile(JniNames.path("Jni"), w.toSource());
  }
}
