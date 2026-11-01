package io.github.stringdotjar.jwebgpu.generator.emit;

import io.github.stringdotjar.jwebgpu.generator.model.CType;
import io.github.stringdotjar.jwebgpu.generator.model.Callback;
import io.github.stringdotjar.jwebgpu.generator.model.FunctionKind;
import io.github.stringdotjar.jwebgpu.generator.model.GpuFunction;
import io.github.stringdotjar.jwebgpu.generator.model.Member;
import io.github.stringdotjar.jwebgpu.generator.model.MemberRole;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.TypeKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Writes the Java methods of the FFM backend, one per method of the API, and the native call wrappers behind them.
 *
 * <p>The methods have exactly the shape that the API emitter gives the abstract methods (the Java compiler checks
 * that, because the backend classes are not abstract):
 *
 * <ul>
 *   <li>A struct pointer is a descriptor object, copied into scratch memory right before the call.
 *   <li>An output struct is zeroed in scratch memory, filled by the call and read back into the caller's object.
 *   <li>An array and its count are {@code (Type[] items, int count)}.
 *   <li>Raw data is a {@code ByteBuffer} or an array with an offset and a count, copied into scratch memory (a direct
 *       buffer is passed without a copy).
 *   <li>A callback info becomes a mode and a callback. The callback is stored in the callback table, and its slot
 *       number travels as {@code userdata1}.
 *   <li>A method that returns an object also has a refilling variant that takes the wrapper to reuse.
 * </ul>
 *
 * <p>Nothing here allocates on the call path, except where the API returns a new wrapper or a buffer.
 */
final class FfmMethods {

  private static final Pattern USES_SCRATCH = Pattern.compile("(^|[^A-Za-z0-9_.])s[.,)]");
  private static final Set<String> RESERVED = Set.of("self", "s", "r", "t", "h", "i", "a");
  private static final String[][] DATA_ARRAYS = { { "byte", "1L", "Bytes" }, { "short", "2L", "Shorts" },
      { "int", "4L", "Ints" }, { "float", "4L", "Floats" } };

  private final Spec spec;
  private final Types types;

  FfmMethods(Spec spec, Types types) {
    this.spec = spec;
    this.types = types;
  }

  static boolean isMethod(GpuFunction f) {
    return f.kind() == FunctionKind.NORMAL || f.kind() == FunctionKind.GET_PROC_ADDRESS;
  }

  /**
   * Writes every overload of a method.
   *
   * @param w The writer of the class.
   * @param f The C function.
   * @param hasSelf Whether the method belongs to an object (it then has a receiver and checks it is alive).
   * @param refills Whether a method that returns an object also gets the refilling overload.
   */
  void emit(SourceWriter w, GpuFunction f, boolean hasSelf, boolean refills) {
    boolean hasData = false;
    for (Member m : f.params()) {
      hasData |= FfmNames.isDataPointer(m);
    }
    List<String[]> datas = new ArrayList<>();
    if (hasData) {
      datas.add(null);
      datas.addAll(List.of(DATA_ARRAYS));
    } else {
      datas.add(new String[0]);
    }
    for (String[] data : datas) {
      emitOne(w, f, hasSelf, data, false);
      if (refills && f.returns() != null && f.returns().kind() == TypeKind.OBJECT && !f.isAsync()) {
        emitOne(w, f, hasSelf, data, true);
      }
    }
  }

  /**
   * Writes the nested class that holds the method handles of some functions, with one static wrapper per function.
   *
   * @param w The writer of the class.
   * @param functions The functions that the class calls.
   */
  void emitFn(SourceWriter w, List<GpuFunction> functions) {
    w.imp("java.lang.foreign.FunctionDescriptor");
    w.imp("java.lang.foreign.MemorySegment");
    w.imp("java.lang.foreign.ValueLayout");
    w.imp("java.lang.invoke.MethodHandle");
    w.doc("Holds the method handles of the native functions this class calls.\n\n"
        + "<p>The handles are created when this nested class loads, which is the first time a method needs one. "
        + "Each wrapper has the raw types of the C function and is small enough for the JIT to inline.");
    w.openType("private static final class Fn");
    for (GpuFunction f : functions) {
      w.line("static final MethodHandle " + FfmNames.handle(f.cName()) + " = Native.handle(\"" + f.cName() + "\", "
          + descriptor(f) + ");");
    }
    w.blank();
    w.line("private Fn() {}");
    w.blank();
    for (GpuFunction f : functions) {
      emitWrapper(w, f);
    }
    w.close();
  }

  private String descriptor(GpuFunction f) {
    List<String> layouts = new ArrayList<>();
    for (Member m : f.params()) {
      layouts.add(FfmNames.layout(m.type(), m.role()));
    }
    if (f.returns() == null) {
      return "FunctionDescriptor.ofVoid(" + String.join(", ", layouts) + ")";
    }
    String ret = FfmNames.layout(f.returns(), MemberRole.VALUE);
    return layouts.isEmpty()
        ? "FunctionDescriptor.of(" + ret + ")"
        : "FunctionDescriptor.of(" + ret + ", " + String.join(", ", layouts) + ")";
  }

  private void emitWrapper(SourceWriter w, GpuFunction f) {
    List<String> params = new ArrayList<>();
    List<String> args = new ArrayList<>();
    for (int i = 0; i < f.params().size(); i++) {
      Member m = f.params().get(i);
      params.add(FfmNames.raw(m.type(), m.role()) + " a" + i);
      args.add("a" + i);
    }
    String ret = f.returns() == null ? "void" : FfmNames.raw(f.returns(), MemberRole.VALUE);
    String call = FfmNames.handle(f.cName()) + ".invokeExact(" + String.join(", ", args) + ")";
    w.open("static " + ret + " " + f.cName() + "(" + String.join(", ", params) + ")");
    w.open("try");
    w.line(ret.equals("void") ? call + ";" : "return (" + ret + ") " + call + ";");
    w.middle("catch (Throwable e)");
    w.line("throw Native.fail(e);");
    w.close();
    w.close();
    w.blank();
  }

  private void emitOne(SourceWriter w, GpuFunction f, boolean hasSelf, String[] data, boolean refill) {
    String name = Names.method(f.specName());
    List<String> jp = new ArrayList<>();
    List<String> pre = new ArrayList<>();
    List<String> post = new ArrayList<>();
    List<String> args = new ArrayList<>();
    int[] tmp = { 0 };
    boolean waitAny = f.cName().equals("wgpuInstanceWaitAny");
    List<Member> ps = f.params();

    for (int i = 0; i < ps.size(); i++) {
      Member m = ps.get(i);
      CType t = m.type();
      checkName(m.name());
      switch (m.role()) {
        case SELF -> args.add("self");
        case CHAIN_HEADER, CHAIN_NEXT, USERDATA -> {
        }
        case ARRAY_COUNT -> args.add(m.name());
        case ARRAY_DATA -> array(w, m, jp, pre, args, tmp);
        case CALLBACK_INFO -> callback(w, f, m, ps, jp, pre, args);
        case VALUE -> {
          if (FfmNames.isDataPointer(m)) {
            i++;
            data(w, m, ps.get(i), data, jp, pre, post, args, tmp);
          } else if (waitAny && m.name().equals("futureCount")) {
            args.add("futureCount");
          } else if (waitAny && m.name().equals("futures")) {
            futures(w, jp, pre, post, args, tmp);
          } else {
            value(w, m, t, jp, pre, post, args, tmp);
          }
        }
      }
    }

    String ffm = null;
    String retType = "void";
    CType r = f.returns();
    if (f.isAsync() || (r != null && r.kind() == TypeKind.STRUCT && r.name().equals("WGPUFuture"))) {
      retType = "long";
    } else if (r != null) {
      retType = switch (r.kind()) {
        case OBJECT -> {
          ffm = FfmNames.object(r.name());
          yield FfmNames.type(w, Names.type(r.name()));
        }
        case ENUM -> FfmNames.type(w, Names.type(r.name()));
        case BOOL -> "boolean";
        case VOID -> FfmNames.type(w, "ByteBuffer");
        default -> types.java(r);
      };
    }
    if (refill) {
      jp.add(FfmNames.type(w, retType) + " into");
    }

    w.line("@Override");
    w.open("public " + retType + " " + name + "(" + String.join(", ", jp) + ")");
    if (refill) {
      w.line(ffm + " t = " + ffm + ".cast(into);");
    }
    if (hasSelf) {
      w.line("long self = live();");
    }
    boolean scratch = false;
    for (String line : pre) {
      scratch |= USES_SCRATCH.matcher(line).find();
    }
    for (String line : args) {
      scratch |= USES_SCRATCH.matcher(line).find();
    }
    if (scratch) {
      w.line("Scratch s = Scratch.begin();");
    }
    for (String line : pre) {
      w.line(line);
    }

    String call = "Fn." + f.cName() + "(" + String.join(", ", args) + ")";
    if (f.returns() == null) {
      w.line(call + ";");
      post.forEach(w::line);
      w.close();
      w.blank();
      return;
    }
    String rawRet = FfmNames.raw(r, MemberRole.VALUE);
    if (f.isAsync()) {
      w.line("return " + call + ";");
      w.close();
      w.blank();
      return;
    }
    w.line(rawRet + " r = " + call + ";");
    post.forEach(w::line);
    switch (r.kind()) {
      case OBJECT -> {
        w.open("if (r == 0L)");
        if (f.returnNullable()) {
          w.line("return null;");
        } else {
          w.line("throw new IllegalStateException(\"" + f.cName() + " returned no object\");");
        }
        w.close();
        if (refill) {
          w.line("t.assign(r);");
          w.line("return t;");
        } else {
          w.line("return new " + ffm + "(r);");
        }
      }
      case ENUM -> w.line("return " + Names.type(r.name()) + ".of(r);");
      case BOOL -> w.line("return r != 0;");
      case VOID -> {
        boolean readOnly = r.pointer() == CType.Pointer.CONST;
        w.line("return Mem.mapped(r, size == -1L ? Fn.wgpuBufferGetSize(self) - offset : size, " + readOnly + ");");
      }
      default -> w.line("return r;");
    }
    w.close();
    w.blank();
  }

  private static void checkName(String name) {
    if (RESERVED.contains(name) || name.matches("n[0-9]+")) {
      throw new IllegalStateException("parameter name '" + name + "' clashes with a local variable of the backend");
    }
  }

  private void array(SourceWriter w, Member m, List<String> jp, List<String> pre, List<String> args, int[] tmp) {
    CType element = m.type().withPointer(CType.Pointer.NONE);
    String count = m.peer();
    String n = "n" + tmp[0]++;
    jp.add(FfmNames.type(w, types.java(element)) + "[] " + m.name());
    jp.add("int " + count);
    pre.add("long " + n + " = 0L;");
    pre.add("if (" + count + " > 0) {");
    switch (element.kind()) {
      case OBJECT -> {
        pre.add("  " + n + " = s.alloc(" + count + " * 8L);");
        pre.add("  for (int i = 0; i < " + count + "; i++) {");
        pre.add("    Mem.putLong(" + n + " + i * 8L, " + FfmNames.object(element.name()) + ".addr(" + m.name()
            + "[i]));");
        pre.add("  }");
      }
      case UINT32 -> {
        pre.add("  " + n + " = s.alloc(" + count + " * 4L);");
        pre.add("  Mem.putInts(" + n + ", " + m.name() + ", 0, " + count + ");");
      }
      default -> throw new IllegalStateException("unsupported array of " + element + " in " + m.name());
    }
    pre.add("}");
    args.add(n);
  }

  private void callback(SourceWriter w, GpuFunction f, Member m, List<Member> ps, List<String> jp,
      List<String> pre, List<String> args) {
    Callback cb = types.callbackByInfo(m.type().name());
    String modeName = "mode";
    for (Member other : ps) {
      if (other.name().equals("mode")) {
        modeName = "callbackMode";
      }
    }
    jp.add("GpuCallbackMode " + modeName);
    w.imp(Names.pkg() + ".GpuCallbackMode");
    jp.add(FfmNames.type(w, Names.type(cb.cName())) + " callback");
    pre.add("if (callback == null) {");
    pre.add("  throw new NullPointerException(\"callback\");");
    pre.add("}");
    var info = spec.struct(m.type().name());
    var offsets = spec.layouts().of(info.cName()).offsets();
    pre.add("long cb = s.callbackAddress;");
    for (int i = 0; i < info.fields().size(); i++) {
      String where = "cb + " + offsets.get(i) + "L";
      switch (info.fields().get(i).name()) {
        case "nextInChain" -> pre.add("Mem.putLong(" + where + ", 0L);");
        case "mode" -> pre.add("Mem.putInt(" + where + ", " + modeName + ".value);");
        case "callback" -> pre.add("Mem.putLong(" + where + ", FfmCallbacks." + FfmCallbackEmitter.stub(cb) + ");");
        case "userdata1" -> pre.add("Mem.putLong(" + where + ", CallbackTable.put(callback));");
        case "userdata2" -> pre.add("Mem.putLong(" + where + ", 0L);");
        default -> throw new IllegalStateException("unexpected callback info field");
      }
    }
    args.add("s.callbackSegment");
  }

  private void futures(SourceWriter w, List<String> jp, List<String> pre, List<String> post, List<String> args,
      int[] tmp) {
    String n = "n" + tmp[0]++;
    String st = FfmNames.struct("WGPUFutureWaitInfo");
    jp.add(FfmNames.type(w, "GpuFutureWaitInfo[]") + " futures");
    jp.add("int futureCount");
    pre.add("long " + n + " = 0L;");
    pre.add("if (futureCount > 0) {");
    pre.add("  " + n + " = s.alloc(futureCount * " + st + ".SIZE);");
    pre.add("  for (int i = 0; i < futureCount; i++) {");
    pre.add("    " + st + ".write(s, futures[i], " + n + " + i * " + st + ".SIZE);");
    pre.add("  }");
    pre.add("}");
    post.add("for (int i = 0; i < futureCount; i++) {");
    post.add("  " + st + ".read(" + n + " + i * " + st + ".SIZE, futures[i]);");
    post.add("}");
    args.add(n);
  }

  private void data(SourceWriter w, Member m, Member size, String[] data, List<String> jp, List<String> pre,
      List<String> post, List<String> args, int[] tmp) {
    boolean writable = m.type().pointer() == CType.Pointer.MUTABLE;
    String d = m.name();
    String p = "n" + tmp[0]++;
    if (data == null) {
      jp.add(FfmNames.type(w, "ByteBuffer") + " " + d);
      pre.add("long " + p + " = Mem." + (writable ? "output" : "input") + "(s, " + d + ");");
      if (writable) {
        post.add("Mem.copyBack(" + d + ", " + p + ");");
      }
      args.add(p);
      args.add(d + ".remaining()");
      return;
    }
    jp.add(data[0] + "[] " + d);
    jp.add("int dataOffset");
    jp.add("int dataCount");
    pre.add("long " + p + " = s.alloc(dataCount * " + data[1] + ");");
    if (writable) {
      post.add("Mem.get" + data[2] + "(" + p + ", " + d + ", dataOffset, dataCount);");
    } else {
      pre.add("Mem.put" + data[2] + "(" + p + ", " + d + ", dataOffset, dataCount);");
    }
    args.add(p);
    args.add("dataCount * " + data[1]);
  }

  private void value(SourceWriter w, Member m, CType t, List<String> jp, List<String> pre, List<String> post,
      List<String> args, int[] tmp) {
    String n = m.name();
    switch (t.kind()) {
      case OBJECT -> {
        jp.add(FfmNames.type(w, Names.type(t.name())) + " " + n);
        String ffm = FfmNames.object(t.name());
        args.add(ffm + "." + (m.nullable() ? "addrOrNull" : "addr") + "(" + n + ")");
      }
      case STRUCT -> {
        if (t.name().equals(Types.STRING_VIEW)) {
          jp.add("String " + n);
          args.add("s.view(" + n + ")");
        } else if (t.pointer() == CType.Pointer.MUTABLE) {
          String api = FfmNames.type(w, Names.type(t.name()));
          String st = FfmNames.struct(t.name());
          String p = "n" + tmp[0]++;
          jp.add(api + " " + n);
          pre.add("long " + p + " = s.alloc(" + st + ".SIZE);");
          pre.add(st + ".init(s, " + n + ", " + p + ");");
          post.add(st + ".read(" + p + ", " + n + ");");
          if (spec.struct(t.name()).freeMembers()) {
            post.add(st + ".freeMembers(" + p + ");");
          }
          args.add(p);
        } else {
          String api = FfmNames.type(w, Names.type(t.name()));
          String st = FfmNames.struct(t.name());
          jp.add(api + " " + n);
          args.add(
              m.nullable() ? "(" + n + " == null ? 0L : " + st + ".put(s, " + n + "))" : st + ".put(s, " + n + ")");
        }
      }
      case ENUM -> {
        jp.add(FfmNames.type(w, Names.type(t.name())) + " " + n);
        args.add("(" + n + " == null ? 0 : " + n + ".value)");
      }
      case BOOL -> {
        jp.add("boolean " + n);
        args.add("(" + n + " ? 1 : 0)");
      }
      default -> {
        jp.add(types.java(t) + " " + n);
        args.add(n);
      }
    }
  }
}
