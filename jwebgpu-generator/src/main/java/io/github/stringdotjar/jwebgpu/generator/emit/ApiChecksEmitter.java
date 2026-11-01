package io.github.stringdotjar.jwebgpu.generator.emit;

import io.github.stringdotjar.jwebgpu.generator.model.BitflagType;
import io.github.stringdotjar.jwebgpu.generator.model.BitflagValue;
import io.github.stringdotjar.jwebgpu.generator.model.CType;
import io.github.stringdotjar.jwebgpu.generator.model.Callback;
import io.github.stringdotjar.jwebgpu.generator.model.CallbackStyle;
import io.github.stringdotjar.jwebgpu.generator.model.Constant;
import io.github.stringdotjar.jwebgpu.generator.model.EnumType;
import io.github.stringdotjar.jwebgpu.generator.model.EnumValue;
import io.github.stringdotjar.jwebgpu.generator.model.InitValue;
import io.github.stringdotjar.jwebgpu.generator.model.Member;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;
import io.github.stringdotjar.jwebgpu.generator.model.StructKind;
import io.github.stringdotjar.jwebgpu.generator.model.TypeKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates {@code GeneratedApiChecks}, plain Java code that checks the generated API against the model.
 *
 * <p>The generated API has hundreds of enums, flag classes, constants and structs, and the checks are the same for
 * each one. Instead of discovering them at run time, this emitter writes the checks out as ordinary statements that
 * read fields and call {@code of(...)} directly. The output goes into the test source set of a module and is called
 * by its tests. A failed check throws an {@link AssertionError} that names what is wrong.
 *
 * <ul>
 *   <li>Enums: every constant has its native number, {@code of(number)} returns the constant, and numbers the enum
 *       does not know map to {@code null}.
 *   <li>Bit flags and constants: every value equals the value in the specification.
 *   <li>Structs: a new struct has the defaults of the C header, the right {@code sType} for extension structs, and
 *       {@code reset()} restores the defaults after every field was changed.
 * </ul>
 */
public final class ApiChecksEmitter {

  private static final String CLASS = "GeneratedApiChecks";

  private final Spec spec;
  private final Types types;

  private ApiChecksEmitter(Spec spec) {
    this.spec = spec;
    this.types = new Types(spec);
  }

  /**
   * Generates the check class.
   *
   * @param spec The resolved specification.
   * @return The file, in the package of the API.
   */
  public static List<GeneratedFile> emit(Spec spec) {
    return List.of(new ApiChecksEmitter(spec).emitClass());
  }

  private GeneratedFile emitClass() {
    SourceWriter w = new SourceWriter(Names.pkg());
    w.doc("Checks of the generated API against the specification. This class is generated.\n\n"
        + "<p>Each public method checks one kind of declaration and throws an {@link AssertionError} on the first "
        + "mismatch.");
    w.openType("public final class " + CLASS);
    w.line("private " + CLASS + "() {}");
    w.blank();

    List<Struct> structs = new ArrayList<>();
    for (Struct s : spec.structs()) {
      if (s.kind() != StructKind.BUILTIN && s.kind() != StructKind.CALLBACK_INFO) {
        structs.add(s);
      }
    }

    w.doc("Checks that every enum has the native numbers and that {@code of} maps them back.");
    w.open("public static void enums()");
    for (EnumType e : spec.enums()) {
      w.line("enum" + Names.type(e.cName()).substring(3) + "();");
    }
    w.close();
    w.blank();
    w.doc("Checks the values of every bit flag class.");
    w.open("public static void bitflags()");
    for (BitflagType b : spec.bitflags()) {
      for (BitflagValue v : b.values()) {
        w.line("check(" + Names.type(b.cName()) + "." + Names.constant(v.specName()) + " == " + v.value() + "L, \""
            + Names.type(b.cName()) + "." + Names.constant(v.specName()) + "\");");
      }
    }
    w.close();
    w.blank();
    w.doc("Checks the value of every constant.");
    w.open("public static void constants()");
    for (Constant c : spec.constants()) {
      String name = "GpuConstants." + Names.constant(c.specName());
      String check = switch (c.kind()) {
        case UINT32 -> name + " == " + (int) c.value();
        case UINT64, USIZE -> name + " == " + c.value() + "L";
        case NAN -> "Float.isNaN(" + name + ")";
      };
      w.line("check(" + check + ", \"" + name + "\");");
    }
    w.close();
    w.blank();
    w.doc("Checks that a new struct has the defaults of the C header.\n\n"
        + "@return The number of structs that were checked.");
    w.open("public static int structDefaults()");
    for (Struct s : structs) {
      w.line("defaults" + suffix(s) + "(new " + Names.type(s.cName()) + "());");
    }
    w.line("return " + structs.size() + ";");
    w.close();
    w.blank();
    w.doc("Checks that {@code reset()} restores the defaults of every struct after all fields were changed.\n\n"
        + "@return The number of structs that were checked.");
    w.open("public static int resetRestoresDefaults()");
    for (Struct s : structs) {
      String api = Names.type(s.cName());
      w.open("");
      w.line(api + " o = new " + api + "();");
      w.line("scramble" + suffix(s) + "(o);");
      w.line("o.reset();");
      w.line("defaults" + suffix(s) + "(o);");
      w.close();
    }
    w.line("return " + structs.size() + ";");
    w.close();
    w.blank();

    for (EnumType e : spec.enums()) {
      emitEnum(w, e);
    }
    for (Struct s : structs) {
      emitDefaults(w, s);
      emitScramble(w, s);
    }
    w.open("private static void check(boolean ok, String what)");
    w.open("if (!ok)");
    w.line("throw new AssertionError(\"Generated API check failed: \" + what);");
    w.close();
    w.close();
    w.close();
    return new GeneratedFile(EnumEmitter.path(CLASS), w.toSource());
  }

  private static String suffix(Struct s) {
    return Names.type(s.cName()).substring(3);
  }

  private void emitEnum(SourceWriter w, EnumType e) {
    String api = Names.type(e.cName());
    w.open("private static void enum" + api.substring(3) + "()");
    w.line("check(" + api + ".values().length == " + e.values().size() + ", \"" + api + " value count\");");
    for (EnumValue v : e.values()) {
      String constant = api + "." + Names.constant(v.specName());
      w.line("check(" + constant + ".value == " + v.value() + " && " + api + ".of(" + v.value() + ") == " + constant
          + ", \"" + constant + "\");");
    }
    w.line("check(" + api + ".of(0x7FFFFFFE) == null, \"" + api + " must not know 0x7FFFFFFE\");");
    w.line("check(" + api + ".of(-12345) == null, \"" + api + " must not know -12345\");");
    w.close();
    w.blank();
  }

  private void emitDefaults(SourceWriter w, Struct s) {
    String api = Names.type(s.cName());
    w.open("private static void defaults" + suffix(s) + "(" + api + " o)");
    if (s.kind() == StructKind.EXTENSION) {
      w.line("check(o.sType() == " + s.sTypeValue() + ", \"" + api + " sType\");");
      w.line("check(o.next == null, \"" + api + ".next\");");
    }
    for (Member m : s.fields()) {
      String where = "\"" + api + "." + m.name() + "\"";
      String f = "o." + Names.ident(m.name());
      switch (m.role()) {
        case CHAIN_HEADER, USERDATA, SELF -> {
        }
        case CHAIN_NEXT -> w.line("check(o.nextInChain == null, " + where + ");");
        case ARRAY_COUNT -> w.line("check(" + f + " == 0, " + where + ");");
        case ARRAY_DATA -> w.line("check(" + f + " != null, " + where + ");");
        case CALLBACK_INFO -> {
          String base = base(m);
          w.line("check(o." + base + "Callback == null, \"" + api + "." + base + "Callback\");");
          if (callbackHasMode(m)) {
            w.line("check(o." + base + "Mode == null, \"" + api + "." + base + "Mode\");");
          }
        }
        case VALUE -> value(w, api, m, f, where);
      }
    }
    w.close();
    w.blank();
  }

  private void value(SourceWriter w, String api, Member m, String f, String where) {
    CType t = m.type();
    if (t.kind() == TypeKind.STRUCT && !t.isPointer() && !t.name().equals(Types.STRING_VIEW)) {
      w.line("check(" + f + " != null, " + where + ");");
      return;
    }
    String type = javaType(m);
    InitValue init = m.init();
    if (init == null) {
      throw new IllegalStateException(api + "." + m.name() + " has no default value");
    }
    String check;
    switch (init.kind()) {
      case NUMBER -> check = f + " == " + number(Long.decode(init.text()), type);
      case FLOAT -> check = f + " == " + floating(init.text(), type);
      case BOOL -> check = f + " == " + (init.text().equals("WGPU_TRUE") ? "true" : "false");
      case ENUM -> {
        check = f + " == " + Names.type(t.name()) + "." + Names.constant(enumValue(t.name(), init.ref()).specName());
      }
      case ENUM_ZERO, NULL_POINTER, STRING_VIEW_INIT, CALLBACK_INFO_INIT ->
        check = f + (type.equals("long") ? " == 0L" : " == null");
      case BITFLAG ->
        check = f + " == " + Names.type(t.name()) + "." + Names.constant(bitflagValue(t.name(), init.ref())
            .specName());
      case CONSTANT -> {
        Constant c = spec.constant(init.ref());
        check = switch (c.kind()) {
          case NAN -> "Float.isNaN(" + f + ")";
          case UINT32 ->
            type.equals("long") ? f + " == " + (c.value() & 0xFFFFFFFFL) + "L" : f + " == " + (int) c.value();
          default -> f + " == " + c.value() + "L";
        };
      }
      case STRUCT_INIT, STRUCT_ZERO -> check = f + " != null";
      default -> throw new IllegalStateException("unhandled default " + init.kind());
    }
    w.line("check(" + check + ", " + where + ");");
  }

  private void emitScramble(SourceWriter w, Struct s) {
    String api = Names.type(s.cName());
    w.open("private static void scramble" + suffix(s) + "(" + api + " o)");
    for (Member m : s.fields()) {
      String f = "o." + Names.ident(m.name());
      switch (m.role()) {
        case ARRAY_COUNT -> w.line(f + " = 12345;");
        case CALLBACK_INFO -> {
          if (callbackHasMode(m)) {
            Callback cb = types.callbackByInfo(m.type().name());
            w.line("o." + base(m) + "Mode = " + lastOf("WGPUCallbackMode") + ";");
            if (cb.style() != CallbackStyle.CALLBACK_MODE) {
              throw new IllegalStateException("unexpected style");
            }
          }
        }
        case VALUE -> scramble(w, m, f);
        default -> {
        }
      }
    }
    w.close();
    w.blank();
  }

  private void scramble(SourceWriter w, Member m, String f) {
    CType t = m.type();
    if (t.isPointer()) {
      if (t.kind() == TypeKind.VOID) {
        w.line(f + " = 12345L;");
      }
      return;
    }
    switch (t.kind()) {
      case BOOL -> w.line(f + " = true;");
      case UINT16, UINT32, INT32 -> w.line(f + " = 12345;");
      case UINT64, USIZE, BITFLAG -> w.line(f + " = 12345L;");
      case FLOAT32 -> w.line(f + " = 12.5f;");
      case FLOAT64 -> w.line(f + " = 12.5;");
      case ENUM -> w.line(f + " = " + lastOf(t.name()) + ";");
      case STRUCT -> {
        if (t.name().equals(Types.STRING_VIEW)) {
          w.line(f + " = \"scrambled\";");
        }
      }
      default -> {
      }
    }
  }

  private String lastOf(String enumCName) {
    List<EnumValue> values = spec.enumType(enumCName).values();
    return Names.type(enumCName) + "." + Names.constant(values.get(values.size() - 1).specName());
  }

  private String javaType(Member m) {
    CType t = m.type();
    if (t.isPointer() && t.kind() == TypeKind.UINT32) {
      return "int[]";
    }
    if (t.kind() == TypeKind.STRUCT && !t.isPointer() && t.name().equals(Types.STRING_VIEW)) {
      return "String";
    }
    return types.java(t);
  }

  private static String base(Member m) {
    return m.name().endsWith("CallbackInfo")
        ? m.name().substring(0, m.name().length() - "CallbackInfo".length())
        : m.name();
  }

  private boolean callbackHasMode(Member m) {
    return types.callbackByInfo(m.type().name()).style() == CallbackStyle.CALLBACK_MODE;
  }

  private EnumValue enumValue(String enumName, String ref) {
    for (EnumValue v : spec.enumType(enumName).values()) {
      if (v.cName().equals(ref)) {
        return v;
      }
    }
    throw new IllegalStateException("unknown enum value " + ref);
  }

  private BitflagValue bitflagValue(String name, String ref) {
    for (BitflagValue v : spec.bitflag(name).values()) {
      if (v.cName().equals(ref)) {
        return v;
      }
    }
    throw new IllegalStateException("unknown bit flag value " + ref);
  }

  private static String number(long value, String type) {
    return switch (type) {
      case "long" -> value + "L";
      case "int" -> "(int) " + value + "L";
      case "short" -> "(short) " + value;
      case "float" -> value + ".0f";
      case "double" -> value + ".0";
      default -> throw new IllegalStateException("number default for " + type);
    };
  }

  private static String floating(String text, String type) {
    String t = text.endsWith("f") || text.endsWith("F") ? text.substring(0, text.length() - 1) : text;
    if (t.endsWith(".")) {
      t += "0";
    }
    return type.equals("float") ? t + "f" : t;
  }
}
