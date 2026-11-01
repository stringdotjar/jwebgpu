plugins {
  id("jwebgpu.java-library")
}

dependencies {
  api(project(":jwebgpu-api"))
  implementation(libs.jetbrains.annotations)
  compileOnly(libs.teavm.core)
}
