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

import java.util.Arrays;

/**
 * Tracks the futures of asynchronous operations.
 *
 * <p>The native API returns a future for every asynchronous call, and a program can poll them with
 * {@code waitAny}. The browser has no futures, so each operation gets a number here and the number is removed when
 * the operation settles. The table is a plain array, and it only grows when more operations are in flight at the same
 * time than ever before.
 */
final class TeaVmFutures {

  private static long nextId = 1L;
  private static long[] pending = new long[16];
  private static int count;

  private TeaVmFutures() {}

  static long begin() {
    long id = nextId++;
    if (count == pending.length) {
      pending = Arrays.copyOf(pending, count * 2);
    }
    pending[count++] = id;
    return id;
  }

  static void complete(long id) {
    for (int i = 0; i < count; i++) {
      if (pending[i] == id) {
        pending[i] = pending[--count];
        return;
      }
    }
  }

  static boolean isPending(long id) {
    for (int i = 0; i < count; i++) {
      if (pending[i] == id) {
        return true;
      }
    }
    return false;
  }
}
