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
//
// -Pjwebgpu.nativeToolchain=false skips Gradle's toolchain lookup and uses the GraalVM in GRAALVM_HOME instead. CI uses
// it because Gradle does not recognize GraalVM on Windows as able to build native images (the tool is native-image.cmd
// there, not an executable).
val nativeToolchain = providers.gradleProperty("jwebgpu.nativeToolchain").map { it.toBoolean() }.getOrElse(true)

graalvmNative {
  toolchainDetection = nativeToolchain
  binaries {
    named("main") {
      imageName = "triangle-offscreen"
      if (nativeToolchain) {
        javaLauncher = javaToolchains.launcherFor {
          languageVersion = JavaLanguageVersion.of(25)
          nativeImageCapable = true
        }
      }
    }
  }
}
