// Generates Java sources from the vendored specification before a module compiles.
//
// The generator (jwebgpu-generator) reads spec/webgpu.json and writes Java files into
// build/generated/sources/jwebgpu/<source set>/java. A module picks the kind of sources with the jwebgpuGenerated
// extension (emit("api"), emit("ffm"), ...). The files are added to the source set, so compiling, Javadoc,
// the sources jar and Checkstyle all see them. They are never committed (they live under build/), and Spotless
// skips them: the emitter already writes correctly formatted code.
plugins {
  `java-library`
  id("com.diffplug.spotless")
}

val generator = configurations.create("jwebgpuGenerator") {
  isCanBeConsumed = false
  isCanBeResolved = true
}

dependencies {
  add("jwebgpuGenerator", project(":jwebgpu-generator"))
}

// Each module chooses what to generate: jwebgpuGenerated { emit("api") }.
extensions.create<JWebGpuGenerated>("jwebgpuGenerated", project, generator)

spotless {
  java {
    targetExclude("build/generated/**")
  }
}
