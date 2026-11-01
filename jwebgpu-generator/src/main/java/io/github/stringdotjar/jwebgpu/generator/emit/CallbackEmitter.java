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
import io.github.stringdotjar.jwebgpu.generator.model.Member;
import io.github.stringdotjar.jwebgpu.generator.model.MemberRole;
import io.github.stringdotjar.jwebgpu.generator.model.Naming;

import java.util.ArrayList;
import java.util.List;

/** Writes one functional interface for each C callback type. */
final class CallbackEmitter {

  private final Docs docs;
  private final Types types;

  CallbackEmitter(Docs docs, Types types) {
    this.docs = docs;
    this.types = types;
  }

  GeneratedFile emit(Callback cb) {
    String name = Names.type(cb.cName());
    String method = "on" + Naming.pascal(cb.specName());
    SourceWriter w = new SourceWriter(Names.pkg());
    String nullable = w.imp("org.jetbrains.annotations.Nullable");

    w.doc(docs.text(cb.doc(), "Receives the result of an operation that finishes later.")
        + "\n\n<p>The native userdata pointers of the C callback do not exist in Java: capture whatever you need in "
        + "a lambda or a field instead. Create the callback once and reuse it, so that nothing is allocated when it "
        + "is passed to the API.\n\n<p>C type: {@code " + cb.cName() + "}.");
    w.line("@FunctionalInterface");
    w.openType("public interface " + name);

    List<String> args = new ArrayList<>();
    List<String> tags = new ArrayList<>();
    for (Member m : cb.params()) {
      if (m.role() == MemberRole.USERDATA) {
        continue;
      }
      CType t = m.type();
      String type;
      String doc;
      switch (t.kind()) {
        case ENUM -> {
          type = "@" + nullable + " " + Names.type(t.name());
          doc = docs.text(m.doc(), "The " + Names.words(m.name()) + ".")
              + "\n\n<p>It is {@code null} if the native library reports a value this API does not know.";
        }
        case OBJECT -> {
          type = "@" + nullable + " " + Names.type(t.name());
          doc = docs.text(m.doc(), "The " + Names.words(m.name()) + ", or {@code null} if the operation failed.");
        }
        case STRUCT -> {
          if (t.name().equals(Types.STRING_VIEW)) {
            type = "@" + nullable + " String";
            doc = docs.text(m.doc(), "The " + Names.words(m.name()) + ".")
                + "\n\n<p>It is {@code null} if the native library passes no text.";
          } else {
            type = Names.type(t.name());
            doc = docs.text(m.doc(), "The " + Names.words(m.name()) + ".")
                + "\n\n<p>The object is only valid while the callback runs. Copy what you need out of it.";
          }
        }
        default -> {
          type = types.java(t);
          doc = docs.text(m.doc(), "The " + Names.words(m.name()) + ".");
        }
      }
      args.add(type + " " + m.name());
      tags.add("@param " + m.name() + " " + Names.flat(doc));
    }
    w.doc("Called when the operation has finished.", tags);
    w.call("void " + method + "(", args, ");");
    w.close();
    return new GeneratedFile(EnumEmitter.path(name), w.toSource());
  }
}
