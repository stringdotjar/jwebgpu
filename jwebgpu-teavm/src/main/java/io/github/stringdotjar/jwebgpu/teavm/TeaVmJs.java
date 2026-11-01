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
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSBoolean;
import org.teavm.jso.core.JSString;

import io.github.stringdotjar.jwebgpu.GpuOptionalBool;

/**
 * Small helpers on top of TeaVM's JavaScript Objects that the generated classes use.
 *
 * <p>Everything here is allocation-free on the Java side. The helpers create the cached JavaScript objects (the
 * dictionaries and arrays that descriptors are written into), fill arrays, and turn Java enums into the interned
 * strings of {@code TeaVmEnums}.
 */
final class TeaVmJs {

  static final JSObject UNDEFINED = undefined();
  static final JSObject TRUE = JSBoolean.valueOf(true);
  static final JSObject FALSE = JSBoolean.valueOf(false);

  private static final JSObject LIST = array();

  private TeaVmJs() {}

  @JSBody(script = "return {};")
  static native JSObject object();

  @JSBody(script = "return [];")
  static native JSObject array();

  @JSBody(script = "return undefined;")
  static native JSObject undefined();

  @JSBody(params = { "a", "n" }, script = "a.length = n;")
  static native void setLength(JSObject a, int n);

  @JSBody(params = { "a", "i", "v" }, script = "a[i] = v === undefined ? null : v;")
  static native void setAt(JSObject a, int i, JSObject v);

  @JSBody(params = { "a", "i", "v" }, script = "a[i] = v >>> 0;")
  static native void setAtUint(JSObject a, int i, int v);

  @JSBody(params = { "a", "v" }, script = "a.push(v);")
  static native void push(JSObject a, JSObject v);

  @JSBody(params = { "a", "v" }, script = "a.push(v >>> 0);")
  static native void pushUint(JSObject a, int v);

  /**
   * Returns a shared, empty JavaScript array.
   *
   * <p>The same array is returned every time and emptied first, so it is only valid until the next call of this
   * method. Calls that build a short list for one browser call use it.
   *
   * @return The emptied array.
   */
  static JSObject list() {
    setLength(LIST, 0);
    return LIST;
  }

  /**
   * Converts a Java string to a JavaScript string.
   *
   * @param s The string.
   * @return The JavaScript string.
   */
  static JSObject string(String s) {
    return JSString.valueOf(s);
  }

  /**
   * Looks up the browser string of an enum value.
   *
   * @param table The strings, indexed by ordinal. A {@code null} entry means "use the default".
   * @param none Whether the entry at the same index is a value that the browser does not have.
   * @param v The value, or {@code null}.
   * @param type The name of the enum, for the error message.
   * @return The string, or {@code null} for "use the default".
   * @throws UnsupportedOperationException If the browser has no equivalent of the value.
   */
  static JSObject lookup(JSObject[] table, boolean[] none, Enum<?> v, String type) {
    if (v == null) {
      return null;
    }
    int i = v.ordinal();
    if (none[i]) {
      throw unsupported(type + "." + v.name() + " has no equivalent in the browser's WebGPU API.");
    }
    return table[i];
  }

  static JSObject optionalBool(GpuOptionalBool v) {
    if (v == GpuOptionalBool.TRUE) {
      return TRUE;
    }
    return v == GpuOptionalBool.FALSE ? FALSE : null;
  }

  static UnsupportedOperationException unsupported(String message) {
    return new UnsupportedOperationException(message);
  }
}
