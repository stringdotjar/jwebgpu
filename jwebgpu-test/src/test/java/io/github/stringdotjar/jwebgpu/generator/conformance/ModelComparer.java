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

import io.github.stringdotjar.jwebgpu.generator.model.BitflagType;
import io.github.stringdotjar.jwebgpu.generator.model.BitflagValue;
import io.github.stringdotjar.jwebgpu.generator.model.Callback;
import io.github.stringdotjar.jwebgpu.generator.model.Constant;
import io.github.stringdotjar.jwebgpu.generator.model.EnumType;
import io.github.stringdotjar.jwebgpu.generator.model.EnumValue;
import io.github.stringdotjar.jwebgpu.generator.model.GpuFunction;
import io.github.stringdotjar.jwebgpu.generator.model.GpuObject;
import io.github.stringdotjar.jwebgpu.generator.model.Member;
import io.github.stringdotjar.jwebgpu.generator.model.MemberRole;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;
import io.github.stringdotjar.jwebgpu.generator.model.StructKind;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Compares a resolved {@link Spec} with a scanned C header and lists every disagreement.
 *
 * <p>Two strictness levels exist. The strict level (used for the vendored upstream header) demands that both sides
 * contain exactly the same declarations, in the same order, and that nullability markers agree. The relaxed level
 * (used for Dawn's header, which only adds declarations) demands that everything in the model exists in the header
 * with the same types and names, and ignores what only the header has. Those header-only extras are returned
 * separately so a test can report them.
 */
final class ModelComparer {

  /** The disagreements between the model and the header. */
  final List<String> mismatches = new ArrayList<>();
  /** Declarations that the header has and the model does not. */
  final List<String> extras = new ArrayList<>();

  private final Spec spec;
  private final CHeader header;
  private final boolean strict;

  ModelComparer(Spec spec, CHeader header, boolean strict) {
    this.spec = spec;
    this.header = header;
    this.strict = strict;
  }

  void compareAll() {
    constants();
    objects();
    enums();
    bitflags();
    callbacks();
    structs();
    functions();
    inits();
  }

  private void names(String kind, Set<String> model, Set<String> header) {
    for (String n : model) {
      if (!header.contains(n)) {
        mismatches.add(kind + " " + n + " is in the model but not in the header");
      }
    }
    for (String n : header) {
      if (!model.contains(n)) {
        (strict ? mismatches : extras).add(kind + " " + n + " is in the header but not in the model");
      }
    }
  }

  private static Set<String> set(Iterable<String> items) {
    Set<String> s = new LinkedHashSet<>();
    items.forEach(s::add);
    return s;
  }

  private void constants() {
    Set<String> model = new LinkedHashSet<>();
    for (Constant c : spec.constants()) {
      model.add(c.cName());
      String expr = header.constants.get(c.cName());
      if (expr != null && !expr.equals(c.cValue())) {
        mismatches.add("constant " + c.cName() + ": model " + c.cValue() + " vs header " + expr);
      }
    }
    Set<String> actual = new LinkedHashSet<>(header.constants.keySet());
    for (String builtin : new String[] { "WGPU_TRUE", "WGPU_FALSE" }) {
      if (!actual.remove(builtin)) {
        mismatches.add("header does not define " + builtin);
      }
    }
    names("constant", model, actual);
  }

  private void objects() {
    Set<String> model = new LinkedHashSet<>();
    for (GpuObject o : spec.objects()) {
      model.add(o.cName());
    }
    names("object", model, header.objects.keySet());
  }

  private void enums() {
    Set<String> model = new LinkedHashSet<>();
    for (EnumType e : spec.enums()) {
      model.add(e.cName());
      CHeader.EnumDecl decl = header.enums.get(e.cName());
      if (decl == null) {
        continue;
      }
      List<String> actual = new ArrayList<>();
      for (CHeader.EnumConst c : decl.values()) {
        if (c.name().equals(e.cName() + "_Force32")) {
          if (c.value() != EnumType.FORCE32) {
            mismatches.add(e.cName() + "_Force32 is not 0x7FFFFFFF");
          }
        } else {
          actual.add(c.name() + "=" + c.value());
        }
      }
      List<String> expected = new ArrayList<>();
      for (EnumValue v : e.values()) {
        expected.add(v.cName() + "=" + (v.value() & 0xFFFFFFFFL));
      }
      if (decl.values().stream().noneMatch(c -> c.name().equals(e.cName() + "_Force32"))) {
        mismatches.add(e.cName() + " has no _Force32 member in the header");
      }
      compareLists("enum " + e.cName(), expected, actual, true);
    }
    names("enum", model, header.enums.keySet());
  }

  private void bitflags() {
    Set<String> model = new LinkedHashSet<>();
    for (BitflagType b : spec.bitflags()) {
      model.add(b.cName());
      List<CHeader.FlagValue> actualValues = header.bitflags.get(b.cName());
      if (actualValues == null) {
        continue;
      }
      List<String> actual = new ArrayList<>();
      for (CHeader.FlagValue v : actualValues) {
        actual.add(v.name() + "=" + Long.toUnsignedString(v.value()));
      }
      List<String> expected = new ArrayList<>();
      for (BitflagValue v : b.values()) {
        expected.add(v.cName() + "=" + Long.toUnsignedString(v.value()));
      }
      compareLists("bitflag " + b.cName(), expected, actual, true);
    }
    names("bitflag", model, header.bitflags.keySet());
  }

  private void callbacks() {
    Set<String> model = new LinkedHashSet<>();
    for (Callback c : spec.callbacks()) {
      model.add(c.cName());
      CHeader.Fn fn = header.callbacks.get(c.cName());
      if (fn != null) {
        compareSlots("callback " + c.cName(), c.params(), fn.params(), strict);
      }
    }
    names("callback", model, header.callbacks.keySet());
  }

  private void structs() {
    Set<String> model = new LinkedHashSet<>();
    for (Struct s : spec.structs()) {
      model.add(s.cName());
      CHeader.Struct decl = header.structs.get(s.cName());
      if (decl != null) {
        compareSlots("struct " + s.cName(), s.fields(), decl.fields(), strict);
      }
    }
    names("struct", model, header.structs.keySet());
    if (strict) {
      List<String> expected = new ArrayList<>();
      spec.structs().forEach(s -> expected.add(s.cName()));
      compareLists("struct declaration order", expected, new ArrayList<>(header.structs.keySet()), false);
    }
  }

  private void functions() {
    Set<String> model = new LinkedHashSet<>();
    for (GpuFunction f : spec.functions()) {
      model.add(f.cName());
      CHeader.Fn fn = header.functions.get(f.cName());
      if (fn == null) {
        continue;
      }
      String expectedRet = f.returns() == null ? "void" : f.returns().declaration();
      if (!fn.ret().type().equals(expectedRet)) {
        mismatches.add("function " + f.cName() + " returns " + expectedRet + " in the model but " + fn.ret().type());
      }
      if (strict && fn.ret().nullable() != f.returnNullable()) {
        mismatches.add("function " + f.cName() + " return nullability differs");
      }
      compareSlots("function " + f.cName(), f.params(), fn.params(), strict);
    }
    names("function", model, header.functions.keySet());
    if (strict) {
      List<String> expected = new ArrayList<>();
      spec.functions().forEach(f -> expected.add(f.cName()));
      compareLists("function declaration order", expected, new ArrayList<>(header.functions.keySet()), false);
    }
  }

  private void inits() {
    int checked = 0;
    for (Struct s : spec.structs()) {
      if (s.kind() == StructKind.BUILTIN) {
        continue;
      }
      List<CHeader.Init> actual = header.inits.get(s.cName());
      if (actual == null) {
        if (strict) {
          mismatches.add("struct " + s.cName() + " has no INIT macro in the header");
        }
        continue;
      }
      checked++;
      List<String> expected = new ArrayList<>();
      for (Member m : s.fields()) {
        if (m.role() == MemberRole.CHAIN_HEADER) {
          expected.add("chain.next=NULL");
          expected.add("chain.sType=" + s.sType());
        } else if (m.init() != null) {
          expected.add(m.name() + "=" + m.init().text());
        } else {
          mismatches.add("struct " + s.cName() + " field " + m.name() + " has no default in the model");
        }
      }
      List<String> got = new ArrayList<>();
      for (CHeader.Init i : actual) {
        got.add(i.field() + "=" + i.expr());
      }
      compareLists("INIT macro of " + s.cName(), expected, got, false);
    }
    if (checked == 0) {
      mismatches.add("no INIT macros were compared");
    }
  }

  private void compareSlots(String where, List<Member> model, List<CHeader.Slot> actual, boolean checkNullable) {
    List<String> expected = new ArrayList<>();
    for (Member m : model) {
      expected.add(m.type().declaration() + " " + m.name() + (checkNullable && m.nullable() ? " (nullable)" : ""));
    }
    List<String> got = new ArrayList<>();
    for (CHeader.Slot s : actual) {
      got.add(s.type() + " " + s.name() + (checkNullable && s.nullable() ? " (nullable)" : ""));
    }
    compareLists(where, expected, got, false);
  }

  private void compareLists(String where, List<String> expected, List<String> actual, boolean allowExtra) {
    if (expected.equals(actual)) {
      return;
    }
    if (strict || !allowExtra) {
      mismatches.add(where + " differs:\n    model:  " + expected + "\n    header: " + actual);
      return;
    }
    // Relaxed mode: the header may have additional entries, but the model's entries must appear in order.
    int at = 0;
    for (String e : expected) {
      int found = actual.indexOf(e);
      if (found < at) {
        mismatches.add(where + " differs:\n    model:  " + expected + "\n    header: " + actual);
        return;
      }
      at = found;
    }
    for (String a : actual) {
      if (!expected.contains(a)) {
        extras.add(where + ": header only: " + a);
      }
    }
  }
}
