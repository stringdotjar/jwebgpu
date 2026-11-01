/**
 * The web backend of jwebgpu, built on TeaVM's JavaScript Objects (JSO) interop.
 *
 * <p>Browsers already ship WebGPU through {@code navigator.gpu}, so this package needs no native
 * library. It implements the platform-neutral API from the {@code jwebgpu-api} module by wrapping
 * the browser's own objects, and bridges the browser's promises to the callback style the API uses
 * for startup requests.
 *
 * <p>A web program installs this backend once at startup, before creating a GPU instance.
 */
package io.github.stringdotjar.jwebgpu.teavm;
