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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The native work area of one thread, used to build the structs of a call without allocating.
 *
 * <p>Think of a scratch pad on a desk: before each call, the pad is wiped (the position goes back to the start),
 * then the call writes its descriptors, arrays and strings on the pad one after the other, hands the page to the
 * native library, and the pad is wiped again at the next call. Nothing is allocated per call, because the pad is
 * one block of native memory that is reused again and again.
 *
 * <p>The block is a direct {@link ByteBuffer}, and the generated code addresses it with absolute addresses: the
 * address of the first byte is asked from the native side once, when the block is created, and every pointer that the
 * generated code writes into a struct is {@code base + offset}. The native glue can then use the bytes as C structs
 * without converting anything, and the Java side reads and writes them with plain buffer accesses.
 *
 * <p>Every thread has its own scratch ({@link #begin()}), created the first time the thread makes a call. The block
 * starts small and only grows when a single call needs more than the block holds. The growth is the only
 * allocation that ever happens, and the block never shrinks by itself (see {@link #trim()}). When a block is
 * replaced in the middle of a call, the old block stays valid until the next call, so addresses that were already
 * written into the call stay correct, and reads and writes at those addresses still work.
 *
 * <p>Reading an address that is not in the scratch memory (for example a string or an array that the native library
 * owns) goes through a native call, so it is meant for the rare paths that hand text and lists over, not for the
 * per-frame path.
 *
 * <p>Calls can nest: the native library may run a callback while a call is still using the pad (for example an
 * uncaptured error callback inside {@code createBuffer}), and the callback may call the API again. The callback
 * dispatcher marks that with {@link #enter()}, and a nested call then keeps what is already on the pad instead of
 * wiping it. {@link #leave()} restores the position when the callback returns.
 */
final class Scratch {

  static final long INITIAL_SIZE = 32 * 1024;

  private static final long STRLEN = -1L;
  private static final ThreadLocal<Scratch> LOCAL = ThreadLocal.withInitial(Scratch::new);

  private long base;
  private long pos;

  private long[] retiredBase = new long[2];
  private long[] markPos = new long[4];
  private long[] markBase = new long[4];

  private int capacity;
  private int retiredCount;
  private int depth;

  private ByteBuffer block;
  private ByteBuffer[] retired = new ByteBuffer[2];

  private Scratch() {
    install(INITIAL_SIZE);
  }

  /**
   * Returns the scratch memory of the calling thread, wiped and ready for the next call.
   *
   * <p>Call it once at the start of every API method that marshals anything. If the thread is inside a callback
   * (see {@link #enter()}), the memory in use is kept, so a nested call cannot overwrite the outer one.
   *
   * @return The scratch memory of this thread. The first call on a thread allocates it.
   */
  static Scratch begin() {
    Scratch s = LOCAL.get();
    if (s.depth == 0) {
      s.pos = s.base;
      if (s.retiredCount != 0) {
        s.dropRetired();
      }
    }
    return s;
  }

  /**
   * Returns the scratch memory of the calling thread without wiping it.
   *
   * @return The scratch memory of this thread.
   */
  static Scratch current() {
    return LOCAL.get();
  }

  /**
   * Frees the scratch memory of the calling thread and starts again with the initial size.
   *
   * <p>Call it after a rare, very large upload (for example a big texture) if the thread should not keep that
   * much native memory. It must not be called from inside a callback or while another call of this thread is
   * running.
   */
  static void trim() {
    Scratch s = LOCAL.get();
    if (s.depth == 0) {
      s.dropRetired();
      s.install(INITIAL_SIZE);
    }
  }

  /**
   * Reserves a block of memory for the current call.
   *
   * <p>The block is aligned to 8 bytes, and its contents are undefined, so the caller has to write every byte it
   * needs.
   *
   * @param size The size in bytes.
   * @return The address of the block.
   */
  long alloc(long size) {
    long start = (pos + 7) & ~7L;
    long end = start + size;
    if (end > base + capacity) {
      return grow(size);
    }
    pos = end;
    return start;
  }

  /**
   * Writes a {@code WGPUStringView} that points at a UTF-8 copy of a string.
   *
   * <p>The text is encoded straight into the scratch memory, without {@code getBytes} or any other allocation. A
   * {@code null} string becomes the view {@code {NULL, WGPU_STRLEN}}, which the native library reads as "not
   * set". Lone surrogates are written as a question mark, like the standard UTF-8 encoder does.
   *
   * @param at The address of the 16-byte view to write.
   * @param text The text, or {@code null}.
   */
  void putView(long at, String text) {
    if (text == null) {
      putLong(at, 0L);
      putLong(at + 8, STRLEN);
      return;
    }
    int n = text.length();
    long max = 3L * n + 1;
    long start = (pos + 7) & ~7L;
    if (start + max > base + capacity) {
      start = grow(max);
    } else {
      pos = start + max;
    }
    ByteBuffer b = block;
    int p = (int) (start - base);
    int first = p;
    for (int i = 0; i < n; i++) {
      char c = text.charAt(i);
      if (c < 0x80) {
        b.put(p++, (byte) c);
      } else if (c < 0x800) {
        b.put(p++, (byte) (0xC0 | (c >> 6)));
        b.put(p++, (byte) (0x80 | (c & 0x3F)));
      } else if (Character.isHighSurrogate(c) && i + 1 < n && Character.isLowSurrogate(text.charAt(i + 1))) {
        int cp = Character.toCodePoint(c, text.charAt(++i));
        b.put(p++, (byte) (0xF0 | (cp >> 18)));
        b.put(p++, (byte) (0x80 | ((cp >> 12) & 0x3F)));
        b.put(p++, (byte) (0x80 | ((cp >> 6) & 0x3F)));
        b.put(p++, (byte) (0x80 | (cp & 0x3F)));
      } else if (Character.isSurrogate(c)) {
        b.put(p++, (byte) '?');
      } else {
        b.put(p++, (byte) (0xE0 | (c >> 12)));
        b.put(p++, (byte) (0x80 | ((c >> 6) & 0x3F)));
        b.put(p++, (byte) (0x80 | (c & 0x3F)));
      }
    }
    b.put(p, (byte) 0);
    pos = base + p + 1;
    putLong(at, base + first);
    putLong(at + 8, p - first);
  }

  /**
   * Writes a string view into a fresh 16-byte slot of this scratch memory and returns its address.
   *
   * @param text The text, or {@code null}.
   * @return The address of the view, ready to be passed to a function that takes a {@code WGPUStringView} by value.
   *     The native glue copies the struct from this address.
   */
  long view(String text) {
    long at = alloc(16);
    putView(at, text);
    return at;
  }

  /**
   * Marks the start of a callback that runs on this thread.
   *
   * <p>Until the matching {@link #leave()}, calls made by this thread keep the memory that is in use instead of
   * wiping it.
   */
  void enter() {
    if (depth == markPos.length) {
      markPos = Arrays.copyOf(markPos, depth * 2);
      markBase = Arrays.copyOf(markBase, depth * 2);
    }
    markPos[depth] = pos;
    markBase[depth] = base;
    depth++;
  }

  /**
   * Marks the end of a callback and gives back the memory that nested calls used.
   *
   * <p>If a nested call had to replace the block, the position stays where it is, because the old position
   * belongs to a block that is no longer current.
   */
  void leave() {
    depth--;
    if (markBase[depth] == base) {
      pos = markPos[depth];
    }
  }

  void putByte(long address, byte value) {
    long o = address - base;
    if (o >= 0 && o < capacity) {
      block.put((int) o, value);
    } else {
      int i = find(address, 1);
      retired[i].put((int) (address - retiredBase[i]), value);
    }
  }

  void putShort(long address, short value) {
    long o = address - base;
    if (o >= 0 && o <= capacity - 2) {
      block.putShort((int) o, value);
    } else {
      int i = find(address, 2);
      retired[i].putShort((int) (address - retiredBase[i]), value);
    }
  }

  void putInt(long address, int value) {
    long o = address - base;
    if (o >= 0 && o <= capacity - 4) {
      block.putInt((int) o, value);
    } else {
      int i = find(address, 4);
      retired[i].putInt((int) (address - retiredBase[i]), value);
    }
  }

  void putLong(long address, long value) {
    long o = address - base;
    if (o >= 0 && o <= capacity - 8) {
      block.putLong((int) o, value);
    } else {
      int i = find(address, 8);
      retired[i].putLong((int) (address - retiredBase[i]), value);
    }
  }

  void putFloat(long address, float value) {
    long o = address - base;
    if (o >= 0 && o <= capacity - 4) {
      block.putFloat((int) o, value);
    } else {
      int i = find(address, 4);
      retired[i].putFloat((int) (address - retiredBase[i]), value);
    }
  }

  void putDouble(long address, double value) {
    long o = address - base;
    if (o >= 0 && o <= capacity - 8) {
      block.putDouble((int) o, value);
    } else {
      int i = find(address, 8);
      retired[i].putDouble((int) (address - retiredBase[i]), value);
    }
  }

  byte getByte(long address) {
    long o = address - base;
    if (o >= 0 && o < capacity) {
      return block.get((int) o);
    }
    int i = findOrNone(address, 1);
    return i < 0 ? Native.peekByte(address) : retired[i].get((int) (address - retiredBase[i]));
  }

  short getShort(long address) {
    long o = address - base;
    if (o >= 0 && o <= capacity - 2) {
      return block.getShort((int) o);
    }
    int i = findOrNone(address, 2);
    return i < 0 ? Native.peekShort(address) : retired[i].getShort((int) (address - retiredBase[i]));
  }

  int getInt(long address) {
    long o = address - base;
    if (o >= 0 && o <= capacity - 4) {
      return block.getInt((int) o);
    }
    int i = findOrNone(address, 4);
    return i < 0 ? Native.peekInt(address) : retired[i].getInt((int) (address - retiredBase[i]));
  }

  long getLong(long address) {
    long o = address - base;
    if (o >= 0 && o <= capacity - 8) {
      return block.getLong((int) o);
    }
    int i = findOrNone(address, 8);
    return i < 0 ? Native.peekLong(address) : retired[i].getLong((int) (address - retiredBase[i]));
  }

  float getFloat(long address) {
    return Float.intBitsToFloat(getInt(address));
  }

  double getDouble(long address) {
    return Double.longBitsToDouble(getLong(address));
  }

  /**
   * Fills a block of scratch memory with zero bytes.
   *
   * @param address The first byte.
   * @param size The number of bytes.
   */
  void zero(long address, long size) {
    long end = address + size;
    long a = address;
    while (a + 8 <= end) {
      putLong(a, 0L);
      a += 8;
    }
    while (a < end) {
      putByte(a, (byte) 0);
      a++;
    }
  }

  void putBytes(long address, byte[] src, int offset, int count) {
    Native.putBytes(address, src, offset, count);
  }

  void putShorts(long address, short[] src, int offset, int count) {
    Native.putShorts(address, src, offset, count);
  }

  void putInts(long address, int[] src, int offset, int count) {
    Native.putInts(address, src, offset, count);
  }

  void putFloats(long address, float[] src, int offset, int count) {
    Native.putFloats(address, src, offset, count);
  }

  void getBytes(long address, byte[] dst, int offset, int count) {
    Native.getBytes(address, dst, offset, count);
  }

  void getShorts(long address, short[] dst, int offset, int count) {
    Native.getShorts(address, dst, offset, count);
  }

  void getInts(long address, int[] dst, int offset, int count) {
    Native.getInts(address, dst, offset, count);
  }

  void getFloats(long address, float[] dst, int offset, int count) {
    Native.getFloats(address, dst, offset, count);
  }

  /**
   * Gives the native library access to the bytes of a {@link ByteBuffer} that a call reads.
   *
   * <p>A direct buffer already lives in native memory, so its address is used as it is and nothing is copied. For
   * a buffer that wraps an array (or is read-only) the remaining bytes are copied into the scratch memory without
   * allocating. The position and limit of the buffer never change.
   *
   * @param data The buffer. Its remaining bytes are the data.
   * @return The address of the first remaining byte.
   */
  long input(ByteBuffer data) {
    int count = data.remaining();
    if (data.isDirect()) {
      return Native.address(data) + data.position();
    }
    long address = alloc(count);
    if (data.hasArray()) {
      Native.putBytes(address, data.array(), data.arrayOffset() + data.position(), count);
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
   * @param data The buffer. Its remaining bytes are the room.
   * @return The address the native library writes to.
   */
  long output(ByteBuffer data) {
    if (data.isDirect()) {
      return Native.address(data) + data.position();
    }
    return alloc(data.remaining());
  }

  /**
   * Copies the result of a call into a buffer that was passed to {@link #output(ByteBuffer)}.
   *
   * @param data The same buffer.
   * @param address The address that {@code output} returned.
   */
  void copyBack(ByteBuffer data, long address) {
    if (data.isDirect()) {
      return;
    }
    int count = data.remaining();
    if (data.hasArray()) {
      Native.getBytes(address, data.array(), data.arrayOffset() + data.position(), count);
    } else {
      int start = data.position();
      for (int i = 0; i < count; i++) {
        data.put(start + i, getByte(address + i));
      }
    }
  }

  /**
   * Reads a string from native memory.
   *
   * @param address The first byte, or 0 for no string.
   * @param length The number of bytes, or {@code -1} (the C constant {@code WGPU_STRLEN}) if the text ends with a
   *     zero byte.
   * @return The decoded text, or {@code null} if the address is 0. The bytes are decoded as real UTF-8 (not the
   *     modified UTF-8 of the JNI string functions). Reading allocates, so it is only used where the native library
   *     hands text over (messages, names), never on a per-frame path.
   */
  String string(long address, long length) {
    if (address == 0) {
      return null;
    }
    long n = length == STRLEN ? Native.strlen(address) : length;
    if (n == 0) {
      return "";
    }
    byte[] bytes = new byte[(int) n];
    Native.getBytes(address, bytes, 0, bytes.length);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  /**
   * Reads a string view that sits in memory.
   *
   * @param viewAddress The address of a {@code WGPUStringView} (a pointer followed by a length).
   * @return The decoded text, or {@code null} if the view has no data.
   */
  String stringView(long viewAddress) {
    return string(getLong(viewAddress), getLong(viewAddress + 8));
  }

  /**
   * Returns the size of the current block.
   *
   * @return The size in bytes.
   */
  long getCapacity() {
    return capacity;
  }

  /**
   * Returns how many bytes of the current block the running call has used so far.
   *
   * @return The number of bytes.
   */
  long getUsed() {
    return pos - base;
  }

  /**
   * Returns the address of the first byte of the current block.
   *
   * @return The address.
   */
  long getBase() {
    return base;
  }

  private int find(long address, int size) {
    int i = findOrNone(address, size);
    if (i < 0) {
      throw new IllegalStateException("The address 0x" + Long.toHexString(address) + " is not in scratch memory");
    }
    return i;
  }

  private int findOrNone(long address, int size) {
    for (int i = 0; i < retiredCount; i++) {
      long o = address - retiredBase[i];
      if (o >= 0 && o <= retired[i].capacity() - size) {
        return i;
      }
    }
    return -1;
  }

  private long grow(long size) {
    long wanted = Math.max(2L * capacity, ((size + 7) & ~7L) + 64);
    if (wanted > Integer.MAX_VALUE - 64) {
      throw new OutOfMemoryError("A single call needs more than 2 GiB of scratch memory");
    }
    if (retiredCount == retired.length) {
      retired = Arrays.copyOf(retired, retiredCount * 2);
      retiredBase = Arrays.copyOf(retiredBase, retiredCount * 2);
    }
    retired[retiredCount] = block;
    retiredBase[retiredCount] = base;
    retiredCount++;
    install(wanted);
    long start = base;
    pos = start + ((size + 7) & ~7L);
    return start;
  }

  private void install(long size) {
    int bytes = (int) size;
    ByteBuffer raw = ByteBuffer.allocateDirect(bytes + 16);
    long address = Native.address(raw);
    int skip = (int) ((16 - (address & 15)) & 15);
    raw.position(skip);
    raw.limit(skip + bytes);
    block = raw.slice().order(ByteOrder.nativeOrder());
    base = address + skip;
    capacity = bytes;
    pos = base;
  }

  private void dropRetired() {
    for (int i = 0; i < retiredCount; i++) {
      retired[i] = null;
    }
    retiredCount = 0;
  }
}
