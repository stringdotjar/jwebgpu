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
package io.github.stringdotjar.jwebgpu.generator.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A resolved C type: a base type plus at most one level of pointer.
 *
 * <p>The WebGPU headers never use pointers to pointers, so a single {@link Pointer} marker is enough. Sizes and
 * alignments describe the LP64 and LLP64 platforms jwebgpu targets (64-bit pointers, 8-byte {@code size_t}).
 *
 * @param kind The category of the base type.
 * @param name The C spelling of the base type, such as {@code uint32_t} or {@code WGPUTextureFormat}.
 * @param pointer Whether the type is a pointer, and if so whether it points to const data.
 */
public record CType(@NotNull TypeKind kind, @NotNull String name, @NotNull Pointer pointer) {

  /** The size in bytes of a pointer, {@code size_t} and every object handle. */
  public static final int POINTER_SIZE = 8;

  /**
   * Creates a type that is not a pointer.
   *
   * @param kind The category of the base type.
   * @param name The C spelling of the base type.
   * @return The new type.
   */
  public static CType of(TypeKind kind, String name) {
    return new CType(kind, name, Pointer.NONE);
  }

  /**
   * Returns the same base type behind a pointer.
   *
   * @param pointer The pointer kind.
   * @return A new type with the given pointer kind.
   */
  public CType withPointer(Pointer pointer) {
    return new CType(kind, name, pointer);
  }

  /**
   * Tells whether this type is a pointer.
   *
   * @return {@code true} unless the pointer kind is {@link Pointer#NONE}.
   */
  public boolean isPointer() {
    return pointer != Pointer.NONE;
  }

  /**
   * Returns the C declaration text of the type, using the same spelling as {@code webgpu.h}.
   *
   * @return For example {@code uint32_t}, {@code WGPUBufferDescriptor const *} or {@code void *}.
   */
  public String declaration() {
    return switch (pointer) {
      case NONE -> name;
      case CONST -> name + " const *";
      case MUTABLE -> name + " *";
    };
  }

  /**
   * Returns the size in bytes of a value of a scalar type.
   *
   * @return The size, or {@code -1} if this is a struct passed by value (ask {@link Layouts} for those) or a
   *     {@code void} that is not behind a pointer.
   */
  public int scalarSize() {
    if (isPointer()) {
      return POINTER_SIZE;
    }
    return switch (kind) {
      case CHAR -> 1;
      case UINT16, INT16 -> 2;
      case BOOL, UINT32, INT32, FLOAT32, ENUM -> 4;
      case UINT64, USIZE, FLOAT64, BITFLAG, OBJECT, FUNCTION_POINTER -> POINTER_SIZE;
      case VOID, STRUCT -> -1;
    };
  }

  @Override
  public @NotNull String toString() {
    return declaration();
  }

  /** Whether a type is a pointer, and if so what the pointee's constness is. */
  public enum Pointer {

    /** Not a pointer. */
    NONE,
    /** A pointer to const data, written {@code T const *}. */
    CONST,
    /** A pointer to writable data, written {@code T *}. */
    MUTABLE;

    /**
     * Converts the specification's {@code pointer} value.
     *
     * @param spec {@code null} or empty, {@code immutable} or {@code mutable}.
     * @return The matching pointer kind.
     * @throws IllegalArgumentException If the value is none of the above.
     */
    public static Pointer fromSpec(@Nullable String spec) {
      if (spec == null || spec.isEmpty()) {
        return NONE;
      }
      return switch (spec) {
        case "immutable" -> CONST;
        case "mutable" -> MUTABLE;
        default -> throw new IllegalArgumentException("unknown pointer kind '" + spec + "'");
      };
    }
  }
}
