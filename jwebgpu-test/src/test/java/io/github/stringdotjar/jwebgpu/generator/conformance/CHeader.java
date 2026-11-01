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
package io.github.stringdotjar.jwebgpu.generator.conformance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal scanner for the declarations in a {@code webgpu.h} style header.
 *
 * <p>It is not a C parser. It understands exactly the shapes the header generators emit: exported function
 * prototypes, struct, enum and callback typedefs, bit flag constants, object handle typedefs, constant macros and
 * {@code INIT} macros. That is enough to act as an independent oracle for the generator's model, because it shares no
 * code and no assumptions with the resolver.
 */
final class CHeader {

  private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
  private static final Pattern LINE_COMMENT = Pattern.compile("//[^\n]*");
  private static final Pattern FUNCTION = Pattern.compile(
      "WGPU_EXPORT\\s+([^;()]+?)\\s+(wgpu\\w+)\\(([^)]*)\\)\\s*WGPU_FUNCTION_ATTRIBUTE;");
  private static final Pattern STRUCT = Pattern.compile(
      "typedef struct (\\w+) \\{([^}]*)\\} (\\w+) WGPU_STRUCTURE_ATTRIBUTE;");
  private static final Pattern ENUM = Pattern.compile("typedef enum (\\w+) \\{([^}]*)\\} (\\w+) WGPU_ENUM_ATTRIBUTE;");
  private static final Pattern CALLBACK = Pattern.compile(
      "typedef void \\(\\*((?!WGPUProc)\\w+Callback)\\)\\(([^)]*)\\) WGPU_FUNCTION_ATTRIBUTE;");
  private static final Pattern OBJECT = Pattern.compile("typedef struct (\\w+)Impl\\* (\\w+) WGPU_OBJECT_ATTRIBUTE;");
  private static final Pattern FLAGS_TYPE = Pattern.compile("typedef WGPUFlags (\\w+);");
  private static final Pattern FLAG_VALUE = Pattern.compile(
      "static const (\\w+) (\\w+) = (0x[0-9A-Fa-f]+);");
  private static final Pattern CONSTANT = Pattern.compile("(?m)^#define (WGPU_\\w+) \\((.+)\\)\\s*$");
  private static final Pattern INIT_MACRO = Pattern.compile(
      "#define (WGPU_\\w+_INIT) _wgpu_MAKE_INIT_STRUCT\\((\\w+), \\{ \\\\\\n((?:[^\\n]*\\\\\\n)*?)\\}\\)");
  private static final Pattern INIT_ENTRY = Pattern.compile("/\\*\\.(\\w+)=\\*/(.*?)(?: _wgpu_COMMA)? \\\\");

  final Map<String, Fn> functions = new LinkedHashMap<>();
  final Map<String, Struct> structs = new LinkedHashMap<>();
  final Map<String, EnumDecl> enums = new LinkedHashMap<>();
  final Map<String, Fn> callbacks = new LinkedHashMap<>();
  final Map<String, Long> objects = new LinkedHashMap<>();
  final Map<String, List<FlagValue>> bitflags = new LinkedHashMap<>();
  final Map<String, String> constants = new LinkedHashMap<>();
  final Map<String, List<Init>> inits = new LinkedHashMap<>();

  private CHeader() {}

  /** A parameter, struct field or return value. */
  record Slot(String type, String name, boolean nullable) {

    String key() {
      return type + " " + name;
    }
  }

  /** A function prototype or callback type. */
  record Fn(String name, Slot ret, List<Slot> params) {
  }

  /** A struct. */
  record Struct(String name, List<Slot> fields) {
  }

  /** An enum constant. */
  record EnumConst(String name, long value) {
  }

  /** An enum. */
  record EnumDecl(String name, List<EnumConst> values) {
  }

  /** A bit flag value. */
  record FlagValue(String name, long value) {
  }

  /** One entry of an {@code INIT} macro. */
  record Init(String field, String expr) {
  }

  static CHeader parse(String raw) {
    CHeader h = new CHeader();
    Matcher constants = CONSTANT.matcher(raw);
    while (constants.find()) {
      h.constants.put(constants.group(1), constants.group(2).trim());
    }
    Matcher macros = INIT_MACRO.matcher(raw);
    while (macros.find()) {
      h.inits.put(macros.group(2), parseInit(macros.group(3)));
    }

    String text = BLOCK_COMMENT.matcher(raw).replaceAll(" ");
    text = LINE_COMMENT.matcher(text).replaceAll("");
    text = dropPreprocessor(text);

    Matcher m = FUNCTION.matcher(text);
    while (m.find()) {
      h.functions.put(m.group(2), new Fn(m.group(2), slot(m.group(1) + " ret"), params(m.group(3))));
    }
    m = STRUCT.matcher(text);
    while (m.find()) {
      List<Slot> fields = new ArrayList<>();
      for (String member : m.group(2).split(";")) {
        if (!member.isBlank()) {
          fields.add(slot(member));
        }
      }
      h.structs.put(m.group(3), new Struct(m.group(3), fields));
    }
    m = ENUM.matcher(text);
    while (m.find()) {
      List<EnumConst> values = new ArrayList<>();
      for (String entry : m.group(2).split(",")) {
        if (!entry.isBlank()) {
          String[] parts = entry.split("=");
          values.add(new EnumConst(parts[0].trim(), Long.decode(parts[1].trim())));
        }
      }
      h.enums.put(m.group(3), new EnumDecl(m.group(3), values));
    }
    m = CALLBACK.matcher(text);
    while (m.find()) {
      h.callbacks.put(m.group(1), new Fn(m.group(1), slot("void ret"), params(m.group(2))));
    }
    m = OBJECT.matcher(text);
    while (m.find()) {
      h.objects.put(m.group(2), 0L);
    }
    m = FLAGS_TYPE.matcher(text);
    while (m.find()) {
      h.bitflags.put(m.group(1), new ArrayList<>());
    }
    m = FLAG_VALUE.matcher(text);
    while (m.find()) {
      h.bitflags.get(m.group(1)).add(new FlagValue(m.group(2), Long.parseUnsignedLong(m.group(3).substring(2), 16)));
    }
    return h;
  }

  private static List<Init> parseInit(String body) {
    List<Init> result = new ArrayList<>();
    String prefix = "";
    for (String line : body.split("\\\\\\n")) {
      Matcher e = INIT_ENTRY.matcher(line.stripTrailing() + " \\");
      if (e.find()) {
        String field = e.group(1);
        String expr = e.group(2).trim();
        if (expr.startsWith("_wgpu_MAKE_INIT_STRUCT(") && expr.endsWith("{")) {
          prefix = field + ".";
        } else {
          result.add(new Init(prefix + field, expr));
        }
      } else if (line.trim().startsWith("})")) {
        prefix = "";
      }
    }
    return result;
  }

  private static String dropPreprocessor(String text) {
    StringBuilder sb = new StringBuilder();
    boolean continued = false;
    for (String line : text.split("\n", -1)) {
      boolean directive = continued || line.stripLeading().startsWith("#");
      continued = directive && line.stripTrailing().endsWith("\\");
      if (!directive) {
        sb.append(line).append('\n');
      }
    }
    return sb.toString();
  }

  private static List<Slot> params(String list) {
    List<Slot> result = new ArrayList<>();
    if (list.isBlank() || list.trim().equals("void")) {
      return result;
    }
    for (String p : list.split(",")) {
      result.add(slot(p));
    }
    return result;
  }

  /** Splits "type name" text into a normalized slot. */
  private static Slot slot(String text) {
    String t = text.trim();
    boolean nullable = t.contains("WGPU_NULLABLE");
    t = t.replace("WGPU_NULLABLE", " ").replaceAll("\\bstruct\\s+", "");
    Matcher m = Pattern.compile("^(.*?)(\\w+)\\s*$", Pattern.DOTALL).matcher(t);
    if (!m.matches()) {
      throw new IllegalArgumentException("cannot split declaration: " + text);
    }
    String type = m.group(1).replaceAll("\\s*\\*", " *").replaceAll("\\s+", " ").trim();
    return new Slot(type, m.group(2), nullable);
  }
}
