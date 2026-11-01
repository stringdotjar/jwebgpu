/**
 * Build-time code generator that turns the vendored WebGPU specifications into Java sources.
 *
 * <p>jwebgpu does not hand-write its bindings. The generator reads the pinned machine-readable
 * specifications kept in the repository's {@code spec/} directory (the YAML description of
 * {@code webgpu.h} and the browser WebIDL), builds a plain model of the API, and then emits the
 * platform-neutral API plus one implementation layer per backend (FFM, JNI and TeaVM). The output
 * is written into each module's {@code build/generated} directory and is never committed.
 *
 * <p>Think of the generator as a printing press: the specification is the master plate, and every
 * backend is a print run made from it. Fix the plate once and every run is fixed with it.
 *
 * <p>This package is a build tool only. It is not a runtime dependency of any published artifact,
 * so unlike the runtime modules it may use the standard Java collections.
 */
package io.github.stringdotjar.jwebgpu.generator;
