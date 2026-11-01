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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Raw access to native memory by absolute address.
 *
 * <p>The generated code stores every native pointer as a plain {@code long}, so a call needs no
 * {@link MemorySegment} object per pointer. This class makes that possible: it owns one segment that starts at
 * address 0 and spans the whole address space, so reading or writing {@code address} is the same as accessing
 * {@code ALL} at the offset {@code address}. The accessors below are tiny static methods that the JIT inlines, and
 * they allocate nothing.
 *
 * <p>The segment is only a view. Nothing here owns native memory, so an invalid address crashes the process just
 * like a bad pointer in C would. The generated code only passes addresses it got from the native library or from
 * {@link Scratch}.
 */
final class Mem {

  static final MemorySegment ALL = MemorySegment.NULL.reinterpret(Long.MAX_VALUE);

  private static final ValueLayout.OfByte BYTE = ValueLayout.JAVA_BYTE;
  private static final ValueLayout.OfShort SHORT = ValueLayout.JAVA_SHORT_UNALIGNED;
  private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT_UNALIGNED;
  private static final ValueLayout.OfLong LONG = ValueLayout.JAVA_LONG_UNALIGNED;
  private static final ValueLayout.OfFloat FLOAT = ValueLayout.JAVA_FLOAT_UNALIGNED;
  private static final ValueLayout.OfDouble DOUBLE = ValueLayout.JAVA_DOUBLE_UNALIGNED;

  private Mem() {}

  static void putByte(long address, byte value) {
    ALL.set(BYTE, address, value);
  }

  static void putShort(long address, short value) {
    ALL.set(SHORT, address, value);
  }

  static void putInt(long address, int value) {
    ALL.set(INT, address, value);
  }

  static void putLong(long address, long value) {
    ALL.set(LONG, address, value);
  }

  static void putFloat(long address, float value) {
    ALL.set(FLOAT, address, value);
  }

  static void putDouble(long address, double value) {
    ALL.set(DOUBLE, address, value);
  }

  static byte getByte(long address) {
    return ALL.get(BYTE, address);
  }

  static short getShort(long address) {
    return ALL.get(SHORT, address);
  }

  static int getInt(long address) {
    return ALL.get(INT, address);
  }

  static long getLong(long address) {
    return ALL.get(LONG, address);
  }

  static float getFloat(long address) {
    return ALL.get(FLOAT, address);
  }

  static double getDouble(long address) {
    return ALL.get(DOUBLE, address);
  }

  /**
   * Fills a block of memory with zero bytes.
   *
   * @param address The first byte.
   * @param size The number of bytes.
   */
  static void zero(long address, long size) {
    long end = address + size;
    long a = address;
    while (a + 8 <= end) {
      ALL.set(LONG, a, 0L);
      a += 8;
    }
    while (a < end) {
      ALL.set(BYTE, a, (byte) 0);
      a++;
    }
  }

  static void putBytes(long address, byte[] src, int offset, int count) {
    MemorySegment.copy(src, offset, ALL, BYTE, address, count);
  }

  static void putShorts(long address, short[] src, int offset, int count) {
    MemorySegment.copy(src, offset, ALL, SHORT, address, count);
  }

  static void putInts(long address, int[] src, int offset, int count) {
    MemorySegment.copy(src, offset, ALL, INT, address, count);
  }

  static void putFloats(long address, float[] src, int offset, int count) {
    MemorySegment.copy(src, offset, ALL, FLOAT, address, count);
  }

  static void getBytes(long address, byte[] dst, int offset, int count) {
    MemorySegment.copy(ALL, BYTE, address, dst, offset, count);
  }

  static void getShorts(long address, short[] dst, int offset, int count) {
    MemorySegment.copy(ALL, SHORT, address, dst, offset, count);
  }

  static void getInts(long address, int[] dst, int offset, int count) {
    MemorySegment.copy(ALL, INT, address, dst, offset, count);
  }

  static void getFloats(long address, float[] dst, int offset, int count) {
    MemorySegment.copy(ALL, FLOAT, address, dst, offset, count);
  }

  /**
   * Gives the native library access to the bytes of a {@link ByteBuffer} that a call reads.
   *
   * <p>A direct buffer already lives in native memory, so its address is used as it is and nothing is copied. For
   * a buffer that wraps an array (or is read-only) the remaining bytes are copied into the scratch memory without
   * allocating. The position and limit of the buffer never change.
   *
   * @param s The scratch memory of the calling thread.
   * @param data The buffer. Its remaining bytes are the data.
   * @return The address of the first remaining byte.
   */
  static long input(Scratch s, ByteBuffer data) {
    int count = data.remaining();
    if (data.isDirect()) {
      return MemorySegment.ofBuffer(data).address();
    }
    long address = s.alloc(count);
    if (data.hasArray()) {
      putBytes(address, data.array(), data.arrayOffset() + data.position(), count);
    } else {
      int start = data.position();
      for (int i = 0; i < count; i++) {
        putByte(address + i, data.get(start + i));
      }
    }
    return address;
  }

  /**
   * Gives the native library room to write into a {@link ByteBuffer}.
   *
   * <p>A direct buffer is written in place. Any other buffer gets a block of scratch memory, and
   * {@link #copyBack(ByteBuffer, long)} copies the result into it after the call.
   *
   * @param s The scratch memory of the calling thread.
   * @param data The buffer. Its remaining bytes are the room.
   * @return The address the native library writes to.
   */
  static long output(Scratch s, ByteBuffer data) {
    if (data.isDirect()) {
      return MemorySegment.ofBuffer(data).address();
    }
    return s.alloc(data.remaining());
  }

  /**
   * Copies the result of a call into a buffer that was passed to {@link #output(Scratch, ByteBuffer)}.
   *
   * @param data The same buffer.
   * @param address The address that {@code output} returned.
   */
  static void copyBack(ByteBuffer data, long address) {
    if (data.isDirect()) {
      return;
    }
    int count = data.remaining();
    if (data.hasArray()) {
      getBytes(address, data.array(), data.arrayOffset() + data.position(), count);
    } else {
      int start = data.position();
      for (int i = 0; i < count; i++) {
        data.put(start + i, getByte(address + i));
      }
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
    ByteBuffer buffer = ALL.asSlice(address, size).asByteBuffer().order(ByteOrder.nativeOrder());
    return readOnly ? buffer.asReadOnlyBuffer().order(ByteOrder.nativeOrder()) : buffer;
  }

  /**
   * Reads a string from native memory.
   *
   * @param address The first byte, or 0 for no string.
   * @param length The number of bytes, or {@code -1} (the C constant {@code WGPU_STRLEN}) if the text ends with a
   *     zero byte.
   * @return The decoded text, or {@code null} if the address is 0. Reading allocates, so it is only used where
   *     the native library hands text over (messages, names), never on a per-frame path.
   */
  static String string(long address, long length) {
    if (address == 0) {
      return null;
    }
    long n = length;
    if (n == -1L) {
      n = 0;
      while (getByte(address + n) != 0) {
        n++;
      }
    }
    if (n == 0) {
      return "";
    }
    byte[] bytes = new byte[(int) n];
    getBytes(address, bytes, 0, bytes.length);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  /**
   * Reads a string view that sits in native memory.
   *
   * @param viewAddress The address of a {@code WGPUStringView} (a pointer followed by a length).
   * @return The decoded text, or {@code null} if the view has no data.
   */
  static String stringView(long viewAddress) {
    return string(getLong(viewAddress), getLong(viewAddress + 8));
  }

  /**
   * Reads a string view that was passed to a callback by value.
   *
   * @param view The segment that holds the pointer and the length.
   * @return The decoded text, or {@code null} if the view has no data.
   */
  static String stringView(MemorySegment view) {
    return string(view.get(ValueLayout.JAVA_LONG, 0), view.get(ValueLayout.JAVA_LONG, 8));
  }
}
