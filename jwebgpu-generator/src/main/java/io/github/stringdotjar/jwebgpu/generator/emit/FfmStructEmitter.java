package io.github.stringdotjar.jwebgpu.generator.emit;

import io.github.stringdotjar.jwebgpu.generator.model.CType;
import io.github.stringdotjar.jwebgpu.generator.model.Callback;
import io.github.stringdotjar.jwebgpu.generator.model.CallbackStyle;
import io.github.stringdotjar.jwebgpu.generator.model.Member;
import io.github.stringdotjar.jwebgpu.generator.model.MemberRole;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;
import io.github.stringdotjar.jwebgpu.generator.model.StructKind;
import io.github.stringdotjar.jwebgpu.generator.model.StructLayout;
import io.github.stringdotjar.jwebgpu.generator.model.TypeKind;

import java.util.List;

/**
 * Generates the classes that copy API structs into native memory and read native structs back.
 *
 * <p>For each struct the backend needs, one package-private class is written, for example
 * {@code FfmGpuRenderPassDescriptorStruct}. It carries the C size and the offset of every field as constants (from the
 * layout the generator computes, which a test cross-checks with a C compiler), and these methods:
 *
 * <ul>
 *   <li>{@code write(Scratch, descriptor, address)} writes the fields of the Java object at an address. Nested structs,
 *       struct pointers, arrays, chained extension structs, strings and callbacks are written into the scratch memory
 *       and linked by address, so nothing is allocated.
 *   <li>{@code put(Scratch, descriptor)} reserves the memory for the struct, calls {@code write} and returns the
 *       address.
 *   <li>{@code init(Scratch, descriptor, address)} prepares a struct that the native library fills (zeroed memory
 *       plus the chain).
 *   <li>{@code read(address, descriptor)} copies a native struct into the Java object.
 *   <li>{@code freeMembers(address)} calls the matching C {@code FreeMembers} function, for structs the library
 *       fills with memory of its own.
 * </ul>
 */
final class FfmStructEmitter {

  private final Spec spec;
  private final Types types;
  private final FfmUse use;

  FfmStructEmitter(Spec spec, Types types, FfmUse use) {
    this.spec = spec;
    this.types = types;
    this.use = use;
  }

  GeneratedFile emit(Struct s) {
    String name = FfmNames.struct(s.cName());
    String api = Names.type(s.cName());
    boolean wr = use.isWritten(s.cName());
    boolean rd = use.isRead(s.cName());
    boolean out = use.isOut(s.cName());
    StructLayout layout = spec.layouts().of(s.cName());
    SourceWriter w = new SourceWriter(FfmNames.PKG);
    w.imp(Names.pkg() + "." + api);

    w.doc("Copies " + Names.article(Names.words(s.cName())) + " struct between {@link " + api + "} and native memory."
        + "\n\n<p>The offsets are the C layout of {@code " + s.cName() + "}, " + layout.size() + " bytes in total. "
        + "This class is generated; see the emitter for the rules.");
    w.openType("final class " + name);

    w.line("static final long SIZE = " + layout.size() + "L;");
    for (int i = 0; i < s.fields().size(); i++) {
      w.line("static final long " + FfmNames.offset(s.fields().get(i).name()) + " = " + layout.offsets().get(i)
          + "L;");
    }
    if (s.kind() == StructKind.EXTENSION) {
      w.line("static final int STYPE = " + s.sTypeValue() + ";");
    }
    w.blank();
    w.line("private " + name + "() {}");
    w.blank();

    if (wr) {
      w.doc("Reserves memory for the struct and writes the object into it.\n\n"
          + "@param s The scratch memory of the calling thread.\n@param d The object to copy.\n"
          + "@return The address of the struct.");
      w.open("static long put(Scratch s, " + api + " d)");
      w.line("long a = s.alloc(SIZE);");
      w.line("write(s, d, a);");
      w.line("return a;");
      w.close();
      w.blank();
      w.doc("Writes the object at an address that has room for {@link #SIZE} bytes.\n\n"
          + "@param s The scratch memory of the calling thread.\n@param d The object to copy.\n"
          + "@param a The address to write to.");
      w.open("static void write(Scratch s, " + api + " d, long a)");
      for (int i = 0; i < s.fields().size(); i++) {
        write(w, s, s.fields().get(i));
      }
      w.close();
      w.blank();
    }
    if (out) {
      w.doc("Prepares memory that the native library fills: zeroes it and attaches the chain.\n\n"
          + "@param s The scratch memory of the calling thread.\n@param d The object whose chain is attached.\n"
          + "@param a The address of the struct.");
      w.open("static void init(Scratch s, " + api + " d, long a)");
      w.line("Mem.zero(a, SIZE);");
      for (Member m : s.fields()) {
        if (m.role() == MemberRole.CHAIN_NEXT) {
          w.line("Mem.putLong(a + " + FfmNames.offset(m.name()) + ", FfmChains.put(s, d.nextInChain));");
        }
      }
      w.close();
      w.blank();
    }
    if (rd) {
      w.doc("Copies a native struct into the object.\n\n@param a The address of the struct.\n"
          + "@param d The object to fill.");
      w.open("static void read(long a, " + api + " d)");
      for (int i = 0; i < s.fields().size(); i++) {
        read(w, s, s.fields().get(i));
      }
      w.close();
      w.blank();
    }
    if (s.freeMembers()) {
      emitFree(w, s);
    }
    w.close();
    return new GeneratedFile(FfmNames.path(name), w.toSource());
  }

  private void emitFree(SourceWriter w, Struct s) {
    String cName = FfmDescriptors.freeFunction(s);
    w.imp("java.lang.foreign.FunctionDescriptor");
    w.imp("java.lang.foreign.MemoryLayout");
    w.imp("java.lang.foreign.MemorySegment");
    w.imp("java.lang.foreign.StructLayout");
    w.imp("java.lang.foreign.ValueLayout");
    w.imp("java.lang.invoke.MethodHandle");
    w.doc("Calls the C function that frees the memory the native library put into the struct.\n\n"
        + "<p>The C function takes the struct by value, so it gets a view of the native memory.\n\n"
        + "@param a The address of the struct.");
    w.open("static void freeMembers(long a)");
    w.line("Free.call(Mem.ALL.asSlice(a, SIZE));");
    w.close();
    w.blank();
    w.doc("Holds the layout and the method handle of the C function.");
    w.open("private static final class Free");
    w.line("static final StructLayout LAYOUT = " + FfmDescriptors.struct(spec, s.cName()).java() + ";");
    w.line("static final MethodHandle HANDLE = Native.handle(\"" + cName + "\", FunctionDescriptor.ofVoid(LAYOUT));");
    w.blank();
    w.open("static void call(MemorySegment view)");
    w.open("try");
    w.line("HANDLE.invokeExact(view);");
    w.middle("catch (Throwable e)");
    w.line("throw Native.fail(e);");
    w.close();
    w.close();
    w.close();
  }

  private static String field(Member m) {
    return Names.ident(m.name());
  }

  private static String addr(Member m) {
    return "a + " + FfmNames.offset(m.name());
  }

  private void write(SourceWriter w, Struct s, Member m) {
    CType t = m.type();
    String f = "d." + field(m);
    switch (m.role()) {
      case CHAIN_HEADER -> {
        w.line("Mem.putLong(a + " + FfmNames.offset(m.name()) + ", 0L);");
        w.line("Mem.putInt(a + " + FfmNames.offset(m.name()) + " + 8L, STYPE);");
      }
      case CHAIN_NEXT -> w.line("Mem.putLong(" + addr(m) + ", FfmChains.put(s, d.nextInChain));");
      case USERDATA, SELF, ARRAY_COUNT -> {
      }
      case ARRAY_DATA -> writeArray(w, s, m);
      case CALLBACK_INFO -> writeCallback(w, m);
      case VALUE -> writeValue(w, m, t, f);
    }
  }

  private void writeValue(SourceWriter w, Member m, CType t, String f) {
    String at = addr(m);
    if (t.isPointer()) {
      switch (t.kind()) {
        case STRUCT -> w.line("Mem.putLong(" + at + ", " + f + " == null ? 0L : " + FfmNames.struct(t.name())
            + ".put(s, " + f + "));");
        case UINT32 -> {
          String size = "d." + sizeFieldOf(m);
          w.open("");
          w.line("long p = 0L;");
          w.open("if (" + f + " != null && " + size + " > 0)");
          w.line("p = s.alloc(" + size + " * 4L);");
          w.line("Mem.putInts(p, " + f + ", 0, " + size + ");");
          w.close();
          w.line("Mem.putLong(" + at + ", p);");
          w.close();
        }
        case VOID -> w.line("Mem.putLong(" + at + ", " + f + ");");
        default -> throw new IllegalStateException("unsupported pointer field " + m.name());
      }
      return;
    }
    switch (t.kind()) {
      case BOOL -> w.line("Mem.putInt(" + at + ", " + f + " ? 1 : 0);");
      case UINT32, INT32 -> w.line("Mem.putInt(" + at + ", " + f + ");");
      case UINT16, INT16 -> w.line("Mem.putShort(" + at + ", (short) " + f + ");");
      case UINT64, USIZE, BITFLAG -> w.line("Mem.putLong(" + at + ", " + f + ");");
      case FLOAT32 -> w.line("Mem.putFloat(" + at + ", " + f + ");");
      case FLOAT64 -> w.line("Mem.putDouble(" + at + ", " + f + ");");
      case ENUM -> w.line("Mem.putInt(" + at + ", " + f + " == null ? 0 : " + f + ".value);");
      case OBJECT -> w.line("Mem.putLong(" + at + ", " + FfmNames.object(t.name()) + ".addrOrNull(" + f + "));");
      case STRUCT -> {
        if (t.name().equals(Types.STRING_VIEW)) {
          w.line("s.putView(" + at + ", " + f + ");");
        } else {
          w.line(FfmNames.struct(t.name()) + ".write(s, " + f + ", " + at + ");");
        }
      }
      default -> throw new IllegalStateException("unsupported field " + m.name() + " of type " + t);
    }
  }

  private static String sizeFieldOf(Member data) {
    if (!data.name().equals("code")) {
      throw new IllegalStateException("no known size field for " + data.name());
    }
    return "codeSize";
  }

  private void writeArray(SourceWriter w, Struct s, Member m) {
    Member count = s.field(m.peer());
    CType element = m.type().withPointer(CType.Pointer.NONE);
    String f = "d." + field(m);
    String n = "d." + field(count);
    String javaElement = types.java(element);
    w.open("");
    w.line("long p = 0L;");
    w.open("if (" + n + " > 0)");
    switch (element.kind()) {
      case STRUCT -> {
        String st = FfmNames.struct(element.name());
        w.line("p = s.alloc(" + n + " * " + st + ".SIZE);");
        w.open("for (int i = 0; i < " + n + "; i++)");
        w.line(st + ".write(s, " + f + "[i], p + i * " + st + ".SIZE);");
        w.close();
      }
      case ENUM -> {
        w.line("p = s.alloc(" + n + " * 4L);");
        w.open("for (int i = 0; i < " + n + "; i++)");
        w.line("Mem.putInt(p + i * 4L, " + f + "[i] == null ? 0 : " + f + "[i].value);");
        w.close();
      }
      case OBJECT -> {
        w.line("p = s.alloc(" + n + " * 8L);");
        w.open("for (int i = 0; i < " + n + "; i++)");
        w.line("Mem.putLong(p + i * 8L, " + FfmNames.object(element.name()) + ".addrOrNull(" + f + "[i]));");
        w.close();
      }
      default -> throw new IllegalStateException("unsupported array element " + javaElement + " in " + m.name());
    }
    w.close();
    w.line("Mem.putLong(a + " + FfmNames.offset(count.name()) + ", " + n + ");");
    w.line("Mem.putLong(" + addr(m) + ", p);");
    w.close();
  }

  private void writeCallback(SourceWriter w, Member m) {
    Callback cb = types.callbackByInfo(m.type().name());
    String base = m.name().endsWith("CallbackInfo")
        ? m.name().substring(0, m.name().length() - "CallbackInfo".length())
        : m.name();
    String callback = "d." + base + "Callback";
    String mode = cb.style() == CallbackStyle.CALLBACK_MODE ? "d." + base + "Mode" : null;
    Struct info = spec.struct(m.type().name());
    String at = addr(m);
    w.open("if (" + callback + " == null)");
    w.line("Mem.zero(" + at + ", " + spec.layouts().of(info.cName()).size() + "L);");
    w.middle("else");
    FfmCallbackEmitter.writeInfo(w, spec, info, cb, at, callback, mode);
    w.close();
  }

  private void read(SourceWriter w, Struct s, Member m) {
    CType t = m.type();
    String f = "d." + field(m);
    String at = addr(m);
    switch (m.role()) {
      case CHAIN_NEXT -> w.line("FfmChains.read(Mem.getLong(" + at + "), d.nextInChain);");
      case CHAIN_HEADER, USERDATA, SELF, ARRAY_COUNT -> {
      }
      case ARRAY_DATA -> readArray(w, s, m);
      case CALLBACK_INFO -> throw new IllegalStateException("cannot read a callback info: " + m.name());
      case VALUE -> {
        if (t.isPointer()) {
          if (t.kind() == TypeKind.VOID) {
            w.line(f + " = Mem.getLong(" + at + ");");
            return;
          }
          throw new IllegalStateException("cannot read the pointer field " + s.cName() + "." + m.name());
        }
        switch (t.kind()) {
          case BOOL -> w.line(f + " = Mem.getInt(" + at + ") != 0;");
          case UINT32, INT32 -> w.line(f + " = Mem.getInt(" + at + ");");
          case UINT16 -> w.line(f + " = Mem.getShort(" + at + ") & 0xFFFF;");
          case INT16 -> w.line(f + " = Mem.getShort(" + at + ");");
          case UINT64, USIZE, BITFLAG -> w.line(f + " = Mem.getLong(" + at + ");");
          case FLOAT32 -> w.line(f + " = Mem.getFloat(" + at + ");");
          case FLOAT64 -> w.line(f + " = Mem.getDouble(" + at + ");");
          case ENUM -> {
            w.imp(Names.pkg() + "." + Names.type(t.name()));
            w.line(f + " = " + Names.type(t.name()) + ".of(Mem.getInt(" + at + "));");
          }
          case OBJECT -> readObject(w, t, f, at);
          case STRUCT -> {
            if (t.name().equals(Types.STRING_VIEW)) {
              w.line(f + " = Mem.stringView(" + at + ");");
            } else {
              w.line(FfmNames.struct(t.name()) + ".read(" + at + ", " + f + ");");
            }
          }
          default -> throw new IllegalStateException("unsupported field " + m.name() + " of type " + t);
        }
      }
    }
  }

  private void readObject(SourceWriter w, CType t, String f, String at) {
    String api = Names.type(t.name());
    String ffm = FfmNames.object(t.name());
    w.open("");
    w.line("long h = Mem.getLong(" + at + ");");
    w.open("if (h != 0L)");
    w.open("if (" + f + " instanceof " + ffm + " w)");
    w.line("w.assign(h);");
    w.middle("else");
    w.line(f + " = new " + ffm + "(h);");
    w.close();
    w.close();
    w.close();
    w.imp(Names.pkg() + "." + api);
  }

  private void readArray(SourceWriter w, Struct s, Member m) {
    Member count = s.field(m.peer());
    CType element = m.type().withPointer(CType.Pointer.NONE);
    String f = "d." + field(m);
    String n = "d." + field(count);
    String javaElement = types.java(element);
    w.imp(Names.pkg() + "." + javaElement);
    w.open("");
    w.line("int n = (int) Mem.getLong(a + " + FfmNames.offset(count.name()) + ");");
    w.line("long p = Mem.getLong(" + addr(m) + ");");
    w.open("if (" + f + " == null || " + f + ".length < n)");
    w.line(f + " = new " + javaElement + "[n];");
    if (element.kind() == TypeKind.STRUCT) {
      w.open("for (int i = 0; i < n; i++)");
      w.line(f + "[i] = new " + javaElement + "();");
      w.close();
    }
    w.close();
    w.open("for (int i = 0; i < n; i++)");
    switch (element.kind()) {
      case STRUCT -> w.line(FfmNames.struct(element.name()) + ".read(p + i * " + FfmNames.struct(element.name())
          + ".SIZE, " + f + "[i]);");
      case ENUM -> w.line(f + "[i] = " + javaElement + ".of(Mem.getInt(p + i * 4L));");
      default -> throw new IllegalStateException("unsupported array element " + javaElement + " in " + m.name());
    }
    w.close();
    w.line(n + " = n;");
    w.close();
  }

  List<GeneratedFile> emitAll() {
    return spec.structs().stream()
        .filter(s -> s.kind() != StructKind.BUILTIN && s.kind() != StructKind.CALLBACK_INFO)
        .filter(s -> use.isWritten(s.cName()) || use.isRead(s.cName()) || use.isOut(s.cName()))
        .map(this::emit)
        .toList();
  }
}
