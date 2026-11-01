/**
 * The platform-neutral WebGPU API shared by every jwebgpu backend.
 *
 * <p>This package contains the types a program uses to talk to the GPU: enums, bit flags, reusable
 * descriptors and one abstract class per WebGPU object (device, queue, buffer, texture, and so
 * on). It deliberately contains no FFM, JNI or JavaScript interop types, so the same classes load
 * on desktop, Android and the web.
 *
 * <p>The API does not implement itself. A platform installs exactly one backend at startup (for
 * example the desktop FFM backend), and that backend creates every object you receive. Think of
 * this package as the plug socket and a backend as the plug: the socket looks the same everywhere,
 * and what sits behind it changes per platform.
 *
 * <p>Descriptors are plain mutable objects meant to be created once and refilled, so an application can
 * render frame after frame without allocating garbage.
 *
 * <h2>Naming</h2>
 *
 * <p>The names mirror the browser's WebGPU API, so the documentation on MDN maps one to one.
 *
 * <ul>
 *   <li>Every type has the {@code Gpu} prefix: objects ({@code GpuDevice}), descriptors ({@code GpuBufferDescriptor}),
 *       enums ({@code GpuTextureFormat}), bit flags ({@code GpuTextureUsage}) and callbacks
 *       ({@code GpuRequestAdapterCallback}).
 *   <li>Methods are camelCase and lose the object name: {@code device.createBuffer(descriptor)}.
 *   <li>Enum and bit flag constants are UPPER_SNAKE_CASE ({@code GpuTextureFormat.RGBA8_UNORM}). A name that starts
 *       with a digit gets a {@code D} in front, so the dimensions are {@code D1}, {@code D2}, {@code D2_ARRAY} and
 *       {@code D3}.
 *   <li>Every enum has a public {@code value} (the number the native library uses) and a static {@code of(int)} that
 *       returns {@code null} for numbers outside the specification.
 * </ul>
 *
 * <h2>Types</h2>
 *
 * <p>Integers up to 32 bits (and the 16-bit {@code uint16_t}) are {@code int}, 64-bit integers and {@code size_t}
 * are {@code long}, and bit flags are {@code long}. Arrays are a fixed-capacity array plus an {@code int} count.
 * Strings are {@code String}, and raw bytes are passed as a {@code ByteBuffer} or as a primitive array with an offset
 * and a count.
 */
package io.github.stringdotjar.jwebgpu;
