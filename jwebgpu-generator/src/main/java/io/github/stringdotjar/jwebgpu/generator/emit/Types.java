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

import io.github.stringdotjar.jwebgpu.generator.model.CType;
import io.github.stringdotjar.jwebgpu.generator.model.Callback;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;

/**
 * Maps C types to Java types.
 *
 * <p>The mapping, in short:
 *
 * <ul>
 *   <li>{@code uint32_t}, {@code int32_t} and {@code uint16_t} become {@code int} (a 16-bit value stays in the range
 *       0 to 65535, and no cast is needed to use it).
 *   <li>{@code uint64_t} and {@code size_t} become {@code long}. Bit flags are {@code long} too.
 *   <li>{@code WGPUBool} becomes {@code boolean}, {@code float} and {@code double} stay as they are.
 *   <li>Enums, objects and structs become the matching {@code Gpu} type, and {@code WGPUStringView} becomes
 *       {@code String}.
 *   <li>A raw native pointer ({@code void *}) becomes a {@code long}.
 * </ul>
 */
final class Types {

  static final String STRING_VIEW = "WGPUStringView";

  private final Spec spec;

  Types(Spec spec) {
    this.spec = spec;
  }

  /**
   * Returns the Java type of a single value of the given C type, ignoring any pointer except for the raw pointer and
   * string cases.
   *
   * @param t The C type.
   * @return The Java type text without annotations.
   */
  String java(CType t) {
    return switch (t.kind()) {
      case BOOL -> "boolean";
      case UINT16, UINT32, INT32 -> "int";
      case INT16 -> "short";
      case UINT64, USIZE, BITFLAG -> "long";
      case FLOAT32 -> "float";
      case FLOAT64 -> "double";
      case ENUM, OBJECT -> Names.type(t.name());
      case STRUCT -> t.name().equals(STRING_VIEW) ? "String" : Names.type(t.name());
      case VOID -> {
        if (!t.isPointer()) {
          throw new IllegalArgumentException("void value has no Java type");
        }
        yield "long";
      }
      case CHAR -> "String";
      case FUNCTION_POINTER -> "long";
    };
  }

  /**
   * Tells whether a type maps to a primitive Java type.
   *
   * @param javaType A Java type text.
   * @return {@code true} for {@code int}, {@code long}, {@code boolean} and the other primitives.
   */
  static boolean primitive(String javaType) {
    return switch (javaType) {
      case "boolean", "byte", "short", "char", "int", "long", "float", "double" -> true;
      default -> false;
    };
  }

  /**
   * Finds the callback whose info struct has the given C name.
   *
   * @param infoName The C name of the callback info struct.
   * @return The callback.
   * @throws IllegalArgumentException If there is none.
   */
  Callback callbackByInfo(String infoName) {
    for (Callback c : spec.callbacks()) {
      if (c.infoName().equals(infoName)) {
        return c;
      }
    }
    throw new IllegalArgumentException("no callback with info struct " + infoName);
  }

  /**
   * Finds the callback with the given function pointer type name.
   *
   * @param cName The C name of the callback type.
   * @return The callback.
   */
  Callback callback(String cName) {
    return spec.callback(cName);
  }
}
