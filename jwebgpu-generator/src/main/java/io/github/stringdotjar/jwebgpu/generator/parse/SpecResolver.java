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
package io.github.stringdotjar.jwebgpu.generator.parse;

import org.jetbrains.annotations.Nullable;

import io.github.stringdotjar.jwebgpu.generator.model.BitflagType;
import io.github.stringdotjar.jwebgpu.generator.model.BitflagValue;
import io.github.stringdotjar.jwebgpu.generator.model.CType;
import io.github.stringdotjar.jwebgpu.generator.model.Callback;
import io.github.stringdotjar.jwebgpu.generator.model.CallbackStyle;
import io.github.stringdotjar.jwebgpu.generator.model.Constant;
import io.github.stringdotjar.jwebgpu.generator.model.ConstantKind;
import io.github.stringdotjar.jwebgpu.generator.model.EnumType;
import io.github.stringdotjar.jwebgpu.generator.model.EnumValue;
import io.github.stringdotjar.jwebgpu.generator.model.FunctionKind;
import io.github.stringdotjar.jwebgpu.generator.model.GpuFunction;
import io.github.stringdotjar.jwebgpu.generator.model.GpuObject;
import io.github.stringdotjar.jwebgpu.generator.model.InitValue;
import io.github.stringdotjar.jwebgpu.generator.model.Member;
import io.github.stringdotjar.jwebgpu.generator.model.MemberRole;
import io.github.stringdotjar.jwebgpu.generator.model.Naming;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;
import io.github.stringdotjar.jwebgpu.generator.model.StructKind;
import io.github.stringdotjar.jwebgpu.generator.model.TypeKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a parsed {@code webgpu.json} into a {@link Spec} that describes the real C ABI of {@code webgpu.h}.
 *
 * <p>{@code webgpu.json} is the machine-readable form of upstream's {@code webgpu.yml}. It is not the header:
 * the header is produced from it by upstream's Go generator, which adds many things the specification file does not
 * spell out. Reading the JSON naively would give a model that disagrees with the compiled library. This class
 * therefore repeats the Go generator's rules (version-pinned in {@code spec/SPEC_VERSION.md}):
 *
 * <ul>
 *   <li><b>Names.</b> Types are {@code WGPU} plus the PascalCase specification name. Functions are {@code wgpu}
 *       plus the object name plus the method name. Parameters and fields are camelCase. Enum values and bit flag
 *       values are the type name, an underscore and the PascalCase entry name.
 *   <li><b>Enums.</b> An entry without an explicit value gets the index of its position (null placeholders keep
 *       their index), combined with the file's {@code enum_prefix} in the high 16 bits. Every enum is 32 bits wide
 *       ({@code _Force32}).
 *   <li><b>Bit flags.</b> They are {@code WGPUFlags}, so 64 bits wide. An entry without an explicit value gets
 *       {@code 1 << (index - 1)}, so the entry at index zero is zero. A {@code value_combination} is the OR of the
 *       named entries.
 *   <li><b>Strings.</b> {@code nullable_string}, {@code string_with_default_empty} and {@code out_string} are all a
 *       {@code WGPUStringView} struct passed by value. {@code bool} is the 32-bit {@code WGPUBool}.
 *   <li><b>Arrays.</b> A member of type {@code array<T>} becomes two C members: a {@code size_t} count named after the
 *       singular of the member (a trailing {@code s} removed, {@code entries} becomes {@code entry}) plus
 *       {@code Count}, and the pointer.
 *   <li><b>Chaining.</b> Extensible structs start with {@code WGPUChainedStruct * nextInChain}. Extension structs
 *       start with an embedded {@code WGPUChainedStruct chain} and carry a {@code WGPUSType_...} value. The structs
 *       an extension extends are recorded on both sides.
 *   <li><b>Callbacks.</b> Each callback gets two C types: the function pointer (the name plus {@code Callback}) whose
 *       parameters end in {@code userdata1} and {@code userdata2}, and an info struct (the name plus
 *       {@code CallbackInfo}) with {@code nextInChain}, {@code mode} (only for the {@code callback_mode} style), the
 *       function pointer and the two userdata pointers. A struct member of callback type is the info struct by value.
 *   <li><b>Async functions.</b> A function with a {@code callback} takes the info struct by value as an extra last
 *       parameter and returns a {@code WGPUFuture} by value.
 *   <li><b>Objects.</b> Every object gets {@code AddRef} and {@code Release} methods after its listed methods.
 *       Every struct marked {@code free_members} gets a {@code FreeMembers} function that takes the struct by
 *       value. Methods and objects are sorted by name. {@code wgpuGetProcAddress} is added as a global function.
 *   <li><b>Defaults.</b> Each struct field gets the default that the header's {@code INIT} macro uses.
 *   <li><b>Struct order.</b> Structs are ordered so each one comes after the structs it contains and after its
 *       extensions, the same way the header orders them.
 * </ul>
 *
 * <p>Features the pinned specification does not use (namespaces, {@code extended} entries, typedefs) are rejected
 * with a {@link SpecException} so an update to the specification cannot silently produce a wrong model.
 */
public final class SpecResolver {

  private static final Pattern ARRAY = Pattern.compile("array<([a-zA-Z0-9._]+)>");
  private static final String STRING_VIEW = "WGPUStringView";
  private static final String CHAINED_STRUCT = "WGPUChainedStruct";

  private final Map<String, JsonValue.Obj> enumsRaw = new LinkedHashMap<>();
  private final Map<String, JsonValue.Obj> bitflagsRaw = new LinkedHashMap<>();
  private final Map<String, JsonValue.Obj> constantsRaw = new LinkedHashMap<>();
  private final Map<String, JsonValue.Obj> structsRaw = new LinkedHashMap<>();
  private final Map<String, JsonValue.Obj> callbacksRaw = new LinkedHashMap<>();
  private final Map<String, JsonValue.Obj> objectsRaw = new LinkedHashMap<>();
  private final Map<String, EnumType> enumsBuilt = new HashMap<>();
  private int enumPrefix;

  private SpecResolver() {}

  private record Owner(JsonValue.Obj raw, boolean isStruct) {
  }

  /**
   * Resolves a parsed specification.
   *
   * @param root The root object of {@code webgpu.json}.
   * @return The resolved specification.
   * @throws SpecException If the specification uses an unsupported feature or is inconsistent.
   * @throws JsonException If a value has an unexpected JSON type.
   */
  public static Spec resolve(JsonValue.Obj root) {
    try {
      return new SpecResolver().run(root);
    } catch (JsonException e) {
      throw new SpecException("malformed specification: " + e.getMessage());
    }
  }

  private Spec run(JsonValue.Obj root) {
    if (!root.arr("typedefs").isEmpty()) {
      throw new SpecException("typedefs are not supported yet");
    }
    long prefix = root.has("enum_prefix") ? root.require("enum_prefix").asLong() : 0;
    if (prefix < 0 || prefix > 0xFFFF) {
      throw new SpecException("enum_prefix out of range: " + prefix);
    }
    enumPrefix = (int) prefix;

    index(root, "constants", constantsRaw);
    index(root, "enums", enumsRaw);
    index(root, "bitflags", bitflagsRaw);
    index(root, "structs", structsRaw);
    index(root, "callbacks", callbacksRaw);
    index(root, "objects", objectsRaw);

    List<Constant> constants = new ArrayList<>();
    for (JsonValue.Obj c : constantsRaw.values()) {
      constants.add(buildConstant(c));
    }
    List<EnumType> enums = new ArrayList<>();
    for (JsonValue.Obj e : enumsRaw.values()) {
      EnumType built = buildEnum(e);
      enumsBuilt.put(e.str("name"), built);
      enums.add(built);
    }
    List<BitflagType> bitflags = new ArrayList<>();
    for (JsonValue.Obj b : bitflagsRaw.values()) {
      bitflags.add(buildBitflag(b));
    }

    List<Callback> callbacks = new ArrayList<>();
    List<Struct> callbackInfos = new ArrayList<>();
    for (JsonValue.Obj c : callbacksRaw.values()) {
      callbacks.add(buildCallback(c));
      callbackInfos.add(buildCallbackInfo(c));
    }

    Map<String, List<String>> extensionsOf = new HashMap<>();
    for (JsonValue.Obj s : structsRaw.values()) {
      if (s.str("type").equals("extension")) {
        for (JsonValue extended : s.arr("extends")) {
          String target = extended.asString();
          if (!structsRaw.containsKey(target)) {
            throw new SpecException("struct " + s.str("name") + " extends unknown struct " + target);
          }
          extensionsOf.computeIfAbsent(target, k -> new ArrayList<>()).add(s.str("name"));
        }
      }
    }
    List<Struct> specStructs = new ArrayList<>();
    for (JsonValue.Obj s : sortStructs(extensionsOf)) {
      specStructs.add(buildStruct(s, extensionsOf));
    }
    List<Struct> structs = new ArrayList<>();
    structs.add(stringViewStruct());
    structs.add(chainedStructStruct());
    structs.addAll(callbackInfos);
    structs.addAll(specStructs);

    List<GpuObject> objects = new ArrayList<>();
    List<GpuFunction> functions = new ArrayList<>();
    buildFunctions(root, objects, functions);

    return new Spec(
        root.str("name", "webgpu"),
        root.str("copyright", "").trim(),
        docOf(root),
        constants,
        enums,
        bitflags,
        callbacks,
        structs,
        objects,
        functions);
  }

  private void index(JsonValue.Obj root, String key, Map<String, JsonValue.Obj> into) {
    for (JsonValue item : root.arr(key)) {
      JsonValue.Obj o = item.asObj();
      String name = o.str("name");
      if (o.has("namespace") || o.bool("extended", false)) {
        throw new SpecException(key + "." + name + " uses 'namespace' or 'extended', which is not supported yet");
      }
      if (into.put(name, o) != null) {
        throw new SpecException("duplicate " + key + " entry '" + name + "'");
      }
    }
  }

  private static String docOf(JsonValue.Obj o) {
    String doc = o.str("doc", "").trim();
    return doc.equals("TODO") ? "" : doc;
  }

  private Constant buildConstant(JsonValue.Obj c) {
    String name = c.str("name");
    JsonValue raw = c.require("value");
    String text = raw instanceof JsonValue.Num n ? n.raw() : raw.asString();
    String cName = "WGPU_" + Naming.constant(name);
    String doc = docOf(c);
    return switch (text) {
      case "uint32_max" -> new Constant(name, cName, ConstantKind.UINT32, 0xFFFFFFFFL, true, doc);
      case "uint64_max" -> new Constant(name, cName, ConstantKind.UINT64, -1L, true, doc);
      case "usize_max" -> new Constant(name, cName, ConstantKind.USIZE, -1L, true, doc);
      case "nan" -> new Constant(name, cName, ConstantKind.NAN, 0, false, doc);
      default -> {
        long v = parseUnsigned(text, "constants." + name);
        yield new Constant(name, cName, (v & ~0xFFFFFFFFL) == 0 ? ConstantKind.UINT32 : ConstantKind.UINT64, v, false,
            doc);
      }
    };
  }

  private static long parseUnsigned(String text, String where) {
    try {
      if (text.startsWith("0x")) {
        return Long.parseUnsignedLong(text.substring(2), 16);
      }
      return Long.parseUnsignedLong(text);
    } catch (NumberFormatException e) {
      throw new SpecException(where + ": cannot parse number '" + text + "'");
    }
  }

  private EnumType buildEnum(JsonValue.Obj e) {
    String name = e.str("name");
    String cName = Naming.typeName(name);
    List<EnumValue> values = new ArrayList<>();
    List<JsonValue> entries = e.arr("entries");
    for (int i = 0; i < entries.size(); i++) {
      if (entries.get(i) instanceof JsonValue.Null) {
        continue;
      }
      JsonValue.Obj entry = entries.get(i).asObj();
      if (entry.has("namespace")) {
        throw new SpecException("enums." + name + ": entry namespaces are not supported yet");
      }
      long v16 = entry.has("value") ? entry.require("value").asLong() : i;
      if (v16 < 0 || v16 > 0xFFFF) {
        throw new SpecException("enums." + name + ": value " + v16 + " does not fit in 16 bits");
      }
      String entryName = entry.str("name");
      values.add(new EnumValue(
          entryName, cName + "_" + Naming.pascal(entryName), (enumPrefix << 16) | (int) v16, docOf(entry)));
    }
    return new EnumType(name, cName, values, docOf(e));
  }

  private BitflagType buildBitflag(JsonValue.Obj b) {
    String name = b.str("name");
    String cName = Naming.typeName(name);
    List<JsonValue> entries = b.arr("entries");
    long[] single = new long[entries.size()];
    List<BitflagValue> values = new ArrayList<>();
    for (int i = 0; i < entries.size(); i++) {
      JsonValue.Obj entry = entries.get(i).asObj();
      String where = "bitflags." + name + "." + entry.str("name");
      if (entry.has("namespace")) {
        throw new SpecException(where + ": namespaces are not supported yet");
      }
      boolean hasCombination = !entry.arr("value_combination").isEmpty();
      if (hasCombination && entry.has("value")) {
        throw new SpecException(where + ": has both 'value' and 'value_combination'");
      }
      long value;
      if (hasCombination) {
        value = 0;
        for (JsonValue part : entry.arr("value_combination")) {
          int at = -1;
          for (int j = 0; j < entries.size(); j++) {
            if (entries.get(j).asObj().str("name").equals(part.asString())) {
              at = j;
              break;
            }
          }
          if (at < 0 || at >= i) {
            throw new SpecException(where + ": combination refers to unknown or later entry '" + part.asString() + "'");
          }
          value |= single[at];
        }
      } else if (entry.has("value")) {
        JsonValue raw = entry.require("value");
        value = parseUnsigned(raw instanceof JsonValue.Num n ? n.raw() : raw.asString(), where);
      } else {
        value = i == 0 ? 0 : 1L << (i - 1);
      }
      single[i] = value;
      String entryName = entry.str("name");
      values.add(
          new BitflagValue(entryName, cName + "_" + Naming.pascal(entryName), value, hasCombination, docOf(entry)));
    }
    return new BitflagType(name, cName, values, docOf(b));
  }

  private CType resolveType(String specType, @Nullable String pointer, String where) {
    CType.Pointer ptr;
    try {
      ptr = CType.Pointer.fromSpec(pointer);
    } catch (IllegalArgumentException e) {
      throw new SpecException(where + ": " + e.getMessage());
    }
    CType base =
        switch (specType) {
          case "bool" -> CType.of(TypeKind.BOOL, "WGPUBool");
          case "nullable_string", "string_with_default_empty", "out_string" ->
            CType.of(TypeKind.STRUCT, STRING_VIEW);
          case "uint16" -> CType.of(TypeKind.UINT16, "uint16_t");
          case "uint32" -> CType.of(TypeKind.UINT32, "uint32_t");
          case "uint64" -> CType.of(TypeKind.UINT64, "uint64_t");
          case "usize" -> CType.of(TypeKind.USIZE, "size_t");
          case "int16" -> CType.of(TypeKind.INT16, "int16_t");
          case "int32" -> CType.of(TypeKind.INT32, "int32_t");
          case "float32", "nullable_float32" -> CType.of(TypeKind.FLOAT32, "float");
          case "float64", "float64_supertype" -> CType.of(TypeKind.FLOAT64, "double");
          case "c_void",
              "c_void_data_ptr",
              "c_void_mapped_range_ptr",
              "c_void_a_native_window",
              "c_void_ca_metal_layer",
              "c_void_h_instance",
              "c_void_h_wnd",
              "c_void_wl_display",
              "c_void_wl_surface",
              "c_void_x11_display",
              "c_void_xcb_connection" ->
            CType.of(TypeKind.VOID, "void");
          default -> resolveReference(specType, where);
        };
    return base.withPointer(ptr);
  }

  private CType resolveReference(String specType, String where) {
    int dot = specType.indexOf('.');
    if (dot < 0) {
      throw new SpecException(where + ": unknown type '" + specType + "'");
    }
    String category = specType.substring(0, dot);
    String name = specType.substring(dot + 1);
    return switch (category) {
      case "enum" -> CType.of(TypeKind.ENUM, Naming.typeName(require(enumsRaw, name, where, specType)));
      case "bitflag" -> CType.of(TypeKind.BITFLAG, Naming.typeName(require(bitflagsRaw, name, where, specType)));
      case "struct" -> CType.of(TypeKind.STRUCT, Naming.typeName(require(structsRaw, name, where, specType)));
      case "object" -> CType.of(TypeKind.OBJECT, Naming.typeName(require(objectsRaw, name, where, specType)));
      default -> throw new SpecException(where + ": unsupported type category in '" + specType + "'");
    };
  }

  private static String require(Map<String, JsonValue.Obj> map, String name, String where, String specType) {
    if (!map.containsKey(name)) {
      throw new SpecException(where + ": type '" + specType + "' does not exist");
    }
    return name;
  }

  private String callbackName(String specType, String where) {
    if (!specType.startsWith("callback.")) {
      throw new SpecException(where + ": expected a callback type but found '" + specType + "'");
    }
    String name = specType.substring("callback.".length());
    return require(callbacksRaw, name, where, specType);
  }

  private static String defaultText(JsonValue v) {
    if (v instanceof JsonValue.Num n) {
      return n.raw();
    }
    if (v instanceof JsonValue.Bool b) {
      return Boolean.toString(b.value());
    }
    return v.asString();
  }

  private List<Member> expand(JsonValue.Obj p, boolean forStruct, String where) {
    String specName = p.str("name");
    String specType = p.str("type");
    String here = where + "." + specName;
    String pointer = p.str("pointer", "");
    boolean optional = p.bool("optional", false);
    String def = p.has("default") ? defaultText(p.require("default")) : null;
    Boolean owned = p.has("passed_with_ownership") ? p.require("passed_with_ownership").asBoolean() : null;
    String doc = docOf(p);
    String name = Naming.camel(specName);

    Matcher array = ARRAY.matcher(specType);
    if (array.matches()) {
      CType element = resolveType(array.group(1), pointer, here);
      if (!element.isPointer()) {
        throw new SpecException(here + ": array members need a 'pointer' kind");
      }
      String countName = Naming.camel(Naming.singular(specName)) + "Count";
      Member count = new Member(
          countName,
          Naming.singular(specName) + "_count",
          CType.of(TypeKind.USIZE, "size_t"),
          MemberRole.ARRAY_COUNT,
          false,
          name,
          null,
          forStruct ? new InitValue(InitValue.Kind.NUMBER, "0", null) : null,
          null,
          "");
      Member data = new Member(
          name,
          specName,
          element,
          MemberRole.ARRAY_DATA,
          optional,
          countName,
          specType,
          forStruct ? new InitValue(InitValue.Kind.NULL_POINTER, "NULL", null) : null,
          owned,
          doc);
      return List.of(count, data);
    }

    if (specType.startsWith("callback.")) {
      String callback = callbackName(specType, here);
      String info = Naming.typeName(callback) + "CallbackInfo";
      InitValue init = forStruct
          ? new InitValue(
              InitValue.Kind.CALLBACK_INFO_INIT, "WGPU_" + Naming.constant(callback) + "_CALLBACK_INFO_INIT", info)
          : null;
      return List.of(new Member(
          name,
          specName,
          CType.of(TypeKind.STRUCT, info),
          MemberRole.CALLBACK_INFO,
          optional,
          null,
          specType,
          init,
          owned,
          doc));
    }

    CType type = resolveType(specType, pointer, here);
    InitValue init = forStruct ? defaultFor(specType, pointer, optional, def, here) : null;
    return List.of(new Member(name, specName, type, MemberRole.VALUE, optional, null, specType, init, owned, doc));
  }

  private InitValue defaultFor(String specType, String pointer, boolean optional, @Nullable String def, String where) {
    if (!pointer.isEmpty()) {
      if (def != null) {
        throw new SpecException(where + ": pointer members cannot have a default");
      }
      return nullPointer();
    }
    if (specType.startsWith("enum.")) {
      String name = specType.substring("enum.".length());
      EnumType type = enumsBuilt.get(name);
      if (def == null) {
        if (type.bySpecName("undefined") != null) {
          String ref = type.cName() + "_Undefined";
          return new InitValue(InitValue.Kind.ENUM, ref, ref);
        }
        return new InitValue(InitValue.Kind.ENUM_ZERO, "_wgpu_ENUM_ZERO_INIT(" + type.cName() + ")", type.cName());
      }
      if (type.bySpecName(def) == null) {
        throw new SpecException(where + ": default '" + def + "' is not an entry of " + specType);
      }
      String ref = type.cName() + "_" + Naming.pascal(def);
      return new InitValue(InitValue.Kind.ENUM, ref, ref);
    }
    if (specType.startsWith("bitflag.")) {
      String name = specType.substring("bitflag.".length());
      String entry = def == null ? "none" : def;
      boolean found = false;
      for (JsonValue v : bitflagsRaw.get(name).arr("entries")) {
        found |= v.asObj().str("name").equals(entry);
      }
      if (!found) {
        throw new SpecException(where + ": bit flag entry '" + entry + "' does not exist in " + specType);
      }
      String ref = Naming.typeName(name) + "_" + Naming.pascal(entry);
      return new InitValue(InitValue.Kind.BITFLAG, ref, ref);
    }
    switch (specType) {
      case "uint16", "uint32", "uint64", "usize", "int32" -> {
        if (def == null) {
          return new InitValue(InitValue.Kind.NUMBER, "0", null);
        }
        return def.startsWith("constant.") ? constantRef(def, where) : new InitValue(InitValue.Kind.NUMBER, def, null);
      }
      case "float32", "nullable_float32" -> {
        if (def == null) {
          return new InitValue(InitValue.Kind.FLOAT, "0.f", null);
        }
        if (def.startsWith("constant.")) {
          return constantRef(def, where);
        }
        return new InitValue(InitValue.Kind.FLOAT, def.contains(".") ? def + "f" : def + ".f", null);
      }
      case "float64", "float64_supertype" -> {
        if (def == null) {
          return new InitValue(InitValue.Kind.FLOAT, "0.", null);
        }
        return def.startsWith("constant.") ? constantRef(def, where) : new InitValue(InitValue.Kind.FLOAT, def, null);
      }
      case "bool" -> {
        if (def == null || def.equals("false")) {
          return new InitValue(InitValue.Kind.BOOL, "WGPU_FALSE", null);
        }
        if (def.equals("true")) {
          return new InitValue(InitValue.Kind.BOOL, "WGPU_TRUE", null);
        }
        if (def.startsWith("constant.")) {
          return constantRef(def, where);
        }
        throw new SpecException(where + ": unsupported bool default '" + def + "'");
      }
      default -> {
      }
    }
    if (specType.startsWith("struct.")) {
      if (optional) {
        return nullPointer();
      }
      if (def == null) {
        String name = specType.substring("struct.".length());
        return new InitValue(
            InitValue.Kind.STRUCT_INIT, "WGPU_" + Naming.constant(name) + "_INIT", Naming.typeName(name));
      }
      if (def.equals("zero")) {
        return new InitValue(InitValue.Kind.STRUCT_ZERO, "_wgpu_STRUCT_ZERO_INIT", null);
      }
      throw new SpecException(where + ": unknown struct default '" + def + "'");
    }
    if (def != null) {
      throw new SpecException(where + ": type '" + specType + "' cannot have a default");
    }
    if (specType.startsWith("object.") || specType.equals("c_void")) {
      return nullPointer();
    }
    return switch (specType) {
      case "out_string", "string_with_default_empty", "nullable_string" ->
        new InitValue(InitValue.Kind.STRING_VIEW_INIT, "WGPU_STRING_VIEW_INIT", null);
      default -> throw new SpecException(where + ": no default rule for type '" + specType + "'");
    };
  }

  private InitValue constantRef(String def, String where) {
    String name = def.substring("constant.".length());
    if (!constantsRaw.containsKey(name)) {
      throw new SpecException(where + ": default refers to unknown constant '" + name + "'");
    }
    String ref = "WGPU_" + Naming.constant(name);
    return new InitValue(InitValue.Kind.CONSTANT, ref, ref);
  }

  private static InitValue nullPointer() {
    return new InitValue(InitValue.Kind.NULL_POINTER, "NULL", null);
  }

  private Callback buildCallback(JsonValue.Obj c) {
    String name = c.str("name");
    String where = "callbacks." + name;
    CallbackStyle style = styleOf(c, where);
    List<Member> params = new ArrayList<>();
    for (JsonValue arg : c.arr("args")) {
      params.addAll(expand(arg.asObj(), false, where));
    }
    params.add(userdata("userdata1"));
    params.add(userdata("userdata2"));
    return new Callback(
        name, Naming.typeName(name) + "Callback", Naming.typeName(name) + "CallbackInfo", style, params, docOf(c));
  }

  private static CallbackStyle styleOf(JsonValue.Obj c, String where) {
    return switch (c.str("style")) {
      case "callback_mode" -> CallbackStyle.CALLBACK_MODE;
      case "immediate" -> CallbackStyle.IMMEDIATE;
      default -> throw new SpecException(where + ": unknown callback style '" + c.str("style") + "'");
    };
  }

  private static Member userdata(String name) {
    return new Member(
        name,
        name,
        CType.of(TypeKind.VOID, "void").withPointer(CType.Pointer.MUTABLE),
        MemberRole.USERDATA,
        true,
        null,
        null,
        nullPointer(),
        null,
        "");
  }

  private Struct buildCallbackInfo(JsonValue.Obj c) {
    String name = c.str("name");
    String where = "callbacks." + name;
    CallbackStyle style = styleOf(c, where);
    List<Member> fields = new ArrayList<>();
    fields.add(nextInChain());
    if (style == CallbackStyle.CALLBACK_MODE) {
      if (!enumsRaw.containsKey("callback_mode")) {
        throw new SpecException(where + ": the callback_mode enum is missing");
      }
      fields.add(new Member(
          "mode",
          "mode",
          CType.of(TypeKind.ENUM, "WGPUCallbackMode"),
          MemberRole.VALUE,
          false,
          null,
          null,
          new InitValue(InitValue.Kind.ENUM_ZERO, "_wgpu_ENUM_ZERO_INIT(WGPUCallbackMode)", "WGPUCallbackMode"),
          null,
          "Controls when the callback may be called. It has no default."));
    }
    fields.add(new Member(
        "callback",
        "callback",
        CType.of(TypeKind.FUNCTION_POINTER, Naming.typeName(name) + "Callback"),
        MemberRole.VALUE,
        false,
        null,
        null,
        nullPointer(),
        null,
        ""));
    fields.add(userdata("userdata1"));
    fields.add(userdata("userdata2"));
    String info = Naming.typeName(name) + "CallbackInfo";
    return new Struct(
        info, info, StructKind.CALLBACK_INFO, fields, List.of(), List.of(), null, 0, false, docOf(c));
  }

  private static Member nextInChain() {
    return new Member(
        "nextInChain",
        "nextInChain",
        CType.of(TypeKind.STRUCT, CHAINED_STRUCT).withPointer(CType.Pointer.MUTABLE),
        MemberRole.CHAIN_NEXT,
        false,
        null,
        null,
        nullPointer(),
        null,
        "");
  }

  private Struct stringViewStruct() {
    List<Member> fields = List.of(
        new Member(
            "data",
            "data",
            CType.of(TypeKind.CHAR, "char").withPointer(CType.Pointer.CONST),
            MemberRole.VALUE,
            true,
            null,
            null,
            null,
            null,
            "The first byte of the UTF-8 text. It is not null-terminated unless length is WGPU_STRLEN."),
        new Member(
            "length",
            "length",
            CType.of(TypeKind.USIZE, "size_t"),
            MemberRole.VALUE,
            false,
            null,
            null,
            null,
            null,
            "The number of bytes, or WGPU_STRLEN (SIZE_MAX) to say that the text is null-terminated."));
    return new Struct(
        STRING_VIEW,
        STRING_VIEW,
        StructKind.BUILTIN,
        fields,
        List.of(),
        List.of(),
        null,
        0,
        false,
        "A pointer and a length that together view a UTF-8 string. A null pointer with the length WGPU_STRLEN is the"
            + " null string; a length of zero is the empty string.");
  }

  private Struct chainedStructStruct() {
    if (!enumsRaw.containsKey("s_type")) {
      throw new SpecException("the s_type enum is missing");
    }
    List<Member> fields = List.of(
        new Member(
            "next",
            "next",
            CType.of(TypeKind.STRUCT, CHAINED_STRUCT).withPointer(CType.Pointer.MUTABLE),
            MemberRole.VALUE,
            false,
            null,
            null,
            null,
            null,
            "The next struct in the chain, or null at the end."),
        new Member(
            "sType",
            "sType",
            CType.of(TypeKind.ENUM, "WGPUSType"),
            MemberRole.VALUE,
            false,
            null,
            null,
            null,
            null,
            "Says which extension struct this header belongs to."));
    return new Struct(
        CHAINED_STRUCT,
        CHAINED_STRUCT,
        StructKind.BUILTIN,
        fields,
        List.of(),
        List.of(),
        null,
        0,
        false,
        "The header of every struct that can be linked into an extensible struct's chain.");
  }

  private List<JsonValue.Obj> sortStructs(Map<String, List<String>> extensionsOf) {
    List<JsonValue.Obj> sorted = new ArrayList<>(structsRaw.values());
    sorted.sort(Comparator.comparing(s -> Naming.pascal(s.str("name"))));
    Map<String, Integer> depth = new HashMap<>();
    List<JsonValue.Obj> result = new ArrayList<>(sorted);
    for (JsonValue.Obj s : sorted) {
      depthOf(s.str("name"), extensionsOf, depth, new ArrayList<>());
    }
    result.sort(Comparator.comparingInt(s -> depth.get(s.str("name"))));
    return result;
  }

  private int depthOf(String name, Map<String, List<String>> extensionsOf, Map<String, Integer> memo,
      List<String> path) {
    Integer known = memo.get(name);
    if (known != null) {
      return known;
    }
    if (path.contains(name)) {
      throw new SpecException("struct " + name + " contains itself through " + path);
    }
    path.add(name);
    JsonValue.Obj s = structsRaw.get(name);
    int max = 0;
    for (JsonValue m : s.arr("members")) {
      String type = m.asObj().str("type");
      Matcher array = ARRAY.matcher(type);
      if (array.matches()) {
        type = array.group(1);
      }
      if (type.startsWith("struct.")) {
        String dep = type.substring("struct.".length());
        if (!structsRaw.containsKey(dep)) {
          throw new SpecException("structs." + name + " uses unknown struct '" + dep + "'");
        }
        max = Math.max(max, depthOf(dep, extensionsOf, memo, path) + 1);
      }
    }
    for (String ext : extensionsOf.getOrDefault(name, List.of())) {
      max = Math.max(max, depthOf(ext, extensionsOf, memo, path) + 1);
    }
    path.remove(path.size() - 1);
    memo.put(name, max);
    return max;
  }

  private Struct buildStruct(JsonValue.Obj s, Map<String, List<String>> extensionsOf) {
    String name = s.str("name");
    String cName = Naming.typeName(name);
    String where = "structs." + name;
    StructKind kind = switch (s.str("type")) {
      case "extensible" -> StructKind.EXTENSIBLE;
      case "extensible_callback_arg" -> StructKind.EXTENSIBLE_CALLBACK_ARG;
      case "extension" -> StructKind.EXTENSION;
      case "standalone" -> StructKind.STANDALONE;
      default -> throw new SpecException(where + ": unknown struct type '" + s.str("type") + "'");
    };
    List<Member> fields = new ArrayList<>();
    if (kind == StructKind.EXTENSIBLE || kind == StructKind.EXTENSIBLE_CALLBACK_ARG) {
      fields.add(nextInChain());
    } else if (kind == StructKind.EXTENSION) {
      fields.add(new Member(
          "chain",
          "chain",
          CType.of(TypeKind.STRUCT, CHAINED_STRUCT),
          MemberRole.CHAIN_HEADER,
          false,
          null,
          null,
          null,
          null,
          ""));
    }
    for (JsonValue m : s.arr("members")) {
      fields.addAll(expand(m.asObj(), true, where));
    }

    List<String> extendsStructs = new ArrayList<>();
    String sType = null;
    long sTypeValue = 0;
    if (kind == StructKind.EXTENSION) {
      for (JsonValue extended : s.arr("extends")) {
        extendsStructs.add(Naming.typeName(extended.asString()));
      }
      sType = "WGPUSType_" + Naming.pascal(name);
      EnumValue entry = null;
      for (EnumValue v : enumsBuilt.get("s_type").values()) {
        if (v.cName().equals(sType)) {
          entry = v;
        }
      }
      if (entry == null) {
        throw new SpecException(where + ": the s_type enum has no entry " + sType);
      }
      sTypeValue = entry.value();
    }
    List<String> extensions = new ArrayList<>();
    for (String ext : extensionsOf.getOrDefault(name, List.of())) {
      extensions.add(Naming.typeName(ext));
    }
    return new Struct(
        name,
        cName,
        kind,
        fields,
        extendsStructs,
        extensions,
        sType,
        sTypeValue,
        s.bool("free_members", false),
        docOf(s));
  }

  private void buildFunctions(JsonValue.Obj root, List<GpuObject> objectsOut, List<GpuFunction> functionsOut) {
    for (JsonValue f : root.arr("functions")) {
      functionsOut.add(buildFunction(f.asObj(), null, null, "functions"));
    }
    functionsOut.add(new GpuFunction(
        "get_proc_address",
        "wgpuGetProcAddress",
        null,
        FunctionKind.GET_PROC_ADDRESS,
        List.of(new Member(
            "procName",
            "proc_name",
            CType.of(TypeKind.STRUCT, STRING_VIEW),
            MemberRole.VALUE,
            false,
            null,
            "string_with_default_empty",
            null,
            null,
            "")),
        CType.of(TypeKind.FUNCTION_POINTER, "WGPUProc"),
        false,
        null,
        null,
        "Returns the procedure address (function pointer) of the named function. Cast the result to the proc"
            + " pointer type of that function.",
        ""));

    List<Owner> all = new ArrayList<>();
    for (JsonValue.Obj o : objectsRaw.values()) {
      all.add(new Owner(o, false));
    }
    for (JsonValue.Obj s : structsRaw.values()) {
      if (s.bool("free_members", false)) {
        all.add(new Owner(s, true));
      }
    }
    all.sort(Comparator.comparing(o -> Naming.pascal(o.raw().str("name")).toUpperCase(Locale.ROOT)));

    for (Owner owner : all) {
      String ownerName = owner.raw().str("name");
      String ownerC = Naming.typeName(ownerName);
      List<GpuFunction> built = new ArrayList<>();
      if (owner.isStruct()) {
        built.add(syntheticMethod(ownerName, ownerC, "free_members", FunctionKind.FREE_MEMBERS, true,
            "Frees members which were allocated by the API."));
      } else {
        List<JsonValue.Obj> methods = new ArrayList<>();
        for (JsonValue m : owner.raw().arr("methods")) {
          methods.add(m.asObj());
        }
        methods.sort(Comparator.comparing(m -> Naming.pascal(m.str("name")).toUpperCase(Locale.ROOT)));
        for (JsonValue.Obj m : methods) {
          built.add(buildFunction(m, ownerName, ownerC, "objects." + ownerName));
        }
        built.add(syntheticMethod(ownerName, ownerC, "add_ref", FunctionKind.ADD_REF, false,
            "Adds a reference to the object. Every call must be balanced by a call to Release."));
        built.add(syntheticMethod(ownerName, ownerC, "release", FunctionKind.RELEASE, false,
            "Removes a reference from the object. The object is destroyed when the last reference is gone."));
        objectsOut.add(new GpuObject(ownerName, ownerC, List.copyOf(built), docOf(owner.raw())));
      }
      functionsOut.addAll(built);
    }
  }

  private GpuFunction syntheticMethod(
      String ownerName, String ownerC, String specName, FunctionKind kind, boolean structOwner, String doc) {
    return new GpuFunction(
        specName,
        "wgpu" + Naming.pascal(ownerName) + Naming.pascal(specName),
        ownerC,
        kind,
        List.of(selfMember(ownerName, ownerC, structOwner)),
        null,
        false,
        null,
        null,
        doc,
        "");
  }

  private static Member selfMember(String ownerName, String ownerC, boolean structOwner) {
    return new Member(
        Naming.camel(ownerName),
        ownerName,
        CType.of(structOwner ? TypeKind.STRUCT : TypeKind.OBJECT, ownerC),
        MemberRole.SELF,
        false,
        null,
        (structOwner ? "struct." : "object.") + ownerName,
        null,
        null,
        "");
  }

  private GpuFunction buildFunction(
      JsonValue.Obj f, @Nullable String ownerName, @Nullable String ownerC, String where) {
    String name = f.str("name");
    String here = where + "." + name;
    List<Member> params = new ArrayList<>();
    if (ownerName != null) {
      params.add(selfMember(ownerName, ownerC, false));
    }
    for (JsonValue arg : f.arr("args")) {
      params.addAll(expand(arg.asObj(), false, here));
    }
    String callback = null;
    CType returns = null;
    boolean returnNullable = false;
    Boolean returnOwned = null;
    String returnDoc = "";
    if (f.has("callback")) {
      String cb = callbackName(f.str("callback"), here);
      callback = Naming.typeName(cb) + "Callback";
      params.add(new Member(
          "callbackInfo",
          "callback_info",
          CType.of(TypeKind.STRUCT, Naming.typeName(cb) + "CallbackInfo"),
          MemberRole.CALLBACK_INFO,
          false,
          null,
          f.str("callback"),
          null,
          null,
          ""));
      if (!structsRaw.containsKey("future")) {
        throw new SpecException(here + ": async functions need the 'future' struct");
      }
      returns = CType.of(TypeKind.STRUCT, "WGPUFuture");
    } else if (f.has("returns")) {
      JsonValue.Obj r = f.obj("returns");
      returns = resolveType(r.str("type"), r.str("pointer", ""), here + ".returns");
      returnNullable = r.bool("optional", false);
      returnOwned = r.has("passed_with_ownership") ? r.require("passed_with_ownership").asBoolean() : null;
      returnDoc = docOf(r);
    }
    String cName =
        ownerName == null ? "wgpu" + Naming.pascal(name) : "wgpu" + Naming.pascal(ownerName) + Naming.pascal(name);
    return new GpuFunction(
        name,
        cName,
        ownerC,
        FunctionKind.NORMAL,
        List.copyOf(params),
        returns,
        returnNullable,
        returnOwned,
        callback,
        docOf(f),
        returnDoc);
  }
}
