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
> **Status: early development.** jwebgpu is not published to Maven Central or JitPack yet. The modules are
> scaffolded and the generator and backends are still being written, so there is nothing to render with today.
> Expect everything to change.

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

> [!NOTE]
> The snippet above shows the planned shape of the API. These classes are placeholders until the generator and the
> backends land.

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
