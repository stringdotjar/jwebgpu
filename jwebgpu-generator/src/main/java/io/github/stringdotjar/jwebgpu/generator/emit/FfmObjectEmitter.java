package io.github.stringdotjar.jwebgpu.generator.emit;

import io.github.stringdotjar.jwebgpu.generator.model.FunctionKind;
import io.github.stringdotjar.jwebgpu.generator.model.GpuFunction;
import io.github.stringdotjar.jwebgpu.generator.model.GpuObject;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates the implementations of the API objects ({@code FfmGpuQueue}) and of the backend ({@code FfmGpuBackend}).
 *
 * <p>An object wrapper holds nothing but the native handle, as a plain {@code long}. Every method checks that the
 * wrapper is alive, marshals its arguments into scratch memory, and calls the native function through a small static
 * wrapper in a nested {@code Fn} class. The nested class is what keeps the downcall handles lazy: a program that
 * never touches a queue never binds the queue functions.
 */
final class FfmObjectEmitter {

  private final Spec spec;
  private final FfmMethods methods;

  FfmObjectEmitter(Spec spec, FfmMethods methods) {
    this.spec = spec;
    this.methods = methods;
  }

  GeneratedFile emit(GpuObject o) {
    String api = Names.type(o.cName());
    String name = FfmNames.object(o.cName());
    String words = Names.words(o.cName());
    SourceWriter w = new SourceWriter(FfmNames.PKG);
    w.imp(Names.pkg() + "." + api);
    w.doc("The desktop implementation of {@link " + api + "}, built on Dawn's {@code " + o.cName() + "}.\n\n"
        + "<p>The wrapper stores only the native handle, as a {@code long}. Calls are marshalled into thread-local "
        + "scratch memory, so they allocate nothing. Methods that create objects also come in a refilling variant "
        + "that reuses an existing wrapper, so per-frame objects need no Java allocation either.\n\n"
        + "<p>This class is generated from the specification.");
    w.openType("public final class " + name + " extends " + api);
    w.line("private long address;");
    w.blank();
    w.doc("Creates an empty wrapper that holds no native object.\n\n"
        + "<p>It reports {@link #isReleased()} until a refilling method stores a handle in it.");
    w.line("public " + name + "() {}");
    w.blank();
    w.doc("Wraps a native handle. The wrapper owns the reference, so release it when done.\n\n"
        + "@param address The native {@code " + o.cName() + "} handle. It must not be 0.");
    w.open("public " + name + "(long address)");
    w.line("this.address = address;");
    w.close();
    w.blank();

    List<GpuFunction> normal = new ArrayList<>();
    List<GpuFunction> getters = new ArrayList<>();
    List<GpuFunction> all = new ArrayList<>();
    for (GpuFunction f : FfmDescriptors.functions(o)) {
      all.add(f);
      if (FfmMethods.isMethod(f)) {
        (MethodWriter.isSimpleGetter(f) ? getters : normal).add(f);
      }
    }
    for (GpuFunction f : normal) {
      methods.emit(w, f, true, true);
    }
    for (GpuFunction f : getters) {
      methods.emit(w, f, true, true);
    }

    GpuFunction release = find(o, FunctionKind.RELEASE);
    GpuFunction addRef = find(o, FunctionKind.ADD_REF);
    w.line("@Override");
    w.open("public void release()");
    w.line("long a = address;");
    w.open("if (a != 0L)");
    w.line("address = 0L;");
    w.line("Fn." + release.cName() + "(a);");
    w.close();
    w.close();
    w.blank();
    w.line("@Override");
    w.open("public void addRef()");
    w.line("Fn." + addRef.cName() + "(live());");
    w.close();
    w.blank();
    w.line("@Override");
    w.open("public boolean isReleased()");
    w.line("return address == 0L;");
    w.close();
    w.blank();

    w.doc("Returns the native handle of a wrapper that was created by this backend.\n\n"
        + "@param o The object. It must not be null or released.\n@return The native handle.\n"
        + "@throws IllegalArgumentException If the object belongs to another backend.\n"
        + "@throws IllegalStateException If the object was released.");
    w.open("static long addr(" + api + " o)");
    w.open("if (o instanceof " + name + " f)");
    w.line("long a = f.address;");
    w.open("if (a == 0L)");
    w.line("throw new IllegalStateException(\"The " + api + " was released\");");
    w.close();
    w.line("return a;");
    w.close();
    w.open("if (o == null)");
    w.line("throw new NullPointerException(\"" + words + "\");");
    w.close();
    w.line("throw new IllegalArgumentException(\"The " + api + " was not created by the FFM backend\");");
    w.close();
    w.blank();
    w.doc("Returns the native handle of a wrapper, or 0 for null.\n\n@param o The object, or null.\n"
        + "@return The native handle, or 0.");
    w.open("static long addrOrNull(" + api + " o)");
    w.line("return o == null ? 0L : addr(o);");
    w.close();
    w.blank();
    w.doc("Checks that an object was created by this backend.\n\n@param o The object. It must not be null.\n"
        + "@return The same object, as the backend type.\n"
        + "@throws IllegalArgumentException If the object belongs to another backend.");
    w.open("static " + name + " cast(" + api + " o)");
    w.open("if (o instanceof " + name + " f)");
    w.line("return f;");
    w.close();
    w.open("if (o == null)");
    w.line("throw new NullPointerException(\"into\");");
    w.close();
    w.line("throw new IllegalArgumentException(\"The " + api + " was not created by the FFM backend\");");
    w.close();
    w.blank();
    w.doc("Stores a new handle in this wrapper and releases the handle it held before.\n\n"
        + "@param handle The new native handle.");
    w.open("void assign(long handle)");
    w.line("long old = address;");
    w.line("address = handle;");
    w.open("if (old != 0L)");
    w.line("Fn." + release.cName() + "(old);");
    w.close();
    w.close();
    w.blank();
    w.open("private long live()");
    w.line("long a = address;");
    w.open("if (a == 0L)");
    w.line("throw new IllegalStateException(\"The " + api + " was released\");");
    w.close();
    w.line("return a;");
    w.close();
    w.blank();
    w.doc("Returns the native handle.\n\n@return The handle, or 0 if the wrapper is empty or released.");
    w.open("public long getAddress()");
    w.line("return address;");
    w.close();
    w.blank();
    methods.emitFn(w, all);
    w.close();
    return new GeneratedFile(FfmNames.path(name), w.toSource());
  }

  GeneratedFile emitBackend() {
    SourceWriter w = new SourceWriter(FfmNames.PKG);
    w.imp(Names.pkg() + ".GpuBackend");
    w.doc("The desktop backend: the functions of the WebGPU API that belong to no object.\n\n"
        + "<p>Install it once with {@link JWebGpuFfm#install()}. It creates the first instance, and every other "
        + "object comes from that instance.\n\n<p>This class is generated from the specification.");
    w.openType("public final class FfmGpuBackend implements GpuBackend");
    w.doc("Creates the backend. Use {@link JWebGpuFfm#install()} instead of calling this yourself.");
    w.line("public FfmGpuBackend() {}");
    w.blank();
    w.line("@Override");
    w.open("public String name()");
    w.line("return \"ffm\";");
    w.close();
    w.blank();
    List<GpuFunction> globals = FfmDescriptors.globals(spec);
    for (GpuFunction f : globals) {
      methods.emit(w, f, false, false);
    }
    methods.emitFn(w, globals);
    w.close();
    return new GeneratedFile(FfmNames.path("FfmGpuBackend"), w.toSource());
  }

  private static GpuFunction find(GpuObject o, FunctionKind kind) {
    for (GpuFunction f : o.methods()) {
      if (f.kind() == kind) {
        return f;
      }
    }
    throw new IllegalStateException(o.cName() + " has no " + kind + " function");
  }
}
