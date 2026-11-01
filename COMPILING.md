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

jwebgpu is not published yet, so other projects (such as FlixelGDX) use it through a **Gradle composite build**. In the
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

The desktop and Android backends call [Dawn](https://dawn.googlesource.com/dawn). The scripts and CI workflow that build
Dawn for each platform are **coming later** (see `scripts/`). Until then, the repository builds and tests without any native
library, because no backend code exists yet.

---

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
