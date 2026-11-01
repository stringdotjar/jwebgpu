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
package io.github.stringdotjar.jwebgpu.generator.web;

import io.github.stringdotjar.jwebgpu.generator.model.BitflagType;
import io.github.stringdotjar.jwebgpu.generator.model.BitflagValue;
import io.github.stringdotjar.jwebgpu.generator.model.CType;
import io.github.stringdotjar.jwebgpu.generator.model.EnumType;
import io.github.stringdotjar.jwebgpu.generator.model.EnumValue;
import io.github.stringdotjar.jwebgpu.generator.model.FunctionKind;
import io.github.stringdotjar.jwebgpu.generator.model.GpuFunction;
import io.github.stringdotjar.jwebgpu.generator.model.GpuObject;
import io.github.stringdotjar.jwebgpu.generator.model.Member;
import io.github.stringdotjar.jwebgpu.generator.model.MemberRole;
import io.github.stringdotjar.jwebgpu.generator.model.Naming;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;
import io.github.stringdotjar.jwebgpu.generator.model.StructKind;
import io.github.stringdotjar.jwebgpu.generator.model.TypeKind;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Attribute;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.DictionaryMember;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Enumeration;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Interface;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Operation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Relates every C enum, bit flag, struct and function to its counterpart in the browser's WebIDL.
 *
 * <p>The two specifications describe the same API in different words, so the web backend is generated from both:
 * the C model says what the shared Java API looks like, and this map says how each piece is spelled in the browser.
 * The rules are:
 *
 * <ul>
 *   <li><b>Enums.</b> A C enum maps to the IDL enum named {@code GPU} plus its name (with a few aliases). A C value
 *       maps to the IDL string that is equal after removing underscores and hyphens and ignoring case, so
 *       {@code BGRA8_unorm_srgb} and {@code "bgra8unorm-srgb"} match. The value called {@code undefined} (and a few
 *       other "use the default" values) is {@link Support#OMIT}: the property is left out so the browser applies its
 *       default. A C value without a string is {@link Support#NONE}: it exists only in the native API, and using it
 *       on the web throws an {@link UnsupportedOperationException}.
 *   <li><b>Bit flags.</b> A C bit flag type maps to the IDL namespace of the same name. The numbers are identical in
 *       both specifications, which {@link #problems()} checks.
 *   <li><b>Structs.</b> A C struct maps to the IDL dictionary of the same name (plus {@code Dict} for the small
 *       value types). Members are matched by their camel-case name. The members of extension structs are flattened
 *       into the dictionary of the struct they extend, because the browser has no chains.
 *   <li><b>Functions.</b> A C method maps to an IDL operation of the same name, to an attribute for getters, or to the
 *       {@code label} attribute for {@code setLabel}. Functions whose shape differs (promises, output structs, raw
 *       memory, the instance and the surface) are marked {@link Binding.Kind#SPECIAL} and implemented by hand.
 * </ul>
 *
 * <p>{@link #problems()} lists everything that does not fit these rules and is not on the documented lists of
 * known differences, so a new specification version cannot silently drift.
 */
public final class WebMap {

  private static final Map<String, String> ENUM_ALIASES = Map.of(
      "composite_alpha_mode", "GPUCanvasAlphaMode",
      "texture_binding_view_dimension", "GPUTextureViewDimension");

  private static final Map<String, Set<String>> ENUM_OMIT = Map.of(
      "composite_alpha_mode", Set.of("auto"));

  private static final Map<String, List<String>> SYNTHETIC_ENUMS = Map.of(
      "feature_level", List.of("compatibility", "core"),
      "predefined_color_space", List.of("srgb", "display-p3"),
      "WGSL_language_feature_name", List.of(
          "readonly_and_readwrite_storage_textures", "packed_4x8_integer_dot_product",
          "unrestricted_pointer_parameters", "pointer_composite_access", "uniform_buffer_standard_layout",
          "subgroup_id", "texture_and_sampler_let", "subgroup_uniformity", "texture_formats_tier1",
          "linear_indexing", "immediate_address_space", "buffer_view", "swizzle_assignment", "fragment_depth"));

  private static final Map<String, String> FLAG_ALIASES = Map.of("color_write_mask", "GPUColorWrite");

  private static final Map<String, String> STRUCT_ALIASES = Map.ofEntries(
      Map.entry("surface_configuration", "GPUCanvasConfiguration"),
      Map.entry("compute_state", "GPUProgrammableStage"),
      Map.entry("pass_timestamp_writes", "GPURenderPassTimestampWrites"));

  /**
   * The C structs that have no dictionary of their own in the browser, with the reason. Some are only read from the
   * browser (they describe results), some are handled by hand, and some belong to native windows.
   */
  private static final Set<String> NO_DICTIONARY = Set.of(
      "adapter_info", "limits", "supported_features", "supported_instance_features",
      "supported_WGSL_language_features", "surface_capabilities", "surface_texture", "compilation_message",
      "compilation_info", "instance_descriptor", "instance_limits", "future", "future_wait_info", "constant_entry",
      "surface_descriptor", "texture_component_swizzle");

  /**
   * C members with no IDL counterpart, as {@code struct.member}. The comments say why.
   */
  private static final Set<String> C_ONLY_MEMBERS = Set.of(
      // The browser sizes the canvas texture from the canvas element, and has no present mode.
      "surface_configuration.width", "surface_configuration.height", "surface_configuration.presentMode",
      // Dawn extension for binding arrays; the browser has none.
      "bind_group_layout_entry.bindingArraySize",
      // The browser picks the adapter, and a canvas does not take part in that choice.
      "request_adapter_options.backendType", "request_adapter_options.compatibleSurface",
      // The browser takes one "resource" that is a buffer range, a sampler or a texture view.
      "bind_group_entry.buffer", "bind_group_entry.offset", "bind_group_entry.size", "bind_group_entry.sampler",
      "bind_group_entry.textureView");

  /**
   * IDL members with no C counterpart, as {@code dictionary.member}. The comments say why.
   */
  private static final Set<String> IDL_ONLY_MEMBERS = Set.of(
      // Hints for the shader compiler; webgpu.h has no equivalent.
      "GPUShaderModuleDescriptor.compilationHints",
      // Come from the surface color management extension and the swizzle extension.
      "GPUCanvasConfiguration.colorSpace", "GPUCanvasConfiguration.toneMapping",
      "GPUTextureViewDescriptor.swizzle",
      // Replace the separate buffer, sampler and texture view members of the C bind group entry.
      "GPUBindGroupEntry.resource",
      // The C extension struct that marks an external texture binding layout has no fields.
      "GPUBindGroupLayoutEntry.externalTexture");

  private final Spec spec;
  private final WebIdl idl;
  private final Map<String, EnumMapping> enums = new LinkedHashMap<>();
  private final Map<String, StructMapping> structs = new LinkedHashMap<>();
  private final Map<String, Binding> bindings = new LinkedHashMap<>();
  private final List<String> flagIssues = new ArrayList<>();
  private final List<String> problems = new ArrayList<>();
  private final List<String> unsupportedValues = new ArrayList<>();
  private final List<String> unboundOperations = new ArrayList<>();

  private WebMap(Spec spec, WebIdl idl) {
    this.spec = spec;
    this.idl = idl;
  }

  /**
   * Merges the two models.
   *
   * @param spec The C model.
   * @param idl The browser model.
   * @return The map. Check {@link #problems()} for anything that did not fit.
   */
  public static WebMap of(Spec spec, WebIdl idl) {
    WebMap m = new WebMap(spec, idl);
    m.mapEnums();
    m.mapFlags();
    m.mapStructs();
    m.mapFunctions();
    return m;
  }

  /**
   * Returns the enum mappings.
   *
   * @return One entry per C enum that has a browser counterpart, in specification order.
   */
  public List<EnumMapping> enums() {
    return List.copyOf(enums.values());
  }

  /**
   * Finds the mapping of a C enum.
   *
   * @param cName The C name, such as {@code WGPUTextureFormat}.
   * @return The mapping, or {@code null} if the enum exists only in the native API.
   */
  public @Nullable EnumMapping enumFor(String cName) {
    return enums.get(cName);
  }

  /**
   * Finds the mapping of a C struct.
   *
   * @param cName The C name, such as {@code WGPUTextureDescriptor}.
   * @return The mapping, or {@code null} if the struct has no dictionary in the browser.
   */
  public @Nullable StructMapping structFor(String cName) {
    return structs.get(cName);
  }

  /**
   * Returns the struct mappings.
   *
   * @return One entry per C struct that becomes a dictionary.
   */
  public List<StructMapping> structs() {
    return List.copyOf(structs.values());
  }

  /**
   * Finds how a C function is implemented.
   *
   * @param cName The C function name, such as {@code wgpuDeviceCreateBuffer}.
   * @return The binding.
   * @throws IllegalArgumentException If the function is unknown.
   */
  public Binding bindingFor(String cName) {
    Binding b = bindings.get(cName);
    if (b == null) {
      throw new IllegalArgumentException("no binding for " + cName);
    }
    return b;
  }

  /**
   * Returns the C values that have no counterpart in the browser and are not the "use the default" value.
   *
   * @return Entries such as {@code error_filter.out_of_memory}. Using one of these on the web throws an
   *     {@link UnsupportedOperationException}.
   */
  public List<String> unsupportedValues() {
    return List.copyOf(unsupportedValues);
  }

  /**
   * Returns the browser operations that no C function maps to.
   *
   * @return Entries such as {@code GPUDevice.importExternalTexture}. The shared API has no way to call them.
   */
  public List<String> unboundOperations() {
    return List.copyOf(unboundOperations);
  }

  /**
   * Returns everything that does not fit the merge rules and is not a documented difference.
   *
   * @return Human-readable problems. An empty list means the two specifications agree.
   */
  public List<String> problems() {
    List<String> all = new ArrayList<>(problems);
    all.addAll(flagIssues);
    return all;
  }

  /**
   * Normalizes a name for comparison: lower case without underscores and hyphens.
   *
   * @param s A C spec name or an IDL string.
   * @return The normalized text.
   */
  public static String norm(String s) {
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c != '_' && c != '-') {
        sb.append(Character.toLowerCase(c));
      }
    }
    return sb.toString();
  }

  private void mapEnums() {
    for (EnumType e : spec.enums()) {
      List<String> values;
      String idlName;
      if (SYNTHETIC_ENUMS.containsKey(e.specName())) {
        values = SYNTHETIC_ENUMS.get(e.specName());
        idlName = "(synthetic)";
      } else {
        idlName = ENUM_ALIASES.getOrDefault(e.specName(), "GPU" + Naming.pascal(e.specName()));
        Enumeration ie = idl.findEnum(idlName);
        if (ie == null) {
          continue;
        }
        values = ie.values();
      }
      List<String> js = new ArrayList<>();
      List<Support> support = new ArrayList<>();
      boolean[] used = new boolean[values.size()];
      Set<String> omit = ENUM_OMIT.getOrDefault(e.specName(), Set.of());
      for (EnumValue v : e.values()) {
        int hit = -1;
        for (int i = 0; i < values.size(); i++) {
          if (norm(values.get(i)).equals(norm(v.specName()))) {
            hit = i;
            break;
          }
        }
        if (hit >= 0) {
          if (used[hit]) {
            problems.add("enum " + e.specName() + ": two C values map to \"" + values.get(hit) + "\"");
          }
          used[hit] = true;
          js.add(values.get(hit));
          support.add(Support.JS);
        } else if (v.specName().equals("undefined") || omit.contains(v.specName())) {
          js.add(null);
          support.add(Support.OMIT);
        } else {
          js.add(null);
          support.add(Support.NONE);
          unsupportedValues.add(e.specName() + "." + v.specName());
        }
      }
      for (int i = 0; i < used.length; i++) {
        if (!used[i]) {
          problems.add("enum " + e.specName() + ": the browser value \"" + values.get(i) + "\" ("
              + idlName + ") has no C value");
        }
      }
      enums.put(e.cName(), new EnumMapping(e, idlName, js, support));
    }
  }

  private void mapFlags() {
    for (BitflagType b : spec.bitflags()) {
      String ns = FLAG_ALIASES.getOrDefault(b.specName(), "GPU" + Naming.pascal(b.specName()));
      WebIdl.Namespace n = idl.findNamespace(ns);
      if (n == null) {
        flagIssues.add("bit flag " + b.specName() + " has no namespace " + ns);
        continue;
      }
      for (WebIdl.Constant c : n.constants()) {
        BitflagValue match = null;
        for (BitflagValue v : b.values()) {
          if (norm(v.specName()).equals(norm(c.name()))) {
            match = v;
          }
        }
        if (match == null) {
          flagIssues.add("bit flag " + ns + "." + c.name() + " has no C value");
        } else if (match.value() != c.number()) {
          flagIssues.add("bit flag " + ns + "." + c.name() + " is " + c.number() + " in the browser but "
              + match.value() + " in C");
        }
      }
      for (BitflagValue v : b.values()) {
        boolean found = v.value() == 0;
        for (WebIdl.Constant c : n.constants()) {
          found |= norm(v.specName()).equals(norm(c.name()));
        }
        if (!found && !v.combination()) {
          flagIssues.add("bit flag value " + b.specName() + "." + v.specName() + " has no browser constant");
        }
      }
    }
  }

  private void mapStructs() {
    for (Struct s : spec.structs()) {
      if (s.kind() == StructKind.BUILTIN || s.kind() == StructKind.CALLBACK_INFO || s.kind() == StructKind.EXTENSION
          || NO_DICTIONARY.contains(s.specName())) {
        continue;
      }
      String name = STRUCT_ALIASES.get(s.specName());
      if (name == null) {
        String base = "GPU" + Naming.pascal(s.specName());
        name = idl.findDictionary(base) != null ? base : base + "Dict";
      }
      if (idl.findDictionary(name) == null) {
        problems.add("struct " + s.specName() + " has no dictionary " + name);
        continue;
      }
      List<DictionaryMember> members = idl.allMembers(name);
      List<FieldMapping> fields = new ArrayList<>();
      boolean[] seen = new boolean[members.size()];
      for (Member m : s.fields()) {
        if (m.role() != MemberRole.VALUE && m.role() != MemberRole.ARRAY_DATA) {
          continue;
        }
        int hit = -1;
        for (int i = 0; i < members.size(); i++) {
          if (members.get(i).name().equals(m.name())) {
            hit = i;
          }
        }
        if (hit < 0 && s.cName().equals("WGPUTexelCopyBufferInfo") && m.name().equals("layout")) {
          fields.add(new FieldMapping(m, null, true));
          continue;
        }
        if (hit >= 0) {
          seen[hit] = true;
          fields.add(new FieldMapping(m, m.name(), false));
        } else {
          if (!C_ONLY_MEMBERS.contains(s.specName() + "." + m.name())) {
            problems.add("struct member " + s.specName() + "." + m.name() + " has no member in " + name);
          }
          fields.add(new FieldMapping(m, null, false));
        }
      }
      if (s.cName().equals("WGPUTexelCopyBufferInfo")) {
        for (int i = 0; i < members.size(); i++) {
          seen[i] |= members.get(i).name().equals("offset") || members.get(i).name().equals("bytesPerRow")
              || members.get(i).name().equals("rowsPerImage");
        }
      }
      for (Struct e : spec.structs()) {
        if (e.kind() == StructKind.EXTENSION && e.extendsStructs().contains(s.cName())) {
          for (Member em : e.fields()) {
            for (int i = 0; i < members.size(); i++) {
              if (members.get(i).name().equals(em.name())) {
                seen[i] = true;
              }
            }
          }
        }
      }
      for (int i = 0; i < members.size(); i++) {
        if (!seen[i] && !IDL_ONLY_MEMBERS.contains(name + "." + members.get(i).name())) {
          problems.add("browser member " + name + "." + members.get(i).name() + " has no C member");
        }
      }
      structs.put(s.cName(), new StructMapping(s, name, fields));
    }
  }

  private void mapFunctions() {
    Set<String> bound = new HashSet<>();
    for (GpuFunction f : spec.functions()) {
      if (f.kind() != FunctionKind.NORMAL && f.kind() != FunctionKind.GET_PROC_ADDRESS) {
        continue;
      }
      Binding b = bind(f);
      bindings.put(f.cName(), b);
      if (b.op() != null) {
        bound.add(b.iface() + "." + b.op().name());
      }
      if (b.attr() != null) {
        bound.add(b.iface() + "." + b.attr().name());
      }
    }
    for (Interface i : idl.interfaces()) {
      if (!i.name().equals("GPU") && !i.name().equals("GPUCanvasContext") && !spec.objects().stream()
          .anyMatch(o -> ifaceName(o).equals(i.name()))) {
        continue;
      }
      for (Operation o : i.operations()) {
        if (!bound.contains(i.name() + "." + o.name()) && !i.name().equals("GPUCanvasContext")) {
          unboundOperations.add(i.name() + "." + o.name());
        }
      }
    }
  }

  private static String ifaceName(GpuObject o) {
    return switch (o.specName()) {
      case "instance" -> "GPU";
      case "surface" -> "GPUCanvasContext";
      default -> "GPU" + Naming.pascal(o.specName());
    };
  }

  private Binding bind(GpuFunction f) {
    if (f.owner() == null) {
      return new Binding(f, Binding.Kind.SPECIAL, "GPU", null, null);
    }
    String iface = switch (f.owner()) {
      case "WGPUInstance" -> "GPU";
      case "WGPUSurface" -> "GPUCanvasContext";
      default -> "GPU" + f.owner().substring("WGPU".length());
    };
    Interface in = idl.findInterface(iface);
    String name = Naming.camel(f.specName());
    boolean special = isSpecial(f);
    if (in == null) {
      return new Binding(f, Binding.Kind.SPECIAL, iface, null, null);
    }
    if (special) {
      Operation op = in.operation(name);
      return new Binding(f, Binding.Kind.SPECIAL, iface, op, in.attribute(name));
    }
    if (name.equals("setLabel")) {
      return new Binding(f, Binding.Kind.LABEL, iface, null, in.attribute("label"));
    }
    int logical = logicalArgs(f);
    if (name.startsWith("get") && logical == 0 && name.length() > 3) {
      String attr = Character.toLowerCase(name.charAt(3)) + name.substring(4);
      Attribute a = in.attribute(attr);
      if (a != null) {
        return new Binding(f, Binding.Kind.GET, iface, null, a);
      }
    }
    Operation chosen = null;
    for (Operation o : in.overloads(name)) {
      if (logical >= o.requiredCount() && logical <= o.args().size()) {
        chosen = o;
        break;
      }
    }
    if (chosen == null) {
      return new Binding(f, Binding.Kind.NONE, iface, null, null);
    }
    return new Binding(f, Binding.Kind.METHOD, iface, chosen, null);
  }

  private static int logicalArgs(GpuFunction f) {
    int n = 0;
    for (Member m : f.params()) {
      switch (m.role()) {
        case SELF, ARRAY_COUNT, USERDATA, CHAIN_HEADER, CHAIN_NEXT -> {
        }
        default -> n++;
      }
    }
    return n;
  }

  private static boolean isSpecial(GpuFunction f) {
    if (f.isAsync()) {
      return true;
    }
    for (Member m : f.params()) {
      CType t = m.type();
      boolean outStruct = t.kind() == TypeKind.STRUCT && t.pointer() == CType.Pointer.MUTABLE;
      if (outStruct || m.role() == MemberRole.ARRAY_DATA && t.kind() == TypeKind.UINT32) {
        return true;
      }
      if (t.kind() == TypeKind.VOID && t.isPointer()) {
        return true;
      }
    }
    if (f.returns() != null && f.returns().kind() == TypeKind.VOID && f.returns().isPointer()) {
      return true;
    }
    return SPECIAL_NAMES.contains(f.cName());
  }

  private static final Set<String> SPECIAL_NAMES = Set.of(
      "wgpuInstanceCreateSurface", "wgpuInstanceProcessEvents", "wgpuInstanceWaitAny",
      "wgpuInstanceHasWGSLLanguageFeature", "wgpuAdapterHasFeature", "wgpuDeviceHasFeature",
      "wgpuDeviceGetLostFuture", "wgpuSurfaceConfigure", "wgpuSurfacePresent", "wgpuSurfaceGetCurrentTexture",
      "wgpuSurfaceGetCapabilities");

  /** Tells how the browser spells a C enum value. */
  public enum Support {
    /** The value has a string in the browser. */
    JS,
    /** The value means "use the default", so the property is left out. */
    OMIT,
    /** The value exists only in the native API. */
    NONE
  }

  /**
   * How a C enum maps to a browser enum.
   *
   * @param c The C enum.
   * @param idlName The browser enum name, or {@code (synthetic)} for a string list that the IDL does not declare.
   * @param js The browser string for each C value, in order, or {@code null} if there is none.
   * @param support What each C value means on the web, in order.
   */
  public record EnumMapping(@NotNull EnumType c, @NotNull String idlName, @NotNull List<String> js,
      @NotNull List<Support> support) {
  }

  /**
   * How one C struct member maps to a dictionary member.
   *
   * @param c The C member.
   * @param js The dictionary member name, or {@code null} if the browser has none (the member is ignored on the web).
   * @param flatten Whether the member is a nested struct whose fields belong to the same dictionary.
   */
  public record FieldMapping(@NotNull Member c, @Nullable String js, boolean flatten) {
  }

  /**
   * How a C struct maps to a dictionary.
   *
   * @param c The C struct.
   * @param idlName The dictionary name.
   * @param fields One entry per value or array member of the C struct.
   */
  public record StructMapping(@NotNull Struct c, @NotNull String idlName, @NotNull List<FieldMapping> fields) {

    /**
     * Finds the mapping of a member.
     *
     * @param name The camel-case member name.
     * @return The mapping, or {@code null} for members that are not mapped (counts, chains).
     */
    public @Nullable FieldMapping field(String name) {
      for (FieldMapping f : fields) {
        if (f.c().name().equals(name)) {
          return f;
        }
      }
      return null;
    }
  }

  /**
   * How a C function is implemented on the web.
   *
   * @param c The C function.
   * @param kind How the call is translated.
   * @param iface The browser interface that holds the operation, such as {@code GPUDevice}.
   * @param op The browser operation, or {@code null}.
   * @param attr The browser attribute, or {@code null}.
   */
  public record Binding(@NotNull GpuFunction c, @NotNull Kind kind, @NotNull String iface,
      @Nullable Operation op, @Nullable Attribute attr) {

    /** How a call is translated. */
    public enum Kind {
      /** One call of an operation with the same name and the same arguments. */
      METHOD,
      /** A read of an attribute. */
      GET,
      /** A write of the {@code label} attribute, or nothing if the browser object has no label. */
      LABEL,
      /** Implemented by hand: promises, output structs, raw memory, the instance and the surface. */
      SPECIAL,
      /** The browser has no such operation, so the call throws {@link UnsupportedOperationException}. */
      NONE
    }
  }
}
