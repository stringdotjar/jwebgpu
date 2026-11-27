# Platform Guide: Desktop, Android, and Web

Your rendering code is the same everywhere, but each platform has its own requirements, setup, and a few differences
in behavior. This guide collects them, platform by platform, and ends with the complete list of what the browser does
not support.

## Table of contents

1. [At a glance](#at-a-glance)
2. [Desktop](#desktop)
3. [Android](#android)
4. [Web](#web)
5. [What the web does not support](#what-the-web-does-not-support)
6. [Writing code that runs everywhere](#writing-code-that-runs-everywhere)
7. [Check your understanding](#check-your-understanding)

## At a glance

| | Desktop | Android | Web |
|---|---------|---------|-----|
| Backend module | `jwebgpu-ffm` | `jwebgpu-jni` (AAR) | `jwebgpu-teavm` |
| Installer | `JWebGpuFfm.install()` | `JWebGpuJni.install()` | `JWebGpuTeaVm.install()` |
| Implementation | Dawn, through Java's FFM API | Dawn, through JNI | The browser's `navigator.gpu` |
| Natives | One `jwebgpu-natives-<target>` jar per platform | Inside the AAR | None (built into the browser) |
| Requirements | JDK 25, `--enable-native-access=ALL-UNNAMED` | Android 8.0 (API 26), arm64-v8a or x86_64 | TeaVM 0.13.0, a browser with WebGPU, a secure context |
| Native APIs used | Vulkan, Metal, Direct3D 12, Direct3D 11 | Vulkan | Whatever the browser uses |
| Callback modes | All three | All three | Ignored: callbacks come from the event loop |
| `processEvents()` | Runs ready callbacks | Runs ready callbacks | Does nothing |
| `waitAny(...)` with a timeout | Yes, with `TIMED_WAIT_ANY` | Yes, with `TIMED_WAIT_ANY` | No, only a timeout of 0 |
| Surfaces | Native window handles | `AndroidSurface` | `TeaVmSurfaceSourceCanvas` |
| Per-frame allocation | None (checked by tests) | None (checked by tests) | No Java objects |

## Desktop

### Natives

`jwebgpu-ffm` carries no native code. The Dawn library comes from a separate jar for each operating system and CPU, so
an application bundles only the platforms it ships to:

| Natives module | Native APIs compiled in |
|----------------|-------------------------|
| `jwebgpu-natives-linux-x86_64`, `jwebgpu-natives-linux-aarch64` | Vulkan (X11 and Wayland surfaces), Null |
| `jwebgpu-natives-windows-x86_64`, `jwebgpu-natives-windows-aarch64` | Direct3D 12, Direct3D 11, Vulkan, Null |
| `jwebgpu-natives-macos-x86_64`, `jwebgpu-natives-macos-aarch64` | Metal, Null |

The **Null** backend is a fake GPU that accepts every call and draws nothing. Request it with
`options.backendType = GpuBackendType.NULL` to run tests on machines without a graphics card.

The Linux libraries are built against glibc 2.28, so they load on every mainstream distribution released since 2018,
and they need nothing else but the system's Vulkan driver at run time.

### Loading the library

`JWebGpuFfm.install()` picks a library in this order:

1. The file you pass to `JWebGpuFfm.install(Path)`.
2. The file named by the system property `jwebgpu.library.path`.
3. The library from the `jwebgpu-natives-<target>` jar for this machine, which is copied once into a cache directory
   (`~/.cache/jwebgpu/natives/<hash>/` on Linux) and loaded from there on later runs.

If none is found, the exception names the exact natives module to add. Options 1 and 2 are useful when you build Dawn
yourself (see [COMPILING.md](../COMPILING.md#native-libraries-dawn)), for example with extra backends compiled in.

### Native access

Start the JVM with `--enable-native-access=ALL-UNNAMED`, or put `Enable-Native-Access: ALL-UNNAMED` in the manifest
of an executable jar. See [Getting Started](getting-started.md#allowing-native-access-on-the-desktop).

### Windows and surfaces

jwebgpu does not open windows. Open one with a windowing library of your choice and chain its native handle onto the
surface descriptor: `GpuSurfaceSourceWindowsHWND`, `GpuSurfaceSourceXlibWindow`, `GpuSurfaceSourceXCBWindow`,
`GpuSurfaceSourceWaylandSurface`, or `GpuSurfaceSourceMetalLayer`. See
[Surfaces and Presenting](surfaces-and-presenting.md#creating-a-surface-on-the-desktop).

### GraalVM native images

The desktop backend works in a GraalVM native image with no configuration of your own. Every jwebgpu jar ships the
metadata the native image builder needs (the native functions and callbacks, and the builder argument
`--enable-native-access=ALL-UNNAMED`), and each natives jar registers its library as a resource, so the image embeds it
and extracts it on the first run. You need **GraalVM for JDK 25 or newer** on Linux x86-64, Linux AArch64, Windows
x86-64, or macOS AArch64, the platforms where GraalVM supports foreign calls.

### Threads

You can call jwebgpu from several threads; each thread gets its own scratch memory. WebGPU objects themselves follow
Dawn's rules: recording into one encoder from two threads at once is not allowed, so give each thread its own
encoders. Callbacks with `ALLOW_SPONTANEOUS` may run on Dawn's own threads (see
[Threads and callbacks](async-and-callbacks.md#threads-and-callbacks)).

## Android

### Requirements

- **Android 8.0 (API 26) or newer**, because Dawn needs Vulkan 1.1.
- **64-bit ABIs only: arm64-v8a and x86_64.** The struct layouts that the bindings compute assume 8-byte pointers. Google
  Play has required 64-bit code since 2019, so almost every phone qualifies.
- A device or emulator with a working Vulkan driver.

The AAR carries `libwebgpu_dawn.so` (Dawn, with Vulkan and the Null backend) and `libjwebgpu_jni.so` (the generated C
glue) for both ABIs, with the C++ runtime linked statically, so your app needs no `libc++_shared.so`. It also ships R8
consumer rules, so shrinking your app does not break the native glue.

### Installing

Call the installer once, early, for example in `Application.onCreate()`:

```java
public final class MyApplication extends Application {

  @Override
  public void onCreate() {
    super.onCreate();
    JWebGpuJni.install();
  }
}
```

### Surfaces and the app lifecycle

Use `AndroidSurface` to turn the `Surface` of a `SurfaceView` or `TextureView` into a `GpuSurface`. Android destroys
the surface when your app goes to the background and creates a new one when it returns, so:

- Create the `AndroidSurface` in `surfaceCreated(...)`.
- Configure it in `surfaceChanged(...)`, which gives you the size.
- Release it in `surfaceDestroyed(...)`, and stop drawing until a new one exists.

Your device, pipelines, and buffers survive this; only the surface is recreated. See
[Creating a surface on Android](surfaces-and-presenting.md#creating-a-surface-on-android).

### Performance notes

The Android build marks the cheap, non-blocking native calls (render pass, command encoder, queue, and buffer calls)
with ART's `@FastNative`, which makes them noticeably faster. Calls that can wait (presenting, compiling shaders and
pipelines, waiting for futures) stay normal native calls, because a fast native call that blocks would also block the
garbage collector. Like on the desktop, a frame allocates nothing.

Dawn may run callbacks on its own threads; the backend attaches such threads to the Java virtual machine for you.

## Web

### Requirements

- **TeaVM 0.13.0.** The same Java translates to **JavaScript** and to **WebAssembly (WasmGC)**; the backend contains
  nothing that is specific to one of them, so pick whichever target you like.
- **A browser with WebGPU.** `JWebGpuTeaVm.isSupported()` tells you whether `navigator.gpu` exists, so you can show a
  friendly message where it does not.
- **A secure context.** Serve the page over HTTPS, or from `http://localhost` while developing. A WasmGC module also
  cannot load from `file://`.

### Installing and starting

```java
public static void main(String[] args) {
  if (!JWebGpuTeaVm.isSupported()) {
    // Show a message: this browser has no WebGPU.
    return;
  }
  JWebGpuTeaVm.install();
  GpuInstance instance = JWebGpu.createInstance();
  instance.requestAdapter(null, GpuCallbackMode.ALLOW_SPONTANEOUS, (status, adapter, message) -> {
    // The browser cannot block, so continue here.
  });
}
```

A browser may answer the very first adapter request with `null` while its GPU process is still starting. That is
allowed by the specification, so retrying once or twice after a short delay is a good idea.

### How the browser differs

- **No blocking.** Every asynchronous call finishes in its callback when the browser resolves the underlying promise,
  after your code returns. The callback mode is ignored, `processEvents()` does nothing, and `waitAny(...)` only polls.
  Drive frames from the browser's frame callback (for example TeaVM's `Window.requestAnimationFrame(...)`).
- **Surfaces are canvases.** Chain a `TeaVmSurfaceSourceCanvas` (from a selector or an element) onto the surface
  descriptor. `configure(...)` sizes the canvas's drawing buffer from `width` and `height`, `getCurrentTexture(...)`
  returns the canvas texture, and `present()` does nothing, because the browser shows the frame when your code yields.
- **Mapped memory is copied.** The browser owns mapped ranges, so `getMappedRange(...)` and `getConstMappedRange(...)`
  throw. Use `readMappedRange(...)` and `writeMappedRange(...)`, which work everywhere.
- **Uploads.** A direct `ByteBuffer` is handed to the browser as a view without copying. Arrays and heap buffers are
  copied once into a reusable staging buffer. Both work on JavaScript and on WasmGC.
- **Release.** `release()` gives up a reference; when the last one goes, buffers, textures, query sets, and devices are
  destroyed, and other objects are simply dropped. The canvas texture is never destroyed by `release()`.
- **The adapter does not report its native API.** `GpuAdapterInfo.backendType` is not meaningful on the web.
- **Options the browser ignores.** `GpuRequestAdapterOptions.backendType` and `compatibleSurface`, the surface
  `presentMode`, and the binding array size of bind group layout entries.

## What the web does not support

Using one of these on the web throws an `UnsupportedOperationException` (or, where noted, has no effect):

- `getMappedRange(...)` and `getConstMappedRange(...)` (use `readMappedRange(...)` and `writeMappedRange(...)`).
- A `waitAny(...)` timeout above 0.
- SPIR-V shader sources (`GpuShaderSourceSPIRV`).
- The surface color management extension struct (`GpuSurfaceColorManagement`).
- `commandEncoder.writeTimestamp(...)` (use the timestamp writes of a pass descriptor instead).
- Instance features and limits (the browser has none).
- `JWebGpu.getProcAddress(...)`, which returns 0.
- A few enum values that have no browser spelling, such as `GpuBufferBindingType.BINDING_NOT_USED`, the
  `UNPREMULTIPLIED` and `INHERIT` alpha modes, and the device lost reasons `CALLBACK_CANCELLED` and `FAILED_CREATION`.

The browser also offers a few things that the C API cannot reach, such as importing video frames as external textures.
Those are not part of the shared API.

## Writing code that runs everywhere

- Keep backend classes (`JWebGpuFfm`, `JWebGpuJni`, `AndroidSurface`, `JWebGpuTeaVm`, `TeaVmSurfaceSourceCanvas`) in
  the platform launchers, and pass the `GpuInstance` and `GpuSurface` to shared code.
- Write startup and readbacks as callback chains, never as loops that wait (see
  [A pattern that works everywhere](async-and-callbacks.md#a-pattern-that-works-everywhere)).
- Call `instance.processEvents()` every frame; it is harmless on the web.
- Use `readMappedRange(...)` and `writeMappedRange(...)` instead of `getMappedRange(...)`.
- Use WGSL, not SPIR-V.
- Pick formats from `surface.getCapabilities(...)` instead of hard-coding `BGRA8_UNORM` or `RGBA8_UNORM`.
- Do not rely on `backendType` to choose code paths; check features and limits instead.

## Check your understanding

1. Your program reads pixels with `getConstMappedRange(...)`. Which platform breaks, and what do you change?
2. Why must Android create a new `AndroidSurface` after the app returns from the background?
3. Which desktop natives module does a program running on an Apple silicon Mac need?

(Answers: the web, where it throws; use `readMappedRange(...)` into a reused array; because Android destroyed the old
`Surface` and its native window when the app went to the background; `jwebgpu-natives-macos-aarch64`.)

**Next:** [Compatibility Mode and Backend Selection](compatibility-mode.md)
