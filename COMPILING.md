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
8. [Troubleshooting](#troubleshooting)

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

> [!NOTE]
> The JNI backend will need the Android NDK and CMake once its native glue is written. They are not required yet.

---

## Native libraries (Dawn)

The desktop backend calls [Dawn](https://dawn.googlesource.com/dawn), Google's WebGPU implementation, through its `webgpu.h`
C API. Dawn is built from source into **one shared library per platform**, and the result is bundled into the
`jwebgpu-ffm` jar. The libraries are **not committed**: CI builds them (see `.github/workflows/natives.yml`), and you can
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

The **Null backend** is a fake GPU that does nothing. Unit tests use it so they can run on machines without a graphics card.
The script also accepts `fetch`, which only downloads the Dawn sources.

The finished library is copied to `jwebgpu-ffm/src/main/resources/natives/<target>/` as `libwebgpu_dawn.so`,
`webgpu_dawn.dll` or `libwebgpu_dawn.dylib`. That folder is gitignored. The library is stripped, and it exports **only** the
`wgpu*` functions from `webgpu.h`; the script checks this at the end and fails if anything else leaks out. On Linux it also
links the C++ runtime statically, so the library needs nothing but glibc (version 2.38 or newer when built on Ubuntu 24.04,
because the build host's glibc sets the floor; the script prints the exact requirement at the end).

### What to install first

| Platform | Install |
|----------|---------|
| **All** | `git`, Python 3, CMake 3.22 or newer, and [Ninja](https://ninja-build.org) (`pip install ninja` works everywhere). |
| **Linux (Ubuntu / Debian)** | `sudo apt install build-essential cmake libx11-dev libx11-xcb-dev libxcb1-dev`. A C++20 compiler is required: GCC 13+ or Clang 16+. |
| **Linux (Fedora / RHEL)** | `sudo dnf install gcc-c++ cmake libX11-devel libxcb-devel` |
| **Linux (Arch)** | `sudo pacman -S base-devel cmake libx11 libxcb` |
| **Windows** | Visual Studio 2022 (or Build Tools) with the **Desktop development with C++** workload, and Git for Windows. Run the script from Git Bash **inside a developer shell**, so `cl.exe` is on `PATH` (for example, open "x64 Native Tools Command Prompt for VS 2022", then start `bash` from there). |
| **macOS** | Xcode command line tools (`xcode-select --install`), then `brew install cmake ninja`. |

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
| `JWEBGPU_OUT_DIR` | the target's folder under `natives/` | Where the finished library is copied. |

The Dawn revision is pinned in `scripts/dawn.version`. Dawn publishes no release tags, so a commit is pinned instead, and the
vendored specification in `spec/` must come from the same revision (Dawn's `DEPS` file names the `webgpu-headers` commit).

### How long it takes

- **First run**: the download is about 1.3 GB of sources, which takes a few minutes on a fast connection and much longer on a
  slow one. The compile takes roughly 30 minutes on 8 cores, and an hour or more on 2 to 4 cores. It needs about 2 GB of disk
  space (1.3 GB of it is the downloaded sources).
- **Later runs**: sources are reused from the cache, and an unchanged build tree finishes in seconds.

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
