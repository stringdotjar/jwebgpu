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

/**
 * Remembers the Java callbacks that the native library will call later.
 *
 * <p>A C callback can only carry a pointer-sized {@code userdata}, so the backend stores the Java callback object in
 * this table and passes its slot number as the {@code userdata}. When the native library calls back, the dispatcher
 * turns the slot number into the object again. Think of a cloakroom: you hand over your coat (the callback) and get
 * a numbered ticket (the slot), and the ticket is all that travels through the native code.
 *
 * <p>The table is made of two plain arrays and a free list, so it needs no map and creates no garbage when slots are
 * reused. A one-shot callback (an async request) frees its slot when it fires. A persistent callback (the uncaptured
 * error callback of a device) keeps its slot. The table is synchronized, because callbacks may fire on threads that
 * the native library owns.
 */
final class CallbackTable {

  private static final Object LOCK = new Object();
  private static Object[] items = new Object[16];
  private static int[] next = new int[16];
  private static int freeHead;
  private static int used;

  private CallbackTable() {}

  /**
   * Stores a callback and returns its slot.
   *
   * @param callback The Java callback object.
   * @return The slot number, always greater than 0. It is passed to the native library as {@code userdata1}.
   */
  static int put(Object callback) {
    synchronized (LOCK) {
      if (freeHead == 0) {
        if (used + 1 >= items.length) {
          int size = items.length * 2;
          Object[] biggerItems = new Object[size];
          int[] biggerNext = new int[size];
          System.arraycopy(items, 0, biggerItems, 0, items.length);
          System.arraycopy(next, 0, biggerNext, 0, next.length);
          items = biggerItems;
          next = biggerNext;
        }
        used++;
        items[used] = callback;
        return used;
      }
      int slot = freeHead;
      freeHead = next[slot];
      items[slot] = callback;
      return slot;
    }
  }

  /**
   * Looks up a callback and keeps its slot.
   *
   * @param slot The slot number from the {@code userdata1} of the native callback.
   * @return The callback, or {@code null} if the slot is not in use.
   */
  static Object get(long slot) {
    synchronized (LOCK) {
      return slot > 0 && slot <= used ? items[(int) slot] : null;
    }
  }

  /**
   * Looks up a callback and frees its slot, for callbacks that fire once.
   *
   * @param slot The slot number from the {@code userdata1} of the native callback.
   * @return The callback, or {@code null} if the slot is not in use.
   */
  static Object take(long slot) {
    synchronized (LOCK) {
      if (slot <= 0 || slot > used || items[(int) slot] == null) {
        return null;
      }
      int i = (int) slot;
      Object callback = items[i];
      items[i] = null;
      next[i] = freeHead;
      freeHead = i;
      return callback;
    }
  }

  /**
   * Counts the slots that hold a callback. Tests use it to check that one-shot callbacks do not leak slots.
   *
   * @return The number of slots in use.
   */
  static int count() {
    synchronized (LOCK) {
      int n = 0;
      for (int i = 1; i <= used; i++) {
        if (items[i] != null) {
          n++;
        }
      }
      return n;
    }
  }

  /**
   * Hands an exception that a user callback threw to the uncaught exception handler of the current thread.
   *
   * <p>An exception must never escape into the native library, because the JVM would end the whole process. The
   * dispatchers catch everything and report it here instead.
   *
   * @param error The exception thrown by the callback.
   */
  static void report(Throwable error) {
    Thread thread = Thread.currentThread();
    try {
      thread.getUncaughtExceptionHandler().uncaughtException(thread, error);
    } catch (Throwable ignored) {
      // Reporting must not throw back into native code.
    }
  }
}
