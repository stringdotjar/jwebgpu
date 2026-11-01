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
package io.github.stringdotjar.jwebgpu.generator.emit;

import io.github.stringdotjar.jwebgpu.generator.model.GpuObject;
import io.github.stringdotjar.jwebgpu.generator.model.Naming;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Type;

/**
 * Names of the classes the web backend generates, and the mapping from IDL types to Java types of the JSO layer.
 *
 * <p>The naming scheme follows the other backends: {@code TeaVmGpuBuffer} implements {@code GpuBuffer},
 * {@code TeaVmGpuBufferDescriptorStruct} marshals a descriptor, and {@code JsGpuBuffer} is the JavaScript Object
 * interface of the browser's {@code GPUBuffer}.
 */
final class TeaVmNames {

  static final String PKG = "io.github.stringdotjar.jwebgpu.teavm";

  private TeaVmNames() {}

  static String path(String name) {
    return PKG.replace('.', '/') + "/" + name + ".java";
  }

  static String object(String cName) {
    return "TeaVm" + Names.type(cName);
  }

  static String struct(String cName) {
    return "TeaVm" + Names.type(cName) + "Struct";
  }

  static String jso(String idlName) {
    return "Js" + (idlName.startsWith("GPU") ? "Gpu" + idlName.substring(3) : idlName);
  }

  static String iface(GpuObject o) {
    return switch (o.specName()) {
      case "instance" -> "GPU";
      case "surface" -> "GPUCanvasContext";
      default -> "GPU" + Naming.pascal(o.specName());
    };
  }

  /** Name of the method that turns a Java enum into its browser string. */
  static String toJs(String cName) {
    return "js" + Names.type(cName).substring(3);
  }

  /** Name of the method that tells whether a Java enum value has a browser string. */
  static String has(String cName) {
    return "has" + Names.type(cName).substring(3);
  }

  /** Name of the method that turns a browser string into a Java enum. */
  static String fromJs(String cName) {
    return "from" + Names.type(cName).substring(3);
  }

  /**
   * Chooses the Java type that the JSO layer uses for an IDL type.
   *
   * @param idl The browser model.
   * @param type The IDL type.
   * @param forReturn Whether the type is a return type. Enums are strings when read, and plain objects when written,
   *     because the "left out" value is JavaScript {@code undefined}, which is not a string.
   * @return The Java type name.
   */
  static String javaType(WebIdl idl, Type type, boolean forReturn) {
    Type t = idl.resolve(type);
    switch (t.kind()) {
      case PROMISE -> {
        return "JSPromise<JSObject>";
      }
      case UNION -> {
        Type single = singleNonUndefined(t);
        return single == null ? "JSObject" : javaType(idl, single, forReturn);
      }
      case SEQUENCE, FROZEN_ARRAY, RECORD -> {
        return "JSObject";
      }
      default -> {
      }
    }
    return switch (t.name()) {
      case "undefined" -> "void";
      case "boolean" -> "boolean";
      case "unsigned long", "long", "unsigned short", "short", "octet", "byte" -> "int";
      case "unsigned long long", "long long", "double", "unrestricted double" -> "double";
      case "float", "unrestricted float" -> "float";
      case "DOMString", "USVString", "ByteString" -> "String";
      default -> {
        if (idl.findInterface(t.name()) != null) {
          yield jso(t.name());
        }
        if (idl.findEnum(t.name()) != null) {
          yield forReturn ? "String" : "JSObject";
        }
        yield "JSObject";
      }
    };
  }

  private static Type singleNonUndefined(Type union) {
    Type found = null;
    for (Type m : union.args()) {
      if (m.is("undefined")) {
        continue;
      }
      if (found != null) {
        return null;
      }
      found = m;
    }
    return found;
  }

  static String capitalize(String s) {
    return Names.capitalize(s);
  }
}
