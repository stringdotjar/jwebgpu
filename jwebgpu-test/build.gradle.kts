plugins {
  id("jwebgpu.java-base")
  java
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

dependencies {
  implementation(project(":jwebgpu-generator"))
  implementation(project(":jwebgpu-api"))
  implementation(project(":jwebgpu-ffm"))
  implementation(libs.jetbrains.annotations)

  testRuntimeOnly(libs.junit.platform.launcher)
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
}

tasks.named<Test>("test") {
  useJUnitPlatform()

  // The generator tests read the vendored specification files.
  systemProperty("jwebgpu.specDir", rootProject.layout.projectDirectory.dir("spec").asFile.absolutePath)

  // The golden files of the emitter tests. Pass -Pjwebgpu.updateGolden=true to rewrite them from the current output.
  systemProperty("jwebgpu.goldenDir", layout.projectDirectory.dir("src/test/resources/golden").asFile.absolutePath)
  providers.gradleProperty("jwebgpu.updateGolden").orNull?.let { systemProperty("jwebgpu.updateGolden", it) }

  // Optional: Dawn's generated webgpu.h, used to report what Dawn adds. The tests skip when it is missing.
  // Pass -Pjwebgpu.dawnHeader=/path/to/webgpu.h to enable them.
  providers.gradleProperty("jwebgpu.dawnHeader").orNull?.let { systemProperty("jwebgpu.dawnHeader", it) }

  // Show what the Dawn comparison reports.
  testLogging {
    showStandardStreams = true
  }
}
