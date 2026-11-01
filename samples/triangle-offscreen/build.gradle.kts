plugins {
  id("jwebgpu.java-base")
  java
  application
  alias(libs.plugins.graalvm.native)
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

dependencies {
  implementation(project(":jwebgpu-ffm"))

  // The Dawn library of this machine. A program that ships to several platforms adds one natives module for each.
  JWebGpuNatives.hostTarget()?.let { runtimeOnly(project(":" + JWebGpuNatives.moduleName(it))) }
}

application {
  mainClass = "io.github.stringdotjar.jwebgpu.samples.TriangleOffscreen"

  // The FFM backend calls native code, which Java only allows when it is asked for.
  applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

// A GraalVM native image of the sample: ./gradlew :samples:triangle-offscreen:nativeCompile. It needs nothing but the
// dependencies above, because the jwebgpu jars ship all the metadata a native image needs. The build runs on a GraalVM
// for JDK 25 (point Gradle at one with -Porg.gradle.java.installations.paths=/path/to/graalvm, or set JAVA_HOME).
graalvmNative {
  binaries {
    named("main") {
      imageName = "triangle-offscreen"
      javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
        nativeImageCapable = true
      }
    }
  }
}
