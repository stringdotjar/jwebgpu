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
package io.github.stringdotjar.jwebgpu.generator.parse;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A parsed JSON value.
 *
 * <p>The parser in {@link Json} produces a tree of these records. Think of the tree as a filing cabinet: an
 * {@link Obj} is a drawer of labeled folders, an {@link Arr} is a numbered stack, and the other records are the
 * single sheets of paper inside. The accessor methods (such as {@link #asObj()}) let callers state what shape
 * they expect and fail with a readable message when the document disagrees.
 */
public sealed interface JsonValue {

  /**
   * Returns a short name for the kind of value, used in error messages.
   *
   * @return The kind name, such as {@code object} or {@code string}.
   */
  String kind();

  /**
   * Casts this value to an object.
   *
   * @return This value as an {@link Obj}.
   * @throws JsonException If this value is not an object.
   */
  default Obj asObj() {
    if (this instanceof Obj o) {
      return o;
    }
    throw new JsonException("expected an object but found " + kind());
  }

  /**
   * Casts this value to an array.
   *
   * @return This value as an {@link Arr}.
   * @throws JsonException If this value is not an array.
   */
  default Arr asArr() {
    if (this instanceof Arr a) {
      return a;
    }
    throw new JsonException("expected an array but found " + kind());
  }

  /**
   * Reads this value as a string.
   *
   * @return The string contents.
   * @throws JsonException If this value is not a string.
   */
  default String asString() {
    if (this instanceof Str s) {
      return s.value();
    }
    throw new JsonException("expected a string but found " + kind());
  }

  /**
   * Reads this value as a boolean.
   *
   * @return The boolean value.
   * @throws JsonException If this value is not a boolean.
   */
  default boolean asBoolean() {
    if (this instanceof Bool b) {
      return b.value();
    }
    throw new JsonException("expected a boolean but found " + kind());
  }

  /**
   * Reads this value as a 64-bit integer.
   *
   * @return The integer value.
   * @throws JsonException If this value is not a number, or the number is not an integer that fits in a
   *     {@code long}.
   */
  default long asLong() {
    if (this instanceof Num n) {
      try {
        return Long.parseLong(n.raw());
      } catch (NumberFormatException e) {
        throw new JsonException("expected an integer but found " + n.raw());
      }
    }
    throw new JsonException("expected a number but found " + kind());
  }

  /**
   * Reads this value as a double.
   *
   * @return The numeric value.
   * @throws JsonException If this value is not a number.
   */
  default double asDouble() {
    if (this instanceof Num n) {
      return Double.parseDouble(n.raw());
    }
    throw new JsonException("expected a number but found " + kind());
  }

  /**
   * A JSON object. Member order is the order the keys appeared in the document.
   *
   * @param members The members, in document order. Keys are unique.
   */
  record Obj(@NotNull Map<String, JsonValue> members) implements JsonValue {

    @Override
    public String kind() {
      return "object";
    }

    /**
     * Tells whether a member with the given key exists.
     *
     * @param key The member name.
     * @return {@code true} if the object has the key.
     */
    public boolean has(String key) {
      return members.containsKey(key);
    }

    /**
     * Looks up a member.
     *
     * @param key The member name.
     * @return The value, or {@code null} if the key is absent.
     */
    public @Nullable JsonValue get(String key) {
      return members.get(key);
    }

    /**
     * Looks up a member that must exist.
     *
     * @param key The member name.
     * @return The value.
     * @throws JsonException If the key is absent.
     */
    public JsonValue require(String key) {
      JsonValue v = members.get(key);
      if (v == null) {
        throw new JsonException("missing required member '" + key + "'");
      }
      return v;
    }

    /**
     * Reads a required string member.
     *
     * @param key The member name.
     * @return The string.
     * @throws JsonException If the key is absent or not a string.
     */
    public String str(String key) {
      return require(key).asString();
    }

    /**
     * Reads an optional string member.
     *
     * @param key The member name.
     * @param fallback The value to return when the key is absent.
     * @return The string, or the fallback.
     */
    public String str(String key, String fallback) {
      JsonValue v = members.get(key);
      return v == null ? fallback : v.asString();
    }

    /**
     * Reads an optional boolean member.
     *
     * @param key The member name.
     * @param fallback The value to return when the key is absent.
     * @return The boolean, or the fallback.
     */
    public boolean bool(String key, boolean fallback) {
      JsonValue v = members.get(key);
      return v == null ? fallback : v.asBoolean();
    }

    /**
     * Reads a required object member.
     *
     * @param key The member name.
     * @return The nested object.
     * @throws JsonException If the key is absent or not an object.
     */
    public Obj obj(String key) {
      return require(key).asObj();
    }

    /**
     * Reads an optional array member.
     *
     * @param key The member name.
     * @return The items, or an empty list if the key is absent.
     */
    public List<JsonValue> arr(String key) {
      JsonValue v = members.get(key);
      return v == null ? List.of() : v.asArr().items();
    }
  }

  /**
   * A JSON array.
   *
   * @param items The elements, in order.
   */
  record Arr(@NotNull List<JsonValue> items) implements JsonValue {

    @Override
    public String kind() {
      return "array";
    }
  }

  /**
   * A JSON string.
   *
   * @param value The decoded text, with all escape sequences resolved.
   */
  record Str(@NotNull String value) implements JsonValue {

    @Override
    public String kind() {
      return "string";
    }
  }

  /**
   * A JSON number, kept as its original text so no precision is lost.
   *
   * @param raw The number exactly as it was written in the document.
   */
  record Num(@NotNull String raw) implements JsonValue {

    @Override
    public String kind() {
      return "number";
    }
  }

  /**
   * A JSON boolean.
   *
   * @param value The boolean value.
   */
  record Bool(boolean value) implements JsonValue {

    @Override
    public String kind() {
      return "boolean";
    }
  }

  /** The JSON {@code null} literal. */
  record Null() implements JsonValue {

    @Override
    public String kind() {
      return "null";
    }
  }
}
