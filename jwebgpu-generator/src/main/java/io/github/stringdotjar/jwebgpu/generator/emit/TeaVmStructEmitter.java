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
import io.github.stringdotjar.jwebgpu.generator.model.Constant;
import io.github.stringdotjar.jwebgpu.generator.model.InitValue;
import io.github.stringdotjar.jwebgpu.generator.model.Member;
import io.github.stringdotjar.jwebgpu.generator.model.MemberRole;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;
import io.github.stringdotjar.jwebgpu.generator.model.StructKind;
import io.github.stringdotjar.jwebgpu.generator.model.TypeKind;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.DictionaryMember;
import io.github.stringdotjar.jwebgpu.generator.web.WebMap;
import io.github.stringdotjar.jwebgpu.generator.web.WebMap.FieldMapping;
import io.github.stringdotjar.jwebgpu.generator.web.WebMap.StructMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Writes the marshallers that fill the browser's dictionaries from the API's descriptors.
 *
 * <p>The browser takes a descriptor as a plain JavaScript object. A marshaller owns one such object, created once, and
 * overwrites every property from the Java descriptor on each call, so a call allocates nothing on the Java side:
 *
 * <ul>
 *   <li>A nested struct has a marshaller of its own, owned by the parent marshaller, so each nesting slot has its own
 *       cached object.
 *   <li>An array is a cached JavaScript array whose length is set on each call. Its struct elements have one cached
 *       marshaller per index, created the first time the index is used.
 *   <li>A string is converted once and reused as long as the Java string is the same object.
 *   <li>A property that holds the "undefined" sentinel of the C header is set to JavaScript {@code undefined}, so the
 *       browser applies its default.
 *   <li>The members of extension structs are written into the same object, because the browser has no chains.
 * </ul>
 */
final class TeaVmStructEmitter {

  private static final Set<String> HAND_WRITTEN = Set.of("bind_group_entry", "bind_group_layout_entry");

  /** Conditions that mark an element of a browser array as a hole ({@code null}), by {@code struct.field}. */
  private static final Map<String, String> HOLES = Map.of(
      "render_pass_descriptor.colorAttachments", "e.view == null",
      "fragment_state.targets", "e.format == GpuTextureFormat.UNDEFINED");

  private final Spec spec;
  private final WebIdl idl;
  private final WebMap web;
  private final Set<String> props = new TreeSet<>();

  TeaVmStructEmitter(Spec spec, WebIdl idl, WebMap web) {
    this.spec = spec;
    this.idl = idl;
    this.web = web;
  }

  /** The names of the property setters that the marshallers use, as {@code kind:name}. */
  Set<String> props() {
    return props;
  }

  List<GeneratedFile> emitAll() {
    List<GeneratedFile> out = new ArrayList<>();
    for (StructMapping sm : web.structs()) {
      if (!HAND_WRITTEN.contains(sm.c().specName())) {
        out.add(emit(sm));
      }
    }
    return out;
  }

  GeneratedFile emit(StructMapping sm) {
    Struct s = sm.c();
    String name = TeaVmNames.struct(s.cName());
    String api = Names.type(s.cName());
    SourceWriter w = new SourceWriter(TeaVmNames.PKG);
    w.imp("org.teavm.jso.JSObject");
    w.imp(Names.pkg() + "." + api);

    List<String> decls = new ArrayList<>();
    List<String> body = new ArrayList<>();
    for (Member m : s.fields()) {
      if (m.role() != MemberRole.VALUE && m.role() != MemberRole.ARRAY_DATA) {
        continue;
      }
      FieldMapping fm = sm.field(m.name());
      if (fm == null || fm.js() == null && !fm.flatten()) {
        continue;
      }
      field(w, s, m, fm, "t", "d", decls, body);
    }
    chain(w, sm, decls, body);

    w.doc("Fills the browser's {@code " + sm.idlName() + "} from a {@link " + api + "}.\n\n"
        + "<p>This class is generated from the specification.");
    w.openType("final class " + name);
    w.line("private static " + name + " shared;");
    w.blank();
    for (String d : decls) {
      w.line(d);
    }
    w.line("private final JSObject obj = TeaVmJs.object();");
    w.blank();
    w.doc("Returns the instance that top-level calls share.\n\n@return The shared marshaller.");
    w.open("static " + name + " shared()");
    w.open("if (shared == null)");
    w.line("shared = new " + name + "();");
    w.close();
    w.line("return shared;");
    w.close();
    w.blank();
    w.doc("Returns the cached JavaScript object.\n\n@return The object that {@link #fill} overwrites.");
    w.open("JSObject obj()");
    w.line("return obj;");
    w.close();
    w.blank();
    w.doc("Overwrites the cached object from a descriptor.\n\n@param d The descriptor.\n"
        + "@return The cached object, ready to pass to the browser.");
    w.open("JSObject fill(" + api + " d)");
    w.line("write(obj, d);");
    w.line("return obj;");
    w.close();
    w.blank();
    w.doc("Writes every property of a descriptor into a JavaScript object.\n\n@param t The object to write into.\n"
        + "@param d The descriptor.");
    w.open("void write(JSObject t, " + api + " d)");
    for (String line : body) {
      w.line(line);
    }
    w.close();
    w.close();
    return new GeneratedFile(TeaVmNames.path(name), w.toSource());
  }

  private void field(SourceWriter w, Struct s, Member m, FieldMapping fm, String t, String d, List<String> decls,
      List<String> body) {
    String n = m.name();
    String src = d + "." + Names.ident(n);
    CType type = m.type();
    String js = fm.js();
    if (m.role() == MemberRole.ARRAY_DATA) {
      array(w, s, m, js, t, d, decls, body);
      return;
    }
    if (n.equals("requiredLimits") && s.specName().equals("device_descriptor")) {
      body.add("TeaVmStructs.limits(" + t + ", " + src + ");");
      return;
    }
    if (n.equals("layout") && s.specName().endsWith("pipeline_descriptor")) {
      body.add("TeaVmStructs.pipelineLayout(" + t + ", " + src + ");");
      return;
    }
    switch (type.kind()) {
      case STRUCT -> {
        if (type.name().equals("WGPUTextureComponentSwizzle")) {
          body.add("TeaVmStructs.swizzle(" + t + ", " + src + ");");
        } else if (type.name().equals(Types.STRING_VIEW)) {
          decls.add("private String " + n + "Key;");
          decls.add("private JSObject " + n + "Js;");
          body.add("if (" + src + " != " + n + "Key) {");
          body.add("  " + n + "Key = " + src + ";");
          body.add("  " + n + "Js = " + src + " == null ? null : TeaVmJs.string(" + src + ");");
          body.add("}");
          body.add(put("Obj", js, t, n + "Js"));
        } else {
          String child = w.imp(TeaVmNames.PKG + "." + TeaVmNames.struct(type.name()));
          if (type.isPointer()) {
            decls.add("private " + child + " " + n + ";");
            body.add("if (" + src + " == null) {");
            body.add("  " + clear(js, t));
            body.add("} else {");
            body.add("  if (" + n + " == null) {");
            body.add("    " + n + " = new " + child + "();");
            body.add("  }");
            body.add("  " + put("Obj", js, t, n + ".fill(" + src + ")"));
            body.add("}");
          } else {
            decls.add("private final " + child + " " + n + " = new " + child + "();");
            if (fm.flatten()) {
              body.add(n + ".write(" + t + ", " + src + ");");
            } else {
              body.add(put("Obj", js, t, n + ".fill(" + src + ")"));
            }
          }
        }
      }
      case ENUM -> {
        if (type.name().equals("WGPUOptionalBool")) {
          body.add(put("Obj", js, t, "TeaVmJs.optionalBool(" + src + ")"));
        } else if (web.enumFor(type.name()) != null) {
          body.add(put("Obj", js, t, "TeaVmEnums." + TeaVmNames.toJs(type.name()) + "(" + src + ")"));
        }
      }
      case OBJECT -> body.add(put("Obj", js, t, TeaVmNames.object(type.name()) + ".jsOrNull(" + src + ")"));
      case BOOL -> body.add(put("Bool", js, t, src));
      case UINT32 -> sentinel(w, m, src, "Uint", js, t, src, body);
      case INT32, UINT16, INT16 -> body.add(put("Int", js, t, src));
      case UINT64, USIZE -> sentinel(w, m, src, "Num", js, t, "(double) " + src, body);
      case BITFLAG -> body.add(put("Uint", js, t, "(int) " + src));
      case FLOAT32, FLOAT64 -> sentinel(w, m, src, "Num", js, t, src, body);
      default -> {
      }
    }
  }

  private void sentinel(SourceWriter w, Member m, String src, String kind, String js, String t, String value,
      List<String> body) {
    InitValue init = m.init();
    if (init != null && init.kind() == InitValue.Kind.CONSTANT) {
      Constant c = spec.constant(init.ref());
      w.imp(Names.pkg() + ".GpuConstants");
      String cond = switch (c.kind()) {
        case NAN -> "Float.isNaN(" + src + ")";
        default -> src + " == GpuConstants." + Names.constant(c.specName());
      };
      body.add("if (" + cond + ") {");
      body.add("  " + clear(js, t));
      body.add("} else {");
      body.add("  " + put(kind, js, t, value));
      body.add("}");
      return;
    }
    body.add(put(kind, js, t, value));
  }

  private void array(SourceWriter w, Struct s, Member m, String js, String t, String d, List<String> decls,
      List<String> body) {
    CType element = m.type().withPointer(CType.Pointer.NONE);
    String n = m.name();
    String count = d + "." + m.peer();
    if (n.equals("constants") && element.name().equals("WGPUConstantEntry")) {
      body.add("TeaVmStructs.constants(" + t + ", " + d + "." + n + ", " + count + ");");
      return;
    }
    decls.add("private final JSObject " + n + "List = TeaVmJs.array();");
    body.add("int " + n + "Count = " + count + ";");
    body.add("TeaVmJs.setLength(" + n + "List, " + n + "Count);");
    String loop = "for (int i = 0; i < " + n + "Count; i++) {";
    String item = d + "." + Names.ident(n) + "[i]";
    switch (element.kind()) {
      case STRUCT -> {
        String child = w.imp(TeaVmNames.PKG + "." + TeaVmNames.struct(element.name()));
        w.imp("java.util.Arrays");
        decls.add("private " + child + "[] " + n + "Kids = new " + child + "[0];");
        body.add("if (" + n + "Kids.length < " + n + "Count) {");
        body.add("  int " + n + "Size = Math.max(" + n + "Count, " + n + "Kids.length * 2);");
        body.add("  " + n + "Kids = Arrays.copyOf(" + n + "Kids, " + n + "Size);");
        body.add("}");
        body.add(loop);
        body.add("  " + child + " k = " + n + "Kids[i];");
        body.add("  if (k == null) {");
        body.add("    k = new " + child + "();");
        body.add("    " + n + "Kids[i] = k;");
        body.add("  }");
        String hole = HOLES.get(s.specName() + "." + n);
        if (hole != null) {
          body.add("  " + w.imp(Names.pkg() + "." + Names.type(element.name())) + " e = " + item + ";");
          body.add("  TeaVmJs.setAt(" + n + "List, i, " + hole + " ? null : k.fill(e));");
          if (hole.contains("GpuTextureFormat")) {
            w.imp(Names.pkg() + ".GpuTextureFormat");
          }
        } else {
          body.add("  TeaVmJs.setAt(" + n + "List, i, k.fill(" + item + "));");
        }
      }
      case ENUM -> {
        body.add(loop);
        body.add("  TeaVmJs.setAt(" + n + "List, i, TeaVmEnums." + TeaVmNames.toJs(element.name()) + "("
            + item + "));");
      }
      case OBJECT -> {
        body.add(loop);
        body.add("  TeaVmJs.setAt(" + n + "List, i, " + TeaVmNames.object(element.name()) + ".jsOrNull("
            + item + "));");
      }
      case UINT32 -> {
        body.add(loop);
        body.add("  TeaVmJs.setAtUint(" + n + "List, i, " + item + ");");
      }
      default -> throw new IllegalStateException("unsupported array of " + element + " in " + s.specName());
    }
    body.add("}");
    body.add(put("Obj", js, t, n + "List"));
  }

  private void chain(SourceWriter w, StructMapping sm, List<String> decls, List<String> body) {
    Struct s = sm.c();
    boolean hasChain = false;
    for (Member m : s.fields()) {
      hasChain |= m.role() == MemberRole.CHAIN_NEXT;
    }
    if (!hasChain) {
      return;
    }
    List<DictionaryMember> members = idl.allMembers(sm.idlName());
    List<Struct> supported = new ArrayList<>();
    for (Struct e : spec.structs()) {
      if (e.kind() == StructKind.EXTENSION && e.extendsStructs().contains(s.cName()) && supports(e, members)) {
        supported.add(e);
      }
    }
    String unsupported = "throw TeaVmJs.unsupported(\"An extension struct is chained onto " + Names.type(s.cName())
        + " that the browser has no equivalent for.\");";
    if (supported.isEmpty()) {
      body.add("if (d.nextInChain != null) {");
      body.add("  " + unsupported);
      body.add("}");
      return;
    }
    body.add("GpuChainedStruct c = d.nextInChain;");
    w.imp(Names.pkg() + ".GpuChainedStruct");
    body.add("while (c != null) {");
    boolean first = true;
    for (Struct e : supported) {
      String api = w.imp(Names.pkg() + "." + Names.type(e.cName()));
      body.add("  " + (first ? "" : "} else ") + "if (c instanceof " + api + " e) {");
      first = false;
      for (Member m : e.fields()) {
        if (m.role() != MemberRole.VALUE) {
          continue;
        }
        List<String> inner = new ArrayList<>();
        FieldMapping fm = new FieldMapping(m, m.name(), false);
        field(w, e, m, fm, "t", "e", decls, inner);
        for (String line : inner) {
          body.add("    " + line);
        }
      }
    }
    body.add("  " + (first ? "" : "} else ") + "{");
    body.add("    " + unsupported);
    body.add("  }");
    body.add("  c = c.next;");
    body.add("}");
  }

  private boolean supports(Struct e, List<DictionaryMember> members) {
    int fields = 0;
    for (Member m : e.fields()) {
      if (m.role() == MemberRole.CHAIN_HEADER) {
        continue;
      }
      CType t = m.type();
      boolean pointer = t.isPointer() && !(t.kind() == TypeKind.STRUCT && t.name().equals(Types.STRING_VIEW));
      boolean found = false;
      for (DictionaryMember dm : members) {
        found |= dm.name().equals(m.name());
      }
      if (pointer || !found) {
        return false;
      }
      fields++;
    }
    return fields > 0;
  }

  private String put(String kind, String js, String t, String value) {
    props.add(kind + ":" + js);
    return "TeaVmProps.put" + kind + TeaVmNames.capitalize(js) + "(" + t + ", " + value + ");";
  }

  private String clear(String js, String t) {
    props.add("Clear:" + js);
    return "TeaVmProps.clear" + TeaVmNames.capitalize(js) + "(" + t + ");";
  }

  GeneratedFile emitProps() {
    SourceWriter w = new SourceWriter(TeaVmNames.PKG);
    w.imp("org.teavm.jso.JSBody");
    w.imp("org.teavm.jso.JSObject");
    w.doc("Property setters for the dictionaries that the marshallers fill.\n\n"
        + "<p>Each method is one JavaScript statement. Writing a property this way needs no property name string at "
        + "run time, so a call allocates nothing. A method that takes an object turns a Java {@code null} into "
        + "JavaScript {@code undefined}, which the browser treats as a missing member.\n\n"
        + "<p>This class is generated from the specification.");
    w.openType("final class TeaVmProps");
    w.line("private TeaVmProps() {}");
    w.blank();
    for (String p : props) {
      String kind = p.substring(0, p.indexOf(':'));
      String js = p.substring(p.indexOf(':') + 1);
      String cap = TeaVmNames.capitalize(js);
      String param;
      String script;
      switch (kind) {
        case "Obj" -> {
          param = "JSObject v";
          script = "o." + js + " = v == null ? undefined : v;";
        }
        case "Bool" -> {
          param = "boolean v";
          script = "o." + js + " = v;";
        }
        case "Int" -> {
          param = "int v";
          script = "o." + js + " = v;";
        }
        case "Uint" -> {
          param = "int v";
          script = "o." + js + " = v >>> 0;";
        }
        case "Num" -> {
          param = "double v";
          script = "o." + js + " = v;";
        }
        case "Clear" -> {
          param = null;
          script = "o." + js + " = undefined;";
        }
        default -> throw new IllegalStateException(kind);
      }
      if (param == null) {
        w.line("@JSBody(params = \"o\", script = \"" + script + "\")");
        w.line("static native void clear" + cap + "(JSObject o);");
      } else {
        w.line("@JSBody(params = { \"o\", \"v\" }, script = \"" + script + "\")");
        w.line("static native void put" + kind + cap + "(JSObject o, " + param + ");");
      }
      w.blank();
    }
    w.close();
    return new GeneratedFile(TeaVmNames.path("TeaVmProps"), w.toSource());
  }
}
