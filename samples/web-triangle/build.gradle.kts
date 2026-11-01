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
    mainClass = "io.github.stringdotjar.jwebgpu.samples.web.Experiment"
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
