package io.github.stringdotjar.jwebgpu.generator.emit;

import io.github.stringdotjar.jwebgpu.generator.model.CType;
import io.github.stringdotjar.jwebgpu.generator.model.Callback;
import io.github.stringdotjar.jwebgpu.generator.model.GpuFunction;
import io.github.stringdotjar.jwebgpu.generator.model.Member;
import io.github.stringdotjar.jwebgpu.generator.model.MemberRole;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;
import io.github.stringdotjar.jwebgpu.generator.model.StructKind;
import io.github.stringdotjar.jwebgpu.generator.model.TypeKind;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Works out which structs the FFM backend has to write into native memory, and which it has to read back.
 *
 * <p>Only some structs travel in each direction: a descriptor is only ever written, and {@code WGPULimits} is read
 * back from the adapter (and also written when a device is requested). Generating the code for one direction only
 * keeps the backend small. The sets are computed from how the functions and callbacks use the structs, so a change
 * of the specification is picked up automatically.
 *
 * <ul>
 *   <li>A struct is <em>written</em> if a function takes a pointer to it, or if a written struct contains it by value,
 *       by pointer or in an array. Every extension struct is written, because it may be chained onto anything.
 *   <li>A struct is <em>read</em> if a function fills it through a mutable pointer, if a callback receives a pointer
 *       to it, or if a read struct contains it. The extension structs of a read struct are read too.
 *   <li>A struct needs an <em>init</em> step (zeroing before the call) if a function fills it through a mutable
 *       pointer.
 * </ul>
 */
final class FfmUse {

  private final Set<String> written = new LinkedHashSet<>();
  private final Set<String> read = new LinkedHashSet<>();
  private final Set<String> outs = new LinkedHashSet<>();

  FfmUse(Spec spec) {
    List<String> writeSeeds = new ArrayList<>();
    List<String> readSeeds = new ArrayList<>();
    for (GpuFunction f : spec.functions()) {
      for (Member m : f.params()) {
        CType t = m.type();
        if (!(t.isPointer() && t.kind() == TypeKind.STRUCT) || FfmNames.isDataPointer(m)) {
          continue;
        }
        if (t.pointer() == CType.Pointer.MUTABLE) {
          readSeeds.add(t.name());
          if (t.name().equals("WGPUFutureWaitInfo")) {
            writeSeeds.add(t.name());
          } else {
            outs.add(t.name());
          }
        } else {
          writeSeeds.add(t.name());
        }
      }
    }
    for (Callback c : spec.callbacks()) {
      for (Member m : c.params()) {
        if (m.type().isPointer() && m.type().kind() == TypeKind.STRUCT) {
          readSeeds.add(m.type().name());
        }
      }
    }
    for (Struct s : spec.structs()) {
      if (s.kind() == StructKind.EXTENSION) {
        writeSeeds.add(s.cName());
      }
    }
    close(spec, writeSeeds, written, false);
    close(spec, readSeeds, read, true);
  }

  /**
   * Tells whether the backend writes a struct.
   *
   * @param cName The C name of the struct.
   * @return {@code true} if a marshaller with {@code write} and {@code put} is needed.
   */
  boolean isWritten(String cName) {
    return written.contains(cName);
  }

  /**
   * Tells whether the backend reads a struct back.
   *
   * @param cName The C name of the struct.
   * @return {@code true} if a marshaller with {@code read} is needed.
   */
  boolean isRead(String cName) {
    return read.contains(cName);
  }

  /**
   * Tells whether a function fills the struct through a mutable pointer.
   *
   * @param cName The C name of the struct.
   * @return {@code true} if a marshaller with {@code init} is needed.
   */
  boolean isOut(String cName) {
    return outs.contains(cName);
  }

  private static void close(Spec spec, List<String> seeds, Set<String> result, boolean withExtensions) {
    List<String> todo = new ArrayList<>(seeds);
    while (!todo.isEmpty()) {
      String name = todo.remove(todo.size() - 1);
      Struct s = spec.findStruct(name);
      if (s == null || s.kind() == StructKind.BUILTIN || s.kind() == StructKind.CALLBACK_INFO
          || !result.add(name)) {
        continue;
      }
      for (Member m : s.fields()) {
        if (m.type().kind() == TypeKind.STRUCT && m.role() != MemberRole.CALLBACK_INFO) {
          todo.add(m.type().name());
        }
      }
      if (withExtensions) {
        todo.addAll(s.extensions());
      }
    }
  }
}
