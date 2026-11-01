plugins {
  id("jwebgpu.java-base")
  java
  application
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

dependencies {
  implementation(project(":jwebgpu-ffm"))
}

application {
  mainClass = "io.github.stringdotjar.jwebgpu.samples.TriangleOffscreen"

  // The FFM backend calls native code, which Java only allows when it is asked for.
  applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}
