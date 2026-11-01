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
import io.github.stringdotjar.jwebgpu.generator.model.GpuFunction;
import io.github.stringdotjar.jwebgpu.generator.model.GpuObject;
import io.github.stringdotjar.jwebgpu.generator.model.Member;
import io.github.stringdotjar.jwebgpu.generator.model.MemberRole;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;
import io.github.stringdotjar.jwebgpu.generator.model.StructLayout;
import io.github.stringdotjar.jwebgpu.generator.model.TypeKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides which native functions the FFM backend binds and what their descriptors look like.
 *
 * <p>This is the single source of truth for both the Java sources of the backend ({@link FfmMethods},
 * {@link FfmCallbackEmitter}, {@link FfmStructEmitter}) and the native image metadata ({@link GraalEmitter}). Neither
 * of them builds a descriptor of its own, so the metadata always covers exactly the calls the code makes.
 */
final class FfmDescriptors {

  private FfmDescriptors() {}

  /**
   * Returns every function an object class binds, including its reference counting functions.
   *
   * @param o The object.
   * @return The functions, in declaration order.
   */
  static List<GpuFunction> functions(GpuObject o) {
    return o.methods();
  }

  /**
   * Returns the functions that belong to no object and that the backend class binds.
   *
   * @param spec The specification.
   * @return The functions, in declaration order.
   */
  static List<GpuFunction> globals(Spec spec) {
    List<GpuFunction> globals = new ArrayList<>();
    for (GpuFunction f : spec.globalFunctions()) {
      if (FfmMethods.isMethod(f)) {
        globals.add(f);
      }
    }
    return globals;
  }

  /**
   * Returns the name of the C function that frees the members a struct holds.
   *
   * @param s The struct, whose {@code freeMembers} flag is set.
   * @return For example {@code wgpuSupportedFeaturesFreeMembers}.
   */
  static String freeFunction(Struct s) {
    return "wgpu" + s.cName().substring("WGPU".length()) + "FreeMembers";
  }

  /**
   * Describes the call of a C function.
   *
   * @param f The function.
   * @return The descriptor.
   */
  static FfmDescriptor of(GpuFunction f) {
    List<FfmLayout> params = new ArrayList<>();
    for (Member m : f.params()) {
      params.add(FfmNames.shape(m.type(), m.role()));
    }
    return new FfmDescriptor(f.returns() == null ? null : FfmNames.shape(f.returns(), MemberRole.VALUE), params);
  }

  /**
   * Describes a callback, which C calls and which returns nothing.
   *
   * @param cb The callback.
   * @return The descriptor.
   */
  static FfmDescriptor of(Callback cb) {
    List<FfmLayout> params = new ArrayList<>();
    for (Member m : cb.params()) {
      params.add(FfmNames.shape(m.type(), m.role()));
    }
    return new FfmDescriptor(null, params);
  }

  /**
   * Describes the call of the function that frees the members of a struct, which takes the struct by value.
   *
   * @param spec The specification.
   * @param s The struct, whose {@code freeMembers} flag is set.
   * @return The descriptor.
   */
  static FfmDescriptor free(Spec spec, Struct s) {
    return new FfmDescriptor(null, List.of(struct(spec, s.cName())));
  }

  /**
   * Describes the memory layout of a struct, with explicit padding, so it can be passed by value.
   *
   * @param spec The specification, which knows the offsets of every field.
   * @param cName The C name of the struct.
   * @return The layout.
   */
  static FfmLayout struct(Spec spec, String cName) {
    Struct s = spec.struct(cName);
    StructLayout layout = spec.layouts().of(cName);
    List<String> java = new ArrayList<>();
    List<String> graal = new ArrayList<>();
    long at = 0;
    for (int i = 0; i < s.fields().size(); i++) {
      Member m = s.fields().get(i);
      long off = layout.offsets().get(i);
      if (off > at) {
        java.add("MemoryLayout.paddingLayout(" + (off - at) + ")");
        graal.add("padding(" + (off - at) + ")");
      }
      CType t = m.type();
      FfmLayout part;
      long size;
      if (!t.isPointer() && t.kind() == TypeKind.STRUCT) {
        part = struct(spec, t.name());
        size = spec.layouts().of(t.name()).size();
      } else {
        size = t.scalarSize();
        part = switch ((int) size) {
          case 8 -> FfmLayout.LONG;
          case 4 -> FfmLayout.INT;
          case 2 -> FfmLayout.SHORT;
          default -> FfmLayout.BYTE;
        };
      }
      java.add(part.java());
      graal.add(part.graal());
      at = off + size;
    }
    if (layout.size() > at) {
      java.add("MemoryLayout.paddingLayout(" + (layout.size() - at) + ")");
      graal.add("padding(" + (layout.size() - at) + ")");
    }
    return new FfmLayout("MemoryLayout.structLayout(" + String.join(", ", java) + ")",
        "struct(" + String.join(", ", graal) + ")", "java.lang.foreign.MemorySegment");
  }

  /**
   * Checks that the layouts of the hand-written {@code Native} class match the model.
   *
   * <p>{@code Native.STRING_VIEW} and {@code Native.CALLBACK_INFO} are written by hand, so their metadata spelling
   * lives in {@link FfmLayout}. This guard makes the generator fail, instead of the metadata drifting, when the
   * specification changes the shape of either struct.
   *
   * @param spec The specification.
   * @throws IllegalStateException If a struct no longer has the expected size or field offsets.
   */
  static void checkNativeLayouts(Spec spec) {
    expect(spec, Types.STRING_VIEW, 16, 0, 8);
    for (GpuFunction f : spec.functions()) {
      for (Member m : f.params()) {
        if (m.role() == MemberRole.CALLBACK_INFO) {
          expect(spec, m.type().name(), 40, 0, 8, 16, 24, 32);
        }
      }
    }
  }

  private static void expect(Spec spec, String cName, long size, long... offsets) {
    StructLayout layout = spec.layouts().of(cName);
    boolean ok = layout.size() == size && layout.offsets().size() == offsets.length;
    for (int i = 0; ok && i < offsets.length; i++) {
      ok = layout.offsets().get(i) == offsets[i];
    }
    if (!ok) {
      throw new IllegalStateException(cName + " no longer matches the layout of the hand-written Native class: "
          + layout);
    }
  }
}
