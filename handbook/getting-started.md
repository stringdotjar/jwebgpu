# Getting Started: Dependencies, Backends, and a First Program

This guide takes you from an empty Gradle project to a program that finds your GPU and prints its name. It covers the
dependencies for each platform, the natives jars, installing a backend, and the one Java flag the desktop needs.

## Table of contents

1. [What you need](#what-you-need)
2. [Adding the dependencies](#adding-the-dependencies)
3. [Installing a backend](#installing-a-backend)
4. [Allowing native access on the desktop](#allowing-native-access-on-the-desktop)
5. [Your first program: find a GPU](#your-first-program-find-a-gpu)
6. [Organizing a multi-platform project](#organizing-a-multi-platform-project)
7. [Using a local checkout of jwebgpu](#using-a-local-checkout-of-jwebgpu)
8. [Common mistakes](#common-mistakes)
9. [Check your understanding](#check-your-understanding)

## What you need

- **JDK 25** for desktop programs. jwebgpu targets Java 25, and the desktop backend uses the Foreign Function and
  Memory API that became final in Java 22.
- **Gradle** (or Maven; the examples use the Gradle Kotlin DSL).
- **For Android:** the Android Gradle plugin and a device or emulator with a 64-bit ABI (arm64-v8a or x86_64) running
  Android 8.0 (API 26) or newer.
- **For the web:** the TeaVM Gradle plugin, version 0.13.0, and a browser with WebGPU. The page must be served from a
  secure context (HTTPS or `localhost`).

You do **not** need to build Dawn yourself. The published natives jars and the Android AAR already contain it.

## Adding the dependencies

jwebgpu is on **Maven Central** under the group `io.github.stringdotjar`. Replace `<version>` with the latest version
(shown by the Maven Central badge in the [README](../README.md)).

```kotlin
repositories {
  mavenCentral()
}
```

Every program needs `jwebgpu-api`, plus the backend of each platform it runs on.

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

The Dawn library is big (about 5 to 11 MB per platform), so each operating system and CPU has its own jar, and
`jwebgpu-ffm` itself carries no native code. The six natives modules are:

| Module | Operating system and CPU |
|--------|--------------------------|
| `jwebgpu-natives-linux-x86_64` | Linux on Intel or AMD |
| `jwebgpu-natives-linux-aarch64` | Linux on ARM64 |
| `jwebgpu-natives-windows-x86_64` | Windows on Intel or AMD |
| `jwebgpu-natives-windows-aarch64` | Windows on ARM64 |
| `jwebgpu-natives-macos-x86_64` | macOS on Intel |
| `jwebgpu-natives-macos-aarch64` | macOS on Apple silicon |

**Android** (an AAR, so the Android Gradle plugin picks up its native libraries and R8 rules):

```kotlin
dependencies {
  implementation("io.github.stringdotjar:jwebgpu-api:<version>")
  implementation("io.github.stringdotjar:jwebgpu-jni:<version>")
}
```

Android needs no natives module, because the AAR carries Dawn for both supported ABIs.

**Web** (with the TeaVM Gradle plugin, version 0.13.0):

```kotlin
plugins {
  java
  id("org.teavm") version "0.13.0"
}

dependencies {
  implementation("io.github.stringdotjar:jwebgpu-api:<version>")
  implementation("io.github.stringdotjar:jwebgpu-teavm:<version>")
}
```

The web needs no natives either: the browser already ships WebGPU.

## Installing a backend

Think of `JWebGpu` as the front desk of a hotel, and of each backend as a hotel behind it. Your program always talks to
the same front desk, but before the doors open, someone has to say which hotel it belongs to. That is what installing a
backend does.

Each platform calls its backend's installer **once, at startup**, before any other jwebgpu call:

```java
// Desktop launcher:
JWebGpuFfm.install();

// Android, for example in Application.onCreate():
JWebGpuJni.install();

// Web, at the start of main(...):
JWebGpuTeaVm.install();

// Platform-neutral code from here on:
GpuInstance instance = JWebGpu.createInstance();
```

Why explicit? Automatic discovery (with `ServiceLoader` or reflection) does not work on platforms that compile ahead
of time, such as TeaVM and GraalVM native images. An explicit call works everywhere, and it makes the choice obvious
when you read the code.

What each installer does:

- `JWebGpuFfm.install()` finds the Dawn library for the current operating system and CPU in the matching natives jar,
  copies it once into a cache directory (`~/.cache/jwebgpu/natives/<hash>/` on Linux), loads it, and registers the
  backend. `JWebGpuFfm.install(Path)` loads a library file of your choice instead, and so does the system property
  `-Djwebgpu.library.path=/path/to/libwebgpu_dawn.so`.
- `JWebGpuJni.install()` loads `libwebgpu_dawn.so` and the generated glue `libjwebgpu_jni.so` from the AAR.
- `JWebGpuTeaVm.install()` checks that `navigator.gpu` exists and throws an `IllegalStateException` with a clear
  message if it does not. `JWebGpuTeaVm.isSupported()` asks the same question without throwing, so you can show a
  friendly message instead.

Calling an installer again does nothing. Installing a different backend while one is installed throws an
`IllegalStateException`. If you forget to install one, `JWebGpu.createInstance()` throws an `IllegalStateException` that
names the three installers. `JWebGpu.isInstalled()` tells you whether a backend is installed.

## Allowing native access on the desktop

The desktop backend calls native code through Java's Foreign Function and Memory API. Java treats that as a restricted
operation and wants you to opt in, so start the program with:

```bash
java --enable-native-access=ALL-UNNAMED -cp ... com.example.Main
```

Without it, Java prints a warning on the first native call (and a future Java version will refuse). An executable jar
can put `Enable-Native-Access: ALL-UNNAMED` in its manifest instead; the `jwebgpu-ffm` jar's own manifest already says
so. With the Gradle `application` plugin, add the flag to the default JVM arguments:

```kotlin
application {
  mainClass = "com.example.Main"
  applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}
```

## Your first program: find a GPU

This desktop program installs the backend, asks for an adapter, and prints what it found. It shows the basic rhythm of
every jwebgpu program: create an instance, start an asynchronous request, and pump events until the answer arrives.

```java
package com.example;

import io.github.stringdotjar.jwebgpu.GpuAdapter;
import io.github.stringdotjar.jwebgpu.GpuAdapterInfo;
import io.github.stringdotjar.jwebgpu.GpuCallbackMode;
import io.github.stringdotjar.jwebgpu.GpuInstance;
import io.github.stringdotjar.jwebgpu.JWebGpu;
import io.github.stringdotjar.jwebgpu.ffm.JWebGpuFfm;

public final class Main {

  private static GpuAdapter adapter;
  private static boolean done;

  public static void main(String[] args) throws InterruptedException {
    JWebGpuFfm.install();
    GpuInstance instance = JWebGpu.createInstance();

    instance.requestAdapter(null, GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, result, message) -> {
      if (result == null) {
        System.out.println("No adapter: " + status + " " + message);
      }
      adapter = result;
      done = true;
    });

    // ALLOW_PROCESS_EVENTS means: run the callback inside processEvents(), on this thread.
    while (!done) {
      instance.processEvents();
      Thread.sleep(1);
    }

    if (adapter != null) {
      GpuAdapterInfo info = new GpuAdapterInfo();
      adapter.getInfo(info);
      System.out.println("GPU: " + info.description + " (" + info.backendType + ", " + info.adapterType + ")");
      adapter.release();
    }
    instance.release();
  }
}
```

Run it with `--enable-native-access=ALL-UNNAMED`. On a machine with a graphics card it prints something like
`GPU: ... (VULKAN, DISCRETE_GPU)`.

From here, the next steps are requesting a device ([Instances, Adapters, and Devices](instances-adapters-and-devices.md))
and drawing a triangle ([Render Passes](render-passes.md)).

## Organizing a multi-platform project

Your rendering code only uses `jwebgpu-api`, and only the platform's launcher touches a backend. A clean layout puts
them in separate Gradle modules:

```
my-app/
  core/       Rendering code. Depends on jwebgpu-api only.
  desktop/    main(...) that calls JWebGpuFfm.install(). Depends on core, jwebgpu-ffm, and the natives jars.
  android/    The Android app that calls JWebGpuJni.install(). Depends on core and jwebgpu-jni.
  web/        The TeaVM main(...) that calls JWebGpuTeaVm.install(). Depends on core and jwebgpu-teavm.
```

Because `core` never names a backend, it compiles and runs the same on all three. Where the launcher lives in another
module than the code that uses the backend, the backend can even be a `runtimeOnly` dependency.

Keep one difference in mind while you write `core`: the browser cannot block, so code that waits for an answer should
continue in a callback instead of looping. The [Asynchronous Calls and Callbacks](async-and-callbacks.md) guide shows a
pattern that works on every platform.

## Using a local checkout of jwebgpu

To try unreleased changes of jwebgpu in your project, put this in your project's `settings.gradle.kts`:

```kotlin
includeBuild("../jwebgpu")
```

Gradle then replaces the Maven Central coordinates above with your local checkout, so nothing else in the build
changes. A local checkout has no Dawn library until you build one; see
[COMPILING.md](../COMPILING.md#native-libraries-dawn).

## Common mistakes

- **Calling `JWebGpu.createInstance()` before installing a backend.** It throws an `IllegalStateException`. Install
  first, at the very top of `main(...)` or `Application.onCreate()`.
- **Forgetting the natives jar.** `JWebGpuFfm.install()` fails with a message that names the exact
  `jwebgpu-natives-<target>` module to add. Add it as a `runtimeOnly` dependency.
- **Forgetting `--enable-native-access=ALL-UNNAMED`.** The program still runs, but prints a warning.
- **Opening the web page from `file://`.** WebGPU needs a secure context, and a WasmGC module cannot load from a
  file. Serve the page from `http://localhost` or over HTTPS.
- **Running on a 32-bit Android ABI.** Only arm64-v8a and x86_64 are supported.

## Check your understanding

1. Why does the desktop need a `jwebgpu-natives-<target>` jar, while Android and the web do not?
2. What happens if you call `JWebGpuFfm.install()` twice?
3. Why does the first program loop around `instance.processEvents()`?

(Answers: the desktop backend carries no native code, so the Dawn library comes from a separate jar per platform,
while the Android AAR includes its own libraries and the browser has WebGPU built in; nothing, the second call is
ignored; the callback was registered with `ALLOW_PROCESS_EVENTS`, so it only runs inside `processEvents()`, and the
loop keeps calling it until the answer has arrived.)

**Next:** [Architecture for Users](architecture.md)
