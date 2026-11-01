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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Writes one Java {@code enum} for each C enum. */
final class EnumEmitter {

  private final Docs docs;

  EnumEmitter(Docs docs) {
    this.docs = docs;
  }

  GeneratedFile emit(EnumType type) {
    String name = Names.type(type.cName());
    SourceWriter w = new SourceWriter(Names.pkg());
    String nullable = w.imp("org.jetbrains.annotations.Nullable");

    String intro = docs.text(type.doc(), "The values of the " + Names.words(type.cName()) + " enum.");
    w.doc(intro + "\n\n<p>Each constant carries the exact number that the native library uses, in {@link #value}. "
        + "Use {@link #of(int)} to turn a number back into a constant.\n\n<p>C type: {@code " + type.cName() + "}.");
    w.openType("public enum " + name);

    List<EnumValue> values = type.values();
    Set<String> seenNames = new HashSet<>();
    Set<Integer> seenValues = new HashSet<>();
    for (int i = 0; i < values.size(); i++) {
      EnumValue v = values.get(i);
      String constant = Names.constant(v.specName());
      if (!seenNames.add(constant) || !seenValues.add(v.value())) {
        throw new IllegalStateException("duplicate enum name or value in " + type.cName() + ": " + v.cName());
      }
      String doc = docs.text(v.doc());
      if (!doc.isEmpty()) {
        w.doc(doc);
      }
      w.line(constant + "(" + v.value() + ")" + (i == values.size() - 1 ? ";" : ","));
      if (!doc.isEmpty() && i < values.size() - 1) {
        w.blank();
      }
    }
    w.blank();

    w.doc("The number that the native library uses for this constant.");
    w.line("public final int value;");
    w.blank();
    w.line(name + "(int value) {");
    w.line("  this.value = value;");
    w.line("}");
    w.blank();

    w.doc("Finds the constant that has the given native number.\n\n<p>The native library may return numbers that this "
        + "version of the API does not know, for example values that an implementation adds as an extension. "
        + "Those map to {@code null}, so always handle that case.\n\n<p>The lookup is a plain {@code switch}, so it "
        + "allocates nothing and is safe to call every frame.",
        List.of("@param value The native number.",
            "@return The matching constant, or {@code null} if the number is not part of the specification."));
    w.open("public static @" + nullable + " " + name + " of(int value)");
    w.open("return switch (value)");
    for (EnumValue v : values) {
      w.line("case " + v.value() + " -> " + Names.constant(v.specName()) + ";");
    }
    w.line("default -> null;");
    w.close(";");
    w.close();
    w.close();
    return new GeneratedFile(path(name), w.toSource());
  }

  static String path(String name) {
    return Names.pkg().replace('.', '/') + "/" + name + ".java";
  }
}
