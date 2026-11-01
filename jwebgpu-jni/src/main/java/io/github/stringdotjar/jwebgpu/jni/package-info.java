/**
 * The Android backend of jwebgpu, built on the Java Native Interface (JNI).
 *
 * <p>Android's runtime does not include the FFM API, so this package implements the
 * platform-neutral API from the {@code jwebgpu-api} module with {@code native} methods that call
 * into a small C layer wrapping Dawn's {@code webgpu.h}. Both the Java classes and the C glue are
 * generated from the same specification as the other backends, so they cannot drift apart.
 *
 * <p>An Android program installs this backend once at startup, before creating a GPU instance.
 */
package io.github.stringdotjar.jwebgpu.jni;
