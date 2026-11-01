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

import io.github.stringdotjar.jwebgpu.generator.model.Constant;
import io.github.stringdotjar.jwebgpu.generator.model.ConstantKind;

import java.util.ArrayList;
import java.util.List;

/** Writes {@code GpuConstants}, the class that holds the sentinel values of the C header. */
final class ConstantsEmitter {

  private final Docs docs;

  ConstantsEmitter(Docs docs) {
    this.docs = docs;
  }

  GeneratedFile emit(List<Constant> constants) {
    SourceWriter w = new SourceWriter(Names.pkg());
    w.doc("Sentinel values that WebGPU uses to say \"not specified\" or \"everything\".\n\n"
        + "<p>The numbers are exactly the ones from the C header. Unsigned 32-bit values are stored in an {@code int} "
        + "and unsigned 64-bit values in a {@code long}, so the largest value reads as -1 in Java; compare it with "
        + "the constant instead of with a literal.");
    w.openType("public final class GpuConstants");
    List<Constant> sorted = new ArrayList<>(constants);
    sorted.sort((a, b) -> Integer.compare(order(a), order(b)));
    for (Constant c : sorted) {
      String name = Names.constant(c.specName());
      w.doc(docs.text(c.doc(), "The " + Names.words(c.specName()) + " sentinel."));
      w.line("public static final " + type(c) + " " + name + " = " + value(c) + ";");
      w.blank();
    }
    w.line("private GpuConstants() {}");
    w.close();
    return new GeneratedFile(EnumEmitter.path("GpuConstants"), w.toSource());
  }

  private static int order(Constant c) {
    return switch (c.kind()) {
      case UINT64, USIZE -> 0;
      case UINT32 -> 1;
      case NAN -> 1;
    };
  }

  private static String type(Constant c) {
    return switch (c.kind()) {
      case UINT64, USIZE -> "long";
      case UINT32 -> "int";
      case NAN -> "float";
    };
  }

  private static String value(Constant c) {
    if (c.kind() == ConstantKind.NAN) {
      return "Float.NaN";
    }
    return switch (c.kind()) {
      case UINT64, USIZE -> "0x" + Long.toHexString(c.value()).toUpperCase() + "L";
      default -> "0x" + Integer.toHexString((int) c.value()).toUpperCase();
    };
  }
}
