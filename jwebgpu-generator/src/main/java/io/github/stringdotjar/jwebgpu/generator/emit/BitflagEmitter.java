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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Writes one final class of {@code long} constants for each C bit flag type. */
final class BitflagEmitter {

  private final Docs docs;

  BitflagEmitter(Docs docs) {
    this.docs = docs;
  }

  GeneratedFile emit(BitflagType type) {
    String name = Names.type(type.cName());
    SourceWriter w = new SourceWriter(Names.pkg());
    String intro = docs.text(type.doc(), "The bit flags of " + Names.words(type.cName()) + ".");
    w.doc(intro + "\n\n<p>Combine flags with the bitwise or operator, for example {@code A | B}. The values are "
        + "64-bit numbers, so a combination is a {@code long}.\n\n<p>C type: {@code " + type.cName() + "}.");
    w.openType("public final class " + name);
    List<BitflagValue> values = type.values();
    Set<String> seen = new HashSet<>();
    for (BitflagValue v : values) {
      String constant = Names.constant(v.specName());
      if (!seen.add(constant)) {
        throw new IllegalStateException("duplicate bit flag name in " + type.cName() + ": " + v.cName());
      }
      w.doc(docs.text(v.doc(), "The " + Names.words(v.specName()) + (v.combination() ? " combination." : " flag.")));
      w.line("public static final long " + constant + " = 0x" + Long.toHexString(v.value()).toUpperCase() + "L;");
      w.blank();
    }
    w.line("private " + name + "() {}");
    w.close();
    return new GeneratedFile(EnumEmitter.path(name), w.toSource());
  }
}
