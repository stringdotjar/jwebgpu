# Project Architecture and Structure

jwebgpu is organized into multiple Gradle modules: one platform-neutral API, one thin backend per platform, and a
build-time generator that writes most of the code.

## Core idea: one neutral API, three thin backends

```
                    your program (or a library built on it)
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
- **`jwebgpu-ffm`**: The desktop backend. It implements the API classes with Java's Foreign Function and Memory API, holding
  a plain `long` handle per object. It loads the Dawn native library for the current OS and architecture from the class
  path (see `jwebgpu-natives-<target>` below) and marshals descriptors through a reused per-thread scratch block. See
  [The FFM backend](#the-ffm-backend).
- **`jwebgpu-natives-<target>`**: Six modules, one per desktop target (`linux-x86_64`, `linux-aarch64`, `windows-x86_64`,
  `windows-aarch64`, `macos-x86_64`, `macos-aarch64`). Each is a jar with no classes whose only content is that target's
  Dawn library at `natives/<target>/<library name>`, which is where the FFM loader looks. They are separate modules, and not
  classifier artifacts of one module, so an application bundles only the platforms it ships to and so that dependency
  substitution in a composite build works per module. They have no build file: `settings.gradle.kts` includes them from the
  directories `jwebgpu-natives/<target>/`, and the convention plugin `jwebgpu.natives-jar` sets them up. Their Javadoc jar is
  empty and their sources jar holds the license texts. The library is gitignored, so a build without it produces an empty jar
  and prints a warning, but every publishing task fails, so a release can never ship an empty natives jar. Android needs no
  such module because the `jwebgpu-jni` AAR carries its own libraries.
- **`jwebgpu-jni`**: The Android backend, built as an Android library (AAR). Android's runtime does not include the FFM API, so
  this backend uses `native` methods that call a small generated C layer wrapping `webgpu.h`. Native handles are stored as
  `long`s. It supports the 64-bit ABIs only (arm64-v8a and x86_64). See [The JNI backend](#the-jni-backend).
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

- `webgpu.json` (the machine-readable form of upstream's `webgpu.yml`, which is the source of truth behind `webgpu.h`)
  produces the Java API, the FFM layer and the JNI layer. The generator's resolver applies the same expansion rules as
  upstream's header generator, and a conformance test checks the result against the vendored `webgpu.h`.
- The WebGPU WebIDL produces the browser (TeaVM) layer.

Generated sources are written into each module's `build/generated` directory and are **never committed**. The spec is the
single source of truth, so there is nothing to drift. Reviewers see changes through the emitters' golden-file tests, and the
published `-sources` and `-javadoc` jars still contain the generated code for browsing in an IDE.

### The generated API

`./gradlew :jwebgpu-api:compileJava` runs the generator first. The convention plugin `jwebgpu.generated-sources` registers
the task `generateJWebGpuSources`, which runs the generator's `ApiEmitter` (`--emit api --out <dir>`) and writes about 180
Java files into `jwebgpu-api/build/generated/sources/jwebgpu/main/java`. That directory is added to the `main` source set,
so compiling, Javadoc, the sources jar and Checkstyle all see it, and Spotless skips it because the emitter already writes
formatted code. The emitter's output is covered by golden-file tests under `jwebgpu-test/src/test/resources/golden`; run
the tests with `-Pjwebgpu.updateGolden=true` to rewrite them after a deliberate change.

The API mirrors the browser's WebGPU names so the MDN documentation maps one to one:

- Every type has the `Gpu` prefix: objects (`GpuDevice`), descriptors (`GpuRenderPassDescriptor`), enums
  (`GpuTextureFormat`), bit flags (`GpuTextureUsage`) and callbacks (`GpuRequestAdapterCallback`). Methods are camelCase and
  lose the object name (`device.createBuffer(descriptor)`).
- Enums are Java enums with a public `value` and a static `of(int)` that returns `null` for numbers outside the
  specification. Constants are UPPER_SNAKE_CASE; a name that starts with a digit gets a `D` in front (`D2`, `D2_ARRAY`).
- Bit flags are classes of `long` constants, and the sentinel values live in `GpuConstants`.
- Descriptors are mutable classes with public fields, a constructor that applies the defaults of the C header, and a
  `reset()` method. Nested structs are final, preallocated fields. Arrays are a fixed-capacity array plus an `int` count.
- Extension structs extend `GpuChainedStruct`; extensible descriptors have a `nextInChain` field.
- Objects are abstract classes on top of `GpuObject`. A method that returns an object also has an overload that refills an
  existing wrapper. Raw bytes are passed as a `ByteBuffer` or as `byte[]`, `short[]`, `int[]` or `float[]` with an offset
  and a count.

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
  per-thread block of native memory, JNI writes into a reused direct `ByteBuffer` that the C glue reads, and TeaVM fills cached JavaScript
  objects. Nothing is allocated per call.
- Callbacks (device lost, errors, async requests) use stubs and listener objects created once.

### Wrapper refill overloads

Some WebGPU objects are created every frame: the surface texture and its view, the command encoder, the pass encoder and the
command buffer. For every API method that returns an object, the generator also emits an overload that takes an existing
wrapper to refill, for example `device.createCommandEncoder(desc, encoder)`. The native handle changes, but no Java object is
allocated.

## The FFM backend

Most of `jwebgpu-ffm` is generated (`--emit ffm`): one `FfmGpu...` class per API object, one `FfmGpu...Struct` marshaller
per struct, `FfmChains` for extension structs, and `FfmCallbacks` with the callback stubs. Only a few small classes are
hand-written: `JWebGpuFfm` (the installer), `NativeLoader`, `Scratch`, `Mem`, `Native` and `CallbackTable`.

How a call stays free of allocation:

- **Handles and pointers are `long`.** Every downcall describes object handles and pointers as 64-bit integers instead of
  `MemorySegment`s. On every supported 64-bit target (System V x86-64, Win64 and AArch64) a pointer and a 64-bit integer use
  the same register, so the call is identical, and no segment object is needed per pointer. The two kinds of values that C
  passes by value and that are not plain integers (`WGPUStringView` and the callback info structs) keep a struct layout and
  use two segments that every thread preallocates. A returned `WGPUFuture` is one 64-bit integer, which all three targets
  return like a `long`.
- **One method handle per C function**, in a nested `Fn` class of the object that calls it, called with `invokeExact`. The
  nested class loads lazily, so a program only binds the functions it uses.
- **Raw memory access.** `Mem` owns one segment that spans the whole address space, so reading or writing an absolute address
  is a static call the JIT inlines and no segment is created per access.
- **Scratch memory.** `Scratch` is a per-thread bump allocator in native memory. A call that marshals anything starts by
  resetting it, then writes descriptors (nested structs, arrays, chained extension structs, strings, struct pointers) at the
  offsets of the C layout. The offsets are `static final long` constants generated from the same layout computer that a test
  checks against a C compiler. Strings are encoded to UTF-8 by hand straight into the block. If one call needs more than the
  block holds, a bigger block replaces it; the old one stays valid until the next call, so addresses already written stay
  correct. Callbacks that run inside a call (for example an uncaptured error callback) mark the pad as in use, so a nested
  call does not overwrite the outer one.
- **Data parameters.** Arrays are copied into the scratch block with the static `MemorySegment.copy` overloads. A direct
  `ByteBuffer` is passed by address without a copy; a heap `ByteBuffer` is copied through its array. Finding a direct
  buffer's address needs a temporary `MemorySegment`, so each thread's scratch remembers the last direct buffer and its
  base address: uploading from the same buffer again (the usual per-frame pattern) allocates nothing, and only a new
  buffer pays for one temporary object.
- **Returned objects.** `createX(desc)` creates a new wrapper. `createX(desc, into)` only stores the new handle in `into`
  (and releases the handle `into` held before). Output structs (`getLimits`, `getInfo`, `getCurrentTexture`, ...) are
  filled in scratch memory and read back into the caller's object; arrays in them are replaced when the capacity is too
  small, and the matching C `FreeMembers` function is called afterwards.
- **Callbacks.** There is one upcall stub per callback type, created once. The Java callback is kept in a plain-array table
  and its slot number travels as `userdata1`. One-shot callbacks free their slot when they fire; the uncaptured error
  callback keeps its slot. The stub catches everything the Java callback throws and hands it to the thread's uncaught
  exception handler, because an exception must never travel into native code.

The class files use method handles for the upcall stubs (the foreign function API requires them), but no reflection.

## The JNI backend

`jwebgpu-jni` mirrors the FFM backend, so the two behave the same: the same wrapper classes with a `long` handle, the same
refilling overloads, the same per-thread scratch memory, the same callback slot table. What differs is how Java reaches C.

**How a call crosses into C.** Java does not pass descriptor objects to C, and C does not unpack anything. Each thread owns
a direct `ByteBuffer` (the scratch block). The generated marshalling code writes the descriptor into it **at the byte
offsets of the C struct layout**, and every pointer it writes inside a struct (an array, a nested struct, a string, a chain
of extension structs) is an **absolute address**: the address of the buffer's first byte, asked from the native side once
when the block is created, plus the offset. The block is therefore already a valid `WGPU*` struct tree. The native method
receives plain `long`s, casts them and calls `webgpu.h`:

```c
static void jw_wgpuQueueSubmit(JW_ENV, jlong a0, jlong a1, jlong a2) {
  wgpuQueueSubmit((WGPUQueue) (uintptr_t) a0, (size_t) a1, (WGPUCommandBuffer const *) (uintptr_t) a2);
}
```

A struct that C takes by value (a string view or a callback info) travels as the address of the struct in scratch memory,
and the glue copies it. Compile-time checks (`_Static_assert`) in the generated C file compare the size and every field
offset the Java side uses with the real header, so a header that differs from the specification fails the build.

**What is generated.** `--emit jni-java` writes the `JniGpu...` object classes, the `JniGpu...Struct` marshallers,
`JniChains`, `JniCallbacks` and `Jni` (one `static native` method per C function). `--emit jni-c` writes the single C file
`jwebgpu_jni.c`: the native functions, the table that registers them with `RegisterNatives` from `JNI_OnLoad` (no
reflection and no name mangling), and the callback entry points. `--emit jni-java-android` writes the same Java classes with
ART's `@FastNative` on the cheap, non-blocking natives (render pass, command encoder, queue and buffer calls). Calls that can
wait (presenting, compiling shaders and pipelines, waiting for futures) stay normal native calls, because a fast native call
that blocks would also block the garbage collector. The annotation is the only Android-only part of the generated code, so the
plain flavor compiles for a desktop JVM too.

**Hand-written parts.** `Scratch` (the per-thread block with the same reentrancy rules as the FFM scratch memory),
`Native` (the loader and the few helper natives: a buffer's address, reading memory the library owns, bulk array copies),
`CallbackTable`, `JWebGpuJni` and, in the `android` package, `AndroidSurface`. Reading memory that the native library owns
(the strings and lists of a callback or of an output struct) goes through a native call, so it is used only on the rare
paths that hand text over; the per-frame path touches only scratch memory.

**Callbacks.** Dawn may call a callback from its own threads (a lost device, an uncaptured error), so the C entry point
attaches the thread to the Java virtual machine as a daemon thread if needed and detaches it when the thread ends. It calls a
static Java method with primitive arguments only (strings arrive as an address and a length and are decoded as real UTF-8,
not as the modified UTF-8 of `NewStringUTF`), so a callback allocates nothing in C. As in the FFM backend, the callback's slot
number travels as `userdata1`, and a nested call made from inside a callback keeps the scratch memory of the call it
interrupted.

**Testing without a phone.** The same generated Java and C also build for the desktop. The Gradle task
`:jwebgpu-test:buildJniHostGlue` compiles `libjwebgpu_jni` for the machine with CMake, and `:jwebgpu-test:testJni` runs the
backend tests (the triangle on the real GPU, the Null backend and, with `VK_ICD_FILENAMES`, a software Vulkan driver; the
10,000-frame allocation check; the marshalling tests) against the JNI backend. The tests in the `backend` package run
unchanged against either backend, chosen by the system property `jwebgpu.backend`.

## The web backend

The browser spells WebGPU differently from `webgpu.h`: enums are strings (`"rgba8unorm"`), descriptors are plain JavaScript
objects, asynchronous calls return promises, and objects have `destroy()` instead of reference counting. So `jwebgpu-teavm`
is generated from **both** specifications: the C model decides what the shared API looks like, and the vendored WebIDL
(`spec/webgpu.idl`) says how each piece is spelled in the browser. Everything goes through TeaVM's JavaScript Objects
(JSO), and nothing is specific to JavaScript or to WasmGC, so one codebase translates to both.

### How the two specifications are merged

`WebMap` (in `jwebgpu-generator`) relates the two models, and its tests fail when a new specification version breaks a rule:

- **Enums.** A C enum maps to the IDL enum named `GPU` plus its name (aliases: `composite_alpha_mode` is `GPUCanvasAlphaMode`,
  and `texture_binding_view_dimension` uses `GPUTextureViewDimension`). A C value maps to the IDL string that is equal after
  removing `_` and `-` and ignoring case, so `BGRA8_unorm_srgb` is `"bgra8unorm-srgb"`. The merge fails if a string that the
  browser exposes has no C value. The C value `undefined` (and `auto` for the canvas alpha mode) means "use the default" and
  leaves the property out. A C value without a string (`BindingNotUsed`, the `unpremultiplied` and `inherit` alpha modes,
  the device-lost reasons `CallbackCancelled` and `FailedCreation`) is listed by `WebMap.unsupportedValues()`, and using it
  throws an `UnsupportedOperationException`. A few C enums have no IDL enum (`feature_level`, the color space, the WGSL
  language features); their strings are listed in `WebMap`.
- **Bit flags.** Each C bit flag type maps to the IDL namespace of the same name (`color_write_mask` is `GPUColorWrite`).
  The numbers are identical in both specifications, and the merge compares every constant.
- **Structs.** A C struct maps to the dictionary of the same name (the small value types add `Dict`: `GPUColorDict`,
  `GPUExtent3DDict`, `GPUOrigin3DDict`). Members match by their camel-case name; counts fold into the array next to
  them; the members of extension structs are flattened into the dictionary they extend, because the browser has no chains.
  Documented differences: `compute_state` is `GPUProgrammableStage`, the surface configuration is
  `GPUCanvasConfiguration` (its width and height size the canvas, and the present mode is ignored), and the layout of a
  buffer-to-texture copy is flattened into `GPUTexelCopyBufferInfo`.
- **Functions.** A C method maps to the IDL operation of the same name, a getter to the attribute of the same name,
  and `setLabel` to the `label` attribute. Calls whose shape differs (promises, output structs, raw memory, the instance
  and the surface) are written by hand in `TeaVmSpecial`. The only C call without a browser operation is
  `commandEncoder.writeTimestamp`, which throws. The browser operations that the C API cannot reach are
  `importExternalTexture`, `copyExternalImageToTexture` and `getPreferredCanvasFormat` (used internally for the surface
  capabilities).

### What is generated and what is written by hand

`--emit teavm` writes: a `Js...` interface for every browser interface (typed `@JSMethod` and `@JSProperty` declarations,
with one overload per number of optional arguments), a `TeaVmGpu...` class for every API object, a `TeaVmGpu...Struct`
marshaller for every descriptor, `TeaVmEnums` and `TeaVmProps`. By hand: `JWebGpuTeaVm` (the installer), `TeaVmJs`,
`TeaVmData`, `TeaVmAsync` and `TeaVmFutures`, `TeaVmReaders` (output structs), `TeaVmSpecial`, `TeaVmStructs` and the two
bind group marshallers (the browser has a single `resource` member, and only the used layout of a layout entry), and
`TeaVmSurfaceSourceCanvas`.

### Garbage-free calls

- **Descriptors** are marshalled into **cached JavaScript objects**: each marshaller owns one object per descriptor type
  and nesting slot, created once, and overwrites every property on each call. Arrays are cached JavaScript arrays whose
  length is set per call, with one cached marshaller per element index. A property is written by a one-statement JSBody
  method, so no property name string exists at run time. A string is converted once and reused as long as the Java string
  object is the same. Enum values are interned strings, found by an array lookup on the ordinal. A call with two
  descriptors of the same type (a copy's source and destination) gets a separate marshaller for each parameter.
- **Wrappers.** Methods that return an object also have the refill variant, which only swaps the browser object in an
  existing wrapper.
- **No Java test of allocation.** The calls need a JavaScript engine, so the usual allocation test (which counts bytes on
  the JVM) cannot run them. Instead a unit test checks the generated marshallers: no `new` outside the lazy first use, no
  lambdas and no string concatenation in the per-call code. The browser test exercises the paths on both targets.

### Uploading data

The browser reads memory through typed arrays. A Java array is one on the JavaScript target (`@JSByRef`), but **not on
WasmGC**, where Java arrays live in the garbage-collected heap that JavaScript cannot see (TeaVM 0.13.0 rejects
`@JSByRef` there). The one block of memory that both targets share with JavaScript is a **direct `ByteBuffer`**: a typed
array on JavaScript, and a range of the linear memory (an `ArrayBuffer`) on WasmGC. So `TeaVmData` works the same on both:

- A direct `ByteBuffer` goes to the browser as it is, as a view over its memory. **Nothing is copied.**
- A `byte[]`, `short[]`, `int[]`, `float[]` or a heap `ByteBuffer` is copied once with a bulk buffer `put` into a reusable
  direct staging buffer (it grows only when an upload is bigger than any before), and the browser gets a view of that.

The trade-off: on the JavaScript target an array upload pays one copy that `@JSByRef` would have avoided, and in return
there is one code path and it works on WasmGC. Every upload also makes one small view object on the JavaScript side
(never a Java object). Do not keep the view: if WasmGC memory grows, the old `ArrayBuffer` is replaced.
Reading is symmetric (`readMappedRange` copies the mapped range into the staging buffer and then into the array).
`getMappedRange` cannot work, because the browser's mapped range is an `ArrayBuffer` that it owns, so it throws and points to
`readMappedRange` and `writeMappedRange`.

### Lifecycle, promises and the event loop

- `release()` gives up one reference (`addRef()` counts them) and, when the last one goes, calls `destroy()` for buffers,
  textures, query sets and devices, and just drops the browser object for everything else. It is idempotent. A texture
  that the canvas hands out every frame is marked external and is never destroyed by `release()`.
- Promises end in the Java callbacks with matching status codes: `requestAdapter`, `requestDevice`, `mapAsync`,
  `onSubmittedWorkDone`, `popErrorScope`, `getCompilationInfo` and the asynchronous pipelines. The callback mode is
  ignored, since a browser callback always comes from the event loop. Each call registers a future, so
  `waitAny(..., 0)` can poll it.
- `device.lost` becomes the device lost callback, and the `uncapturederror` event becomes the uncaptured error callback.
- `processEvents` does nothing, and `waitAny` with a timeout above 0 throws (the browser cannot block).

### Surfaces

A surface is a `GPUCanvasContext`. Chain a `TeaVmSurfaceSourceCanvas` (a canvas element or a CSS selector) onto the
surface descriptor. `configure` sizes the canvas from the configuration, `getCurrentTexture` returns the canvas texture,
and `present` does nothing because the browser shows the frame when the task yields.

### What the web does not support

`getMappedRange`/`getConstMappedRange`, a `waitAny` timeout, SPIR-V shader sources, the surface color management extension
struct, `writeTimestamp`, instance features and limits (the browser has none), `getProcAddress`
(returns 0), the adapter backend type, compatible surface and binding array size (ignored), and the enum values listed in
`WebMap.unsupportedValues()`. Using an unsupported extension struct or enum value throws an `UnsupportedOperationException`.

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
