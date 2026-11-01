/**
 * Reads the vendored specification files.
 *
 * <p>{@link io.github.stringdotjar.jwebgpu.generator.parse.Json} is a small strict JSON parser with no
 * dependencies. {@link io.github.stringdotjar.jwebgpu.generator.parse.SpecResolver} turns the parsed document into
 * the model of the C API by applying the same expansion rules as upstream's header generator, and
 * {@link io.github.stringdotjar.jwebgpu.generator.parse.SpecLoader} is the convenient entry point that does both.
 */
package io.github.stringdotjar.jwebgpu.generator.parse;
