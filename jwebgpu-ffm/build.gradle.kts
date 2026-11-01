plugins {
  id("jwebgpu.java-library")
  id("jwebgpu.generated-sources")
}

dependencies {
  api(project(":jwebgpu-api"))
  implementation(libs.jetbrains.annotations)
}

jwebgpuGenerated {
  emit("ffm")
}

tasks.jar {
  manifest {
    // Lets a program started with "java -jar" call native code without the --enable-native-access flag.
    attributes("Enable-Native-Access" to "ALL-UNNAMED")
  }
}
