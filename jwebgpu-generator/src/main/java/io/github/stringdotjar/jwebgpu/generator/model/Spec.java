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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The resolved WebGPU C API: everything that {@code webgpu.h} declares, as plain immutable data.
 *
 * <p>Build one with {@code SpecLoader.load(...)} from the {@code parse} package. All lookups use C names (for
 * example {@code WGPUBufferDescriptor} or {@code wgpuDeviceCreateBuffer}).
 *
 * <p>The lists keep a stable, deterministic order:
 *
 * <ul>
 *   <li>{@link #structs()} is in C declaration order, so every struct comes after the structs it contains by value.
 *   <li>{@link #functions()} is in the order of the header: global functions, {@code wgpuGetProcAddress}, then the
 *       methods of each object sorted by object name.
 *   <li>The other lists keep the order of the specification file.
 * </ul>
 */
public final class Spec {

  private final String name;
  private final String copyright;
  private final String doc;
  private final List<Constant> constants;
  private final List<EnumType> enums;
  private final List<BitflagType> bitflags;
  private final List<Callback> callbacks;
  private final List<Struct> structs;
  private final List<GpuObject> objects;
  private final List<GpuFunction> functions;
  private final Map<String, Constant> constantIndex;
  private final Map<String, EnumType> enumIndex;
  private final Map<String, BitflagType> bitflagIndex;
  private final Map<String, Callback> callbackIndex;
  private final Map<String, Struct> structIndex;
  private final Map<String, GpuObject> objectIndex;
  private final Map<String, GpuFunction> functionIndex;
  private Layouts layouts;

  /**
   * Creates a specification. The resolver is the only intended caller.
   *
   * @param name The specification name, {@code webgpu} for the core specification.
   * @param copyright The copyright text of the specification file.
   * @param doc The overview documentation of the specification.
   * @param constants The constants.
   * @param enums The enums.
   * @param bitflags The bit flag types.
   * @param callbacks The callback types.
   * @param structs The structs in C declaration order.
   * @param objects The object types.
   * @param functions Every function in header order.
   */
  public Spec(
      String name,
      String copyright,
      String doc,
      List<Constant> constants,
      List<EnumType> enums,
      List<BitflagType> bitflags,
      List<Callback> callbacks,
      List<Struct> structs,
      List<GpuObject> objects,
      List<GpuFunction> functions) {
    this.name = name;
    this.copyright = copyright;
    this.doc = doc;
    this.constants = List.copyOf(constants);
    this.enums = List.copyOf(enums);
    this.bitflags = List.copyOf(bitflags);
    this.callbacks = List.copyOf(callbacks);
    this.structs = List.copyOf(structs);
    this.objects = List.copyOf(objects);
    this.functions = List.copyOf(functions);
    this.constantIndex = index(this.constants, Constant::cName);
    this.enumIndex = index(this.enums, EnumType::cName);
    this.bitflagIndex = index(this.bitflags, BitflagType::cName);
    this.callbackIndex = index(this.callbacks, Callback::cName);
    this.structIndex = index(this.structs, Struct::cName);
    this.objectIndex = index(this.objects, GpuObject::cName);
    this.functionIndex = index(this.functions, GpuFunction::cName);
  }

  private static <T> Map<String, T> index(List<T> items, Function<T, String> key) {
    Map<String, T> map = new LinkedHashMap<>();
    for (int i = 0; i < items.size(); i++) {
      T item = items.get(i);
      if (map.put(key.apply(item), item) != null) {
        throw new IllegalArgumentException("duplicate name " + key.apply(item));
      }
    }
    return Collections.unmodifiableMap(map);
  }

  private static <T> T require(Map<String, T> map, String kind, String cName) {
    T value = map.get(cName);
    if (value == null) {
      throw new IllegalArgumentException("no " + kind + " named " + cName);
    }
    return value;
  }

  /**
   * Returns the layout calculator for this specification's structs.
   *
   * @return A shared {@link Layouts} instance.
   */
  public synchronized Layouts layouts() {
    if (layouts == null) {
      layouts = new Layouts(this);
    }
    return layouts;
  }

  /**
   * Returns every function that takes no receiver.
   *
   * @return The global functions, including {@code wgpuGetProcAddress}, in header order.
   */
  public List<GpuFunction> globalFunctions() {
    return functions.stream().filter(f -> f.owner() == null).toList();
  }

  /**
   * Finds a constant.
   *
   * @param cName The macro name, such as {@code WGPU_STRLEN}.
   * @return The constant.
   * @throws IllegalArgumentException If there is none.
   */
  public Constant constant(String cName) {
    return require(constantIndex, "constant", cName);
  }

  /**
   * Finds an enum.
   *
   * @param cName The C name, such as {@code WGPUTextureFormat}.
   * @return The enum.
   * @throws IllegalArgumentException If there is none.
   */
  public EnumType enumType(String cName) {
    return require(enumIndex, "enum", cName);
  }

  /**
   * Finds a bit flag type.
   *
   * @param cName The C name, such as {@code WGPUTextureUsage}.
   * @return The bit flag type.
   * @throws IllegalArgumentException If there is none.
   */
  public BitflagType bitflag(String cName) {
    return require(bitflagIndex, "bitflag", cName);
  }

  /**
   * Finds a callback.
   *
   * @param cName The C name of the function pointer type, such as {@code WGPURequestAdapterCallback}.
   * @return The callback.
   * @throws IllegalArgumentException If there is none.
   */
  public Callback callback(String cName) {
    return require(callbackIndex, "callback", cName);
  }

  /**
   * Finds a struct.
   *
   * @param cName The C name, such as {@code WGPUBufferDescriptor}.
   * @return The struct.
   * @throws IllegalArgumentException If there is none.
   */
  public Struct struct(String cName) {
    return require(structIndex, "struct", cName);
  }

  /**
   * Finds an object type.
   *
   * @param cName The C name of the handle type, such as {@code WGPUDevice}.
   * @return The object.
   * @throws IllegalArgumentException If there is none.
   */
  public GpuObject object(String cName) {
    return require(objectIndex, "object", cName);
  }

  /**
   * Finds a function or method.
   *
   * @param cName The C name, such as {@code wgpuDeviceCreateBuffer}.
   * @return The function.
   * @throws IllegalArgumentException If there is none.
   */
  public GpuFunction function(String cName) {
    return require(functionIndex, "function", cName);
  }

  /**
   * Finds a struct without failing.
   *
   * @param cName The C name.
   * @return The struct, or {@code null} if there is none.
   */
  public @Nullable Struct findStruct(String cName) {
    return structIndex.get(cName);
  }

  /**
   * Finds an enum without failing.
   *
   * @param cName The C name.
   * @return The enum, or {@code null} if there is none.
   */
  public @Nullable EnumType findEnum(String cName) {
    return enumIndex.get(cName);
  }

  /**
   * Finds a function without failing.
   *
   * @param cName The C name.
   * @return The function, or {@code null} if there is none.
   */
  public @Nullable GpuFunction findFunction(String cName) {
    return functionIndex.get(cName);
  }

  /**
   * Returns the specification name.
   *
   * @return {@code webgpu} for the core specification.
   */
  public @NotNull String name() {
    return name;
  }

  /**
   * Returns the copyright text of the specification file.
   *
   * @return The copyright text.
   */
  public @NotNull String copyright() {
    return copyright;
  }

  /**
   * Returns the overview documentation of the specification.
   *
   * @return The documentation text.
   */
  public @NotNull String doc() {
    return doc;
  }

  /**
   * Returns the constants.
   *
   * @return The constants in specification order.
   */
  public @NotNull List<Constant> constants() {
    return constants;
  }

  /**
   * Returns the enums.
   *
   * @return The enums in specification order.
   */
  public @NotNull List<EnumType> enums() {
    return enums;
  }

  /**
   * Returns the bit flag types.
   *
   * @return The bit flag types in specification order.
   */
  public @NotNull List<BitflagType> bitflags() {
    return bitflags;
  }

  /**
   * Returns the callback types.
   *
   * @return The callbacks in specification order.
   */
  public @NotNull List<Callback> callbacks() {
    return callbacks;
  }

  /**
   * Returns every struct, including the built-in and callback info structs.
   *
   * @return The structs in C declaration order.
   */
  public @NotNull List<Struct> structs() {
    return structs;
  }

  /**
   * Returns the object types.
   *
   * @return The objects sorted by name.
   */
  public @NotNull List<GpuObject> objects() {
    return objects;
  }

  /**
   * Returns every function and method.
   *
   * @return The functions in header order.
   */
  public @NotNull List<GpuFunction> functions() {
    return functions;
  }
}
