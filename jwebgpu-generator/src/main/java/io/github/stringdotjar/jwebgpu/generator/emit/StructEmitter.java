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

import io.github.stringdotjar.jwebgpu.generator.model.BitflagType;
import io.github.stringdotjar.jwebgpu.generator.model.BitflagValue;
import io.github.stringdotjar.jwebgpu.generator.model.CType;
import io.github.stringdotjar.jwebgpu.generator.model.Callback;
import io.github.stringdotjar.jwebgpu.generator.model.CallbackStyle;
import io.github.stringdotjar.jwebgpu.generator.model.Constant;
import io.github.stringdotjar.jwebgpu.generator.model.ConstantKind;
import io.github.stringdotjar.jwebgpu.generator.model.EnumType;
import io.github.stringdotjar.jwebgpu.generator.model.EnumValue;
import io.github.stringdotjar.jwebgpu.generator.model.InitValue;
import io.github.stringdotjar.jwebgpu.generator.model.Member;
import io.github.stringdotjar.jwebgpu.generator.model.MemberRole;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;
import io.github.stringdotjar.jwebgpu.generator.model.StructKind;
import io.github.stringdotjar.jwebgpu.generator.model.TypeKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes one mutable descriptor class for each C struct.
 *
 * <p>The classes have public fields (backends read them directly, so no getter call and no garbage), a no-argument
 * constructor that applies the defaults of the C header's {@code INIT} macro, and a {@code reset()} method that
 * restores those defaults. Nested structs are final, preallocated fields, and arrays are fixed-capacity arrays with a
 * separate count field.
 */
final class StructEmitter {

  private static final Map<String, Integer> DEFAULT_CAPACITY = Map.of(
      "colorAttachments", 1,
      "targets", 1,
      "buffers", 1,
      "colorFormats", 1,
      "entries", 4,
      "attributes", 4,
      "bindGroupLayouts", 2);

  private final Spec spec;
  private final Docs docs;
  private final Types types;

  StructEmitter(Spec spec, Docs docs, Types types) {
    this.spec = spec;
    this.docs = docs;
    this.types = types;
  }

  GeneratedFile emit(Struct s) {
    String name = Names.type(s.cName());
    boolean extension = s.kind() == StructKind.EXTENSION;
    Plan plan = plan(s);
    SourceWriter w = new SourceWriter(Names.pkg());
    String nullable = "Nullable";
    boolean usesNullable = false;
    for (Field f : plan.fields) {
      usesNullable |= f.nullable;
    }
    if (usesNullable) {
      nullable = w.imp("org.jetbrains.annotations.Nullable");
    }

    w.doc(structDoc(s, name));
    w.openType("public final class " + name + (extension ? " extends GpuChainedStruct" : ""));

    List<Field> ordered = new ArrayList<>(plan.fields);
    ordered.sort((a, b) -> Integer.compare(a.tier, b.tier));
    for (Field f : ordered) {
      w.doc(f.doc);
      String type = f.nullable ? "@" + nullable + " " + f.type : f.type;
      w.line("public " + (f.isFinal ? "final " : "") + type + " " + f.name
          + (f.init == null ? "" : " = " + f.init) + ";");
      w.blank();
    }

    emitConstructors(w, name, plan);
    emitReset(w, s, plan);
    if (extension) {
      emitSType(w, s);
    }
    w.close();
    return new GeneratedFile(EnumEmitter.path(name), w.toSource());
  }

  private String structDoc(Struct s, String name) {
    StringBuilder sb = new StringBuilder();
    sb.append(docs.text(s.doc(), "A reusable " + Names.words(s.cName()) + " struct."));
    switch (s.kind()) {
      case EXTENSION -> {
        sb.append("\n\n<p>This is an extension struct. Attach it to the {@code nextInChain} field of ");
        sb.append(linkList(s.extendsStructs())).append('.');
      }
      case EXTENSIBLE, EXTENSIBLE_CALLBACK_ARG -> {
        if (!s.extensions().isEmpty()) {
          sb.append("\n\n<p>Extend it by chaining ").append(linkList(s.extensions()));
          sb.append(" onto {@link #nextInChain}.");
        }
      }
      default -> {
      }
    }
    sb.append("\n\n<p>Create it once and refill the fields, because a new descriptor allocates. {@link #reset()} ");
    sb.append("restores the defaults from the C header.");
    sb.append("\n\n<p>C type: {@code ").append(s.cName()).append("}.");
    return sb.toString();
  }

  private static String linkList(List<String> cNames) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < cNames.size(); i++) {
      if (i > 0) {
        sb.append(i == cNames.size() - 1 ? " or " : ", ");
      }
      sb.append("{@link ").append(Names.type(cNames.get(i))).append('}');
    }
    return sb.toString();
  }

  private void emitConstructors(SourceWriter w, String name, Plan plan) {
    if (plan.arrays.isEmpty()) {
      w.doc("Creates the struct with the defaults of the C header.");
      w.open("public " + name + "()");
      w.line("reset();");
      w.close();
      w.blank();
      return;
    }
    StringBuilder defaults = new StringBuilder();
    StringBuilder params = new StringBuilder();
    List<String> tags = new ArrayList<>();
    for (int i = 0; i < plan.arrays.size(); i++) {
      ArrayInfo a = plan.arrays.get(i);
      defaults.append(i == 0 ? "" : ", ").append(a.capacity);
      params.append(i == 0 ? "" : ", ").append("int ").append(a.field).append("Capacity");
      tags.add("@param " + a.field + "Capacity How many elements {@link #" + a.field + "} can hold.");
    }
    StringBuilder defaultDoc = new StringBuilder("Creates the struct with the defaults of the C header and the "
        + "default array capacities (");
    for (int i = 0; i < plan.arrays.size(); i++) {
      ArrayInfo a = plan.arrays.get(i);
      defaultDoc.append(i == 0 ? "" : ", ").append(a.capacity).append(" for {@link #").append(a.field).append('}');
    }
    defaultDoc.append(").");
    w.doc(defaultDoc.toString());
    w.open("public " + name + "()");
    w.line("this(" + defaults + ");");
    w.close();
    w.blank();

    w.doc("Creates the struct with the defaults of the C header and arrays of the given capacities.\n\n"
        + "<p>The arrays are allocated once here. Elements that are structs are allocated too, so that filling the "
        + "arrays later allocates nothing.", tags);
    w.open("public " + name + "(" + params + ")");
    for (ArrayInfo a : plan.arrays) {
      w.line(a.field + " = new " + a.elemType + "[" + a.field + "Capacity];");
      if (a.structElem) {
        w.open("for (int i = 0; i < " + a.field + ".length; i++)");
        w.line(a.field + "[i] = new " + a.elemType + "();");
        w.close();
      }
    }
    w.line("reset();");
    w.close();
    w.blank();
  }

  private void emitReset(SourceWriter w, Struct s, Plan plan) {
    boolean nested = !plan.arrays.isEmpty();
    for (Field f : plan.fields) {
      nested |= f.isFinal;
    }
    w.doc("Restores every field to the default of the C header."
        + (nested ? "\n\n<p>Nested structs and array elements are reset too. Arrays keep their capacity, and nothing "
            + "is allocated." : ""));
    w.open("public void reset()");
    for (String r : plan.resets) {
      w.line(r);
    }
    w.close();
    w.blank();
  }

  private void emitSType(SourceWriter w, Struct s) {
    EnumType stype = spec.enumType("WGPUSType");
    String constant = null;
    for (EnumValue v : stype.values()) {
      if (v.cName().equals(s.sType())) {
        if (v.value() != s.sTypeValue()) {
          throw new IllegalStateException("sType value mismatch for " + s.cName());
        }
        constant = Names.constant(v.specName());
      }
    }
    if (constant == null) {
      throw new IllegalStateException("no sType value for " + s.cName());
    }
    w.doc("Returns the tag that tells the native library which extension struct this is.",
        List.of("@return The native {@code WGPUSType} number of this struct."));
    w.line("@Override");
    w.open("public int sType()");
    w.line("return GpuSType." + constant + ".value;");
    w.close();
  }

  private Plan plan(Struct s) {
    Plan plan = new Plan();
    if (s.kind() == StructKind.EXTENSION) {
      plan.resets.add("next = null;");
    }
    for (Member m : s.fields()) {
      switch (m.role()) {
        case CHAIN_HEADER, USERDATA, SELF -> {
        }
        case CHAIN_NEXT -> {
          plan.fields.add(new Field("GpuChainedStruct", "nextInChain", 1, false, null, true,
              "The first extension struct chained onto this one, or {@code null} for none.\n\n"
                  + "<p>Extension structs form a singly linked list through their {@code next} field."));
          plan.resets.add("nextInChain = null;");
        }
        case ARRAY_COUNT -> {
          plan.fields.add(new Field("int", m.name(), 1, false, null, false,
              docs.text(m.doc(), "The number of valid elements in {@link #" + m.peer() + "}.")));
          plan.resets.add(m.name() + " = 0;");
        }
        case ARRAY_DATA -> planArray(plan, s, m);
        case CALLBACK_INFO -> planCallback(plan, m);
        case VALUE -> planValue(plan, m);
      }
    }
    return plan;
  }

  private void planArray(Plan plan, Struct s, Member m) {
    CType element = m.type().withPointer(CType.Pointer.NONE);
    String elemType = types.java(element);
    boolean structElem = element.kind() == TypeKind.STRUCT;
    int capacity = DEFAULT_CAPACITY.getOrDefault(m.name(), 0);
    boolean outStruct = s.cName().startsWith("WGPUSupported") || s.kind() == StructKind.EXTENSIBLE_CALLBACK_ARG
        || s.cName().equals("WGPUSurfaceCapabilities");
    String doc = docs.text(m.doc(), "The " + Names.words(m.name()) + ".")
        + "\n\n<p>Only the first {@link #" + m.peer() + "} elements are used. The capacity is fixed when the struct is "
        + "created, so check it before you fill the array."
        + (outStruct ? " When the native library fills this struct, it replaces the array with a larger one if the "
            + "capacity is too small." : "");
    plan.fields.add(new Field(elemType + "[]", m.name(), 1, false, null, false, doc));
    plan.arrays.add(new ArrayInfo(m.name(), elemType, structElem, capacity));
    if (structElem) {
      plan.resets.add("for (int i = 0; i < " + m.name() + ".length; i++) {");
      plan.resets.add("  " + m.name() + "[i].reset();");
      plan.resets.add("}");
    } else if (element.kind() == TypeKind.OBJECT) {
      plan.resets.add("for (int i = 0; i < " + m.name() + ".length; i++) {");
      plan.resets.add("  " + m.name() + "[i] = null;");
      plan.resets.add("}");
    }
  }

  private void planCallback(Plan plan, Member m) {
    Callback cb = types.callbackByInfo(m.type().name());
    String base = m.name().endsWith("CallbackInfo")
        ? m.name().substring(0, m.name().length() - "CallbackInfo".length())
        : m.name();
    String callbackType = Names.type(cb.cName());
    String callbackName = base + "Callback";
    plan.fields.add(new Field(callbackType, callbackName, 1, false, null, true,
        docs.text(m.doc(), "The " + Names.words(base) + " callback, or {@code null} for none.")
            + "\n\n<p>Set it once; the backend keeps the reference."));
    plan.resets.add(callbackName + " = null;");
    if (cb.style() == CallbackStyle.CALLBACK_MODE) {
      String modeName = base + "Mode";
      plan.fields.add(new Field("GpuCallbackMode", modeName, 1, false, null, true,
          "When {@link #" + callbackName + "} may run.\n\n<p>Set this whenever you set the callback. A {@code null} "
              + "mode is passed to the native library as 0, which is not a valid mode."));
      plan.resets.add(modeName + " = null;");
    }
  }

  private void planValue(Plan plan, Member m) {
    CType t = m.type();
    String fallback = "The " + Names.words(m.name()) + ".";
    String doc = docs.text(m.doc(), fallback);
    InitValue init = m.init();
    String name = Names.ident(m.name());
    if (t.kind() == TypeKind.STRUCT && !t.isPointer() && !t.name().equals(Types.STRING_VIEW)) {
      String type = Names.type(t.name());
      plan.fields.add(new Field(type, name, 1, true, "new " + type + "()", false, doc));
      plan.resets.add(name + ".reset();");
      if (m.init() != null && m.init().kind() == InitValue.Kind.STRUCT_ZERO) {
        zero(plan, name, spec.struct(t.name()));
      }
      return;
    }
    if (t.kind() == TypeKind.STRUCT && t.name().equals(Types.STRING_VIEW)) {
      plan.fields.add(new Field("String", name, 1, false, null, true,
          doc + "\n\n<p>A {@code null} string means \"not set\"."));
      plan.resets.add(name + " = null;");
      return;
    }
    String type;
    boolean nullable = false;
    if (t.isPointer() && t.kind() == TypeKind.STRUCT) {
      type = Names.type(t.name());
      nullable = m.nullable();
    } else if (t.isPointer() && t.kind() == TypeKind.UINT32) {
      type = "int[]";
      nullable = false;
    } else if (t.kind() == TypeKind.ENUM) {
      type = Names.type(t.name());
      nullable = init != null && init.kind() == InitValue.Kind.ENUM_ZERO;
    } else if (t.kind() == TypeKind.OBJECT) {
      type = Names.type(t.name());
      nullable = m.nullable();
    } else {
      type = types.java(t);
    }
    if (type.endsWith("[]")) {
      nullable = false;
    }
    if (t.kind() == TypeKind.ENUM && nullable) {
      doc += "\n\n<p>A {@code null} value is passed to the native library as 0.";
    }
    plan.fields.add(new Field(type, name, tier(type), false, null, nullable && !Types.primitive(type), doc));
    plan.resets.add(name + " = " + initExpr(m, init, type) + ";");
  }

  /**
   * Writes the resets that turn a nested struct into the all-zero struct that the C header's {@code INIT} macro
   * asks for.
   *
   * <p>Most nested structs start with the defaults of their own {@code INIT} macro, but a few members are documented
   * to start as plain zero. The binding layouts of a bind group layout entry are the example: their own defaults say
   * "undefined", which means "use the default kind of binding", while zero means "not used", and an entry must have
   * exactly one used binding layout.
   */
  private void zero(Plan plan, String name, Struct nested) {
    for (Member f : nested.fields()) {
      if (f.role() != MemberRole.VALUE) {
        continue;
      }
      String field = name + "." + Names.ident(f.name());
      CType ft = f.type();
      switch (ft.kind()) {
        case ENUM -> {
          EnumType e = spec.enumType(ft.name());
          String constant = "null";
          for (EnumValue v : e.values()) {
            if (v.value() == 0) {
              constant = Names.type(ft.name()) + "." + Names.constant(v.specName());
            }
          }
          plan.resets.add(field + " = " + constant + ";");
        }
        case BOOL -> plan.resets.add(field + " = false;");
        case UINT64, USIZE, BITFLAG -> plan.resets.add(field + " = 0L;");
        case UINT32, INT32, UINT16, INT16 -> plan.resets.add(field + " = 0;");
        case FLOAT32 -> plan.resets.add(field + " = 0.0f;");
        case FLOAT64 -> plan.resets.add(field + " = 0.0;");
        default -> {
        }
      }
    }
  }

  private static int tier(String type) {
    return switch (type) {
      case "long", "double" -> 0;
      case "short", "char" -> 2;
      case "boolean", "byte" -> 3;
      default -> 1;
    };
  }

  private String initExpr(Member m, InitValue init, String type) {
    if (init == null) {
      return defaultFor(type);
    }
    return switch (init.kind()) {
      case NUMBER -> number(init.text(), type);
      case FLOAT -> floating(init.text(), type);
      case BOOL -> init.text().equals("WGPU_TRUE") ? "true" : "false";
      case ENUM -> enumRef(m.type().name(), init.ref());
      case BITFLAG -> bitflagRef(m.type().name(), init.ref());
      case CONSTANT -> constantRef(init.ref(), type);
      case ENUM_ZERO, NULL_POINTER, STRING_VIEW_INIT, CALLBACK_INFO_INIT, STRUCT_INIT, STRUCT_ZERO ->
        defaultFor(type);
    };
  }

  private static String defaultFor(String type) {
    return switch (type) {
      case "long" -> "0L";
      case "int", "short" -> "0";
      case "float" -> "0.0f";
      case "double" -> "0.0";
      case "boolean" -> "false";
      default -> "null";
    };
  }

  private static String number(String text, String type) {
    return switch (type) {
      case "long" -> text + "L";
      case "float" -> text + "f";
      case "double" -> text + ".0";
      default -> text;
    };
  }

  private static String floating(String text, String type) {
    String t = text.endsWith("f") || text.endsWith("F") ? text.substring(0, text.length() - 1) : text;
    if (t.endsWith(".")) {
      t += "0";
    }
    return type.equals("float") ? t + "f" : t;
  }

  private String enumRef(String typeName, String ref) {
    EnumType e = spec.enumType(typeName);
    for (EnumValue v : e.values()) {
      if (v.cName().equals(ref)) {
        return Names.type(typeName) + "." + Names.constant(v.specName());
      }
    }
    throw new IllegalStateException("unknown enum value " + ref);
  }

  private String bitflagRef(String typeName, String ref) {
    BitflagType b = spec.bitflag(typeName);
    for (BitflagValue v : b.values()) {
      if (v.cName().equals(ref)) {
        return Names.type(typeName) + "." + Names.constant(v.specName());
      }
    }
    throw new IllegalStateException("unknown bit flag value " + ref);
  }

  private String constantRef(String ref, String fieldType) {
    Constant c = spec.constant(ref);
    String expr = "GpuConstants." + Names.constant(c.specName());
    if (c.kind() == ConstantKind.UINT32 && fieldType.equals("long")) {
      return "(" + expr + " & 0xFFFFFFFFL)";
    }
    return expr;
  }

  private record Field(String type, String name, int tier, boolean isFinal, String init, boolean nullable,
      String doc) {
  }

  private record ArrayInfo(String field, String elemType, boolean structElem, int capacity) {
  }

  private static final class Plan {
    private final List<Field> fields = new ArrayList<>();
    private final List<String> resets = new ArrayList<>();
    private final List<ArrayInfo> arrays = new ArrayList<>();
  }
}
