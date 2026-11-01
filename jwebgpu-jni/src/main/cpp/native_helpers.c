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

/*
 * The hand-written native helpers of the JNI backend: the functions of the Java class Native that are not WebGPU
 * functions, and (on Android only) the functions that turn an android.view.Surface into an ANativeWindow.
 *
 * The WebGPU functions, the callbacks and JNI_OnLoad are generated; see jwebgpu_jni.c.
 */

#include <jni.h>
#include <stdint.h>
#include <string.h>

#ifdef __ANDROID__
#include <android/native_window_jni.h>
#endif

#define JW_UNUSED __attribute__((unused))
#define JW_ENV JNIEnv* env JW_UNUSED, jclass cls JW_UNUSED

static jlong helper_address(JNIEnv* env, jclass cls JW_UNUSED, jobject buffer) {
  return (jlong) (uintptr_t) (*env)->GetDirectBufferAddress(env, buffer);
}

static jobject helper_wrap(JNIEnv* env, jclass cls JW_UNUSED, jlong address, jlong size) {
  return (*env)->NewDirectByteBuffer(env, (void*) (uintptr_t) address, size);
}

static jlong helper_strlen(JW_ENV, jlong address) {
  return (jlong) strlen((const char*) (uintptr_t) address);
}

static jbyte helper_peek_byte(JW_ENV, jlong address) {
  return *(const jbyte*) (uintptr_t) address;
}

static jshort helper_peek_short(JW_ENV, jlong address) {
  jshort value;
  memcpy(&value, (const void*) (uintptr_t) address, sizeof(value));
  return value;
}

static jint helper_peek_int(JW_ENV, jlong address) {
  jint value;
  memcpy(&value, (const void*) (uintptr_t) address, sizeof(value));
  return value;
}

static jlong helper_peek_long(JW_ENV, jlong address) {
  jlong value;
  memcpy(&value, (const void*) (uintptr_t) address, sizeof(value));
  return value;
}

static void helper_put_bytes(JNIEnv* env, jclass cls JW_UNUSED, jlong address, jbyteArray src, jint offset, jint count) {
  (*env)->GetByteArrayRegion(env, src, offset, count, (jbyte*) (uintptr_t) address);
}

static void helper_put_shorts(JNIEnv* env, jclass cls JW_UNUSED, jlong address, jshortArray src, jint offset, jint count) {
  (*env)->GetShortArrayRegion(env, src, offset, count, (jshort*) (uintptr_t) address);
}

static void helper_put_ints(JNIEnv* env, jclass cls JW_UNUSED, jlong address, jintArray src, jint offset, jint count) {
  (*env)->GetIntArrayRegion(env, src, offset, count, (jint*) (uintptr_t) address);
}

static void helper_put_floats(JNIEnv* env, jclass cls JW_UNUSED, jlong address, jfloatArray src, jint offset, jint count) {
  (*env)->GetFloatArrayRegion(env, src, offset, count, (jfloat*) (uintptr_t) address);
}

static void helper_get_bytes(JNIEnv* env, jclass cls JW_UNUSED, jlong address, jbyteArray dst, jint offset, jint count) {
  (*env)->SetByteArrayRegion(env, dst, offset, count, (const jbyte*) (uintptr_t) address);
}

static void helper_get_shorts(JNIEnv* env, jclass cls JW_UNUSED, jlong address, jshortArray dst, jint offset, jint count) {
  (*env)->SetShortArrayRegion(env, dst, offset, count, (const jshort*) (uintptr_t) address);
}

static void helper_get_ints(JNIEnv* env, jclass cls JW_UNUSED, jlong address, jintArray dst, jint offset, jint count) {
  (*env)->SetIntArrayRegion(env, dst, offset, count, (const jint*) (uintptr_t) address);
}

static void helper_get_floats(JNIEnv* env, jclass cls JW_UNUSED, jlong address, jfloatArray dst, jint offset, jint count) {
  (*env)->SetFloatArrayRegion(env, dst, offset, count, (const jfloat*) (uintptr_t) address);
}

static const JNINativeMethod g_helpers[] = {
  {"address", "(Ljava/nio/ByteBuffer;)J", (void*) helper_address},
  {"wrap", "(JJ)Ljava/nio/ByteBuffer;", (void*) helper_wrap},
  {"strlen", "(J)J", (void*) helper_strlen},
  {"peekByte", "(J)B", (void*) helper_peek_byte},
  {"peekShort", "(J)S", (void*) helper_peek_short},
  {"peekInt", "(J)I", (void*) helper_peek_int},
  {"peekLong", "(J)J", (void*) helper_peek_long},
  {"putBytes", "(J[BII)V", (void*) helper_put_bytes},
  {"putShorts", "(J[SII)V", (void*) helper_put_shorts},
  {"putInts", "(J[III)V", (void*) helper_put_ints},
  {"putFloats", "(J[FII)V", (void*) helper_put_floats},
  {"getBytes", "(J[BII)V", (void*) helper_get_bytes},
  {"getShorts", "(J[SII)V", (void*) helper_get_shorts},
  {"getInts", "(J[III)V", (void*) helper_get_ints},
  {"getFloats", "(J[FII)V", (void*) helper_get_floats},
};

#ifdef __ANDROID__

static jlong android_window_from_surface(JNIEnv* env, jclass cls JW_UNUSED, jobject surface) {
  return (jlong) (uintptr_t) ANativeWindow_fromSurface(env, surface);
}

static void android_release_window(JW_ENV, jlong window) {
  ANativeWindow_release((ANativeWindow*) (uintptr_t) window);
}

static const JNINativeMethod g_android[] = {
  {"windowFromSurface", "(Landroid/view/Surface;)J", (void*) android_window_from_surface},
  {"releaseWindow", "(J)V", (void*) android_release_window},
};

#endif

/* Called from JNI_OnLoad. Returns 0 on success. */
int jw_register_helpers(JNIEnv* env) {
  jclass natives = (*env)->FindClass(env, "io/github/stringdotjar/jwebgpu/jni/Native");
  if (natives == NULL) {
    return -1;
  }
  if ((*env)->RegisterNatives(env, natives, g_helpers, (jint) (sizeof(g_helpers) / sizeof(g_helpers[0]))) != 0) {
    return -1;
  }
#ifdef __ANDROID__
  jclass surface = (*env)->FindClass(env, "io/github/stringdotjar/jwebgpu/jni/android/AndroidSurface");
  if (surface == NULL) {
    return -1;
  }
  if ((*env)->RegisterNatives(env, surface, g_android, (jint) (sizeof(g_android) / sizeof(g_android[0]))) != 0) {
    return -1;
  }
#endif
  return 0;
}
