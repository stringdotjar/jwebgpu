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
package io.github.stringdotjar.jwebgpu.jni;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The small native helpers of the JNI backend, and the loader of the two native libraries.
 *
 * <p>The generated {@code Jni} class holds one native method per WebGPU function. This class holds the few helpers
 * that are not WebGPU functions: finding the address of a direct buffer, reading and copying memory that the native
 * library owns, and wrapping a mapped GPU range as a buffer. They are small C functions of the glue library.
 *
 * <p>All addresses are {@code long}. The backend only supports 64-bit targets (arm64-v8a and x86_64 on Android),
 * because the struct layouts that the generator computes assume 8-byte pointers.
 */
final class Native {

  /** The system property that names the Dawn library file to load instead of looking it up by name. */
  static final String DAWN_PROPERTY = "jwebgpu.library.path";

  /** The system property that names the glue library file to load instead of looking it up by name. */
  static final String GLUE_PROPERTY = "jwebgpu.jni.library.path";

  private static final Object LOCK = new Object();
  private static boolean loaded;

  private Native() {}

  /**
   * Loads the Dawn library and the glue library, once.
   *
   * <p>On Android both libraries are found by name in the application's native library directory
   * ({@code libwebgpu_dawn.so} and {@code libjwebgpu_jni.so}). On a desktop Java virtual machine, where the unit tests
   * run this backend, the files are named by the system properties {@value #DAWN_PROPERTY} and
   * {@value #GLUE_PROPERTY}.
   *
   * @throws UnsatisfiedLinkError If a library cannot be found or loaded.
   */
  static void load() {
    synchronized (LOCK) {
      if (loaded) {
        return;
      }
      String dawn = System.getProperty(DAWN_PROPERTY);
      if (dawn != null && !dawn.isEmpty()) {
        System.load(dawn);
      } else {
        System.loadLibrary("webgpu_dawn");
      }
      String glue = System.getProperty(GLUE_PROPERTY);
      if (glue != null && !glue.isEmpty()) {
        System.load(glue);
      } else {
        System.loadLibrary("jwebgpu_jni");
      }
      loaded = true;
    }
  }

  /**
   * Tells whether the native libraries are loaded.
   *
   * @return {@code true} after {@link #load()} succeeded.
   */
  static boolean isLoaded() {
    synchronized (LOCK) {
      return loaded;
    }
  }

  /**
   * Wraps a mapped buffer range as a {@link ByteBuffer}.
   *
   * <p>The buffer reads and writes the GPU's memory directly, so it is only valid until the GPU buffer is
   * unmapped or destroyed. Using it afterwards is a mistake that can crash the process, just like in C.
   *
   * @param address The address the native library returned.
   * @param size The size of the range in bytes.
   * @param readOnly Whether the buffer must not be written.
   * @return The buffer in native byte order, or {@code null} if the address is 0.
   */
  static ByteBuffer mapped(long address, long size, boolean readOnly) {
    if (address == 0L) {
      return null;
    }
    ByteBuffer buffer = wrap(address, size).order(ByteOrder.nativeOrder());
    return readOnly ? buffer.asReadOnlyBuffer().order(ByteOrder.nativeOrder()) : buffer;
  }

  /**
   * Returns the address of the first byte of a direct buffer.
   *
   * @param buffer A direct buffer.
   * @return The address, ignoring the position of the buffer.
   */
  static native long address(ByteBuffer buffer);

  static native ByteBuffer wrap(long address, long size);

  static native long strlen(long address);

  static native byte peekByte(long address);

  static native short peekShort(long address);

  static native int peekInt(long address);

  static native long peekLong(long address);

  static native void putBytes(long address, byte[] src, int offset, int count);

  static native void putShorts(long address, short[] src, int offset, int count);

  static native void putInts(long address, int[] src, int offset, int count);

  static native void putFloats(long address, float[] src, int offset, int count);

  static native void getBytes(long address, byte[] dst, int offset, int count);

  static native void getShorts(long address, short[] dst, int offset, int count);

  static native void getInts(long address, int[] dst, int offset, int count);

  static native void getFloats(long address, float[] dst, int offset, int count);
}
