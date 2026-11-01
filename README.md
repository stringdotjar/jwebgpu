<div align="center">

  # jwebgpu

  [![CI](https://github.com/stringdotjar/jwebgpu/actions/workflows/ci_build.yml/badge.svg)](https://github.com/stringdotjar/jwebgpu/actions/workflows/ci_build.yml)
  [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
  [![Java 25](https://img.shields.io/badge/Java-25-orange)](https://adoptium.net/temurin/releases?version=25&os=any&arch=any)
  [![Platforms](https://img.shields.io/badge/platforms-Desktop%20%7C%20Android%20%7C%20Web-brightgreen)](ARCHITECTURE.md)

  Java bindings for [WebGPU](https://www.w3.org/TR/webgpu/): one API for desktop, Android and the web.

</div>

jwebgpu gives Java programs modern GPU access through a single, platform-neutral API. On desktop and Android it
talks to Google's [Dawn](https://dawn.googlesource.com/dawn) through the standard `webgpu.h` C interface. In the
browser it talks to the GPU through `navigator.gpu`. You write your rendering code once against `jwebgpu-api`,
and a small backend module takes care of the platform.

The bindings are **generated** from vendored, pinned WebGPU specifications, so the desktop, Android and web layers
stay in step. They are also designed to be garbage-free: descriptors are mutable objects that you create once and
refill every frame.

jwebgpu is a standalone library with no dependencies on other projects, and it can be used by any Java program.

> [!IMPORTANT]
> **Status: early development.** jwebgpu is not published to Maven Central or JitPack yet. The generator, the API and
> the desktop (FFM) and Android (JNI) backends work and draw on real GPUs; the web (TeaVM) backend is still being
> written. Expect everything to change.

## Modules

| Module | Purpose | Platform |
|--------|---------|----------|
| `jwebgpu-api` | The platform-neutral API. No dependencies besides annotations. | Everywhere |
| `jwebgpu-ffm` | Backend built on Java's Foreign Function and Memory API, calling Dawn. | Desktop |
| `jwebgpu-jni` | Backend built on JNI, shipped as an Android library. | Android |
| `jwebgpu-teavm` | Backend that wraps the browser's `navigator.gpu` using TeaVM. | Web |
| `jwebgpu-generator` | Build-time tool that generates the API and the backends from the specs. Never published. | Build only |
| `jwebgpu-test` | JUnit 5 tests. Never published. | Build only |

See [ARCHITECTURE.md](ARCHITECTURE.md) for how the pieces fit together.

## Using jwebgpu

Until jwebgpu is published, consume it through a Gradle **composite build**. In your project's `settings.gradle.kts`,
point at your local checkout:

```kotlin
includeBuild("../jwebgpu")
```

Then declare the dependencies with their normal coordinates. Gradle substitutes them with the local projects, so
nothing changes in your build once jwebgpu is published:

```kotlin
dependencies {
  implementation("io.github.stringdotjar:jwebgpu-api:<version>")
  runtimeOnly("io.github.stringdotjar:jwebgpu-ffm:<version>") // Desktop. Use -jni on Android, -teavm on the web.
}
```

### Installing a backend

Backends are installed **explicitly**. jwebgpu never discovers them with reflection or `ServiceLoader`, so it works on
ahead-of-time compiled platforms such as TeaVM. Each platform calls its backend's installer once at startup, and after
that the API asks the installed backend for everything:

```java
// Desktop launcher. Android and the web call their own backend's installer instead.
JWebGpuFfm.install();

// Platform-neutral code from here on.
GpuInstance instance = JWebGpu.createInstance();
```

If no backend has been installed, `JWebGpu.createInstance()` throws an `IllegalStateException` with a clear message.

### Running on the desktop

The desktop backend calls native code through Java's Foreign Function and Memory API, which Java only allows when it is
asked for. Start the program with:

```bash
java --enable-native-access=ALL-UNNAMED -cp ... YourMain
```

(An executable jar can say `Enable-Native-Access: ALL-UNNAMED` in its manifest instead; the `jwebgpu-ffm` jar does.)

`JWebGpuFfm.install()` loads Dawn for the current operating system and CPU from the `jwebgpu-ffm` jar. It copies the
library once into a cache directory (`~/.cache/jwebgpu/natives/<hash>/` on Linux), so later runs start without writing
anything. To use another build of the library, call `JWebGpuFfm.install(Path.of("/path/to/libwebgpu_dawn.so"))` or start
the program with `-Djwebgpu.library.path=/path/to/libwebgpu_dawn.so`. The libraries are not committed; see
[COMPILING.md](COMPILING.md#native-libraries-dawn) to build one.

### Running on Android

The Android backend, `jwebgpu-jni`, is an Android library (AAR) for the **64-bit ABIs arm64-v8a and x86_64** with a
**minSdk of 26** (Dawn needs Vulkan 1.1, which Android 8.0 introduced). 32-bit ABIs are not supported because the struct
layouts the generator computes assume 8-byte pointers. The AAR carries `libjwebgpu_jni.so` (the generated C glue) and
Dawn's `libwebgpu_dawn.so` for each ABI; both load when you install the backend:

```java
// Once, at startup (for example in Application.onCreate()).
JWebGpuJni.install();

GpuInstance instance = JWebGpu.createInstance();
```

To draw on the screen, turn an Android `Surface` (a `SurfaceView` or `TextureView` surface) into a WebGPU surface with
`AndroidSurface`, and release it when Android destroys the surface:

```java
public void surfaceCreated(SurfaceHolder holder) {
  target = AndroidSurface.create(instance, holder.getSurface());
  GpuSurface surface = target.getSurface(); // configure it, then render
}

public void surfaceDestroyed(SurfaceHolder holder) {
  target.release();
}
```

The library ships R8 consumer rules, so shrinking an app does not break the native glue. `samples/android-triangle` is a
small app that runs the backend on a device: see [COMPILING.md](COMPILING.md#building-the-android-backend) to build it.

### Garbage-free calls

The desktop and Android backends allocate nothing per call. Object handles are plain `long`s inside the wrappers, and descriptors,
strings, arrays and callbacks are copied into a native scratch block that every thread reuses. A frame that creates an
encoder and a render pass, records state and a draw, uploads some floats and submits allocates 0 bytes on the Java heap
(the unit tests check that). Methods that create objects also have an overload that refills an existing wrapper, such as
`device.createCommandEncoder(descriptor, encoder)`.

The scratch block of a thread grows when one call needs more than it holds and never shrinks by itself. After a rare, huge
upload, call `JWebGpuFfm.trimScratch()` (or `JWebGpuJni.trimScratch()` on Android) on that thread to give the memory back.

### Try it

`samples/triangle-offscreen` draws a triangle into a texture that is never shown and prints the pixels as text:

```bash
./gradlew :samples:triangle-offscreen:run
```

## Building

jwebgpu needs **JDK 25**. Gradle downloads a matching toolchain for you through the foojay resolver. See
[COMPILING.md](COMPILING.md) for the full setup, including the optional Android SDK.

```bash
./gradlew compileJava
./gradlew :jwebgpu-test:test
```

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md) first. Project roles and how
decisions are made are described in [GOVERNANCE.md](GOVERNANCE.md).

## License

jwebgpu is released under the [MIT License](LICENSE).
