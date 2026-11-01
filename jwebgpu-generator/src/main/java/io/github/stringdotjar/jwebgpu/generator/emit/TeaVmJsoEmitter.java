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

import io.github.stringdotjar.jwebgpu.generator.model.WebIdl;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Argument;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Attribute;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Interface;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Operation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Writes one JavaScript Object (JSO) interface for each interface of the browser's WebGPU IDL.
 *
 * <p>An interface such as {@code JsGpuDevice} mirrors {@code GPUDevice}: attributes become {@code @JSProperty}
 * accessors and operations become {@code @JSMethod} methods, typed as precisely as the IDL allows. An operation with
 * optional arguments gets one Java overload for every argument count, so a call can leave the trailing arguments out
 * without passing a placeholder.
 */
final class TeaVmJsoEmitter {

  private final WebIdl idl;

  TeaVmJsoEmitter(WebIdl idl) {
    this.idl = idl;
  }

  List<GeneratedFile> emitAll() {
    List<GeneratedFile> out = new ArrayList<>();
    for (Interface i : idl.interfaces()) {
      out.add(emit(i));
    }
    return out;
  }

  GeneratedFile emit(Interface i) {
    String name = TeaVmNames.jso(i.name());
    SourceWriter w = new SourceWriter(TeaVmNames.PKG);
    w.imp("org.teavm.jso.JSObject");
    w.doc("The JavaScript object interface of the browser's {@code " + i.name() + "}.\n\n"
        + "<p>The methods call the browser directly, so they allocate no Java objects. Most programs use the wrapper "
        + "classes of the API instead; this interface is for code that needs a feature the shared API does not "
        + "have.\n\n<p>This interface is generated from the WebGPU WebIDL.");
    w.openType("public interface " + name + " extends JSObject");
    for (Attribute a : i.attributes()) {
      emitAttribute(w, a);
    }
    if (i.setLike()) {
      w.imp("org.teavm.jso.JSMethod");
      w.imp("org.teavm.jso.JSProperty");
      w.doc("Tells whether the set contains a string.",
          List.of("@param value The JavaScript string to look for.", "@return {@code true} if the set contains it."));
      w.line("@JSMethod(\"has\")");
      w.line("boolean has(JSObject value);");
      w.blank();
      w.doc("Returns the number of strings in the set.", List.of("@return The number of strings."));
      w.line("@JSProperty(\"size\")");
      w.line("int getSize();");
      w.blank();
      w.doc("Returns the strings of the set as a JavaScript array.", List.of("@return The array."));
      w.line("@JSMethod(\"values\")");
      w.line("JSObject values();");
      w.blank();
    }
    Set<String> seen = new HashSet<>();
    for (Operation o : i.operations()) {
      if (o.isStatic()) {
        continue;
      }
      for (int n = o.requiredCount(); n <= o.args().size(); n++) {
        emitOperation(w, o, n, seen);
      }
    }
    w.close();
    String text = w.toSource();
    return new GeneratedFile(TeaVmNames.path(name), text);
  }

  private void emitAttribute(SourceWriter w, Attribute a) {
    w.imp("org.teavm.jso.JSProperty");
    String type = TeaVmNames.javaType(idl, a.type(), true);
    if (type.startsWith("JSPromise")) {
      w.imp("org.teavm.jso.core.JSPromise");
    }
    String cap = TeaVmNames.capitalize(a.name());
    w.doc("Reads the {@code " + a.name() + "} attribute.", List.of("@return The value of the attribute."));
    w.line("@JSProperty(\"" + a.name() + "\")");
    w.line(type + " get" + cap + "();");
    w.blank();
    if (!a.readonly()) {
      String setType = TeaVmNames.javaType(idl, a.type(), false);
      if (setType.startsWith("JSPromise")) {
        w.imp("org.teavm.jso.core.JSPromise");
      }
      w.doc("Writes the {@code " + a.name() + "} attribute.", List.of("@param value The new value."));
      w.line("@JSProperty(\"" + a.name() + "\")");
      w.line("void set" + cap + "(" + setType + " value);");
      w.blank();
    }
  }

  private void emitOperation(SourceWriter w, Operation o, int argCount, Set<String> seen) {
    List<String> params = new ArrayList<>();
    List<String> tags = new ArrayList<>();
    StringBuilder key = new StringBuilder(o.name()).append('(');
    for (int k = 0; k < argCount; k++) {
      Argument a = o.args().get(k);
      String type = TeaVmNames.javaType(idl, a.type(), false);
      if (type.startsWith("JSPromise")) {
        w.imp("org.teavm.jso.core.JSPromise");
      }
      String pname = Names.ident(a.name());
      params.add(type + " " + pname);
      key.append(type).append(',');
      tags.add("@param " + pname + " The {@code " + a.name() + "} argument.");
    }
    if (!seen.add(key.toString())) {
      return;
    }
    String ret = TeaVmNames.javaType(idl, o.returns(), true);
    if (ret.startsWith("JSPromise")) {
      w.imp("org.teavm.jso.core.JSPromise");
    }
    if (!ret.equals("void")) {
      tags.add("@return The result of the call.");
    }
    w.imp("org.teavm.jso.JSMethod");
    String note = argCount < o.args().size()
        ? " The last " + (o.args().size() - argCount) + " optional argument(s) are left out." : "";
    w.doc("Calls {@code " + o.name() + "} of the browser object." + note, tags);
    w.line("@JSMethod(\"" + o.name() + "\")");
    w.call(ret + " " + Names.ident(o.name()) + "(", params, ");");
    w.blank();
  }
}
