# Troubleshooting

Common error messages and symptoms, what causes them, and how to fix them. If your problem is not here, set an
uncaptured error callback and label your objects (see [Error Handling and Device Loss](errors-and-device-loss.md)):
the message usually says exactly what is wrong.

## Table of contents

1. [Setup and loading](#setup-and-loading)
2. [Adapters and devices](#adapters-and-devices)
3. [Callbacks that never run](#callbacks-that-never-run)
4. [Nothing on the screen](#nothing-on-the-screen)
5. [Validation errors](#validation-errors)
6. [Reading data back](#reading-data-back)
7. [The web](#the-web)
8. [Android](#android)
9. [Performance](#performance)

## Setup and loading

**"No jwebgpu backend is installed. Call JWebGpuFfm.install() (desktop), ..."**
You called `JWebGpu.createInstance()` (or another `JWebGpu` method) before installing a backend. Call the installer of
your platform first, once, at startup. ([Getting Started](getting-started.md#installing-a-backend))

**"A jwebgpu backend is already installed (...), so ... cannot be installed as well."**
Two different backends were installed in one program, usually because a launcher of one platform ran code meant for
another. Install exactly one backend per program.

**`JWebGpuFfm.install()` fails and names a `jwebgpu-natives-...` module.**
The natives jar for your operating system and CPU is not on the class path. Add the module the message names as a
`runtimeOnly` dependency. When you work from a local checkout, build the library first (see
[COMPILING.md](../COMPILING.md#native-libraries-dawn)).

**"WARNING: A restricted method in java.lang.foreign... has been called".**
Start the JVM with `--enable-native-access=ALL-UNNAMED`. ([Getting Started](getting-started.md#allowing-native-access-on-the-desktop))

**The library fails to load on Linux with a glibc error.**
A library you built yourself needs the glibc of the machine it was built on, or newer. Use the published natives jar,
which needs glibc 2.28, or build on an older distribution.

## Adapters and devices

**The adapter callback gets `null` with `UNAVAILABLE`.**
No adapter matches your options. Check, in order:

- Is there a working driver? Try with `options` left `null`.
- Did you set `backendType` to a backend that is not compiled into your natives, or not supported by the machine? Leave
  it at `UNDEFINED`. ([Compatibility Mode](compatibility-mode.md#choosing-a-backend-type))
- Did you ask for `OPENGL` or `OPENGLES` without `featureLevel = COMPATIBILITY`? They only exist in compatibility mode.
  ([Compatibility Mode](compatibility-mode.md#opengl-and-opengl-es))
- Is the GPU too old for core? Try `featureLevel = COMPATIBILITY`. ([Compatibility Mode](compatibility-mode.md#a-fallback-pattern-core-first-then-compatibility))
- On a server or in CI without a GPU, use `GpuBackendType.NULL`, or install a software Vulkan driver.

**The device request fails.**
You required a feature the adapter does not have, or a limit above what it offers. Check `adapter.hasFeature(...)` and
`adapter.getLimits(...)` before you ask. ([Instances, Adapters, and Devices](instances-adapters-and-devices.md#requesting-a-device))

**"The GpuDevice was released" (or another object).**
You called a method on an object after `release()`. Look for a release that runs too early, for example at the end of a
frame for an object you meant to keep. If it is a per-frame wrapper, refill it (for example
`device.createCommandEncoder(null, encoder)`) before using it again.

## Callbacks that never run

**A callback with `ALLOW_PROCESS_EVENTS` never runs.**
Call `instance.processEvents()` regularly, for example at the start of every frame.
([Asynchronous Calls and Callbacks](async-and-callbacks.md#pumping-events-with-processevents))

**A callback with `WAIT_ANY_ONLY` never runs.**
Those only run inside `instance.waitAny(...)` for their future. Use `ALLOW_PROCESS_EVENTS` if you pump events instead.

**`waitAny(...)` with a timeout fails.**
Create the instance with the instance feature `TIMED_WAIT_ANY`. On the web, only a timeout of 0 works.
([Asynchronous Calls and Callbacks](async-and-callbacks.md#waiting-for-a-future-with-waitany))

**The program hangs on the web while waiting for a callback.**
A loop that waits for a callback never ends in the browser, because callbacks only run after your code returns. Continue
in the callback instead. ([Asynchronous Calls and Callbacks](async-and-callbacks.md#the-browser))

## Nothing on the screen

**The window stays black.**
- Is there an uncaptured error callback, and is it silent? If not, read the message.
- Did you call `surface.present()` after submitting?
- Does the color attachment use `loadOp = CLEAR` and `storeOp = STORE`?
- Does the pipeline's color target format equal the format you configured the surface with?
- Are your vertex positions inside clip space (-1 to 1)? Try `cullMode = GpuCullMode.NONE` in case the triangles face
  away from the camera.

**`getCurrentTexture(...)` reports `OUTDATED` or `LOST`.**
The window changed. Configure the surface again with the new size; if `LOST` keeps coming, create a new surface.
([Surfaces and Presenting](surfaces-and-presenting.md#resizing-and-lost-surfaces))

**The picture is stretched after a resize.**
Configure the surface again with the new size, and recreate the depth texture.

**Colors have red and blue swapped.**
The surface format is `BGRA8_UNORM`, and your code (or a readback) assumes `RGBA8_UNORM`. Use the format from
`surface.getCapabilities(...)` everywhere, and swap channels when you read `BGRA` pixels.

## Validation errors

**"... usage ... does not include ..."**
A buffer or texture is missing a usage flag. Add it in the descriptor.
([Buffers and Textures](buffers-and-textures.md#usages-telling-the-gpu-what-a-resource-is-for))

**An error about the attachment format or the color target.**
The pipeline's `targets[i].format` must match the format of the view in `colorAttachments[i]`.

**An error about a binding or a location.**
The `@group`, `@binding`, or `@location` numbers in the shader do not match the bind group layout, the bind group, or
the vertex attributes. ([Shaders and Pipelines](shaders-and-pipelines.md))

**An error about `bytesPerRow`.**
Copies between textures and buffers need `bytesPerRow` to be a multiple of 256.
([Buffers and Textures](buffers-and-textures.md#copies-between-buffers-and-textures))

**A shader compiles with errors but no line numbers.**
Call `shader.getCompilationInfo(...)` and print its messages.
([Shaders and Pipelines](shaders-and-pipelines.md#checking-compiler-messages))

**A storage buffer in a vertex or fragment shader fails only on some machines.**
Those are probably compatibility devices whose `maxStorageBuffersInVertexStage` or `maxStorageBuffersInFragmentStage`
is 0. Read `GpuCompatibilityModeLimits`, and use another path there.
([Compatibility Mode](compatibility-mode.md#compatibility-limits))

## Reading data back

**The pixels or numbers read back are all zero.**
- Did you wait for the `mapAsync(...)` callback before reading?
- Was the copy into the readback buffer recorded in the same (or an earlier) submitted command buffer?
- Is the readback buffer's usage `MAP_READ | COPY_DST`?

**Multi-byte values read back as nonsense.**
A `ByteBuffer` is big-endian by default; the GPU is little-endian. Call `.order(ByteOrder.LITTLE_ENDIAN)`, or use the
`float[]` and `int[]` overloads of `readMappedRange(...)`.

## The web

**"WebGPU is not available in this browser: navigator.gpu does not exist."**
The browser has no WebGPU, or the page is not a secure context. Serve it over HTTPS or from `http://localhost`, and use
a browser with WebGPU. Check `JWebGpuTeaVm.isSupported()` first to show your own message.

**The first adapter request returns `null`.**
Some browsers answer the very first request with `null` while their GPU process starts. Retry after a short delay.

**`getMappedRange(...)` throws `UnsupportedOperationException`.**
The browser owns mapped memory. Use `readMappedRange(...)` and `writeMappedRange(...)`.
([Platform Guide](platforms.md#what-the-web-does-not-support))

**The WasmGC page does not load from a file.**
WasmGC modules cannot load from `file://`. Serve the directory over HTTP, for example with
`python3 -m http.server --directory <folder>`.

## Android

**The app crashes on start on an old or 32-bit device.**
jwebgpu needs Android 8.0 (API 26) and a 64-bit ABI (arm64-v8a or x86_64).

**Drawing stops after the app returns from the background.**
Android destroyed the old `Surface`. Release the `AndroidSurface` in `surfaceDestroyed(...)` and create a new one in
`surfaceCreated(...)`. ([Surfaces and Presenting](surfaces-and-presenting.md#creating-a-surface-on-android))

**No adapter on a device without a working Vulkan driver.**
The Android libraries are built with Vulkan. A device without a usable Vulkan driver has no adapter unless the natives
are built with Dawn's OpenGL ES backend and you request it in compatibility mode.
([Compatibility Mode](compatibility-mode.md#opengl-and-opengl-es))

## Performance

**The frame rate is fine, but the animation stutters every few seconds.**
Probably garbage collection. Measure allocations per frame and move every `new` out of the frame.
([Garbage-Free Usage](garbage-free.md#measuring-allocations))

**Memory keeps growing slowly.**
Look for objects that are created every frame and never released: views, encoders, command buffers, bind groups. Release
per-frame objects at the end of the frame, or refill them.

**Native memory grew after loading and stays high.**
The scratch block of the loading thread grew for a large upload. Call `JWebGpuFfm.trimScratch()` (or
`JWebGpuJni.trimScratch()`) on that thread afterwards. ([Garbage-Free Usage](garbage-free.md#scratch-memory-and-trimscratch))

**The CPU waits for the GPU every frame.**
Reading results back and waiting for them each frame makes the CPU and GPU take turns. Read the previous frame's
results instead, from a second buffer. ([Compute](compute.md#compute-every-frame))
