package io.github.stringdotjar.jwebgpu.generator.emit;

import io.github.stringdotjar.jwebgpu.generator.model.CType;
import io.github.stringdotjar.jwebgpu.generator.model.Callback;
import io.github.stringdotjar.jwebgpu.generator.model.GpuFunction;
import io.github.stringdotjar.jwebgpu.generator.model.Member;
import io.github.stringdotjar.jwebgpu.generator.model.MemberRole;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;
import io.github.stringdotjar.jwebgpu.generator.model.StructKind;
import io.github.stringdotjar.jwebgpu.generator.model.StructLayout;
import io.github.stringdotjar.jwebgpu.generator.model.TypeKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates the C glue of the JNI backend: one file that calls {@code webgpu.h} on behalf of Java.
 *
 * <p>The Java side writes every descriptor into a scratch buffer at the offsets of the C layout, and it writes the
 * pointers inside the structs as absolute addresses. The scratch buffer therefore already <em>is</em> a valid C
 * struct, so the glue does not copy or convert anything. Each native function only casts its {@code long} arguments to
 * the C types and calls the C function. Think of a postal worker who gets a parcel that is already addressed: nothing
 * is repacked, the worker only carries it to the right door.
 *
 * <p>What the file contains:
 *
 * <ul>
 *   <li>compile-time checks ({@code _Static_assert}) that the size and the field offsets the Java side uses are the
 *       ones of the real {@code webgpu.h}, so a header that differs from the specification fails the build instead of
 *       corrupting memory at run time,
 *   <li>one {@code static} function per C function of the API, a table that registers them with
 *       {@code RegisterNatives}, and {@code JNI_OnLoad},
 *   <li>one callback function per callback type of the API. It attaches the calling thread to the Java virtual machine
 *       if needed (Dawn calls some callbacks from its own threads) and calls a static Java method with primitive
 *       arguments only, so a callback allocates nothing.
 * </ul>
 */
public final class JniCEmitter {

  static final String CLASS_PATH = "io/github/stringdotjar/jwebgpu/jni/";

  private static final String LICENSE = """
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
      """;

  private static final String PROLOGUE = """

      /* This file is generated from the specification. Do not edit it. */

      #include <jni.h>
      #include <pthread.h>
      #include <stddef.h>
      #include <stdint.h>

      #include "webgpu.h"

      _Static_assert(sizeof(void*) == 8, "the JNI backend needs a 64-bit target");

      #define JW_UNUSED __attribute__((unused))
      #define JW_ENV JNIEnv* env JW_UNUSED, jclass cls JW_UNUSED

      #ifdef __ANDROID__
      #define JW_ENV_PTR(p) ((JNIEnv**) (p))
      #else
      #define JW_ENV_PTR(p) ((void**) (p))
      #endif

      int jw_register_helpers(JNIEnv* env);

      static JavaVM* g_vm;
      static jclass g_callbacks;
      static pthread_key_t g_detach_key;

      static void jw_detach(void* value) {
        (void) value;
        (*g_vm)->DetachCurrentThread(g_vm);
      }

      /*
       * Returns the environment of the calling thread. Dawn calls some callbacks (a lost device, an uncaptured
       * error) from threads the Java virtual machine does not know, so such a thread is attached as a daemon thread,
       * and a key destructor detaches it again when the thread ends.
       */
      static JNIEnv* jw_env(void) {
        JNIEnv* env = NULL;
        jint r = (*g_vm)->GetEnv(g_vm, (void**) &env, JNI_VERSION_1_6);
        if (r == JNI_OK) {
          return env;
        }
        if (r == JNI_EDETACHED) {
          if ((*g_vm)->AttachCurrentThreadAsDaemon(g_vm, JW_ENV_PTR(&env), NULL) != JNI_OK) {
            return NULL;
          }
          pthread_setspecific(g_detach_key, (void*) 1);
          return env;
        }
        return NULL;
      }

      static void jw_check(JNIEnv* env) {
        if ((*env)->ExceptionCheck(env)) {
          (*env)->ExceptionDescribe(env);
          (*env)->ExceptionClear(env);
        }
      }

      """;

  private final Spec spec;
  private final FfmUse use;

  JniCEmitter(Spec spec, FfmUse use) {
    this.spec = spec;
    this.use = use;
  }

  /**
   * Returns the JNI type signature letter of a raw Java type.
   *
   * @param raw {@code long}, {@code int}, {@code short}, {@code float} or {@code double}.
   * @return The letter, such as {@code J} for {@code long}.
   */
  static String jniSignature(String raw) {
    return switch (raw) {
      case "long" -> "J";
      case "int" -> "I";
      case "short" -> "S";
      case "float" -> "F";
      case "double" -> "D";
      default -> throw new IllegalArgumentException("no JNI signature for " + raw);
    };
  }

  /**
   * Generates the C glue file.
   *
   * @param spec The resolved specification.
   * @return The single file {@code jwebgpu_jni.c}. The result is the same every time for the same specification.
   */
  public static List<GeneratedFile> emit(Spec spec) {
    return List.of(new JniCEmitter(spec, new FfmUse(spec)).emitFile());
  }

  private GeneratedFile emitFile() {
    StringBuilder c = new StringBuilder(LICENSE).append(PROLOGUE);
    emitLayoutChecks(c);
    List<Callback> callbacks = spec.callbacks();
    for (int i = 0; i < callbacks.size(); i++) {
      emitCallback(c, callbacks.get(i), i);
    }
    emitCallbackTable(c);
    for (GpuFunction f : spec.functions()) {
      emitFunction(c, f);
    }
    emitRegistration(c);
    return new GeneratedFile("jwebgpu_jni.c", c.toString());
  }

  private void emitLayoutChecks(StringBuilder c) {
    c.append("/* The Java side uses these sizes and offsets, so they must be the ones of the real header. */\n");
    for (Struct s : spec.structs()) {
      boolean needed = use.isWritten(s.cName()) || use.isRead(s.cName()) || use.isOut(s.cName())
          || s.kind() == StructKind.CALLBACK_INFO || s.cName().equals(Types.STRING_VIEW);
      if (!needed) {
        continue;
      }
      StructLayout layout = spec.layouts().of(s.cName());
      c.append("_Static_assert(sizeof(").append(s.cName()).append(") == ").append(layout.size()).append(", \"")
          .append(s.cName()).append(" size\");\n");
      for (int i = 0; i < s.fields().size(); i++) {
        Member m = s.fields().get(i);
        c.append("_Static_assert(offsetof(").append(s.cName()).append(", ").append(m.name()).append(") == ")
            .append(layout.offsets().get(i)).append(", \"").append(s.cName()).append('.').append(m.name())
            .append("\");\n");
      }
    }
    c.append('\n');
  }

  private void emitCallback(StringBuilder c, Callback cb, int index) {
    List<String> params = new ArrayList<>();
    List<String> args = new ArrayList<>();
    for (Member m : cb.params()) {
      CType t = m.type();
      params.add(cDecl(t) + " " + m.name());
      if (t.kind() == TypeKind.STRUCT && !t.isPointer() && t.name().equals(Types.STRING_VIEW)) {
        args.add("(jlong) (uintptr_t) " + m.name() + ".data");
        args.add("(jlong) " + m.name() + ".length");
      } else {
        args.add(toJava(t, m.name(), m.role()));
      }
    }
    c.append("static jmethodID g_method_").append(index).append(";\n");
    c.append("static void jw_callback_").append(index).append('(').append(String.join(", ", params)).append(") {\n");
    c.append("  JNIEnv* env = jw_env();\n");
    c.append("  if (env == NULL) {\n    return;\n  }\n");
    c.append("  (*env)->CallStaticVoidMethod(env, g_callbacks, g_method_").append(index);
    for (String a : args) {
      c.append(", ").append(a);
    }
    c.append(");\n  jw_check(env);\n}\n");
    c.append("/* Compile-time check that the function has the shape the header expects. */\n");
    c.append("static const ").append(cb.cName()).append(" g_check_").append(index).append(" JW_UNUSED = jw_callback_")
        .append(index).append(";\n\n");
  }

  private void emitCallbackTable(StringBuilder c) {
    List<Callback> callbacks = spec.callbacks();
    c.append("static void* const g_callback_table[] = {\n");
    for (int i = 0; i < callbacks.size(); i++) {
      c.append("  (void*) jw_callback_").append(i).append(",\n");
    }
    c.append("};\n\n");
    c.append("static jlong jw_callback_address(JW_ENV, jint index) {\n");
    c.append("  return (jlong) (uintptr_t) g_callback_table[index];\n}\n\n");
  }

  private void emitFunction(StringBuilder c, GpuFunction f) {
    List<String> params = new ArrayList<>();
    List<String> args = new ArrayList<>();
    for (int i = 0; i < f.params().size(); i++) {
      Member m = f.params().get(i);
      params.add("j" + JniNames.raw(m.type(), m.role()) + " a" + i);
      args.add(fromJava(m.type(), "a" + i));
    }
    String ret = f.returns() == null ? "void" : "j" + JniNames.raw(f.returns(), MemberRole.VALUE);
    c.append("static ").append(ret).append(" jw_").append(f.cName()).append("(JW_ENV");
    for (String p : params) {
      c.append(", ").append(p);
    }
    c.append(") {\n");
    String call = f.cName() + "(" + String.join(", ", args) + ")";
    if (f.returns() == null) {
      c.append("  ").append(call).append(";\n");
    } else {
      c.append("  return ").append(toJavaReturn(f.returns(), call)).append(";\n");
    }
    c.append("}\n\n");
  }

  private void emitRegistration(StringBuilder c) {
    c.append("static const JNINativeMethod g_methods[] = {\n");
    c.append("  {\"callbackAddress\", \"(I)J\", (void*) jw_callback_address},\n");
    for (GpuFunction f : spec.functions()) {
      StringBuilder sig = new StringBuilder("(");
      for (Member m : f.params()) {
        sig.append(jniSignature(JniNames.raw(m.type(), m.role())));
      }
      sig.append(')');
      sig.append(f.returns() == null ? "V" : jniSignature(JniNames.raw(f.returns(), MemberRole.VALUE)));
      c.append("  {\"").append(f.cName()).append("\", \"").append(sig).append("\", (void*) jw_").append(f.cName())
          .append("},\n");
    }
    c.append("};\n\n");

    c.append("JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved JW_UNUSED) {\n");
    c.append("  JNIEnv* env = NULL;\n");
    c.append("  g_vm = vm;\n");
    c.append("  if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_6) != JNI_OK) {\n    return JNI_ERR;\n  }\n");
    c.append("  if (pthread_key_create(&g_detach_key, jw_detach) != 0) {\n    return JNI_ERR;\n  }\n");
    c.append("  jclass natives = (*env)->FindClass(env, \"").append(CLASS_PATH).append("Jni\");\n");
    c.append("  if (natives == NULL) {\n    return JNI_ERR;\n  }\n");
    c.append("  if ((*env)->RegisterNatives(env, natives, g_methods, (jint) (sizeof(g_methods) / sizeof(g_methods[0])))"
        + " != 0) {\n    return JNI_ERR;\n  }\n");
    c.append("  if (jw_register_helpers(env) != 0) {\n    return JNI_ERR;\n  }\n");
    c.append("  jclass callbacks = (*env)->FindClass(env, \"").append(CLASS_PATH).append("JniCallbacks\");\n");
    c.append("  if (callbacks == NULL) {\n    return JNI_ERR;\n  }\n");
    c.append("  g_callbacks = (jclass) (*env)->NewGlobalRef(env, callbacks);\n");
    List<Callback> callbacks = spec.callbacks();
    for (int i = 0; i < callbacks.size(); i++) {
      Callback cb = callbacks.get(i);
      c.append("  g_method_").append(i).append(" = (*env)->GetStaticMethodID(env, g_callbacks, \"")
          .append(JniCallbackEmitter.method(cb)).append("\", \"").append(JniCallbackEmitter.signature(cb))
          .append("\");\n");
      c.append("  if (g_method_").append(i).append(" == NULL) {\n    return JNI_ERR;\n  }\n");
    }
    c.append("  return JNI_VERSION_1_6;\n}\n");
  }

  private static String cDecl(CType t) {
    return switch (t.pointer()) {
      case NONE -> t.name();
      case CONST -> t.name() + " const *";
      case MUTABLE -> t.name() + " *";
    };
  }

  private static String fromJava(CType t, String a) {
    if (t.isPointer()) {
      return "(" + cDecl(t) + ") (uintptr_t) " + a;
    }
    return switch (t.kind()) {
      case OBJECT, FUNCTION_POINTER -> "(" + t.name() + ") (uintptr_t) " + a;
      case STRUCT -> "*(" + t.name() + " const *) (uintptr_t) " + a;
      case VOID, CHAR -> throw new IllegalArgumentException("unsupported parameter type " + t);
      default -> "(" + t.name() + ") " + a;
    };
  }

  private static String toJava(CType t, String value, MemberRole role) {
    if (t.isPointer()) {
      return "(jlong) (uintptr_t) " + value;
    }
    return switch (t.kind()) {
      case OBJECT, FUNCTION_POINTER -> "(jlong) (uintptr_t) " + value;
      case ENUM, BOOL, UINT32, INT32 -> "(jint) " + value;
      case UINT16, INT16 -> "(jshort) " + value;
      case UINT64, USIZE, BITFLAG -> "(jlong) " + value;
      case FLOAT32 -> "(jfloat) " + value;
      case FLOAT64 -> "(jdouble) " + value;
      default -> throw new IllegalArgumentException("unsupported callback parameter " + t + " (" + role + ")");
    };
  }

  private static String toJavaReturn(CType t, String call) {
    if (t.kind() == TypeKind.STRUCT && !t.isPointer()) {
      if (t.name().equals("WGPUFuture")) {
        return "(jlong) " + call + ".id";
      }
      throw new IllegalArgumentException("unsupported struct return " + t.name());
    }
    return toJava(t, call, MemberRole.VALUE);
  }
}
