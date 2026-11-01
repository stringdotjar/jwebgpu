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
package io.github.stringdotjar.jwebgpu.teavm;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSBuffer;
import org.teavm.jso.JSBufferType;
import org.teavm.jso.JSObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * Moves raw data between Java and the browser without allocating Java objects.
 *
 * <p>The browser reads and writes memory through JavaScript typed arrays, and a Java array is not one on every TeaVM
 * target: with the JavaScript target a Java {@code float[]} happens to be a {@code Float32Array}, but with the WasmGC
 * target Java arrays live in the garbage-collected heap, which JavaScript cannot see (the by-reference annotation
 * of TeaVM is rejected there). The only memory that both targets share with JavaScript is the memory of a <em>direct
 * byte buffer</em>: on the JavaScript target it is a typed array, and on the WasmGC target it lives in the linear memory
 * that JavaScript sees as an {@code ArrayBuffer}. So this class works the same way on both targets:
 *
 * <ul>
 *   <li>A direct {@link ByteBuffer} is handed to the browser as it is, as a view over its memory. Nothing is copied.
 *   <li>Everything else (arrays and heap buffers) is copied once into a reusable direct staging buffer, and the
 *       browser gets a view over that. The copy is a bulk operation of the buffer API, and the staging buffer only
 *       grows when a bigger upload than any before comes along.
 * </ul>
 *
 * <p>Both targets create one small view object on the JavaScript side per call (that is how a direct buffer is
 * passed to JavaScript), but no Java object, so the Java heap stays still.
 *
 * <p>After each {@code stage...} call, {@link #offset} and {@link #bytes} describe where the data starts inside the
 * view that was returned and how many bytes it has.
 */
final class TeaVmData {

  /** The first byte of the staged data inside the returned view. */
  static int offset;

  /** The number of staged bytes. */
  static int bytes;

  private static final int MIN_SIZE = 64 * 1024;

  private static ByteBuffer staging;
  private static ShortBuffer shorts;
  private static IntBuffer ints;
  private static FloatBuffer floats;

  private TeaVmData() {}

  @JSBody(params = "b", script = "return b;")
  private static native JSObject viewOf(@JSBuffer(JSBufferType.UINT8) ByteBuffer b);

  @JSBody(params = { "dst", "dstOffset", "src", "length" },
      script = "dst.set(src.subarray(0, length), dstOffset);")
  static native void copy(JSObject dst, int dstOffset, JSObject src, int length);

  @JSBody(params = { "view", "start", "length" }, script = "return view.subarray(start, start + length);")
  static native JSObject sub(JSObject view, int start, int length);

  static JSObject stage(ByteBuffer data) {
    int n = data.remaining();
    if (data.isDirect()) {
      offset = data.position();
      bytes = n;
      return viewOf(data);
    }
    reserve(n);
    int pos = data.position();
    staging.clear();
    if (data.hasArray()) {
      staging.put(data.array(), data.arrayOffset() + pos, n);
    } else {
      for (int i = 0; i < n; i++) {
        staging.put(i, data.get(pos + i));
      }
    }
    offset = 0;
    bytes = n;
    return viewOf(staging);
  }

  static JSObject stage(byte[] data, int dataOffset, int dataCount) {
    reserve(dataCount);
    staging.clear();
    staging.put(data, dataOffset, dataCount);
    return staged(dataCount);
  }

  static JSObject stage(short[] data, int dataOffset, int dataCount) {
    reserve(dataCount * 2);
    shorts.clear();
    shorts.put(data, dataOffset, dataCount);
    return staged(dataCount * 2);
  }

  static JSObject stage(int[] data, int dataOffset, int dataCount) {
    reserve(dataCount * 4);
    ints.clear();
    ints.put(data, dataOffset, dataCount);
    return staged(dataCount * 4);
  }

  static JSObject stage(float[] data, int dataOffset, int dataCount) {
    reserve(dataCount * 4);
    floats.clear();
    floats.put(data, dataOffset, dataCount);
    return staged(dataCount * 4);
  }

  static JSObject stageOut(ByteBuffer data) {
    int n = data.remaining();
    if (data.isDirect()) {
      offset = data.position();
      bytes = n;
      return viewOf(data);
    }
    reserve(n);
    return staged(n);
  }

  static JSObject stageOut(byte[] data, int dataOffset, int dataCount) {
    reserve(dataCount);
    return staged(dataCount);
  }

  static JSObject stageOut(short[] data, int dataOffset, int dataCount) {
    reserve(dataCount * 2);
    return staged(dataCount * 2);
  }

  static JSObject stageOut(int[] data, int dataOffset, int dataCount) {
    reserve(dataCount * 4);
    return staged(dataCount * 4);
  }

  static JSObject stageOut(float[] data, int dataOffset, int dataCount) {
    reserve(dataCount * 4);
    return staged(dataCount * 4);
  }

  static void unstage(ByteBuffer data) {
    if (data.isDirect()) {
      return;
    }
    int pos = data.position();
    if (data.hasArray() && !data.isReadOnly()) {
      staging.clear();
      staging.get(data.array(), data.arrayOffset() + pos, bytes);
    } else {
      for (int i = 0; i < bytes; i++) {
        data.put(pos + i, staging.get(i));
      }
    }
  }

  static void unstage(byte[] data, int dataOffset, int dataCount) {
    staging.clear();
    staging.get(data, dataOffset, dataCount);
  }

  static void unstage(short[] data, int dataOffset, int dataCount) {
    shorts.clear();
    shorts.get(data, dataOffset, dataCount);
  }

  static void unstage(int[] data, int dataOffset, int dataCount) {
    ints.clear();
    ints.get(data, dataOffset, dataCount);
  }

  static void unstage(float[] data, int dataOffset, int dataCount) {
    floats.clear();
    floats.get(data, dataOffset, dataCount);
  }

  private static JSObject staged(int byteCount) {
    offset = 0;
    bytes = byteCount;
    return viewOf(staging);
  }

  private static void reserve(int byteCount) {
    ByteBuffer s = staging;
    if (s != null && s.capacity() >= byteCount) {
      return;
    }
    int size = Math.max(Math.max(byteCount, MIN_SIZE), s == null ? 0 : s.capacity() * 2);
    s = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN);
    staging = s;
    shorts = s.asShortBuffer();
    ints = s.asIntBuffer();
    floats = s.asFloatBuffer();
  }
}
