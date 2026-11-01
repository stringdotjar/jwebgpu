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

  // The GraalVM native image metadata: every foreign call and callback, so a native image of an application needs no
  // configuration of its own.
  emitResources("graal")
}

tasks.jar {
  manifest {
    // Lets a program started with "java -jar" call native code without the --enable-native-access flag.
    attributes("Enable-Native-Access" to "ALL-UNNAMED")
  }
}
