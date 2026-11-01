plugins {
  id("jwebgpu.java-library")
  id("jwebgpu.generated-sources")
}

dependencies {
  api(project(":jwebgpu-api"))
  implementation(libs.jetbrains.annotations)
  compileOnly(libs.teavm.core)
  compileOnly(libs.teavm.jso)
  compileOnly(libs.teavm.jso.apis)
}

// The browser classes (JSO interfaces, marshallers, enum tables) are generated from the specification.
jwebgpuGenerated {
  emit("teavm")
}
