package io.github.stringdotjar.jwebgpu.samples.web;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBuffer;
import org.teavm.jso.JSBufferType;
import org.teavm.jso.JSByRef;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSMethod;
import org.teavm.jso.core.JSString;

public final class Experiment {
  public interface Thing extends JSObject {
    @JSMethod("foo") int foo(int a);
    @JSMethod("foo") int foo(int a, int b);
  }

  @JSBody(params = {"b"}, script = "return b;")
  static native JSObject view(@JSBuffer(JSBufferType.UINT8) ByteBuffer b);

  @JSBody(params = {"b"}, script = "return b.constructor.name + ':' + b.length + ':' + b.byteOffset + ':' + (b.buffer.byteLength);")
  static native String describe(JSObject b);

  @JSBody(params = {"b"}, script = "console.log('X ' + b);")
  static native void log(String b);

  @JSBody(params = {"a"}, script = "return a.constructor.name + ':' + a.length;")
  static native String describeRef(@JSByRef float[] a);

  @JSBody(params = {"o", "v"}, script = "o.x = v;")
  static native void setLong(JSObject o, double v);

  public static void main(String[] args) {
    log("hello " + System.getProperty("java.version"));
    ByteBuffer d = ByteBuffer.allocateDirect(64).order(ByteOrder.LITTLE_ENDIAN);
    d.putInt(1);
    d.putInt(2);
    d.flip();
    log("direct pos0: " + describe(view(d)));
    d.position(4);
    log("direct pos4: " + describe(view(d)));
    ByteBuffer h = ByteBuffer.wrap(new byte[16]);
    try {
      log("heap: " + describe(view(h)));
    } catch (Throwable e) {
      log("heap failed " + e);
    }
  }
}
