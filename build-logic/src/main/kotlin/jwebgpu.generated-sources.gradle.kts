// Generates the platform-neutral API sources from the vendored specification before the module compiles.
//
// The generator (jwebgpu-generator) reads spec/webgpu.json and writes Java files into
// build/generated/sources/jwebgpu/main/java. Those files are added to the main source set, so compiling, Javadoc,
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

val specDir = rootProject.layout.projectDirectory.dir("spec")
val generatedDir = layout.buildDirectory.dir("generated/sources/jwebgpu/main/java")

val generateSources = tasks.register<JavaExec>("generateJWebGpuSources") {
  group = "build"
  description = "Generates the Java sources of the API from the specification."
  classpath = generator
  mainClass = "io.github.stringdotjar.jwebgpu.generator.Generator"
  javaLauncher = javaToolchains.launcherFor(java.toolchain)
  inputs.dir(specDir)
  outputs.dir(generatedDir)
  argumentProviders.add(CommandLineArgumentProvider {
    listOf(
      "--spec", specDir.file("webgpu.json").asFile.absolutePath,
      "--emit", "api",
      "--out", generatedDir.get().asFile.absolutePath
    )
  })
  doFirst {
    // Remove the output of an earlier run so that files of deleted types do not linger.
    delete(generatedDir)
  }
}

sourceSets.named("main") {
  java.srcDir(generateSources)
}

tasks.matching { it.name == "compileJava" || it.name == "javadoc" || it.name == "sourcesJar" ||
    it.name == "checkstyleMain" }.configureEach {
  dependsOn(generateSources)
}

spotless {
  java {
    targetExclude("build/generated/**")
  }
}
