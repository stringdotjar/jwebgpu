plugins {
  id("jwebgpu.java-base")
  java
  alias(libs.plugins.teavm)
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

dependencies {
  implementation(project(":jwebgpu-teavm"))
  implementation(libs.teavm.jso)
  implementation(libs.teavm.jso.apis)
}

teavm {
  all {
    mainClass = "io.github.stringdotjar.jwebgpu.samples.web.WebTriangle"
  }
  js {
    addedToWebApp = true
    targetFileName = "app.js"
  }
  wasmGC {
    addedToWebApp = true
    targetFileName = "app.wasm"
  }
}

val webDist = tasks.register<Sync>("webDist") {
  group = "build"
  description = "Collects the translated JavaScript and WasmGC programs and their pages in build/web."
  dependsOn("generateJavaScript", "generateWasmGC", "copyWasmGCRuntime")
  from(layout.buildDirectory.dir("generated/teavm/js"))
  from(layout.buildDirectory.dir("generated/teavm/wasm-gc"))
  from("src/web")
  into(layout.buildDirectory.dir("web"))
}

// Runs both translations in a headless Chrome with a software adapter and checks the pixels they read back.
// The task is skipped when Node.js is missing, and the script itself skips when there is no Chrome.
tasks.register<Exec>("browserTest") {
  group = "verification"
  description = "Runs the JavaScript and WasmGC translations of the sample in headless Chrome."
  dependsOn(webDist)
  val node = providers.exec { commandLine("which", "node"); isIgnoreExitValue = true }.result.get().exitValue == 0
  onlyIf("Node.js is installed") { node }
  commandLine(
    "node", rootProject.file("scripts/run_browser_test.mjs").absolutePath,
    layout.buildDirectory.dir("web").get().asFile.absolutePath, "js.html", "wasm.html"
  )
}
