/**
 * The emitters, which turn the model into source text.
 *
 * <p>Every emitter is a pure function: the resolved {@link io.github.stringdotjar.jwebgpu.generator.model.Spec} goes
 * in and a list of {@link io.github.stringdotjar.jwebgpu.generator.emit.GeneratedFile} comes out. Nothing here touches
 * the file system, which keeps the emitters easy to unit test with golden files. The command line entry point
 * ({@link io.github.stringdotjar.jwebgpu.generator.Generator}) is the only place that writes the files to disk.
 *
 * <p>Today the only emitter is {@link io.github.stringdotjar.jwebgpu.generator.emit.ApiEmitter}, which writes the
 * platform-neutral {@code jwebgpu-api} module.
 */
package io.github.stringdotjar.jwebgpu.generator.emit;
