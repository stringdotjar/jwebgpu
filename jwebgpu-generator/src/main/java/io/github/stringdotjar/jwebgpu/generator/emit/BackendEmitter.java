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

import io.github.stringdotjar.jwebgpu.generator.model.GpuFunction;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;

import java.util.List;

/** Writes {@code GpuBackend}, the interface that each platform backend implements. */
final class BackendEmitter {

  private final Spec spec;
  private final MethodWriter methods;

  BackendEmitter(Spec spec, MethodWriter methods) {
    this.spec = spec;
    this.methods = methods;
  }

  GeneratedFile emit() {
    SourceWriter w = new SourceWriter(Names.pkg());
    w.doc("The contract between the API and one platform backend.\n\n"
        + "<p>A backend (desktop, Android or web) implements this interface once. It carries the functions of the "
        + "WebGPU API that do not belong to any object, which is where everything starts: the backend creates the "
        + "first {@link GpuInstance}, and every other object is created from it, so each object is implemented by "
        + "the same backend that made it.\n\n"
        + "<p>Programs do not call a backend directly. A platform installs its backend once with "
        + "{@link JWebGpu#install(GpuBackend)}, and {@link JWebGpu} forwards the global functions to it.");
    w.openType("public interface GpuBackend");
    w.doc("Returns a short name for this backend, for logs and error messages.",
        List.of("@return The name, such as {@code ffm}, {@code jni} or {@code teavm}."));
    w.line("String name();");
    w.blank();
    for (GpuFunction f : spec.globalFunctions()) {
      if (MethodWriter.isMethod(f)) {
        methods.emit(w, f, true, "the backend");
      }
    }
    w.close();
    return new GeneratedFile(EnumEmitter.path("GpuBackend"), w.toSource());
  }
}
