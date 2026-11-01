package io.github.stringdotjar.jwebgpu.generator.emit;

import io.github.stringdotjar.jwebgpu.generator.model.GpuObject;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates the Java part of the Android JNI backend ({@code jwebgpu-jni}) from the model.
 *
 * <p>The emitter is a pure function: the resolved specification goes in, and a list of source files comes out. They
 * go into the package {@code io.github.stringdotjar.jwebgpu.jni} and complete the hand-written part of the module
 * (the loader, the scratch memory and the helper functions):
 *
 * <ul>
 *   <li>one {@code JniGpu...} class per API object, plus {@code JniGpuBackend} for the global functions,
 *   <li>one {@code JniGpu...Struct} class per struct the backend writes or reads,
 *   <li>{@code JniChains}, which writes and reads the lists of extension structs,
 *   <li>{@code JniCallbacks}, which receives the native callbacks,
 *   <li>{@code Jni}, which declares the {@code native} methods that the C glue (see {@link JniCEmitter})
 *       implements.
 * </ul>
 *
 * <p>The same sources also compile for the desktop Java virtual machine, which is how the unit tests run the JNI
 * backend. Only the {@code @FastNative} annotation of ART is Android-only, so it is written when the emitter is asked
 * for the Android flavor.
 *
 * <p>Example:
 *
 * <pre>{@code
 * Spec spec = SpecLoader.load(Path.of("spec/webgpu.json"));
 * for (GeneratedFile file : JniJavaEmitter.emit(spec, false)) {
 *   Files.writeString(out.resolve(file.path()), file.source());
 * }
 * }</pre>
 */
public final class JniJavaEmitter {

  private JniJavaEmitter() {}

  /**
   * Generates every source file of the backend.
   *
   * @param spec The resolved specification.
   * @param android Whether to mark the cheap native methods with ART's {@code @FastNative} annotation.
   * @return The files, in a stable order. The result is the same every time for the same specification.
   * @throws IllegalStateException If two generated types would get the same name.
   */
  public static List<GeneratedFile> emit(Spec spec, boolean android) {
    Types types = new Types(spec);
    FfmUse use = new FfmUse(spec);
    JniMethods methods = new JniMethods(spec, types);
    JniObjectEmitter objects = new JniObjectEmitter(spec, methods);
    JniStructEmitter structs = new JniStructEmitter(spec, types, use);

    List<GeneratedFile> files = new ArrayList<>();
    for (GpuObject o : spec.objects()) {
      files.add(objects.emit(o));
    }
    files.add(objects.emitBackend());
    files.addAll(structs.emitAll());
    files.add(new JniChainEmitter(spec, use).emit());
    files.add(new JniCallbackEmitter(spec, types).emit());
    files.add(new JniNativeEmitter(spec, android).emit());

    Set<String> paths = new HashSet<>();
    for (GeneratedFile f : files) {
      if (!paths.add(f.path())) {
        throw new IllegalStateException("two generated files have the path " + f.path());
      }
    }
    return files;
  }
}
