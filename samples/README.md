# Samples

Small, runnable programs that show how to use jwebgpu the way an application would. Samples are not published to Maven.
They double as living documentation and as the manual check that a backend really draws something on a real GPU.

| Sample | What it shows | Run it |
|--------|---------------|--------|
| `triangle-offscreen` | Installs the desktop backend, requests an adapter and a device, builds a pipeline from a WGSL shader, draws a triangle into a texture that is never shown, and prints the pixels as text. It needs no window. | `./gradlew :samples:triangle-offscreen:run` |
| `android-triangle` | An Android app that installs the JNI backend on a real device, draws a triangle off screen and on a `SurfaceView`, checks that 10,000 frames allocate nothing, and times single calls. Results go to logcat under the tag `jwebgpu`. | See [Building the Android backend](../COMPILING.md#building-the-android-backend) |

The desktop samples need the Dawn library for your machine (see [COMPILING.md](../COMPILING.md#native-libraries-dawn)). The
Gradle `run` task already passes `--enable-native-access=ALL-UNNAMED`; add the same flag when you start a program yourself.
