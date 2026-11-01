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
 * <p>Descriptors are plain mutable objects meant to be created once and refilled, so a game can
 * render frame after frame without allocating garbage.
 */
package io.github.stringdotjar.jwebgpu;
