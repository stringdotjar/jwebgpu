package io.github.stringdotjar.jwebgpu.generator.emit;

import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;
import io.github.stringdotjar.jwebgpu.generator.model.StructKind;

import java.util.List;

/**
 * Generates {@code JniChains}, which writes and reads linked lists of extension structs.
 *
 * <p>An API descriptor holds its extensions as a linked list of Java objects through {@code nextInChain}. The native
 * library wants a linked list of C structs, each starting with a pointer to the next one and a tag that names its
 * kind. {@code put} writes the Java list into scratch memory, struct by struct, choosing the writer by the tag, and
 * links the C structs together. {@code read} walks the native list and the Java list side by side after a call, for
 * extensions that the native library fills (such as the compatibility mode limits of an adapter).
 */
final class JniChainEmitter {

  private final Spec spec;
  private final FfmUse use;

  JniChainEmitter(Spec spec, FfmUse use) {
    this.spec = spec;
    this.use = use;
  }

  GeneratedFile emit() {
    SourceWriter w = new SourceWriter(JniNames.PKG);
    w.imp(Names.pkg() + ".GpuChainedStruct");
    w.doc("Writes and reads chains of extension structs.\n\n"
        + "<p>The chain is the linked list behind {@code nextInChain}. See {@link GpuChainedStruct}.");
    w.openType("final class JniChains");
    w.line("private JniChains() {}");
    w.blank();

    w.doc("Writes a chain into scratch memory.\n\n@param s The scratch memory of the calling thread.\n"
        + "@param chain The first extension struct, or {@code null} for no chain.\n"
        + "@return The address of the first native struct, or 0 for no chain.");
    w.open("static long put(Scratch s, GpuChainedStruct chain)");
    w.line("long first = 0L;");
    w.line("long prev = 0L;");
    w.line("GpuChainedStruct c = chain;");
    w.open("while (c != null)");
    w.line("long a = putOne(s, c);");
    w.open("if (prev == 0L)");
    w.line("first = a;");
    w.middle("else");
    w.line("s.putLong(prev, a);");
    w.close();
    w.line("prev = a;");
    w.line("c = c.next;");
    w.close();
    w.line("return first;");
    w.close();
    w.blank();

    w.open("private static long putOne(Scratch s, GpuChainedStruct c)");
    w.open("return switch (c.sType())");
    for (Struct e : extensions()) {
      String api = Names.type(e.cName());
      w.imp(Names.pkg() + "." + api);
      w.line("case " + e.sTypeValue() + " -> " + JniNames.struct(e.cName()) + ".put(s, (" + api + ") c);");
    }
    w.line("default -> throw new IllegalArgumentException(\"Unknown chained struct with sType \" + c.sType());");
    w.close(";");
    w.close();
    w.blank();

    w.doc("Copies the native chain into the Java chain after a call that fills extension structs.\n\n"
        + "@param s The scratch memory of the calling thread.\n@param first The address of the first native struct, or 0.\n@param chain The first Java extension struct.");
    w.open("static void read(Scratch s, long first, GpuChainedStruct chain)");
    w.line("long a = first;");
    w.line("GpuChainedStruct c = chain;");
    w.open("while (a != 0L && c != null)");
    w.open("switch (c.sType())");
    for (Struct e : extensions()) {
      if (!use.isRead(e.cName())) {
        continue;
      }
      String api = Names.type(e.cName());
      w.line("case " + e.sTypeValue() + " -> " + JniNames.struct(e.cName()) + ".read(s, a, (" + api + ") c);");
    }
    w.line("default -> {}");
    w.close();
    w.line("a = s.getLong(a);");
    w.line("c = c.next;");
    w.close();
    w.close();
    w.close();
    return new GeneratedFile(JniNames.path("JniChains"), w.toSource());
  }

  private List<Struct> extensions() {
    return spec.structs().stream().filter(s -> s.kind() == StructKind.EXTENSION).toList();
  }
}
