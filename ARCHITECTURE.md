# Project Architecture and Structure

jwebgpu is organized into multiple Gradle modules: one platform-neutral API, one thin backend per platform, and a
build-time generator that writes most of the code.

## Core idea: one neutral API, three thin backends

```
                    your program (or a game framework)
                                  |
                           jwebgpu-api            <- platform-neutral; safe on desktop, Android, web
            ______________________|______________________
           |                      |                      |
     jwebgpu-ffm            jwebgpu-jni            jwebgpu-teavm
   (desktop, FFM)         (Android, JNI)          (web, TeaVM JSO)
           |                      |                      |
         Dawn                   Dawn               navigator.gpu
```

The rules that shape this layout:

- **`jwebgpu-api` must run everywhere**, so it contains no FFM, JNI or JavaScript interop types. FFM types in the API would
  break Android, and JavaScript interop types would break desktop.
- **Each backend is the only place its interop technology appears.** Fixing or swapping one backend never touches the others.
- **Only one backend is on the classpath at runtime**, so every API call is a monomorphic virtual call that the JIT (and
  TeaVM's devirtualizer) can inline. The abstraction costs nothing per frame.
- **The generator never ships.** It is a build-time tool, so it may use standard collections (the same exception the
  coding rules give to every build-time tool).

## Modules

### Runtime modules (published)

- **`jwebgpu-api`**: The platform-neutral API. It holds generated enums, bit flags, descriptors and one abstract class per
  WebGPU object (`GpuDevice`, `GpuQueue`, `GpuBuffer`, and so on), plus a small hand-written core: the `JWebGpu` entry
  point, the `GpuBackend` interface that each backend implements, and a neutral description of a window surface. It depends
  on nothing except the JetBrains annotations.
- **`jwebgpu-ffm`**: The desktop backend. It implements the API classes with Java's Foreign Function and Memory API,
  holding a `MemorySegment` per object. It loads the Dawn native library for the current OS and architecture from the jar
  and marshals descriptors through a reused scratch arena.
- **`jwebgpu-jni`**: The Android backend, built as an Android library (AAR). Android's runtime does not include the FFM API, so
  this backend uses `native` methods that call a small generated C layer wrapping `webgpu.h`. Native handles are stored as
  `long`s.
- **`jwebgpu-teavm`**: The web backend. Browsers already ship WebGPU, so there is no native code. It declares the browser's
  `navigator.gpu` types with TeaVM's JavaScript Objects (JSO) interop, wraps them in API implementations, and bridges
  promises to the callback style the API uses at startup. It depends on `teavm-core` at compile time.

### Build-time modules (never published)

- **`jwebgpu-generator`**: Reads the vendored specifications in `spec/` and emits Java and C sources for the API and for each
  backend. Each emitter is a pure function (model in, source text out), which makes it easy to unit test with golden files.
- **`jwebgpu-test`**: JUnit 5 tests for the generator, the emitted code and the backends. Run them with
  `./gradlew :jwebgpu-test:test`.

### Other directories

- **`spec/`**: Vendored, pinned specification files and the notes on how to update them (`SPEC_VERSION.md`).
- **`samples/`**: Small runnable examples for each platform. Not published.
- **`scripts/`**: Helper scripts, such as the ones that build the Dawn natives.
- **`build-logic/`**: Gradle convention plugins (`jwebgpu.java-library`, `jwebgpu.android-library`, and so on) that every
  module applies, so the build rules cannot drift between modules.

## Why generate the bindings

WebGPU has roughly 200 functions and 150 structs. Writing three binding layers by hand would let them drift apart and rot.
Instead, the repository vendors the official machine-readable specifications and **generates** every layer:

- `webgpu.yml` (the source of truth behind `webgpu.h`) produces the Java API, the FFM layer and the JNI layer.
- The WebGPU WebIDL produces the browser (TeaVM) layer.

Generated sources are written into each module's `build/generated` directory and are **never committed**. The spec is the
single source of truth, so there is nothing to drift. Reviewers see changes through the emitters' golden-file tests, and the
published `-sources` and `-javadoc` jars still contain the generated code for browsing in an IDE.

## Typed wrapper objects

Every WebGPU object is a typed Java wrapper (`GpuTexture`) instead of a raw `long` handle:

1. **The web has no pointers.** On desktop and Android a WebGPU object is a C pointer; in the browser it is a JavaScript
   object. A wrapper hides that difference so one API works on all three platforms.
2. **The compiler catches mistakes.** Passing a buffer where a texture is expected is a compile error, not a GPU crash.
3. **The API is discoverable.** `texture.createView(desc)` is easier to find and read than `wgpuTextureCreateView(ptr, desc)`.
4. **Release is safe.** `release()` can be idempotent, and debug builds can report leaked objects.
5. **There is no per-frame cost.** Wrappers are created when a resource is created, never per draw call.

## Garbage-free descriptors

- Descriptors in `jwebgpu-api` are plain mutable Java objects. Callers create them once, keep them, and refill their fields
  every frame.
- Nested arrays (color attachments, bind group entries) are fixed-capacity arrays plus a `count` field, so they never
  reallocate.
- Each backend **marshals** a descriptor into its own pre-allocated scratch memory at call time: FFM writes into a reused
  `MemorySegment`, JNI writes into a reused direct `ByteBuffer` that the C glue reads, and TeaVM fills cached JavaScript
  objects. Nothing is allocated per call.
- Callbacks (device lost, errors, async requests) use stubs and listener objects created once.

### Wrapper refill overloads

Some WebGPU objects are created every frame: the surface texture and its view, the command encoder, the pass encoder and the
command buffer. For every API method that returns an object, the generator also emits an overload that takes an existing
wrapper to refill, for example `device.createCommandEncoder(desc, encoder)`. The native handle changes, but no Java object is
allocated.

## Explicit backend install

A backend is never found by reflection. A platform calls its backend's installer once at startup (for example
`JWebGpuFfm.install()` in a desktop launcher). The installer loads the natives and registers a `GpuBackend` with `JWebGpu`.
`JWebGpu.createInstance()` then asks the installed backend for an instance, and throws an `IllegalStateException` with a clear
message if none is installed.

Every object after that is created by the backend itself, so method calls dispatch through the object's own class and the
global registration is never consulted again. Tests can install a fake backend. There is no `ServiceLoader` and no
`Class.forName`, which keeps jwebgpu working on platforms that compile ahead of time.

## Dawn as the native implementation

The native side of desktop and Android is [Dawn](https://dawn.googlesource.com/dawn), the WebGPU implementation used by
Chrome. It was chosen because it ships to billions of Android phones inside Chrome (with workarounds for buggy Vulkan
drivers), matches the browser's behavior so the native and web paths act the same, fits a CMake build, and has a null backend
that makes unit tests possible on machines without a GPU. jwebgpu only uses it through the standard `webgpu.h` C interface, so
swapping the implementation later means swapping the native library and regenerating.

## Build System

jwebgpu uses **Gradle** with the **Kotlin DSL**.

- **`build.gradle.kts`**: The root aggregator. It registers the `javadocAll` task and applies the root Spotless and Checkstyle
  setup.
- **`settings.gradle.kts`**: Lists the modules. Note that `jwebgpu-jni` is excluded by default unless `includeAndroid=true` is
  set in a `local.properties` file or `-PincludeAndroid=true` is passed on the command line. See [COMPILING.md](COMPILING.md).
- **`gradle.properties`**: JVM settings, the Maven group and the POM metadata.
- **`gradle/libs.versions.toml`**: The version catalog.
- **`build-logic/`**: The convention plugins, including Spotless, Checkstyle, Javadoc and Maven publishing rules.

jwebgpu is not published yet. Other Gradle builds consume it through a composite build (`includeBuild("../jwebgpu")`), and the
group (`io.github.stringdotjar`) and module names match the future Maven coordinates, so dependency substitution works without
changing any declarations.

## GitHub Integration

The GitHub configuration lives in [`.github/`](.github): issue and pull request templates, Dependabot, release notes
categories, and the CI and publishing workflows.
