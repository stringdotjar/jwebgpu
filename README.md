<div align="center">

  # jwebgpu

  [![CI](https://github.com/stringdotjar/jwebgpu/actions/workflows/ci_build.yml/badge.svg)](https://github.com/stringdotjar/jwebgpu/actions/workflows/ci_build.yml)
  [![Maven Central](https://img.shields.io/maven-central/v/io.github.stringdotjar/jwebgpu-api)](https://central.sonatype.com/namespace/io.github.stringdotjar)
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
> **Status: early development.** jwebgpu is published to Maven Central as `0.x` versions. The generator, the API and
> the desktop (FFM), Android (JNI) and web (TeaVM) backends work and draw; the web backend is checked in headless Chrome on
> a software adapter. Until 1.0, a minor version may change the API.

## Modules

| Module | Purpose | Platform |
|--------|---------|----------|
| `jwebgpu-api` | The platform-neutral API. No dependencies besides annotations. | Everywhere |
| `jwebgpu-ffm` | Backend built on Java's Foreign Function and Memory API, calling Dawn. Carries no native library. | Desktop |
| `jwebgpu-natives-<target>` | The Dawn library of one desktop target, one module each: `linux-x86_64`, `linux-aarch64`, `windows-x86_64`, `windows-aarch64`, `macos-x86_64` and `macos-aarch64`. | Desktop |
| `jwebgpu-jni` | Backend built on JNI, shipped as an Android library. | Android |
| `jwebgpu-teavm` | Backend that wraps the browser's `navigator.gpu` using TeaVM. | Web |
| `jwebgpu-generator` | Build-time tool that generates the API and the backends from the specs. Never published. | Build only |
| `jwebgpu-test` | JUnit 5 tests, plus the browser test of the web backend in `jwebgpu-test/browser`. Never published. | Build only |

See [ARCHITECTURE.md](ARCHITECTURE.md) for how the pieces fit together.

## Using jwebgpu

jwebgpu is on **Maven Central** under the group `io.github.stringdotjar`. Replace `<version>` below with the latest
version shown by the badge at the top. Make sure `mavenCentral()` is in your repositories:

```kotlin
repositories {
  mavenCentral()
}
```

Every program needs `jwebgpu-api`, plus the backend of each platform it runs on. Your rendering code only uses the API,
and only the platform's launcher touches the backend, so the backend can be a `runtimeOnly` dependency where the launcher
lives in another module.

**Desktop** (Windows, macOS, Linux):

```kotlin
dependencies {
  implementation("io.github.stringdotjar:jwebgpu-api:<version>")
  implementation("io.github.stringdotjar:jwebgpu-ffm:<version>")

  // The Dawn library of every platform you ship to. Add just the ones you need.
  runtimeOnly("io.github.stringdotjar:jwebgpu-natives-linux-x86_64:<version>")
  runtimeOnly("io.github.stringdotjar:jwebgpu-natives-windows-x86_64:<version>")
  runtimeOnly("io.github.stringdotjar:jwebgpu-natives-macos-aarch64:<version>")
}
```

**Android** (an AAR, so the Android Gradle plugin picks up its native libraries and R8 rules):

```kotlin
dependencies {
  implementation("io.github.stringdotjar:jwebgpu-api:<version>")
  implementation("io.github.stringdotjar:jwebgpu-jni:<version>")
}
```

**Web** (with the TeaVM Gradle plugin, version 0.13.0):

```kotlin
dependencies {
  implementation("io.github.stringdotjar:jwebgpu-api:<version>")
  implementation("io.github.stringdotjar:jwebgpu-teavm:<version>")
}
```

To work on jwebgpu and a project that uses it at the same time, put `includeBuild("../jwebgpu")` in that project's
`settings.gradle.kts`. Gradle then replaces the coordinates above with your local checkout, so nothing else in the build
changes. See [COMPILING.md](COMPILING.md#using-a-local-checkout-from-another-project).

The desktop libraries are big (about 5 to 11 MB each), so they live in one jar per platform instead of in
`jwebgpu-ffm`. An application bundles only the platforms it ships to. The other available modules are
`jwebgpu-natives-linux-aarch64`, `jwebgpu-natives-windows-aarch64` and `jwebgpu-natives-macos-x86_64`. Android and the
web need no natives module: the Android AAR carries its own libraries, and the browser already ships WebGPU.

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

`JWebGpuFfm.install()` loads Dawn for the current operating system and CPU from the matching `jwebgpu-natives-<target>`
jar on the class path. If that jar is missing, the error names the exact module to add. It copies the library once into a cache directory (`~/.cache/jwebgpu/natives/<hash>/` on Linux), so later runs start without writing
anything. To use another build of the library, call `JWebGpuFfm.install(Path.of("/path/to/libwebgpu_dawn.so"))` or start
the program with `-Djwebgpu.library.path=/path/to/libwebgpu_dawn.so`. The libraries are not committed; see
[COMPILING.md](COMPILING.md#native-libraries-dawn) to build one.

### Building a GraalVM native image

The desktop backend works in a GraalVM native image without any configuration of your own. Add the same dependencies as for a
normal run (`jwebgpu-api`, `jwebgpu-ffm` and the `jwebgpu-natives-<target>` jar of each platform), then build the image the way
you always do, with `native-image` or with the GraalVM Gradle plugin (`org.graalvm.buildtools.native`). Every jwebgpu jar ships
the metadata the native image builder needs, generated from the same specification as the code:

- `jwebgpu-ffm` lists every native function and every callback (the foreign downcalls and upcalls), the callback methods that
  the backend looks up by name, and the builder argument `--enable-native-access=ALL-UNNAMED`.
- Each `jwebgpu-natives-<target>` jar registers its Dawn library as a resource, so the image embeds it and extracts it on the
  first run, exactly like a run on the JVM.

You need **GraalVM for JDK 25 or newer** (Oracle GraalVM or GraalVM Community) on **Linux x86-64, Linux AArch64, Windows
x86-64 or macOS AArch64**, because those are the platforms GraalVM supports for foreign calls. The unit tests check that
the metadata lists exactly the functions and callbacks the backend uses.

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

The library ships R8 consumer rules, so shrinking an app does not break the native glue.

### Running on the web

The web backend, `jwebgpu-teavm`, has no native code: it wraps the browser's own WebGPU (`navigator.gpu`) with TeaVM's
JavaScript Objects interop. The same Java translates to **JavaScript and to WebAssembly (WasmGC)** with TeaVM 0.13.0; the
backend contains nothing that is specific to one of them. The page must be a secure context (HTTPS or `localhost`).

```java
public static void main(String[] args) {
  JWebGpuTeaVm.install();   // throws a clear exception if navigator.gpu does not exist
  GpuInstance instance = JWebGpu.createInstance();
  instance.requestAdapter(null, GpuCallbackMode.ALLOW_SPONTANEOUS, (status, adapter, message) -> {
    // the browser cannot block, so continue here
  });
}
```

Asynchronous calls (adapter, device, buffer mapping, pipelines in the background, error scopes) finish in their
callbacks when the browser resolves the promise, so there is no `processEvents` to call and `waitAny` only polls. To draw
to a `<canvas>`, chain a `TeaVmSurfaceSourceCanvas` onto the surface descriptor. The few calls that the browser cannot
offer are listed in [ARCHITECTURE.md](ARCHITECTURE.md#the-web-backend).

### Garbage-free calls

The desktop and Android backends allocate nothing per call. Object handles are plain `long`s inside the wrappers, and descriptors,
strings, arrays and callbacks are copied into a native scratch block that every thread reuses. A frame that creates an
encoder and a render pass, records state and a draw, uploads some floats and submits allocates 0 bytes on the Java heap
(the unit tests check that). Methods that create objects also have an overload that refills an existing wrapper, such as
`device.createCommandEncoder(descriptor, encoder)`.

The scratch block of a thread grows when one call needs more than it holds and never shrinks by itself. After a rare, huge
upload, call `JWebGpuFfm.trimScratch()` (or `JWebGpuJni.trimScratch()` on Android) on that thread to give the memory back.

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
