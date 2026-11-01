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
    mainClass = "io.github.stringdotjar.jwebgpu.samples.web.WebSamples"
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
// By default the task is skipped when Node.js is missing, and the script itself skips when there is no Chrome.
// Pass -Pjwebgpu.requireBrowser=true (CI does) to turn both skips into failures. The console output of the pages
// goes to build/browser-test/console.log.
tasks.register<Exec>("browserTest") {
  group = "verification"
  description = "Runs the JavaScript and WasmGC translations of the sample in headless Chrome."
  dependsOn(webDist)
  val require = providers.gradleProperty("jwebgpu.requireBrowser").map { it.toBoolean() }.getOrElse(false)
  val log = layout.buildDirectory.file("browser-test/console.log").get().asFile
  val node = providers.exec { commandLine("which", "node"); isIgnoreExitValue = true }.result.get().exitValue == 0
  if (require && !node) {
    doFirst { throw GradleException("Node.js is required (jwebgpu.requireBrowser=true) but was not found on the PATH.") }
  } else {
    onlyIf("Node.js is installed") { node }
  }
  if (require) {
    environment("JWEBGPU_REQUIRE_BROWSER", "1")
  }
  environment("JWEBGPU_BROWSER_LOG", log.absolutePath)
  commandLine(
    "node", rootProject.file("scripts/run_browser_test.mjs").absolutePath,
    layout.buildDirectory.dir("web").get().asFile.absolutePath, "js.html", "wasm.html"
  )
}
