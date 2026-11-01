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

import io.github.stringdotjar.jwebgpu.generator.model.EnumType;
import io.github.stringdotjar.jwebgpu.generator.model.EnumValue;
import io.github.stringdotjar.jwebgpu.generator.web.WebMap;
import io.github.stringdotjar.jwebgpu.generator.web.WebMap.EnumMapping;
import io.github.stringdotjar.jwebgpu.generator.web.WebMap.Support;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes {@code TeaVmEnums}: the tables that turn Java enums into the browser's strings and back.
 *
 * <p>The browser spells an enum value as a string such as {@code "bgra8unorm-srgb"}. Building that string for every
 * call would allocate, so the strings are created once, the first time an enum is used, and a Java enum is turned into
 * its string with an array lookup by ordinal. A value the browser does not have throws an
 * {@link UnsupportedOperationException} when it is used. The value called {@code undefined} (and a few other "use the
 * default" values) becomes {@code null}, which the property setters turn into JavaScript {@code undefined}.
 */
final class TeaVmEnumEmitter {

  private final WebMap web;

  TeaVmEnumEmitter(WebMap web) {
    this.web = web;
  }

  GeneratedFile emit() {
    SourceWriter w = new SourceWriter(TeaVmNames.PKG);
    w.imp("org.teavm.jso.JSObject");
    w.doc("Turns Java enums into the browser's strings and back.\n\n"
        + "<p>This class is generated from the WebGPU WebIDL and the C specification.");
    w.openType("final class TeaVmEnums");
    w.line("private TeaVmEnums() {}");
    w.blank();
    for (EnumMapping m : web.enums()) {
      EnumType e = m.c();
      String type = w.imp(Names.pkg() + "." + Names.type(e.cName()));
      String table = Names.type(e.cName()).substring(3) + "Table";
      w.doc("Returns the browser string of a value.\n\n@param v The value, or null for none.\n"
          + "@return The string, or null if the value means \"use the default\".\n"
          + "@throws UnsupportedOperationException If the browser has no string for the value.");
      w.open("static JSObject " + TeaVmNames.toJs(e.cName()) + "(" + type + " v)");
      w.line("return TeaVmJs.lookup(" + table + ".JS, " + table + ".NONE, v, \"" + type + "\");");
      w.close();
      w.blank();
      w.doc("Tells whether the browser has a string for a value.\n\n@param v The value, or null.\n"
          + "@return {@code true} if the value maps to a string.");
      w.open("static boolean " + TeaVmNames.has(e.cName()) + "(" + type + " v)");
      w.line("return v != null && " + table + ".JS[v.ordinal()] != null;");
      w.close();
      w.blank();
      w.doc("Returns the value that the browser string stands for.\n\n@param s The string, or null.\n"
          + "@return The value, or null if the string is not part of this version of the API.");
      w.open("static " + type + " " + TeaVmNames.fromJs(e.cName()) + "(String s)");
      w.open("if (s == null)");
      w.line("return null;");
      w.close();
      w.open("return switch (s)");
      List<String> seen = new ArrayList<>();
      for (int i = 0; i < e.values().size(); i++) {
        if (m.support().get(i) == Support.JS && !seen.contains(m.js().get(i))) {
          seen.add(m.js().get(i));
          w.line(
              "case \"" + m.js().get(i) + "\" -> " + type + "." + Names.constant(e.values().get(i).specName()) + ";");
        }
      }
      w.line("default -> null;");
      w.close(";");
      w.close();
      w.blank();
    }
    for (EnumMapping m : web.enums()) {
      EnumType e = m.c();
      String table = Names.type(e.cName()).substring(3) + "Table";
      w.doc("The strings of {@code " + e.cName() + "}, by ordinal.");
      w.openType("private static final class " + table);
      w.line("static final JSObject[] JS = {");
      for (int i = 0; i < e.values().size(); i++) {
        EnumValue v = e.values().get(i);
        String item = switch (m.support().get(i)) {
          case JS -> "TeaVmJs.string(\"" + m.js().get(i) + "\")";
          case OMIT -> "null";
          case NONE -> "null";
        };
        w.line("  " + item + (i == e.values().size() - 1 ? "" : ",") + " // " + v.specName());
      }
      w.line("};");
      w.blank();
      StringBuilder none = new StringBuilder("static final boolean[] NONE = { ");
      for (int i = 0; i < e.values().size(); i++) {
        none.append(i == 0 ? "" : ", ").append(m.support().get(i) == Support.NONE);
      }
      w.line(none.append(" };").toString());
      w.blank();
      w.line("private " + table + "() {}");
      w.close();
      w.blank();
    }
    w.close();
    return new GeneratedFile(TeaVmNames.path("TeaVmEnums"), w.toSource());
  }
}
