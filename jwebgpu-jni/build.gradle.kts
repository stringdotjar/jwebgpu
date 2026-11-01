import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
  id("jwebgpu.android-library")
}

// The generator writes the Java classes and the C glue of this backend from the vendored specification. The files go
// into build/generated and are never committed.
val jwebgpuGenerator = configurations.create("jwebgpuGenerator") {
  isCanBeConsumed = false
  isCanBeResolved = true
}

dependencies {
  add("jwebgpuGenerator", project(":jwebgpu-generator"))
}

val specDir = rootProject.layout.projectDirectory.dir("spec")
val generatedJava = layout.buildDirectory.dir("generated/sources/jwebgpu/main/java")
val generatedGlue = layout.buildDirectory.dir("generated/sources/jwebgpu/main/cpp")

fun generate(name: String, kind: String, outDir: Provider<Directory>) = tasks.register<JavaExec>(name) {
  group = "build"
  description = "Generates the '$kind' sources of the JNI backend from the specification."
  classpath = jwebgpuGenerator
  mainClass.set("io.github.stringdotjar.jwebgpu.generator.Generator")
  javaLauncher.set(
    project.extensions.getByType<JavaToolchainService>()
      .launcherFor(project.extensions.getByType<JavaPluginExtension>().toolchain)
  )
  inputs.dir(specDir)
  outputs.dir(outDir)
  argumentProviders.add(CommandLineArgumentProvider {
    listOf(
      "--spec", specDir.file("webgpu.json").asFile.absolutePath,
      "--emit", kind,
      "--out", outDir.get().asFile.absolutePath
    )
  })
  doFirst {
    // Remove the output of an earlier run so that files of deleted types do not linger.
    delete(outDir)
  }
}

// The Android flavor marks the cheap native methods with ART's @FastNative annotation.
val generateJava = generate("generateJWebGpuJniJavaAndroidSources", "jni-java-android", generatedJava)
val generateGlue = generate("generateJWebGpuJniCSources", "jni-c", generatedGlue)

// The Dawn headers: by default the vendored spec/webgpu.h. To build against the header of a particular Dawn build
// instead, pass -Pjwebgpu.dawnInclude=/path/to/the/directory/that/contains/webgpu.h.
val webgpuInclude = providers.gradleProperty("jwebgpu.dawnInclude")
  .orElse(specDir.asFile.absolutePath)

android {
  namespace = "io.github.stringdotjar.jwebgpu.jni"
  compileSdk = 36

  // 64-bit only: the struct layouts the generator computes assume 8-byte pointers. 32-bit ABIs are not supported.
  defaultConfig {
    minSdk = 26
    ndk {
      abiFilters += listOf("arm64-v8a", "x86_64")
    }
    consumerProguardFiles("consumer-rules.pro")
    externalNativeBuild {
      cmake {
        arguments(
          "-DJWEBGPU_GLUE_DIR=${generatedGlue.get().asFile.absolutePath}",
          "-DWEBGPU_INCLUDE=${webgpuInclude.get()}",
          "-DANDROID_STL=none",
          "-DANDROID_PLATFORM=android-26"
        )
      }
    }
  }
  ndkVersion = "28.2.13676358"
  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }
  sourceSets.getByName("main") {
    java.srcDir(files(generatedJava).builtBy(generateJava))
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
  }
}

// The CMake build reads the generated C file, so it must exist before any native build starts.
tasks.named("preBuild") {
  dependsOn(generateGlue, generateJava)
}

dependencies {
  api(project(":jwebgpu-api"))
  implementation(libs.jetbrains.annotations)
}
