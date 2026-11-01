/**
 * Root aggregator for the jwebgpu multi-module build.
 *
 * The root project holds no source itself; it only applies IDE plugins and registers the
 * aggregate Javadoc task. All subproject setup lives in the convention plugins under
 * build-logic/src/main/kotlin/.
 */

plugins {
  eclipse
  idea
  id("jwebgpu.spotless-android")
  id("jwebgpu.checkstyle-android")
  alias(libs.plugins.vanniktech) apply false
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
}

val groupId: String by project

group = groupId
version = gitVersion()

fun gitVersion(): String = try {
  val proc = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
    .directory(rootDir)
    .start()
  proc.waitFor()
  proc.inputStream.bufferedReader().readText().trim().removePrefix("v").ifEmpty { "unspecified" }
} catch (_: Exception) {
  "unspecified"
}

eclipse.project.name = "jwebgpu-parent"

idea {
  module {
    outputDir = file("build/classes/java/main")
    testOutputDir = file("build/classes/java/test")
  }
}

tasks.register("javadocAll") {
  group = "verification"
  description = "Runs Javadoc (with doclint) on all Java library modules."
  val modules = arrayListOf(
    ":jwebgpu-generator:javadoc",
    ":jwebgpu-api:javadoc",
    ":jwebgpu-ffm:javadoc",
    ":jwebgpu-teavm:javadoc"
  )
  if (gradle.extra["includeAndroid"] as Boolean) {
    modules.add(":jwebgpu-jni:javadoc")
  }
  dependsOn(modules)
}
