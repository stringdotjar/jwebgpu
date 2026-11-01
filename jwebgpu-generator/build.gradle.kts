plugins {
  id("jwebgpu.java-tool")
  application
}

dependencies {
  implementation(libs.jetbrains.annotations)
}

application {
  mainClass = "io.github.stringdotjar.jwebgpu.generator.Generator"
}

tasks.named<JavaExec>("run") {
  // Run from the repository root so the default path spec/webgpu.json resolves.
  workingDir = rootProject.projectDir
}
