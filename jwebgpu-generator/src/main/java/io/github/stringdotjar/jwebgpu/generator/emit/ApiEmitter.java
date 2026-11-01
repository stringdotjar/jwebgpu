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
import io.github.stringdotjar.jwebgpu.generator.model.Callback;
import io.github.stringdotjar.jwebgpu.generator.model.EnumType;
import io.github.stringdotjar.jwebgpu.generator.model.GpuObject;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;
import io.github.stringdotjar.jwebgpu.generator.model.StructKind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates the platform-neutral {@code jwebgpu-api} module from the model.
 *
 * <p>The emitter is a pure function: the resolved specification goes in, and a list of source files comes out. It
 * writes these files into the package {@code io.github.stringdotjar.jwebgpu}:
 *
 * <ul>
 *   <li>one Java {@code enum} per C enum ({@code GpuTextureFormat}),
 *   <li>one final class of {@code long} constants per bit flag type ({@code GpuTextureUsage}),
 *   <li>{@code GpuConstants} with the sentinel values,
 *   <li>one mutable descriptor class per struct ({@code GpuRenderPassDescriptor}), plus the abstract base class
 *       {@code GpuChainedStruct} for extension structs,
 *   <li>one abstract class per object ({@code GpuDevice}) on top of the common base class {@code GpuObject},
 *   <li>one functional interface per callback ({@code GpuRequestAdapterCallback}),
 *   <li>and the {@code GpuBackend} interface for the global functions.
 * </ul>
 *
 * <p>Example:
 *
 * <pre>{@code
 * Spec spec = SpecLoader.load(Path.of("spec/webgpu.json"));
 * for (GeneratedFile file : ApiEmitter.emit(spec)) {
 *   Files.writeString(out.resolve(file.path()), file.source());
 * }
 * }</pre>
 */
public final class ApiEmitter {

  private ApiEmitter() {}

  /**
   * Generates every source file of the API.
   *
   * @param spec The resolved specification.
   * @return The files, in a stable order. The result is the same every time for the same specification.
   * @throws IllegalStateException If two generated types would get the same name.
   */
  public static List<GeneratedFile> emit(Spec spec) {
    Docs docs = new Docs(spec);
    Types types = new Types(spec);
    MethodWriter methods = new MethodWriter(docs, types);
    EnumEmitter enums = new EnumEmitter(docs);
    BitflagEmitter flags = new BitflagEmitter(docs);
    StructEmitter structs = new StructEmitter(spec, docs, types);
    ObjectEmitter objects = new ObjectEmitter(docs, methods);
    CallbackEmitter callbacks = new CallbackEmitter(docs, types);

    List<GeneratedFile> files = new ArrayList<>();
    for (EnumType e : spec.enums()) {
      files.add(enums.emit(e));
    }
    for (BitflagType b : spec.bitflags()) {
      files.add(flags.emit(b));
    }
    files.add(new ConstantsEmitter(docs).emit(spec.constants()));
    files.add(objects.emitChainedStruct());
    for (Struct s : spec.structs()) {
      if (s.kind() != StructKind.BUILTIN && s.kind() != StructKind.CALLBACK_INFO) {
        files.add(structs.emit(s));
      }
    }
    files.add(objects.emitBase());
    for (GpuObject o : spec.objects()) {
      files.add(objects.emit(o));
    }
    for (Callback c : spec.callbacks()) {
      files.add(callbacks.emit(c));
    }
    files.add(new BackendEmitter(spec, methods).emit());

    Set<String> paths = new HashSet<>();
    for (GeneratedFile f : files) {
      if (!paths.add(f.path())) {
        throw new IllegalStateException("two generated files have the path " + f.path());
      }
    }
    return files;
  }
}
