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
import io.github.stringdotjar.jwebgpu.generator.model.FunctionKind;
import io.github.stringdotjar.jwebgpu.generator.model.GpuFunction;
import io.github.stringdotjar.jwebgpu.generator.model.Member;
import io.github.stringdotjar.jwebgpu.generator.model.MemberRole;
import io.github.stringdotjar.jwebgpu.generator.model.TypeKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes the Java methods for the C functions of an object, or for the global functions.
 *
 * <p>The same rules apply in both places:
 *
 * <ul>
 *   <li>The receiver parameter is dropped.
 *   <li>A struct pointer becomes a descriptor object. An output struct pointer becomes an object that the caller
 *       passes in and the call fills.
 *   <li>An array pointer and its count become {@code (Type[] items, int count)}.
 *   <li>A pointer to raw bytes plus a size becomes several overloads: one taking a {@code ByteBuffer}, and one each
 *       for {@code byte[]}, {@code short[]}, {@code int[]} and {@code float[]} with an offset and a count.
 *   <li>A callback info struct becomes a {@code (GpuCallbackMode mode, Callback callback)} pair, and the method
 *       returns the id of the future as a {@code long}.
 *   <li>A method that returns an object gets a second overload that refills an existing wrapper.
 * </ul>
 */
final class MethodWriter {

  private static final String DATA_PTR = "c_void_data_ptr";
  private static final String[][] DATA_ARRAYS = {
      { "byte", "bytes" }, { "short", "shorts" }, { "int", "ints" }, { "float", "floats" }
  };

  private final Docs docs;
  private final Types types;

  MethodWriter(Docs docs, Types types) {
    this.docs = docs;
    this.types = types;
  }

  /**
   * Tells whether a function is an ordinary method that gets a Java method.
   *
   * @param f A function of the model.
   * @return {@code false} for reference counting and the {@code FreeMembers} helpers, which the API handles itself.
   */
  static boolean isMethod(GpuFunction f) {
    return f.kind() == FunctionKind.NORMAL || f.kind() == FunctionKind.GET_PROC_ADDRESS;
  }

  /**
   * Tells whether a function is a simple getter, which the style rules place below the other methods.
   *
   * @param f A function of the model.
   * @return {@code true} for a method called {@code get...} that takes no parameters except the receiver.
   */
  static boolean isSimpleGetter(GpuFunction f) {
    int params = 0;
    for (Member m : f.params()) {
      if (m.role() != MemberRole.SELF) {
        params++;
      }
    }
    return Names.method(f.specName()).startsWith("get") && params == 0 && f.returns() != null && !f.isAsync();
  }

  /**
   * Writes every Java overload of one function.
   *
   * @param w The writer.
   * @param f The function.
   * @param iface Whether the methods go into an interface (no modifiers) or an abstract class.
   * @param self How the docs name the receiver, such as {@code this device}.
   * @param refills Whether to add the overload that refills an existing wrapper for methods that return an object.
   */
  void emit(SourceWriter w, GpuFunction f, boolean iface, String self) {
    emit(w, f, iface, self, !iface);
  }

  void emit(SourceWriter w, GpuFunction f, boolean iface, String self, boolean refills) {
    String name = Names.method(f.specName());
    List<Param> base = params(w, f);
    Ret ret = ret(w, f);
    String description = docs.text(f.doc(), fallback(f, name, self));
    if (f.isAsync()) {
      description += "\n\n<p>The operation finishes later. The result is delivered to the callback, depending on "
          + "the mode, when the instance processes events or waits (see {@link GpuInstance#processEvents()}).";
    }

    List<List<Param>> variants = new ArrayList<>();
    int dataAt = base.indexOf(null);
    if (dataAt < 0) {
      variants.add(base);
    } else {
      Member data = dataMember(f);
      boolean writable = data.type().pointer() == CType.Pointer.MUTABLE;
      variants.add(expand(w, base, dataAt, data.name(), writable, null));
      for (String[] a : DATA_ARRAYS) {
        variants.add(expand(w, base, dataAt, data.name(), writable, a));
      }
    }

    List<Variant> all = new ArrayList<>();
    for (List<Param> v : variants) {
      all.add(new Variant(v, false));
    }
    if (refills && f.returns() != null && f.returns().kind() == TypeKind.OBJECT && !f.isAsync()) {
      for (List<Param> v : variants) {
        List<Param> refill = new ArrayList<>(v);
        refill.add(new Param("@" + w.imp("org.jetbrains.annotations.NotNull") + " " + ret.rawType(), "into",
            "The existing wrapper to refill. It must have been created by the same backend. Its previous native "
                + "handle is released first, if it has one."));
        all.add(new Variant(refill, true));
      }
    }
    all.sort((a, b) -> Integer.compare(a.params.size(), b.params.size()));
    for (Variant v : all) {
      emitOne(w, name, description, v, ret, iface);
    }
  }

  private void emitOne(SourceWriter w, String name, String description, Variant v, Ret ret, boolean iface) {
    String text = description;
    List<String> tags = new ArrayList<>();
    if (v.refill) {
      text = "Refills an existing wrapper instead of allocating a new one.\n\n<p>This is the garbage-free variant of "
          + "{@link #" + name + "}: the native handle held by {@code into} changes, but no Java object is created. "
          + "Use it for objects that are created every frame. See that method for the full description.";
    }
    for (Param p : v.params) {
      tags.add("@param " + p.name + " " + Names.flat(p.doc));
    }
    if (!ret.type.equals("void")) {
      tags.add("@return " + Names.flat(v.refill ? ret.refillDoc : ret.doc));
    }
    w.doc(text, tags);
    List<String> args = new ArrayList<>();
    for (Param p : v.params) {
      args.add(p.type + " " + p.name);
    }
    String returnType = ret.type;
    if (iface && returnType.startsWith("@")) {
      // Without a modifier in front, the formatter puts a leading annotation on its own line.
      int space = returnType.indexOf(' ');
      w.line(returnType.substring(0, space));
      returnType = returnType.substring(space + 1);
    }
    String head = (iface ? "" : "public abstract ") + returnType + " " + name + "(";
    w.call(head, args, ");");
    w.blank();
  }

  private List<Param> params(SourceWriter w, GpuFunction f) {
    List<Param> out = new ArrayList<>();
    List<Member> ps = f.params();
    boolean waitAny = f.cName().equals("wgpuInstanceWaitAny");
    for (int i = 0; i < ps.size(); i++) {
      Member m = ps.get(i);
      switch (m.role()) {
        case SELF, ARRAY_COUNT, USERDATA, CHAIN_HEADER, CHAIN_NEXT -> {
        }
        case ARRAY_DATA -> {
          CType element = m.type().withPointer(CType.Pointer.NONE);
          out.add(new Param(types.java(element) + "[]", m.name(),
              docs.text(m.doc(), "The " + Names.words(m.name()) + ".")
                  + "\n\n<p>Only the first {@code " + m.peer() + "} elements are used."));
          out.add(new Param("int", m.peer(), "The number of elements of {@code " + m.name() + "} to use."));
        }
        case CALLBACK_INFO -> {
          Callback cb = types.callbackByInfo(m.type().name());
          String modeName = "mode";
          for (Member other : ps) {
            if (other.name().equals("mode")) {
              modeName = "callbackMode";
            }
          }
          out.add(new Param("GpuCallbackMode", modeName,
              "When the callback may run. See {@link GpuCallbackMode}."));
          out.add(new Param(Names.type(cb.cName()), "callback",
              "Receives the result of the operation. See {@link " + Names.type(cb.cName()) + "}."));
        }
        case VALUE -> {
          if (DATA_PTR.equals(m.specType())) {
            out.add(null);
            i++;
          } else if (waitAny && m.name().equals("futureCount")) {
            continue;
          } else if (waitAny && m.name().equals("futures")) {
            out.add(new Param("GpuFutureWaitInfo[]", "futures",
                docs.text(m.doc(), "The futures to wait for.")
                    + "\n\n<p>Only the first {@code futureCount} elements are used. The completed flag of each one "
                    + "is updated by the call."));
            out.add(new Param("int", "futureCount", "The number of elements of {@code futures} to use."));
          } else {
            out.add(valueParam(w, m));
          }
        }
      }
    }
    return out;
  }

  private Param valueParam(SourceWriter w, Member m) {
    CType t = m.type();
    String doc = docs.text(m.doc(), "The " + Names.words(m.name()) + ".");
    String type = types.java(t);
    String annotation = "";
    if ((t.kind() == TypeKind.STRUCT && t.isPointer()) || t.kind() == TypeKind.OBJECT) {
      if (m.nullable()) {
        annotation = "@" + w.imp("org.jetbrains.annotations.Nullable") + " ";
      }
      if (t.pointer() == CType.Pointer.MUTABLE) {
        doc += "\n\n<p>Pass an object to fill. The call overwrites its fields and copies every string, so there is "
            + "nothing to free afterwards.";
      }
    }
    return new Param(annotation + type, m.name(), doc);
  }

  private Member dataMember(GpuFunction f) {
    for (Member m : f.params()) {
      if (DATA_PTR.equals(m.specType())) {
        return m;
      }
    }
    throw new IllegalStateException("no data member in " + f.cName());
  }

  private List<Param> expand(SourceWriter w, List<Param> base, int at, String dataName, boolean writable,
      String[] array) {
    List<Param> out = new ArrayList<>(base);
    out.remove(at);
    String verb = writable ? "receives the bytes" : "holds the data";
    if (array == null) {
      String bb = w.imp("java.nio.ByteBuffer");
      String use = writable
          ? "the call fills the bytes from its position up to its limit."
          : "the bytes from its position up to its limit are used.";
      out.add(at, new Param(bb, dataName, "The buffer that " + verb + ": " + use
          + " The position and limit are not changed. A direct buffer avoids a copy."));
    } else {
      String type = array[0];
      out.add(at, new Param(type + "[]", dataName, "The array that " + verb + "."));
      out.add(at + 1, new Param("int", "dataOffset", "The index of the first element of {@code " + dataName
          + "} to use."));
      out.add(at + 2, new Param("int", "dataCount", array[0].equals("byte")
          ? "The number of bytes to use."
          : "The number of " + array[1] + " to use. This counts elements, not bytes."));
    }
    return out;
  }

  private Ret ret(SourceWriter w, GpuFunction f) {
    CType r = f.returns();
    String doc = docs.text(f.returnDoc());
    if (r == null) {
      return new Ret("void", "void", "", "");
    }
    String type;
    String fallback;
    if (f.isAsync() || (r.kind() == TypeKind.STRUCT && r.name().equals("WGPUFuture"))) {
      type = "long";
      fallback = "The id of the future that tracks this operation. Pass it to {@link GpuInstance#waitAny}.";
    } else if (r.kind() == TypeKind.OBJECT) {
      String t = Names.type(r.name());
      type = f.returnNullable() ? "@" + w.imp("org.jetbrains.annotations.Nullable") + " " + t : t;
      fallback = "The " + Names.words(t) + ".";
      String raw = t;
      String refillDoc = "{@code into}, now holding the object."
          + (f.returnNullable() ? " It is {@code null} if the native call returned no object, and then {@code into} "
              + "is left unchanged." : "");
      return new Ret(type, raw, doc.isEmpty() ? fallback : doc, refillDoc);
    } else if (r.kind() == TypeKind.ENUM) {
      type = "@" + w.imp("org.jetbrains.annotations.Nullable") + " " + types.java(r);
      fallback = "The result, or {@code null} if the native library returned a value this API does not know.";
    } else if (r.kind() == TypeKind.VOID && r.isPointer()) {
      type = "@" + w.imp("org.jetbrains.annotations.Nullable") + " " + w.imp("java.nio.ByteBuffer");
      fallback = "A buffer over the mapped memory, or {@code null} if it cannot be accessed.";
    } else if (r.kind() == TypeKind.BOOL) {
      type = "boolean";
      fallback = "{@code true} or {@code false}, depending on the check.";
    } else {
      type = types.java(r);
      fallback = "The " + Names.words(Names.method(f.specName()).replaceFirst("^get", "")) + ".";
    }
    return new Ret(type, type, doc.isEmpty() ? fallback : doc, "");
  }

  private static String fallback(GpuFunction f, String name, String self) {
    String words = Names.words(name);
    String rest = words.contains(" ") ? words.substring(words.indexOf(' ') + 1) : "";
    if (name.startsWith("get") && !rest.isEmpty()) {
      return "Returns the " + rest + " of " + self + ".";
    }
    if (name.startsWith("set") && !rest.isEmpty()) {
      return "Sets the " + rest + " of " + self + ".";
    }
    if (name.startsWith("has") && !rest.isEmpty()) {
      return "Tells whether " + self + " has the " + rest + ".";
    }
    if (name.startsWith("create") && !rest.isEmpty()) {
      return "Creates a new " + rest + " on " + self + ".";
    }
    return "Performs the " + words + " operation on " + self + ".";
  }

  private record Param(String type, String name, String doc) {
  }

  private record Ret(String type, String rawType, String doc, String refillDoc) {
  }

  private record Variant(List<Param> params, boolean refill) {
  }
}
