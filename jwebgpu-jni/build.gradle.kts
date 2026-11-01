plugins {
  id("jwebgpu.android-library")
}

android {
  namespace = "io.github.stringdotjar.jwebgpu.jni"
  compileSdk = 36

  defaultConfig {
    minSdk = 24
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
  }
}

dependencies {
  api(project(":jwebgpu-api"))
  implementation(libs.jetbrains.annotations)
}
