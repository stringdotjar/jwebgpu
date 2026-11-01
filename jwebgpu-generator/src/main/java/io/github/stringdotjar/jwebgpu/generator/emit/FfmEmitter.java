package io.github.stringdotjar.jwebgpu.generator.emit;

import io.github.stringdotjar.jwebgpu.generator.model.GpuObject;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates the desktop FFM backend ({@code jwebgpu-ffm}) from the model.
 *
 * <p>The emitter is a pure function: the resolved specification goes in, and a list of source files comes out. They
 * go into the package {@code io.github.stringdotjar.jwebgpu.ffm} and complete the hand-written part of the module
 * (the loader, the scratch memory and the raw memory access):
 *
 * <ul>
 *   <li>one {@code FfmGpu...} class per API object, plus {@code FfmGpuBackend} for the global functions,
 *   <li>one {@code FfmGpu...Struct} class per struct the backend writes or reads,
 *   <li>{@code FfmChains} for chained extension structs,
 *   <li>{@code FfmCallbacks} with one upcall stub per callback type.
 * </ul>
 *
 * <p>Example:
 *
 * <pre>{@code
 * Spec spec = SpecLoader.load(Path.of("spec/webgpu.json"));
 * for (GeneratedFile file : FfmEmitter.emit(spec)) {
 *   Files.writeString(out.resolve(file.path()), file.source());
 * }
 * }</pre>
 */
public final class FfmEmitter {

  private FfmEmitter() {}

  /**
   * Generates every source file of the backend.
   *
   * @param spec The resolved specification.
   * @return The files, in a stable order. The result is the same every time for the same specification.
   * @throws IllegalStateException If two generated types would get the same name.
   */
  public static List<GeneratedFile> emit(Spec spec) {
    Types types = new Types(spec);
    FfmUse use = new FfmUse(spec);
    FfmMethods methods = new FfmMethods(spec, types);
    FfmObjectEmitter objects = new FfmObjectEmitter(spec, methods);
    FfmStructEmitter structs = new FfmStructEmitter(spec, types, use);

    List<GeneratedFile> files = new ArrayList<>();
    for (GpuObject o : spec.objects()) {
      files.add(objects.emit(o));
    }
    files.add(objects.emitBackend());
    files.addAll(structs.emitAll());
    files.add(new FfmChainEmitter(spec, use).emit());
    files.add(new FfmCallbackEmitter(spec, types).emit());

    Set<String> paths = new HashSet<>();
    for (GeneratedFile f : files) {
      if (!paths.add(f.path())) {
        throw new IllegalStateException("two generated files have the path " + f.path());
      }
    }
    return files;
  }
}
