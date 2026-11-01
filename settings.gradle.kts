import java.util.Properties

/**
 * Root settings for the jwebgpu multi-module build.
 *
 * <p>Declares the build-logic included build so convention plugins are available to all
 * subprojects, centralizes repository declarations, and conditionally includes the Android
 * module when the Android SDK is present.
 */

pluginManagement {
  includeBuild("build-logic")
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
    maven("https://s01.oss.sonatype.org")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// The Android module is optional so the library can be built without an Android SDK.
// Enable via: -PincludeAndroid=true (CI / one-off) or includeAndroid=true in local.properties (gitignored).
val includeAndroidFromCli = startParameter.projectProperties["includeAndroid"] == "true"
val includeAndroidFromLocal = run {
  val f = File(settingsDir, "local.properties")
  if (f.exists()) {
    val props = Properties()
    f.inputStream().use(props::load)
    props.getProperty("includeAndroid", "false") == "true"
  } else {
    false
  }
}
val includeAndroid = includeAndroidFromCli || includeAndroidFromLocal
gradle.extra["includeAndroid"] = includeAndroid

dependencyResolutionManagement {
  repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
  repositories {
    mavenCentral()
    gradlePluginPortal()
    google()
    mavenLocal()
    maven("https://s01.oss.sonatype.org")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    maven("https://jitpack.io")
  }
}

rootProject.name = "jwebgpu"

include(
  "jwebgpu-generator",
  "jwebgpu-api",
  "jwebgpu-ffm",
  "jwebgpu-teavm",
  "jwebgpu-test",
  "samples:triangle-offscreen",
  "samples:web-triangle"
)

// One module per desktop target holds that target's Dawn library, so an application only bundles the platforms it
// ships to. The modules need no build file of their own; the convention plugin sets them up.
val nativeTargets = listOf(
  "linux-x86_64", "linux-aarch64", "windows-x86_64", "windows-aarch64", "macos-x86_64", "macos-aarch64"
)
for (target in nativeTargets) {
  include("jwebgpu-natives-$target")
  // The library of a target is never committed, so a fresh checkout has no folder for it, and Gradle refuses a
  // project without one.
  project(":jwebgpu-natives-$target").projectDir = file("jwebgpu-natives/$target").also { it.mkdirs() }
}

if (includeAndroid) {
  include("jwebgpu-jni")
  include("samples:android-triangle")
}
