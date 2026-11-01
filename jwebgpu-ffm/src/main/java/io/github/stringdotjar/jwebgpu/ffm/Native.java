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
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * The link between the generated code and the native WebGPU library.
 *
 * <p>This class holds the loaded library, creates the downcall handles and upcall stubs, and describes the two small
 * structs that are passed to C functions by value.
 *
 * <p><b>Pointers are {@code long}.</b> Every downcall describes object handles and pointers to scratch memory as
 * {@link ValueLayout#JAVA_LONG} instead of {@link ValueLayout#ADDRESS}. On every 64-bit target jwebgpu supports
 * (System V x86-64, Win64 and AArch64) a pointer and a 64-bit integer travel in the same kind of register, so the
 * call is identical, and the Java side never has to create a {@link MemorySegment} for a pointer. Two kinds of values
 * are not plain integers and do keep a struct layout: {@code WGPUStringView} and the callback info structs, which C
 * passes by value. A returned {@code WGPUFuture} is a struct of one 64-bit integer, which every one of those targets
 * returns in the same register as a plain {@code long}, so it is declared as one.
 */
final class Native {

  /** A {@code WGPUStringView}: a pointer and a length. */
  static final StructLayout STRING_VIEW = MemoryLayout.structLayout(
      ValueLayout.JAVA_LONG.withName("data"),
      ValueLayout.JAVA_LONG.withName("length"));

  /** A callback info struct: {@code nextInChain}, {@code mode}, the callback and two userdata pointers. */
  static final StructLayout CALLBACK_INFO = MemoryLayout.structLayout(
      ValueLayout.JAVA_LONG.withName("nextInChain"),
      ValueLayout.JAVA_INT.withName("mode"),
      MemoryLayout.paddingLayout(4),
      ValueLayout.JAVA_LONG.withName("callback"),
      ValueLayout.JAVA_LONG.withName("userdata1"),
      ValueLayout.JAVA_LONG.withName("userdata2"));

  private static final Linker LINKER = Linker.nativeLinker();
  private static volatile SymbolLookup lookup;

  private Native() {}

  /**
   * Remembers the loaded library. Called by the loader before any native function is bound.
   *
   * @param library The symbols of the library.
   */
  static void setLibrary(SymbolLookup library) {
    lookup = library;
  }

  /**
   * Tells whether a native library has been loaded.
   *
   * @return {@code true} after {@link #setLibrary(SymbolLookup)}.
   */
  static boolean isLoaded() {
    return lookup != null;
  }

  /**
   * Binds a native function.
   *
   * @param name The C name, such as {@code wgpuQueueSubmit}.
   * @param descriptor The shape of the function.
   * @return The handle. Call it with {@code invokeExact}.
   * @throws IllegalStateException If the library is not loaded or has no such function.
   */
  static MethodHandle handle(String name, FunctionDescriptor descriptor) {
    SymbolLookup library = lookup;
    if (library == null) {
      throw new IllegalStateException("The native WebGPU library is not loaded. Call JWebGpuFfm.install() first.");
    }
    MemorySegment symbol = library.find(name)
        .orElseThrow(() -> new IllegalStateException("The native library has no function " + name));
    return LINKER.downcallHandle(symbol, descriptor);
  }

  /**
   * Creates an upcall stub, which is a native function pointer that calls a static Java method.
   *
   * <p>A method handle is how the foreign function API names the Java method; this is not reflection.
   *
   * @param lookup A lookup that can see the method.
   * @param owner The class that declares the method.
   * @param method The name of the static method.
   * @param descriptor The shape of the callback, which also decides the Java parameter types.
   * @return The address of the stub. The stub lives as long as the program.
   */
  static long upcall(MethodHandles.Lookup lookup, Class<?> owner, String method, FunctionDescriptor descriptor) {
    try {
      MethodHandle target = lookup.findStatic(owner, method, descriptor.toMethodType());
      return LINKER.upcallStub(target, descriptor, Arena.global()).address();
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException("Cannot create the callback " + method, e);
    }
  }

  /**
   * Turns whatever a method handle threw into an unchecked exception.
   *
   * @param error The thrown value.
   * @return The same exception if it is already unchecked, otherwise a wrapper. Errors are rethrown.
   */
  static RuntimeException fail(Throwable error) {
    if (error instanceof RuntimeException e) {
      return e;
    }
    if (error instanceof Error e) {
      throw e;
    }
    return new IllegalStateException(error);
  }
}
