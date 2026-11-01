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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Arrays;

/**
 * The native work area of one thread, used to build the structs of a call without allocating.
 *
 * <p>Think of a scratch pad on a desk: before each call, the pad is wiped (the position goes back to the start),
 * then the call writes its descriptors, arrays and strings on the pad one after the other, hands the page to the
 * native library, and the pad is wiped again at the next call. Nothing is allocated per call, because the pad is
 * one block of native memory that is reused again and again.
 *
 * <p>Every thread has its own scratch ({@link #begin()}), created the first time the thread makes a call. The block
 * starts small and only grows when a single call needs more than the block holds. The growth is the only
 * allocation that ever happens, and the block never shrinks by itself (see {@link #trim()}). When a block is
 * replaced in the middle of a call, the old block stays valid until the next call, so addresses that were already
 * written into the call stay correct.
 *
 * <p>The generated marshalling code works with raw addresses ({@code long}), which {@link Mem} reads and writes.
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
  private long limit;
  private long pos;

  /** The address of {@link #viewSegment}. */
  final long viewAddress;

  /** The address of {@link #callbackSegment}. */
  final long callbackAddress;

  /** The 16 bytes that carry a {@code WGPUStringView} when it is passed to a function by value. */
  final MemorySegment viewSegment;

  /** The 40 bytes that carry a callback info struct when it is passed to a function by value. */
  final MemorySegment callbackSegment;

  private final MemorySegment holder;
  private MemorySegment block;
  private MemorySegment[] retired = new MemorySegment[2];
  private long[] markPos = new long[4];
  private long[] markBase = new long[4];
  private int retiredCount;
  private int depth;

  private Scratch() {
    holder = Arena.ofAuto().allocate(16 + 48, 16);
    viewSegment = holder.asSlice(0, 16);
    viewAddress = viewSegment.address();
    callbackSegment = holder.asSlice(16, 40);
    callbackAddress = callbackSegment.address();
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
    if (end > limit) {
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
      Mem.putLong(at, 0L);
      Mem.putLong(at + 8, STRLEN);
      return;
    }
    int n = text.length();
    long max = 3L * n + 1;
    long start = (pos + 7) & ~7L;
    if (start + max > limit) {
      start = grow(max);
    } else {
      pos = start + max;
    }
    long p = start;
    for (int i = 0; i < n; i++) {
      char c = text.charAt(i);
      if (c < 0x80) {
        Mem.putByte(p++, (byte) c);
      } else if (c < 0x800) {
        Mem.putByte(p++, (byte) (0xC0 | (c >> 6)));
        Mem.putByte(p++, (byte) (0x80 | (c & 0x3F)));
      } else if (Character.isHighSurrogate(c) && i + 1 < n && Character.isLowSurrogate(text.charAt(i + 1))) {
        int cp = Character.toCodePoint(c, text.charAt(++i));
        Mem.putByte(p++, (byte) (0xF0 | (cp >> 18)));
        Mem.putByte(p++, (byte) (0x80 | ((cp >> 12) & 0x3F)));
        Mem.putByte(p++, (byte) (0x80 | ((cp >> 6) & 0x3F)));
        Mem.putByte(p++, (byte) (0x80 | (cp & 0x3F)));
      } else if (Character.isSurrogate(c)) {
        Mem.putByte(p++, (byte) '?');
      } else {
        Mem.putByte(p++, (byte) (0xE0 | (c >> 12)));
        Mem.putByte(p++, (byte) (0x80 | ((c >> 6) & 0x3F)));
        Mem.putByte(p++, (byte) (0x80 | (c & 0x3F)));
      }
    }
    Mem.putByte(p, (byte) 0);
    pos = p + 1;
    Mem.putLong(at, start);
    Mem.putLong(at + 8, p - start);
  }

  /**
   * Writes a string view into the by-value slot of this scratch memory and returns it.
   *
   * @param text The text, or {@code null}.
   * @return The segment that holds the view, ready to be passed to a function that takes a
   *     {@code WGPUStringView} by value.
   */
  MemorySegment view(String text) {
    putView(viewAddress, text);
    return viewSegment;
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

  /**
   * Returns the size of the current block.
   *
   * @return The size in bytes.
   */
  long getCapacity() {
    return limit - base;
  }

  /**
   * Returns how many bytes of the current block the running call has used so far.
   *
   * @return The number of bytes.
   */
  long getUsed() {
    return pos - base;
  }

  private long grow(long size) {
    long capacity = Math.max((limit - base) * 2, ((size + 7) & ~7L) + 64);
    if (retiredCount == retired.length) {
      MemorySegment[] bigger = new MemorySegment[retired.length * 2];
      System.arraycopy(retired, 0, bigger, 0, retiredCount);
      retired = bigger;
    }
    retired[retiredCount++] = block;
    install(capacity);
    long start = base;
    pos = start + ((size + 7) & ~7L);
    return start;
  }

  private void install(long capacity) {
    block = Arena.ofAuto().allocate(capacity, 16);
    base = block.address();
    limit = base + capacity;
    pos = base;
  }

  private void dropRetired() {
    for (int i = 0; i < retiredCount; i++) {
      retired[i] = null;
    }
    retiredCount = 0;
  }
}
