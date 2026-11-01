plugins {
  id("jwebgpu.java-base")
  java
  checkstyle
  id("jwebgpu.generated-sources")
}

// The JNI backend runs on a desktop Java virtual machine too, so the unit tests can exercise it without a phone. This
// source set holds the generated Java classes of the backend plus the hand-written ones of jwebgpu-jni, except the
// package that needs the Android framework.
val jniHost: SourceSet by sourceSets.creating {
  java.srcDir(rootProject.layout.projectDirectory.dir("jwebgpu-jni/src/main/java"))
  java.exclude("**/android/**")
}

// The hand-written JNI classes are formatted by the root project, and the generated ones are written formatted already.
spotless {
  java {
    target("src/**/*.java")
  }
}

// The checks of the generated API are generated too, as plain Java, so the tests need no reflection.
var jniGlue: TaskProvider<JavaExec>? = null
jwebgpuGenerated {
  emit("api-checks", "test")
  emit("jni-java", "jniHost")
  jniGlue = emitNative("jni-c", "jniHost")
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

// Only the generated and hand-written JNI classes are checked here (the tests themselves are not).
checkstyle {
  toolVersion = "10.21.0"
  configDirectory.set(rootProject.layout.projectDirectory.dir("gradle/checkstyle"))
}

tasks.named("checkstyleTest") {
  enabled = false
}

dependencies {
  implementation(project(":jwebgpu-generator"))
  implementation(project(":jwebgpu-api"))
  implementation(project(":jwebgpu-ffm"))
  implementation(libs.jetbrains.annotations)

  "jniHostImplementation"(project(":jwebgpu-api"))
  "jniHostImplementation"(libs.jetbrains.annotations)
  testImplementation(jniHost.output)

  // The Dawn library of this machine. The jwebgpu-ffm jar carries no library, so the tests get it from the natives
  // module of the host platform only.
  JWebGpuNatives.hostTarget()?.let { testRuntimeOnly(project(":" + JWebGpuNatives.moduleName(it))) }

  testRuntimeOnly(libs.junit.platform.launcher)
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
}

tasks.named<Test>("test") {
  useJUnitPlatform()

  // The tests of the JNI backend run in their own task, with the JNI backend installed (see testJni below).
  filter {
    excludeTestsMatching("io.github.stringdotjar.jwebgpu.jni.*")
  }

  // The FFM backend calls native code, which Java only allows when it is asked for.
  jvmArgs("--enable-native-access=ALL-UNNAMED")

  // The native library for the FFM tests. By default the tests use the one from the natives module of this machine
  // (build it with scripts/build_natives.sh) and skip when there is none. Pass -Pjwebgpu.library=/path/to/lib to use
  // another file.
  providers.gradleProperty("jwebgpu.library").orNull?.let { systemProperty("jwebgpu.library.path", it) }

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

// Builds libjwebgpu_jni for this machine from the generated C glue, linked against the Dawn library. The task is
// skipped when the Dawn library is missing, and the JNI tests are then skipped too.
val osName = org.gradle.internal.os.OperatingSystem.current()
val dawnLibrary = providers.gradleProperty("jwebgpu.library").orElse(
  JWebGpuNatives.hostTarget()?.let { JWebGpuNatives.libraryFile(rootDir, it).absolutePath } ?: "unsupported-host"
)
val glueBuildDir = layout.buildDirectory.dir("jni-host")
val glueLibrary = glueBuildDir.map {
  it.file(if (osName.isMacOsX) "libjwebgpu_jni.dylib" else "libjwebgpu_jni.so")
}
val glueSource = layout.buildDirectory.dir("generated/sources/jwebgpu/jniHost/cpp")
val cmakeSource = rootProject.layout.projectDirectory.dir("jwebgpu-jni/src/main/cpp")
val webgpuInclude = providers.gradleProperty("jwebgpu.dawnInclude")
  .orElse(rootProject.layout.projectDirectory.dir("spec").asFile.absolutePath)

val buildJniHostGlue = tasks.register<Exec>("buildJniHostGlue") {
  group = "build"
  description = "Builds the JNI glue library for this machine, so the JNI backend can run on a desktop JVM."
  dependsOn(requireNotNull(jniGlue))
  inputs.dir(glueSource)
  inputs.dir(cmakeSource)
  inputs.property("dawn", dawnLibrary)
  outputs.dir(glueBuildDir)
  onlyIf {
    val dawn = file(dawnLibrary.get())
    if (!dawn.isFile) {
      logger.lifecycle("Skipping the JNI glue build: no Dawn library at $dawn")
    }
    dawn.isFile
  }
  commandLine(
    "sh", "-c",
    "cmake -S '${cmakeSource.asFile}' -B '${glueBuildDir.get().asFile}' -DCMAKE_BUILD_TYPE=Release " +
      "-DJWEBGPU_GLUE_DIR='${glueSource.get().asFile}' -DDAWN_LIBRARY='${dawnLibrary.get()}' " +
      "-DWEBGPU_INCLUDE='${webgpuInclude.get()}' && cmake --build '${glueBuildDir.get().asFile}'"
  )
}

// Runs the backend tests with the JNI backend instead of the FFM backend. The "jni" package tests the internals of the
// backend, and the "backend" package holds the behavior tests that also run against the FFM backend in the test task.
val testJni = tasks.register<Test>("testJni") {
  group = "verification"
  description = "Runs the backend tests against the JNI backend on the desktop JVM."
  useJUnitPlatform()
  testClassesDirs = sourceSets["test"].output.classesDirs
  classpath = sourceSets["test"].runtimeClasspath
  filter {
    includeTestsMatching("io.github.stringdotjar.jwebgpu.jni.*")
    includeTestsMatching("io.github.stringdotjar.jwebgpu.backend.*")
  }
  dependsOn(buildJniHostGlue)
  jvmArgs("--enable-native-access=ALL-UNNAMED")
  systemProperty("jwebgpu.backend", "jni")
  systemProperty("jwebgpu.specDir", rootProject.layout.projectDirectory.dir("spec").asFile.absolutePath)
  doFirst {
    if (glueLibrary.get().asFile.isFile && file(dawnLibrary.get()).isFile) {
      systemProperty("jwebgpu.library.path", dawnLibrary.get())
      systemProperty("jwebgpu.jni.library.path", glueLibrary.get().asFile.absolutePath)
    }
  }
  testLogging {
    showStandardStreams = true
  }
}

// The generated JNI classes are checked together with the other main sources.
tasks.named("checkstyleMain") {
  dependsOn("checkstyleJniHost")
}

tasks.named("check") {
  dependsOn(testJni)
}
