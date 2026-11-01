plugins {
  id("jwebgpu.java-library")
}

dependencies {
  api(project(":jwebgpu-api"))
  implementation(libs.jetbrains.annotations)
  compileOnly(libs.teavm.core)
  compileOnly(libs.teavm.jso)
  compileOnly(libs.teavm.jso.apis)
}
