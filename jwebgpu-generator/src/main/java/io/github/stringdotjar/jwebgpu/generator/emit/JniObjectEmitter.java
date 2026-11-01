package io.github.stringdotjar.jwebgpu.generator.emit;

import io.github.stringdotjar.jwebgpu.generator.model.FunctionKind;
import io.github.stringdotjar.jwebgpu.generator.model.GpuFunction;
import io.github.stringdotjar.jwebgpu.generator.model.GpuObject;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates the implementations of the API objects ({@code JniGpuQueue}) and of the backend ({@code JniGpuBackend}).
 *
 * <p>An object wrapper holds nothing but the native handle, as a plain {@code long}. Every method checks that the
 * wrapper is alive, marshals its arguments into scratch memory, and calls the native function, which is a static
 * {@code native} method of the class {@code Jni}.
 */
final class JniObjectEmitter {

  private final Spec spec;
  private final JniMethods methods;

  JniObjectEmitter(Spec spec, JniMethods methods) {
    this.spec = spec;
    this.methods = methods;
  }

  GeneratedFile emit(GpuObject o) {
    String api = Names.type(o.cName());
    String name = JniNames.object(o.cName());
    String words = Names.words(o.cName());
    SourceWriter w = new SourceWriter(JniNames.PKG);
    w.imp(Names.pkg() + "." + api);
    w.doc("The Android implementation of {@link " + api + "}, built on Dawn's {@code " + o.cName() + "}.\n\n"
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
    for (GpuFunction f : o.methods()) {
      if (JniMethods.isMethod(f)) {
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
    w.line("Jni." + release.cName() + "(a);");
    w.close();
    w.close();
    w.blank();
    w.line("@Override");
    w.open("public void addRef()");
    w.line("Jni." + addRef.cName() + "(live());");
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
    w.line("throw new IllegalArgumentException(\"The " + api + " was not created by the JNI backend\");");
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
    w.line("throw new IllegalArgumentException(\"The " + api + " was not created by the JNI backend\");");
    w.close();
    w.blank();
    w.doc("Stores a new handle in this wrapper and releases the handle it held before.\n\n"
        + "@param handle The new native handle.");
    w.open("void assign(long handle)");
    w.line("long old = address;");
    w.line("address = handle;");
    w.open("if (old != 0L)");
    w.line("Jni." + release.cName() + "(old);");
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
    w.close();
    return new GeneratedFile(JniNames.path(name), w.toSource());
  }

  GeneratedFile emitBackend() {
    SourceWriter w = new SourceWriter(JniNames.PKG);
    w.imp(Names.pkg() + ".GpuBackend");
    w.doc("The Android backend: the functions of the WebGPU API that belong to no object.\n\n"
        + "<p>Install it once with {@link JWebGpuJni#install()}. It creates the first instance, and every other "
        + "object comes from that instance.\n\n<p>This class is generated from the specification.");
    w.openType("public final class JniGpuBackend implements GpuBackend");
    w.doc("Creates the backend. Use {@link JWebGpuJni#install()} instead of calling this yourself.");
    w.line("public JniGpuBackend() {}");
    w.blank();
    w.line("@Override");
    w.open("public String name()");
    w.line("return \"jni\";");
    w.close();
    w.blank();
    for (GpuFunction f : spec.globalFunctions()) {
      if (JniMethods.isMethod(f)) {
        methods.emit(w, f, false, false);
      }
    }
    w.close();
    return new GeneratedFile(JniNames.path("JniGpuBackend"), w.toSource());
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
