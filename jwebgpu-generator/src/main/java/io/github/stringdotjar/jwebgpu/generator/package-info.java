/**
 * Build-time code generator that turns the vendored WebGPU specifications into Java sources.
 *
 * <p>jwebgpu does not hand-write its bindings. The generator reads the pinned machine-readable
 * specifications kept in the repository's {@code spec/} directory (today {@code webgpu.json}, the machine-readable
 * form of upstream's {@code webgpu.yml}), builds a plain model of the C API, and will emit the platform-neutral API
 * plus one implementation layer per backend (FFM, JNI and TeaVM). The output is written into each module's
 * {@code build/generated} directory and is never committed.
 *
 * <p>Think of the generator as a printing press: the specification is the master plate, and every
 * backend is a print run made from it. Fix the plate once and every run is fixed with it.
 *
 * <p>The sub-packages are:
 *
 * <ul>
 *   <li>{@code model}: the immutable description of the C API (structs, functions, enums, and so on).
 *   <li>{@code parse}: the JSON parser and the resolver that builds the model from the specification.
 *   <li>{@code doc}: the converter that makes documentation text safe for Javadoc.
 * </ul>
 *
 * <p>This package is a build tool only. It is not a runtime dependency of any published artifact,
 * so unlike the runtime modules it may use the standard Java collections.
 */
package io.github.stringdotjar.jwebgpu.generator;
