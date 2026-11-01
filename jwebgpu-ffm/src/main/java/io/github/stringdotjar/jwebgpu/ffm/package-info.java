/**
 * The desktop backend of jwebgpu, built on Java's Foreign Function and Memory (FFM) API.
 *
 * <p>This package implements the platform-neutral API from the {@code jwebgpu-api} module by
 * calling Dawn's {@code webgpu.h} C interface directly, with no JNI glue and no native code of our
 * own. It is the only place on desktop where FFM types appear, so the rest of a program never has
 * to touch raw memory segments.
 *
 * <p>A desktop program installs this backend once at startup, before creating a GPU instance.
 * After that, every object comes from this backend and calls it through ordinary virtual methods.
 */
package io.github.stringdotjar.jwebgpu.ffm;
