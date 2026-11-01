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
import io.github.stringdotjar.jwebgpu.generator.model.GpuObject;
import io.github.stringdotjar.jwebgpu.generator.model.Member;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.TypeKind;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Argument;
import io.github.stringdotjar.jwebgpu.generator.web.WebMap;
import io.github.stringdotjar.jwebgpu.generator.web.WebMap.Binding;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Writes the web implementation of every API object: a class that holds the browser object and implements each method
 * of the abstract API class.
 *
 * <p>How a method is implemented depends on its {@link Binding}:
 *
 * <ul>
 *   <li>{@code METHOD}: one call of the browser's operation. Each argument is converted without allocating: objects
 *       to their browser objects, descriptors to cached dictionaries, enums to interned strings, and 64-bit numbers
 *       to doubles. A size argument that holds {@code WHOLE_SIZE} is left out of the call, because the browser's
 *       optional argument means the same.
 *   <li>{@code GET}: a read of an attribute, converted back to the C types of the API.
 *   <li>{@code LABEL}: a write of the {@code label} attribute.
 *   <li>{@code SPECIAL}: a call to a hand-written method of {@code TeaVmSpecial} (promises, output structs, raw memory,
 *       the instance and the surface). The generated method only prepares raw data.
 *   <li>{@code NONE}: throws an {@link UnsupportedOperationException}, because the browser has no such operation.
 * </ul>
 */
final class TeaVmObjectEmitter {

  private static final Set<String> RESERVED = Set.of("self", "res", "staged", "into", "t", "i");
  private static final String[][] DATA_ARRAYS = { { "byte", "1" }, { "short", "2" }, { "int", "4" }, { "float", "4" } };

  private final Spec spec;
  private final WebIdl idl;
  private final WebMap web;
  private final Types types;

  TeaVmObjectEmitter(Spec spec, WebIdl idl, WebMap web, Types types) {
    this.spec = spec;
    this.idl = idl;
    this.web = web;
    this.types = types;
  }

  GeneratedFile emit(GpuObject o) {
    String api = Names.type(o.cName());
    String name = TeaVmNames.object(o.cName());
    String ifaceName = TeaVmNames.iface(o);
    String jso = TeaVmNames.jso(ifaceName);
    boolean destroyable = idl.findInterface(ifaceName) != null && idl.iface(ifaceName).operation("destroy") != null
        && !o.specName().equals("instance");
    String words = Names.words(o.cName());
    SourceWriter w = new SourceWriter(TeaVmNames.PKG);
    w.imp(Names.pkg() + "." + api);
    w.doc("The web implementation of {@link " + api + "}, wrapping the browser's {@code " + ifaceName + "}.\n\n"
        + "<p>The wrapper stores the JavaScript object. Calls convert their arguments into cached JavaScript objects, so "
        + "they allocate nothing on the Java side. Methods that create objects also come in a refilling variant that "
        + "reuses an existing wrapper.\n\n"
        + "<p>{@link #release()} gives up one reference"
        + (destroyable ? " and calls {@code destroy()} of the browser object when the last reference is gone" : "")
        + ". The browser frees the rest when the JavaScript object is garbage collected.\n\n"
        + "<p>This class is generated from the specification.");
    w.openType("public final class " + name + " extends " + api);
    w.line("private int refs;");
    w.line("private " + jso + " js;");
    if (destroyable) {
      w.line("private boolean external;");
    }
    List<String> slots = new ArrayList<>();
    w.blank();
    w.doc("Creates an empty wrapper that holds no browser object.\n\n"
        + "<p>It reports {@link #isReleased()} until a refilling method stores an object in it.");
    w.line("public " + name + "() {}");
    w.blank();
    w.doc("Wraps a browser object. The wrapper owns one reference, so release it when done.\n\n"
        + "@param js The browser object. It must not be null.");
    w.open("public " + name + "(" + jso + " js)");
    w.line("this.js = js;");
    w.line("this.refs = 1;");
    w.close();
    w.blank();

    List<GpuFunction> normal = new ArrayList<>();
    List<GpuFunction> getters = new ArrayList<>();
    for (GpuFunction f : o.methods()) {
      if (f.kind() == FunctionKind.NORMAL) {
        (MethodWriter.isSimpleGetter(f) ? getters : normal).add(f);
      }
    }
    for (GpuFunction f : normal) {
      emitMethod(w, f, true, slots);
    }
    for (GpuFunction f : getters) {
      emitMethod(w, f, true, slots);
    }

    w.line("@Override");
    w.open("public void release()");
    w.line(jso + " j = js;");
    w.open("if (j != null)");
    w.line("refs--;");
    w.open("if (refs <= 0)");
    w.line("js = null;");
    w.line("refs = 0;");
    if (destroyable) {
      w.open("if (!external)");
      w.line("j.destroy();");
      w.close();
    }
    w.close();
    w.close();
    w.close();
    w.blank();
    w.line("@Override");
    w.open("public void addRef()");
    w.line("live();");
    w.line("refs++;");
    w.close();
    w.blank();
    w.line("@Override");
    w.open("public boolean isReleased()");
    w.line("return js == null;");
    w.close();
    w.blank();

    w.doc("Returns the browser object of a wrapper that was created by this backend.\n\n"
        + "@param o The object. It must not be null or released.\n@return The browser object.\n"
        + "@throws IllegalArgumentException If the object belongs to another backend.\n"
        + "@throws IllegalStateException If the object was released.");
    w.open("static " + jso + " js(" + api + " o)");
    w.open("if (o instanceof " + name + " f)");
    w.line(jso + " j = f.js;");
    w.open("if (j == null)");
    w.line("throw new IllegalStateException(\"The " + api + " was released\");");
    w.close();
    w.line("return j;");
    w.close();
    w.open("if (o == null)");
    w.line("throw new NullPointerException(\"" + words + "\");");
    w.close();
    w.line("throw new IllegalArgumentException(\"The " + api + " was not created by the web backend\");");
    w.close();
    w.blank();
    w.doc("Returns the browser object of a wrapper, or null for null.\n\n@param o The object, or null.\n"
        + "@return The browser object, or null.");
    w.open("static " + jso + " jsOrNull(" + api + " o)");
    w.line("return o == null ? null : js(o);");
    w.close();
    w.blank();
    w.doc("Checks that an object was created by this backend.\n\n@param o The object. It must not be null.\n"
        + "@return The same object, as the backend type.\n"
        + "@throws IllegalArgumentException If the object belongs to another backend.");
    w.open("static " + name + " cast(" + api + " o)");
    w.open("if (o instanceof " + name + " f)");
    w.line("return f;");
    w.close();
    w.open("if (o == null)");
    w.line("throw new NullPointerException(\"into\");");
    w.close();
    w.line("throw new IllegalArgumentException(\"The " + api + " was not created by the web backend\");");
    w.close();
    w.blank();
    w.doc("Stores a new browser object in this wrapper and gives up the one it held before.\n\n"
        + "@param j The new browser object.");
    w.open("void assign(" + jso + " j)");
    w.line("release();");
    w.line("js = j;");
    w.line("refs = 1;");
    if (destroyable) {
      w.line("external = false;");
    }
    w.close();
    w.blank();
    if (destroyable) {
      w.doc("Marks the browser object as owned by someone else, so that releasing the wrapper does not destroy it.\n\n"
          + "<p>A texture that the canvas hands out every frame is one example.\n\n"
          + "@param value Whether the object belongs to someone else.");
      w.open("void setExternal(boolean value)");
      w.line("external = value;");
      w.close();
      w.blank();
    }
    w.open("private " + jso + " live()");
    w.line(jso + " j = js;");
    w.open("if (j == null)");
    w.line("throw new IllegalStateException(\"The " + api + " was released\");");
    w.close();
    w.line("return j;");
    w.close();
    w.blank();
    w.doc("Returns the browser object.\n\n@return The object, or null if the wrapper is empty or released.");
    w.open("public " + jso + " getJs()");
    w.line("return js;");
    w.close();
    for (String slot : slots) {
      if (slot.startsWith("private static") && slot.endsWith(";")) {
        w.blank();
      }
      w.line(slot);
    }
    w.close();
    return new GeneratedFile(TeaVmNames.path(name), w.toSource());
  }

  GeneratedFile emitBackend() {
    SourceWriter w = new SourceWriter(TeaVmNames.PKG);
    w.imp(Names.pkg() + ".GpuBackend");
    w.doc("The web backend: the functions of the WebGPU API that belong to no object.\n\n"
        + "<p>Install it once with {@link JWebGpuTeaVm#install()}. It creates the first instance, and every other "
        + "object comes from that instance.\n\n<p>This class is generated from the specification.");
    w.openType("public final class TeaVmGpuBackend implements GpuBackend");
    w.doc("Creates the backend. Use {@link JWebGpuTeaVm#install()} instead of calling this yourself.");
    w.line("public TeaVmGpuBackend() {}");
    w.blank();
    w.line("@Override");
    w.open("public String name()");
    w.line("return \"teavm\";");
    w.close();
    w.blank();
    List<String> slots = new ArrayList<>();
    for (GpuFunction f : spec.globalFunctions()) {
      if (MethodWriter.isMethod(f)) {
        emitMethod(w, f, false, slots);
      }
    }
    w.close();
    return new GeneratedFile(TeaVmNames.path("TeaVmGpuBackend"), w.toSource());
  }

  private void emitMethod(SourceWriter w, GpuFunction f, boolean hasSelf, List<String> slots) {
    boolean hasData = false;
    for (Member m : f.params()) {
      hasData |= FfmNames.isDataPointer(m);
    }
    List<String[]> datas = new ArrayList<>();
    if (hasData) {
      datas.add(null);
      datas.addAll(List.of(DATA_ARRAYS));
    } else {
      datas.add(new String[0]);
    }
    for (String[] data : datas) {
      emitOne(w, f, hasSelf, data, false, slots);
      if (hasSelf && f.returns() != null && f.returns().kind() == TypeKind.OBJECT && !f.isAsync()) {
        emitOne(w, f, hasSelf, data, true, slots);
      }
    }
  }

  private void emitOne(SourceWriter w, GpuFunction f, boolean hasSelf, String[] data, boolean refill,
      List<String> slots) {
    Binding b = web.bindingFor(f.cName());
    String name = Names.method(f.specName());
    List<String> jp = new ArrayList<>();
    List<String> pre = new ArrayList<>();
    List<String> callArgs = new ArrayList<>();
    List<String> specialArgs = new ArrayList<>();
    List<String> post = new ArrayList<>();
    String[] last = new String[2];
    boolean dataOut = false;
    boolean waitAny = f.cName().equals("wgpuInstanceWaitAny");
    List<Member> ps = f.params();
    int logical = 0;
    for (int i = 0; i < ps.size(); i++) {
      Member m = ps.get(i);
      CType t = m.type();
      if (RESERVED.contains(m.name())) {
        throw new IllegalStateException("parameter name '" + m.name() + "' clashes with a local variable");
      }
      switch (m.role()) {
        case SELF, CHAIN_HEADER, CHAIN_NEXT, USERDATA, ARRAY_COUNT -> {
        }
        case ARRAY_DATA -> {
          CType element = t.withPointer(CType.Pointer.NONE);
          jp.add(arrayType(w, element) + " " + m.name());
          jp.add("int " + m.peer());
          specialArgs.add(m.name());
          specialArgs.add(m.peer());
          if (b.kind() == Binding.Kind.METHOD) {
            if (element.kind() != TypeKind.OBJECT) {
              throw new IllegalStateException("unsupported array of " + element + " in " + f.cName());
            }
            String cls = TeaVmNames.object(element.name());
            pre.add("JSObject " + m.name() + "Js = TeaVmJs.list();");
            pre.add("for (int i = 0; i < " + m.peer() + "; i++) {");
            pre.add("  TeaVmJs.push(" + m.name() + "Js, " + cls + ".js(" + m.name() + "[i]));");
            pre.add("}");
            w.imp("org.teavm.jso.JSObject");
            callArgs.add(m.name() + "Js");
            last[0] = null;
          }
          logical++;
        }
        case CALLBACK_INFO -> {
          Callback cb = types.callbackByInfo(t.name());
          String modeName = "mode";
          for (Member other : ps) {
            if (other.name().equals("mode")) {
              modeName = "callbackMode";
            }
          }
          jp.add(w.imp(Names.pkg() + ".GpuCallbackMode") + " " + modeName);
          jp.add(w.imp(Names.pkg() + "." + Names.type(cb.cName())) + " callback");
          specialArgs.add(modeName);
          specialArgs.add("callback");
        }
        case VALUE -> {
          if (FfmNames.isDataPointer(m)) {
            i++;
            dataOut = t.pointer() == CType.Pointer.MUTABLE;
            String d = m.name();
            if (data == null) {
              jp.add(w.imp("java.nio.ByteBuffer") + " " + d);
              pre.add("JSObject staged = TeaVmData." + (dataOut ? "stageOut" : "stage") + "(" + d + ");");
              if (dataOut) {
                post.add("TeaVmData.unstage(" + d + ");");
              }
            } else {
              jp.add(data[0] + "[] " + d);
              jp.add("int dataOffset");
              jp.add("int dataCount");
              pre.add("JSObject staged = TeaVmData." + (dataOut ? "stageOut" : "stage") + "(" + d + ", dataOffset, "
                  + "dataCount);");
              if (dataOut) {
                post.add("TeaVmData.unstage(" + d + ", dataOffset, dataCount);");
              }
            }
            w.imp("org.teavm.jso.JSObject");
            specialArgs.add("staged");
            logical++;
          } else if (waitAny && m.name().equals("futureCount")) {
            continue;
          } else if (waitAny && m.name().equals("futures")) {
            jp.add(w.imp(Names.pkg() + ".GpuFutureWaitInfo") + "[] futures");
            jp.add("int futureCount");
            specialArgs.add("futures");
            specialArgs.add("futureCount");
          } else {
            valueParam(w, f, b, m, logical, jp, pre, callArgs, specialArgs, last, slots);
            logical++;
          }
        }
        default -> {
        }
      }
    }

    String ret = "void";
    String retApi = null;
    CType r = f.returns();
    if (f.isAsync() || r != null && r.kind() == TypeKind.STRUCT && r.name().equals("WGPUFuture")) {
      ret = "long";
    } else if (r != null) {
      switch (r.kind()) {
        case OBJECT -> {
          retApi = Names.type(r.name());
          ret = w.imp(Names.pkg() + "." + retApi);
        }
        case ENUM -> ret = w.imp(Names.pkg() + "." + Names.type(r.name()));
        case BOOL -> ret = "boolean";
        case VOID -> ret = w.imp("java.nio.ByteBuffer");
        default -> ret = types.java(r);
      }
    }
    if (refill) {
      jp.add(ret + " into");
    }

    w.line("@Override");
    w.open("public " + ret + " " + name + "(" + String.join(", ", jp) + ")");
    String cls = null;
    if (refill) {
      cls = TeaVmNames.object(r.name());
      w.line(cls + " t = " + cls + ".cast(into);");
    }
    switch (b.kind()) {
      case NONE -> {
        w.line("throw TeaVmJs.unsupported(\"" + Names.type(f.owner() == null ? "WGPUBackend" : f.owner())
            + "." + name + " is not available in the browser's WebGPU API.\");");
      }
      case SPECIAL -> {
        for (String line : pre) {
          w.line(line);
        }
        List<String> all = new ArrayList<>();
        if (hasSelf) {
          all.add("this");
        }
        all.addAll(specialArgs);
        if (refill) {
          all.add("t");
        }
        String call = "TeaVmSpecial." + specialName(f) + "(" + String.join(", ", all) + ")";
        if (post.isEmpty()) {
          w.line((ret.equals("void") ? "" : "return ") + call + ";");
        } else {
          w.line((ret.equals("void") ? "" : ret + " res = ") + call + ";");
          post.forEach(w::line);
          if (!ret.equals("void")) {
            w.line("return res;");
          }
        }
      }
      case LABEL -> {
        if (b.attr() != null) {
          w.line("live().setLabel(label);");
        } else {
          w.line("live();");
        }
      }
      case GET -> {
        w.line(jsoOf(f) + " self = live();");
        emitGet(w, f, b, refill, ret, cls);
      }
      case METHOD -> {
        if (hasSelf) {
          w.line(jsoOf(f) + " self = live();");
        }
        for (String line : pre) {
          w.line(line);
        }
        emitCall(w, f, b, callArgs, last, refill, ret, cls);
      }
    }
    w.close();
    w.blank();
  }

  private String jsoOf(GpuFunction f) {
    return TeaVmNames.jso(web.bindingFor(f.cName()).iface());
  }

  private static String specialName(GpuFunction f) {
    String method = Names.method(f.specName());
    if (f.owner() == null) {
      return method;
    }
    String owner = f.owner().substring("WGPU".length());
    return Character.toLowerCase(owner.charAt(0)) + owner.substring(1) + Names.capitalize(method);
  }

  private String arrayType(SourceWriter w, CType element) {
    return switch (element.kind()) {
      case OBJECT, ENUM, STRUCT -> w.imp(Names.pkg() + "." + Names.type(element.name())) + "[]";
      default -> types.java(element) + "[]";
    };
  }

  private void valueParam(SourceWriter w, GpuFunction f, Binding b, Member m, int logical, List<String> jp,
      List<String> pre, List<String> callArgs, List<String> specialArgs, String[] last, List<String> slots) {
    CType t = m.type();
    String n = m.name();
    String javaType;
    String converted;
    Argument arg = b.op() != null && logical < b.op().args().size() ? b.op().args().get(logical) : null;
    boolean convert = b.kind() == Binding.Kind.METHOD;
    String target = arg == null ? "JSObject" : TeaVmNames.javaType(idl, arg.type(), false);
    if (!convert) {
      target = "any";
    }
    switch (t.kind()) {
      case OBJECT -> {
        javaType = w.imp(Names.pkg() + "." + Names.type(t.name()));
        converted = TeaVmNames.object(t.name()) + (m.nullable() ? ".jsOrNull(" : ".js(") + n + ")";
      }
      case STRUCT -> {
        if (t.name().equals(Types.STRING_VIEW)) {
          javaType = "String";
          converted = n;
        } else {
          javaType = w.imp(Names.pkg() + "." + Names.type(t.name()));
          converted = n;
          if (convert) {
            String fill = slot(slots, f, n, t.name()) + "().fill(" + n + ")";
            converted = m.nullable() ? "(" + n + " == null ? TeaVmJs.UNDEFINED : " + fill + ")" : fill;
          }
          w.imp("org.teavm.jso.JSObject");
        }
      }
      case ENUM -> {
        javaType = w.imp(Names.pkg() + "." + Names.type(t.name()));
        converted = "TeaVmEnums." + TeaVmNames.toJs(t.name()) + "(" + n + ")";
      }
      case BOOL -> {
        javaType = "boolean";
        converted = n;
      }
      case UINT16, UINT32, INT32, INT16 -> {
        javaType = types.java(t);
        converted = switch (target) {
          case "double" -> "(double) " + n;
          case "int", "JSObject", "any" -> n;
          default -> throw bad(f, m, target);
        };
      }
      case UINT64, USIZE, BITFLAG -> {
        javaType = types.java(t);
        converted = switch (target) {
          case "double", "any" -> "(double) " + n;
          case "int" -> "(int) " + n;
          default -> throw bad(f, m, target);
        };
        if (b.kind() == Binding.Kind.METHOD && arg != null && arg.optional() && target.equals("double")
            && logical == b.op().args().size() - 1 && t.kind() != TypeKind.BITFLAG) {
          last[0] = n;
          last[1] = converted;
        }
      }
      case FLOAT32, FLOAT64 -> {
        javaType = types.java(t);
        converted = switch (target) {
          case "double", "float", "any" -> n;
          default -> throw bad(f, m, target);
        };
      }
      default -> throw new IllegalStateException("unsupported parameter " + m.name() + " of " + f.cName());
    }
    jp.add(javaType + " " + n);
    specialArgs.add(n);
    callArgs.add(converted);
  }

  /**
   * Registers a cached marshaller for one parameter of one method and returns the name of its accessor.
   *
   * <p>Two parameters of the same struct type in one call (for example the source and the destination of a copy) must
   * not share a marshaller, because the second fill would overwrite the first object. Every parameter of every method
   * gets its own, and the overloads of a method share it.
   */
  private static String slot(List<String> slots, GpuFunction f, String param, String struct) {
    String name = "slot" + Names.capitalize(Names.method(f.specName())) + Names.capitalize(param);
    String type = TeaVmNames.struct(struct);
    String field = "private static " + type + " " + name + ";";
    if (!slots.contains(field)) {
      slots.add(field);
      slots.add("private static " + type + " " + name + "() {");
      slots.add("  if (" + name + " == null) {");
      slots.add("    " + name + " = new " + type + "();");
      slots.add("  }");
      slots.add("  return " + name + ";");
      slots.add("}");
    }
    return name;
  }

  private static IllegalStateException bad(GpuFunction f, Member m, String target) {
    return new IllegalStateException("cannot pass " + m.type() + " " + m.name() + " of " + f.cName()
        + " to the browser as " + target);
  }

  private void emitCall(SourceWriter w, GpuFunction f, Binding b, List<String> callArgs, String[] last,
      boolean refill, String ret, String cls) {
    CType r = f.returns();
    String call = "self." + Names.ident(b.op().name()) + "(" + String.join(", ", callArgs) + ")";
    if (last[0] != null) {
      if (r != null) {
        throw new IllegalStateException("a size sentinel is only supported for methods without a result: "
            + f.cName());
      }
      List<String> shorter = new ArrayList<>(callArgs.subList(0, callArgs.size() - 1));
      String shortCall = "self." + Names.ident(b.op().name()) + "(" + String.join(", ", shorter) + ")";
      w.imp(Names.pkg() + ".GpuConstants");
      w.open("if (" + last[0] + " == GpuConstants.WHOLE_SIZE)");
      w.line(shortCall + ";");
      w.middle("else");
      w.line(call + ";");
      w.close();
      return;
    }
    if (r == null) {
      w.line(call + ";");
      return;
    }
    if (r.kind() != TypeKind.OBJECT) {
      throw new IllegalStateException("unsupported return of " + f.cName());
    }
    String jso = TeaVmNames.javaType(idl, b.op().returns(), true);
    w.line(jso + " res = " + call + ";");
    String wrapper = TeaVmNames.object(r.name());
    if (refill) {
      w.line("t.assign(res);");
      w.line("return t;");
    } else {
      w.line("return new " + wrapper + "(res);");
    }
  }

  private void emitGet(SourceWriter w, GpuFunction f, Binding b, boolean refill, String ret, String cls) {
    CType r = f.returns();
    String getter = "self.get" + TeaVmNames.capitalize(b.attr().name()) + "()";
    String jsType = TeaVmNames.javaType(idl, b.attr().type(), true);
    switch (r.kind()) {
      case OBJECT -> {
        w.line(jsType + " res = " + getter + ";");
        if (refill) {
          w.line("t.assign(res);");
          w.line("return t;");
        } else {
          w.line("return new " + TeaVmNames.object(r.name()) + "(res);");
        }
      }
      case ENUM -> w.line("return TeaVmEnums." + TeaVmNames.fromJs(r.name()) + "(" + getter + ");");
      case BOOL -> w.line("return " + getter + ";");
      case BITFLAG -> w.line("return " + getter + (jsType.equals("int") ? " & 0xFFFFFFFFL" : "") + ";");
      case UINT64, USIZE -> w.line("return (long) " + getter + ";");
      case UINT32, INT32, UINT16, INT16 -> w.line("return " + (jsType.equals("double") ? "(int) " : "") + getter + ";");
      case FLOAT32, FLOAT64 -> w.line("return " + getter + ";");
      default -> throw new IllegalStateException("unsupported getter " + f.cName());
    }
  }
}
