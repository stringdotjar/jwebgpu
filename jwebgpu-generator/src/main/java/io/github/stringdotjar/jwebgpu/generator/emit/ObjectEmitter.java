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
import io.github.stringdotjar.jwebgpu.generator.model.GpuObject;

import java.util.ArrayList;
import java.util.List;

/** Writes the abstract class of each WebGPU object, and the common base class {@code GpuObject}. */
final class ObjectEmitter {

  private final Docs docs;
  private final MethodWriter methods;

  ObjectEmitter(Docs docs, MethodWriter methods) {
    this.docs = docs;
    this.methods = methods;
  }

  GeneratedFile emit(GpuObject o) {
    String name = Names.type(o.cName());
    String words = Names.words(o.cName());
    SourceWriter w = new SourceWriter(Names.pkg());
    w.doc(docs.text(o.doc(), Names.capitalize(Names.article(words)) + ", one of the objects of the WebGPU API.")
        + "\n\n<p>Every method is abstract: the installed backend provides the implementation. Release the object "
        + "with {@link #release()} when you are done with it.\n\n<p>C type: {@code " + o.cName() + "}.");
    w.openType("public abstract class " + name + " extends GpuObject");
    w.doc("Creates the object. Only backends call this.");
    w.line("protected " + name + "() {}");
    w.blank();

    List<GpuFunction> normal = new ArrayList<>();
    List<GpuFunction> getters = new ArrayList<>();
    for (GpuFunction f : o.methods()) {
      if (MethodWriter.isMethod(f)) {
        (MethodWriter.isSimpleGetter(f) ? getters : normal).add(f);
      }
    }
    for (GpuFunction f : normal) {
      methods.emit(w, f, false, "this " + words);
    }
    for (GpuFunction f : getters) {
      methods.emit(w, f, false, "this " + words);
    }
    w.close();
    return new GeneratedFile(EnumEmitter.path(name), w.toSource());
  }

  GeneratedFile emitBase() {
    SourceWriter w = new SourceWriter(Names.pkg());
    w.doc("The common base class of every WebGPU object, such as a device, a buffer or a texture.\n\n"
        + "<p>An object is a handle to something that lives inside the GPU driver. Think of it as a library card: "
        + "the card is small and cheap, the books it lets you borrow are not. The driver counts how many cards point "
        + "to the same thing, and the thing is freed when the last card is handed back. {@link #release()} hands a "
        + "card back, and {@link #addRef()} makes another card for the same thing.\n\n"
        + "<p>Objects are created by a backend, never with {@code new}. Release every object you create, for "
        + "example in a shutdown method, and keep long-lived objects in fields instead of creating them every "
        + "frame.");
    w.openType("public abstract class GpuObject");
    w.doc("An empty array that backends and subclasses share, for methods that take an optional array.\n\n"
        + "<p>Passing it (with a count of 0) instead of a new array keeps per-frame calls free of allocation.");
    w.line("protected static final int[] EMPTY_INTS = new int[0];");
    w.blank();
    w.doc("Creates the object. Only backends call this.");
    w.line("protected GpuObject() {}");
    w.blank();
    w.doc("Gives up this reference to the native object.\n\n<p>The call is idempotent: releasing an object that is "
        + "already released does nothing. Using the object after it has been released throws an "
        + "{@link IllegalStateException}.");
    w.line("public abstract void release();");
    w.blank();
    w.doc("Adds one more reference to the native object, which must be released separately.\n\n"
        + "<p>Use it when two parts of a program share an object and each one calls {@link #release()} when it "
        + "is done.");
    w.line("public abstract void addRef();");
    w.blank();
    w.doc("Tells whether this wrapper no longer holds a native object.",
        List.of("@return {@code true} after {@link #release()} was called, or if the wrapper was never filled."));
    w.line("public abstract boolean isReleased();");
    w.close();
    return new GeneratedFile(EnumEmitter.path("GpuObject"), w.toSource());
  }

  GeneratedFile emitChainedStruct() {
    SourceWriter w = new SourceWriter(Names.pkg());
    String nullable = w.imp("org.jetbrains.annotations.Nullable");
    w.doc("The base class of every extension struct.\n\n"
        + "<p>Many WebGPU descriptors can be extended with extra information. The extras form a chain, like "
        + "carriages of a train: the descriptor holds the first carriage in its {@code nextInChain} field, and "
        + "each carriage points to the next one through {@link #next}. For example, a surface descriptor "
        + "gets its window from a chained extension struct:\n\n"
        + "<pre>{@code\n"
        + "GpuSurfaceSourceXlibWindow source = new GpuSurfaceSourceXlibWindow();\n"
        + "source.display = display;\n"
        + "source.window = window;\n"
        + "GpuSurfaceDescriptor descriptor = new GpuSurfaceDescriptor();\n"
        + "descriptor.nextInChain = source;\n"
        + "}</pre>\n\n"
        + "<p>The tag that tells the native library which kind of struct a carriage is comes from "
        + "{@link #sType()}, so there is nothing to set by hand.");
    w.openType("public abstract class GpuChainedStruct");
    w.doc("The next extension struct in the chain, or {@code null} if this is the last one.");
    w.line("public @" + nullable + " GpuChainedStruct next;");
    w.blank();
    w.doc("Creates the struct. Only the generated extension structs call this.");
    w.line("protected GpuChainedStruct() {}");
    w.blank();
    w.doc("Returns the tag that tells the native library which extension struct this is.",
        List.of("@return The native {@code WGPUSType} number of this struct."));
    w.line("public abstract int sType();");
    w.close();
    return new GeneratedFile(EnumEmitter.path("GpuChainedStruct"), w.toSource());
  }
}
