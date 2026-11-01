package io.github.stringdotjar.jwebgpu.generator.emit;

import io.github.stringdotjar.jwebgpu.generator.model.CType;
import io.github.stringdotjar.jwebgpu.generator.model.Callback;
import io.github.stringdotjar.jwebgpu.generator.model.CallbackStyle;
import io.github.stringdotjar.jwebgpu.generator.model.Member;
import io.github.stringdotjar.jwebgpu.generator.model.MemberRole;
import io.github.stringdotjar.jwebgpu.generator.model.Naming;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;
import io.github.stringdotjar.jwebgpu.generator.model.TypeKind;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates {@code FfmCallbacks}, the class that turns native callbacks into calls of the Java callbacks.
 *
 * <p>There is one upcall stub per callback type, created once when the class loads. The stub is a plain native
 * function pointer. When the native library calls it, the matching static method looks up the Java callback in the
 * {@code CallbackTable} by the slot number in {@code userdata1}, converts the native arguments into API values and
 * calls the Java callback. Anything the Java callback throws is reported to the thread's uncaught exception handler,
 * because an exception must never travel back into native code.
 */
final class FfmCallbackEmitter {

  private final Spec spec;
  private final Types types;

  FfmCallbackEmitter(Spec spec, Types types) {
    this.spec = spec;
    this.types = types;
  }

  /**
   * Writes the statements that fill a callback info struct in native memory.
   *
   * @param w The writer of the file.
   * @param spec The specification.
   * @param info The callback info struct.
   * @param cb The callback type.
   * @param at A Java expression for the address of the struct.
   * @param callback A Java expression for the callback object (not null at this point).
   * @param mode A Java expression for the callback mode, or {@code null} if the struct has no mode.
   */
  static void writeInfo(SourceWriter w, Spec spec, Struct info, Callback cb, String at, String callback,
      String mode) {
    var offsets = spec.layouts().of(info.cName()).offsets();
    for (int i = 0; i < info.fields().size(); i++) {
      Member f = info.fields().get(i);
      String where = "(" + at + ") + " + offsets.get(i) + "L";
      switch (f.name()) {
        case "nextInChain" -> w.line("Mem.putLong(" + where + ", 0L);");
        case "mode" -> w.line("Mem.putInt(" + where + ", " + mode + " == null ? 0 : " + mode + ".value);");
        case "callback" -> w.line("Mem.putLong(" + where + ", FfmCallbacks." + stub(cb) + ");");
        case "userdata1" -> w.line("Mem.putLong(" + where + ", CallbackTable.put(" + callback + "));");
        case "userdata2" -> w.line("Mem.putLong(" + where + ", 0L);");
        default -> throw new IllegalStateException("unexpected callback info field " + f.name());
      }
    }
  }

  /**
   * Returns the name of the constant that holds the stub address of a callback.
   *
   * @param cb The callback.
   * @return For example {@code REQUEST_ADAPTER}.
   */
  static String stub(Callback cb) {
    return Names.snake(cb.specName());
  }

  GeneratedFile emit() {
    SourceWriter w = new SourceWriter(FfmNames.PKG);
    w.imp("java.lang.foreign.FunctionDescriptor");
    w.imp("java.lang.foreign.MemorySegment");
    w.imp("java.lang.foreign.ValueLayout");
    w.imp("java.lang.invoke.MethodHandles");
    w.doc("The native entry points of every callback, and the code that forwards them to Java.\n\n"
        + "<p>Each constant is the address of an upcall stub for one callback type. The stubs are created once, "
        + "so registering a callback allocates nothing. The matching private method converts the native arguments "
        + "and calls the Java callback that is stored in the {@code CallbackTable} under the slot number that the "
        + "native library hands back as {@code userdata1}.");
    w.openType("final class FfmCallbacks");

    for (Callback cb : spec.callbacks()) {
      w.line("static final long " + stub(cb) + " = Native.upcall(MethodHandles.lookup(), FfmCallbacks.class, \""
          + method(cb) + "\", " + descriptor(cb) + ");");
    }
    w.blank();
    w.line("private FfmCallbacks() {}");
    w.blank();

    Set<String> readers = new LinkedHashSet<>();
    for (Callback cb : spec.callbacks()) {
      emitDispatch(w, cb, readers);
    }
    for (String struct : readers) {
      String api = Names.type(struct);
      w.imp(Names.pkg() + "." + api);
      w.open("private static " + api + " read" + api.substring("Gpu".length()) + "(long p)");
      w.line(api + " value = new " + api + "();");
      w.line(FfmNames.struct(struct) + ".read(p, value);");
      w.line("return value;");
      w.close();
      w.blank();
    }
    w.close();
    return new GeneratedFile(FfmNames.path("FfmCallbacks"), w.toSource());
  }

  private static String method(Callback cb) {
    return "on" + Naming.pascal(cb.specName());
  }

  private String descriptor(Callback cb) {
    List<String> layouts = new ArrayList<>();
    for (Member m : cb.params()) {
      layouts.add(FfmNames.layout(m.type(), m.role()));
    }
    return "FunctionDescriptor.ofVoid(" + String.join(", ", layouts) + ")";
  }

  private void emitDispatch(SourceWriter w, Callback cb, Set<String> readers) {
    String api = Names.type(cb.cName());
    w.imp(Names.pkg() + "." + api);
    List<String> params = new ArrayList<>();
    List<String> args = new ArrayList<>();
    for (Member m : cb.params()) {
      params.add(FfmNames.raw(m.type(), m.role()) + " " + m.name());
      if (m.role() != MemberRole.USERDATA) {
        args.add(convert(w, m, readers));
      }
    }
    boolean persistent = cb.style() == CallbackStyle.IMMEDIATE;
    w.open("private static void " + method(cb) + "(" + String.join(", ", params) + ")");
    w.line("Object slot = CallbackTable." + (persistent ? "get" : "take") + "(userdata1);");
    w.open("if (!(slot instanceof " + api + " cb))");
    w.line("return;");
    w.close();
    w.line("Scratch sc = Scratch.current();");
    w.line("sc.enter();");
    w.open("try");
    w.call("cb." + method(cb) + "(", args, ");");
    w.middle("catch (Throwable e)");
    w.line("CallbackTable.report(e);");
    w.middle("finally");
    w.line("sc.leave();");
    w.close();
    w.close();
    w.blank();
  }

  private String convert(SourceWriter w, Member m, Set<String> readers) {
    CType t = m.type();
    String n = m.name();
    return switch (t.kind()) {
      case ENUM -> {
        w.imp(Names.pkg() + "." + Names.type(t.name()));
        yield Names.type(t.name()) + ".of(" + n + ")";
      }
      case OBJECT -> {
        w.imp(Names.pkg() + "." + Names.type(t.name()));
        String ffm = FfmNames.object(t.name());
        yield t.isPointer()
            ? "Mem.getLong(" + n + ") == 0L ? null : new " + ffm + "(Mem.getLong(" + n + "))"
            : n + " == 0L ? null : new " + ffm + "(" + n + ")";
      }
      case STRUCT -> {
        if (t.name().equals(Types.STRING_VIEW)) {
          yield "Mem.stringView(" + n + ")";
        }
        readers.add(t.name());
        yield "read" + Names.type(t.name()).substring("Gpu".length()) + "(" + n + ")";
      }
      default -> {
        if (t.kind() == TypeKind.VOID) {
          yield n;
        }
        throw new IllegalStateException("unsupported callback parameter " + m.name() + " of " + t);
      }
    };
  }
}
