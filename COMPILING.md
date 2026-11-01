# Compiling & Testing

jwebgpu is a library, not an application, so it is not run by itself. You build it and run its unit tests from this
repository, and you try it out in a separate project that consumes it through a Gradle composite build. This guide walks
you through every step from a clean machine.

---

## Table of contents

1. [Prerequisites](#prerequisites)
2. [Getting the source](#getting-the-source)
3. [Building and checking](#building-and-checking)
4. [How to run unit tests](#how-to-run-unit-tests)
5. [Using a local checkout from another project](#using-a-local-checkout-from-another-project)
6. [Setting up the Android SDK (for the JNI backend)](#setting-up-the-android-sdk-for-the-jni-backend)
7. [Native libraries (Dawn)](#native-libraries-dawn)
8. [Building the Android backend](#building-the-android-backend)
9. [Building and testing the web backend](#building-and-testing-the-web-backend)
10. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### 1. Git

You need Git to clone the repository. To contribute, create a free account at [github.com](https://github.com).

| Platform | How to install Git |
|----------|--------------------|
| **Windows** | Download and run the installer from [git-scm.com](https://git-scm.com/download/win). |
| **macOS** | Run `xcode-select --install` or `brew install git`. |
| **Linux (Ubuntu / Debian)** | `sudo apt update && sudo apt install git` |
| **Linux (Fedora / RHEL)** | `sudo dnf install git` |
| **Linux (Arch)** | `sudo pacman -S git` |

Verify with `git --version`.

### 2. Java (JDK 25 with Eclipse Temurin)

jwebgpu requires **Java 25** (LTS) because the desktop backend uses the Foreign Function and Memory API. The build uses
the Gradle wrapper (Gradle 9.x).

**You do not have to install JDK 25 by hand.** The build declares Java 25 toolchains and uses the
[foojay resolver](https://github.com/gradle/foojay-toolchains) to download a matching JDK on the first build. Gradle itself
also needs a JDK 25 to run (`gradle/gradle-daemon-jvm.properties` asks for one), and it downloads that the same way.

If you would rather install it yourself, use **[Eclipse Temurin 25 (Adoptium)](https://adoptium.net/temurin/releases/?package=jdk&version=25)**:

- **Windows**: install the MSI and enable the options that set `JAVA_HOME` and add it to `PATH`.
- **macOS**: `brew install --cask temurin@25`, or use the `.pkg` installer.
- **Linux**: use the [Adoptium package repositories](https://adoptium.net/installation/linux/) (`temurin-25-jdk`).

Verify with `java -version`; it should show **25** and mention `Temurin` or `OpenJDK`.

> [!TIP]
> If your network is slow, the first toolchain download can time out. Install a JDK 25 yourself and point Gradle at it with
> `-Porg.gradle.java.installations.paths=/path/to/jdk-25 -Porg.gradle.java.installations.auto-download=false`. Do not commit
> that path anywhere.

---

## Getting the source

```bash
git clone https://github.com/stringdotjar/jwebgpu.git
cd jwebgpu
```

If you are contributing, see [CONTRIBUTING.md](CONTRIBUTING.md) for the workflow.

---

## Building and checking

Run these from the repository root (use `gradlew.bat` on Windows):

| Command | What it does |
|---------|--------------|
| `./gradlew compileJava` | Compiles every Java module. |
| `./gradlew spotlessApply` | Formats the sources with the project's Spotless rules. |
| `./gradlew checkstyleMain` | Runs Checkstyle (Javadoc completeness, banned collections, naming, and more). |
| `./gradlew javadocAll` | Runs Javadoc with doclint on every Java library module. |
| `./gradlew :jwebgpu-test:test` | Runs the unit tests. |

Run all five before you finish any change.

---

## How to run unit tests

All unit tests live in the `jwebgpu-test` module:

```bash
./gradlew :jwebgpu-test:test
```

Most tests need nothing but Java. The tests of the desktop backend (the triangle, the allocation check) call the real
native library, so they need the Dawn library for your machine (see [Native libraries](#native-libraries-dawn)). **When the
library is missing they are skipped, not failed**, so a plain checkout still passes. They use Dawn's Null backend wherever
possible, so no graphics card is needed, and they add checks on the real GPU (Intel, AMD, NVIDIA or Apple) when the adapter is
one. To also run the triangle on a software Vulkan driver (for example Mesa's llvmpipe), point Vulkan at it:

```bash
VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd.json ./gradlew :jwebgpu-test:test
```

To test another build of the library, pass `-Pjwebgpu.library=/path/to/libwebgpu_dawn.so`. The test task adds
`--enable-native-access=ALL-UNNAMED` itself; you need the same flag when you run your own programs.

---

## Using a local checkout from another project

jwebgpu is not published yet, so other projects use it through a **Gradle composite build**. In the
consuming project's `settings.gradle.kts`:

```kotlin
includeBuild("../jwebgpu")
```

Use the normal coordinates in the dependencies; Gradle substitutes the local projects automatically:

```kotlin
dependencies {
  implementation("io.github.stringdotjar:jwebgpu-api:<version>")
  runtimeOnly("io.github.stringdotjar:jwebgpu-ffm:<version>")
}
```

- **Windows**: use forward slashes in the path, for example `C:/Users/You/jwebgpu`.
- The version is ignored by a composite build, so any placeholder works.
- To include the Android module in the substitution, enable it in jwebgpu's own `local.properties` (see below).

---

## Setting up the Android SDK (for the JNI backend)

### The Android module is optional (no SDK required by default)

The repository **does not require an Android SDK** to compile. By default the `jwebgpu-jni` module is **not included** in the
build, so you can clone, build the other modules and contribute without installing the SDK.

To build the Android module, enable it in one of two ways (do **not** add this to the committed `gradle.properties`):

- **CI or one-off builds**: pass the property on the command line:
  ```bash
  ./gradlew -PincludeAndroid=true :jwebgpu-jni:assembleDebug
  ```
- **Local development**: add a line to **local.properties** (this file is gitignored, so you never commit it):
  ```properties
  includeAndroid=true
  sdk.dir=/path/to/android/sdk
  ```

### Installing the Android SDK

[Android Studio](https://developer.android.com/studio) is the easiest way to install it.

1. Install Android Studio and open **SDK Manager**.
2. Under **SDK Platforms**, install **Android API 36**.
3. Under **SDK Tools**, install **Android SDK Build-Tools 36** and **Android SDK Platform-Tools**.
4. Note the **Android SDK Location** and put it in `local.properties` as `sdk.dir`.

The typical location is `C:\Users\<You>\AppData\Local\Android\Sdk` on Windows, `~/Library/Android/sdk` on macOS, and
`~/Android/Sdk` on Linux.

The JNI backend also needs the **Android NDK 28.2.13676358** and **CMake 3.22.1** (install both from **SDK Tools** in the
SDK Manager). See [Building the Android backend](#building-the-android-backend).

---

## Native libraries (Dawn)

The desktop and Android backends call [Dawn](https://dawn.googlesource.com/dawn), Google's WebGPU implementation, through
its `webgpu.h` C API. Dawn is built from source into **one shared library per platform and architecture**. Desktop libraries
are bundled into the `jwebgpu-ffm` jar, and Android libraries into the `jwebgpu-jni` AAR. The libraries are **not committed**: CI builds them (see `.github/workflows/natives.yml`), and you can
build the one for your own machine with `scripts/build_natives.sh`. You only need to do this when you run code that actually
calls the GPU; compiling and the unit tests that do not touch the native library work without it.

### Building the library for your machine

```bash
./scripts/build_natives.sh linux-x86_64
```

Pick the target that matches your machine:

| Target | Build it on | Backends compiled in |
|--------|-------------|----------------------|
| `linux-x86_64`, `linux-aarch64` | Linux with the same CPU | Vulkan (X11 and Wayland surfaces), Null |
| `windows-x86_64`, `windows-aarch64` | Windows with the same CPU | Direct3D 12, Direct3D 11, Vulkan, Null |
| `macos-x86_64`, `macos-aarch64` | macOS (either CPU can build both) | Metal, Null |
| `android-arm64-v8a`, `android-x86_64` | Linux or macOS with the Android NDK r28+ | Vulkan (`ANativeWindow` surfaces), Null |

Every library CI builds covers all of that platform's window systems at once: Linux has X11 (Xlib and XCB) and Wayland
compiled in and picks one at run time, Windows uses Win32 windows, macOS uses Metal layers, and Android uses `ANativeWindow`.
Only the 64-bit Android ABIs are built, because the generated struct layouts assume 64-bit pointers, Dawn requires Vulkan
1.1 (which almost no 32-bit-only phones have), and Google Play has required 64-bit code since 2019.

The **Null backend** is a fake GPU that does nothing. Unit tests use it so they can run on machines without a graphics card.
The script also accepts `fetch`, which only downloads the Dawn sources.

Desktop libraries are copied to `jwebgpu-ffm/src/main/resources/natives/<target>/` as `libwebgpu_dawn.so`,
`webgpu_dawn.dll` or `libwebgpu_dawn.dylib`. Android libraries are copied to `jwebgpu-jni/src/main/jniLibs/<abi>/` as
`libwebgpu_dawn.so`, with the C++ runtime linked statically so apps need no `libc++_shared.so`. Both folders are gitignored. The library is stripped, and it exports **only** the
`wgpu*` functions from `webgpu.h`; the script checks this at the end and fails if anything else leaks out. On Linux it also
links the C++ runtime statically, so the library needs nothing but glibc. The build machine's glibc sets the floor, so CI
builds the Linux libraries inside the `manylinux_2_28` container (glibc 2.28), which makes them load on every mainstream
distribution released since 2018. A local build on a newer distribution needs that distribution's glibc or newer; the script
prints the exact requirement at the end.

To get every library at once without building, download the `dawn-natives-all` artifact from the latest **Build Natives**
workflow run. It is laid out like the repository, so you can extract it straight into your checkout.

### What to install first

| Platform | Install |
|----------|---------|
| **All** | `git`, Python 3, CMake 3.22 or newer, and [Ninja](https://ninja-build.org) (`pip install ninja` works everywhere). |
| **Linux (Ubuntu / Debian)** | `sudo apt install build-essential cmake libx11-dev libx11-xcb-dev libxcb1-dev`. A C++20 compiler is required: GCC 13+ or Clang 16+. |
| **Linux (Fedora / RHEL)** | `sudo dnf install gcc-c++ cmake libX11-devel libxcb-devel` |
| **Linux (Arch)** | `sudo pacman -S base-devel cmake libx11 libxcb` |
| **Windows** | Visual Studio 2022 (or Build Tools) with the **Desktop development with C++** workload, and Git for Windows. Run the script from Git Bash **inside a developer shell**, so `cl.exe` is on `PATH` (for example, open "x64 Native Tools Command Prompt for VS 2022", then start `bash` from there). |
| **macOS** | Xcode command line tools (`xcode-select --install`), then `brew install cmake ninja`. |
| **Android** | The Android NDK r28 or newer (Android Studio's SDK Manager, **NDK (Side by side)**), on Linux or macOS. Point the script at it with `JWEBGPU_ANDROID_NDK` if it is not at `~/Android/Sdk/ndk/28.2.13676358`. |

Dawn loads X11, Wayland and the Vulkan loader at run time, so only the **X11 headers** are needed to compile on Linux. If you
cannot install `libx11-xcb-dev`, build without X11 window support for local experiments:

```bash
JWEBGPU_X11=OFF ./scripts/build_natives.sh linux-x86_64
```

Never ship a library built that way. CI always builds with X11 and Wayland on.

### Settings

| Variable | Default | Meaning |
|----------|---------|---------|
| `JWEBGPU_DAWN_CACHE` | `~/.cache/jwebgpu/dawn` | Where the Dawn sources (`src/`) and build trees (`build/<target>/`) live. Delete it to start clean. |
| `JWEBGPU_JOBS` | number of CPU cores | Parallel compile jobs. Lower it if the build runs out of memory. |
| `JWEBGPU_X11`, `JWEBGPU_WAYLAND` | `ON` | Linux surface support. |
| `JWEBGPU_OUT_DIR` | the target's folder under `natives/` or `jniLibs/` | Where the finished library is copied. |
| `JWEBGPU_MAX_GLIBC` | no limit | Linux only. Fails the build if the library needs a newer glibc (CI uses `2.28`). |
| `JWEBGPU_ANDROID_API` | `26` | Lowest Android API level the Android library supports. |
| `JWEBGPU_ANDROID_NDK` | see above | Path to the Android NDK. |

The Dawn revision is pinned in `scripts/dawn.version`. Dawn publishes no release tags, so a commit is pinned instead, and the
vendored specification in `spec/` must come from the same revision (Dawn's `DEPS` file names the `webgpu-headers` commit).

### How long it takes

- **First run**: the download is about 1.3 GB of sources, which takes a few minutes on a fast connection and much longer on a
  slow one. The compile takes roughly 30 minutes on 8 cores, and an hour or more on 2 to 4 cores. It needs about 2 GB of disk
  space (1.3 GB of it is the downloaded sources).
- **Later runs**: sources are reused from the cache, and an unchanged build tree finishes in seconds.

## Building the Android backend

The JNI backend has two native parts: Dawn (a prebuilt library per ABI) and the C glue that Gradle generates and compiles
with the NDK. It supports **arm64-v8a and x86_64** only, because the struct layouts assume 8-byte pointers.

1. **Get the Dawn libraries.** CI builds them, and `scripts/build_natives.sh android-arm64-v8a` (or `android-x86_64`) builds
   one yourself and copies it to `jwebgpu-jni/src/main/jniLibs/<abi>/libwebgpu_dawn.so`. That folder is gitignored. The libraries are built for
   Android 8.0 (API 26) with Vulkan and the Null backend (OpenGL ES is switched off), and link the C++ runtime statically,
   so no `libc++_shared.so` is needed.
2. **Enable the Android module** (see above) and build:
   ```bash
   ./gradlew -PincludeAndroid=true :jwebgpu-jni:assembleRelease
   ```
   Gradle runs the generator (`--emit jni-java-android` and `--emit jni-c`), then CMake compiles
   `build/generated/sources/jwebgpu/main/cpp/jwebgpu_jni.c` with `src/main/cpp/native_helpers.c` into `libjwebgpu_jni.so`
   and links it against the Dawn library of each ABI. The AAR carries both libraries.
3. **Optional settings** (Gradle properties):

   | Property | Meaning |
   |----------|---------|
   | `-Pjwebgpu.abis=arm64-v8a` | Build only some ABIs (comma separated). Useful while you only have one Dawn library. |
   | `-Pjwebgpu.dawnInclude=/path/to/dir` | The directory that contains `webgpu.h`. The default is the vendored `spec/` directory. Point it at the include directory of a particular Dawn build to compile against its header: the glue checks every struct layout it uses at compile time, so a header that differs from the specification fails the build. |
   | `-Pjwebgpu.fastNative=false` | Generate plain `native` methods instead of ART's `@FastNative`, for example to measure what the annotation saves. |

The sample app `samples/android-triangle` runs the backend on a device. It is a release build (not debuggable, shrunk with
R8, signed with the debug key so it installs), and it reports to logcat:

```bash
./gradlew -PincludeAndroid=true :samples:android-triangle:assembleRelease
adb install -r samples/android-triangle/build/outputs/apk/release/android-triangle-release.apk
adb shell am start -n io.github.stringdotjar.jwebgpu.samples.android/.MainActivity
adb logcat -s jwebgpu          # ends with "RESULT OK" and "ALL DONE"
adb uninstall io.github.stringdotjar.jwebgpu.samples.android
```

### Testing the JNI backend on the desktop

The same generated Java and C build for the desktop JVM, so the unit tests can run the Android backend without a phone:

```bash
./gradlew :jwebgpu-test:testJni
```

The task builds `libjwebgpu_jni` for your machine with CMake and the desktop Dawn library (the one from
[Native libraries](#native-libraries-dawn), or `-Pjwebgpu.library=/path/to/libwebgpu_dawn.so`). It needs CMake and a C
compiler; **without the Dawn library the glue build is skipped and the JNI tests are skipped, not failed**. The software
Vulkan run works the same way as for the FFM backend (`VK_ICD_FILENAMES=... ./gradlew :jwebgpu-test:testJni`).

## Building and testing the web backend

`jwebgpu-teavm` compiles like every other module (`./gradlew :jwebgpu-teavm:compileJava`). The generator reads the browser's
WebIDL (`spec/webgpu.idl`) next to `spec/webgpu.json` and writes the browser interfaces, the descriptor marshallers and the
object classes. TeaVM is pinned to **0.13.0**.

`samples/web-triangle` is the program that proves it works. Translate it to JavaScript and to WasmGC and collect the pages:

```bash
./gradlew :samples:web-triangle:webDist      # build/web/{js.html,wasm.html,app.js,app.wasm,...}
```

and run both in a headless Chrome with a software WebGPU adapter (no graphics card needed):

```bash
./gradlew :samples:web-triangle:browserTest
```

The task needs Node.js 18 or newer and a Chrome or Chromium; it finds the browser on the `PATH`, or from the
`JWEBGPU_CHROME` environment variable. **Without a browser the script prints `SKIPPED` and the task passes**, so CI
machines without Chrome are fine. The flags that select the software adapter are in `scripts/run_browser_test.mjs`
(`--enable-unsafe-webgpu --enable-features=Vulkan --use-vulkan=swiftshader --use-angle=swiftshader
--enable-unsafe-swiftshader --disable-vulkan-surface`); override them with `JWEBGPU_CHROME_FLAGS`. Drawing to a canvas
needs the Vulkan flags: with only `--use-webgpu-adapter=swiftshader` the offscreen part works but the device is lost as
soon as a canvas texture is used.

To look at the page yourself, serve `build/web` over `http://localhost` (WebGPU needs a secure context and a WasmGC
module cannot load from `file://`), for example with `python3 -m http.server --directory samples/web-triangle/build/web`.

## Troubleshooting

### Wrong Java version

- **Symptom**: Build fails with "invalid target release", "class file version" or "Dependency requires at least JVM runtime
  version 25".
- **Fix**: Make sure Gradle runs on JDK 25 (set `JAVA_HOME` to a JDK 25, or let `gradle-daemon-jvm.properties` provision
  one), then run `java -version` to check.

### Toolchain download fails or times out

- **Symptom**: The foojay resolver cannot download a JDK.
- **Fix**: Install JDK 25 yourself and pass the two `-Porg.gradle.java.installations...` flags from the Java section above.

### Gradle wrapper not executable (Linux / macOS)

- **Symptom**: `./gradlew` fails with "Permission denied".
- **Fix**: Run `chmod +x gradlew`, then try again.

### Android: SDK not found or licenses not accepted

- **Symptom**: Building the Android module fails with "SDK location not found" or license errors.
- **Fix**: Create `local.properties` in the repository root with `sdk.dir=/path/to/android/sdk`, and run
  `sdkmanager --licenses` (or accept the licenses in Android Studio).

### Android: the Dawn library or the glue is missing

- **Symptom**: The CMake step fails with "The Dawn library does not exist", or the app crashes with an
  `UnsatisfiedLinkError` for `webgpu_dawn`.
- **Fix**: Put `libwebgpu_dawn.so` in `jwebgpu-jni/src/main/jniLibs/<abi>/` for every ABI you build (or restrict the build
  with `-Pjwebgpu.abis=...`), and run the app on an arm64-v8a or x86_64 device or emulator.

### Dependency not found from another project

- **Symptom**: The consuming project cannot resolve `io.github.stringdotjar:jwebgpu-api`.
- **Fix**: Check that `includeBuild(...)` in that project's `settings.gradle.kts` points at the correct absolute path of the
  jwebgpu checkout, and that the coordinates use the group `io.github.stringdotjar`.

### Line endings (CRLF vs LF) on Windows

- **Symptom**: `./gradlew` fails with "bad interpreter".
- **Fix**: Make sure `gradlew` uses LF line endings (`git config core.autocrlf input`, then re-checkout).

### Gradle daemon or port issues

- **Symptom**: "Address already in use" or daemon-related errors.
- **Fix**: Run `./gradlew --stop`, then try again. This project sets `org.gradle.daemon=false`, so the daemon may already be
  disabled.

If you hit an error not listed here, open an issue with your OS, Java version (`java -version`), Gradle version
(`./gradlew --version`), and the full error message.
