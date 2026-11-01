import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.JavaLibrary
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven

// Convention for the jwebgpu-natives-<target> modules. Each one is a jar that holds nothing but the Dawn library of one
// desktop target at natives/<target>/<library name>, so an application only bundles the platforms it ships to. The
// library is not committed: CI builds it and scripts/build_natives.sh copies it into src/main/resources.
plugins {
  id("jwebgpu.java-library")
}

val target = project.name.removePrefix("jwebgpu-natives-")
val library = JWebGpuNatives.libraryFile(rootProject.projectDir, target)

description = "The Dawn native library of jwebgpu-ffm for $target."

// A natives jar without its library is useless, so building one only warns (the other modules do not need it), while
// publishing one fails so that a release can never ship an empty jar.
tasks.named("processResources") {
  doFirst {
    if (!library.isFile) {
      logger.quiet(
        "WARNING: ${project.name} is empty because ${library.relativeTo(rootProject.projectDir)} does not exist. " +
          "Build it with scripts/build_natives.sh $target, or download the dawn-natives-all artifact of the " +
          "Build Natives workflow."
      )
    }
  }
}

tasks.withType<AbstractPublishToMaven>().configureEach {
  doFirst {
    if (!library.isFile) {
      throw GradleException(
        "Refusing to publish ${project.name}: ${library.relativeTo(rootProject.projectDir)} does not exist. " +
          "Build it with scripts/build_natives.sh $target first."
      )
    }
  }
}

// The jar also tells GraalVM native image about the library, so an application's native image embeds it and the loader
// finds it as a resource at run time. The file is written at build time (never committed) from the same target and
// library names the loader uses.
val metadataDir = layout.buildDirectory.dir("generated/native-image-metadata")
val metadataGroup = rootProject.property("groupId") as String
val generateNativeImageMetadata = tasks.register("generateNativeImageMetadata") {
  group = "build"
  description = "Writes the native image resource metadata of $target."
  val file = metadataDir.map { it.file("META-INF/native-image/$metadataGroup/${project.name}/reachability-metadata.json") }
  val resources = listOf("natives/$target/${JWebGpuNatives.libraryName(target)}", "natives/$target/licenses.txt")
  inputs.property("resources", resources)
  outputs.file(file)
  doLast {
    val out = file.get().asFile
    out.parentFile.mkdirs()
    out.writeText(
      resources.joinToString(
        separator = ",\n",
        prefix = "{\n  \"resources\": [\n",
        postfix = "\n  ]\n}\n"
      ) { "    { \"glob\": \"$it\" }" }
    )
  }
}

sourceSets.named("main") {
  resources.srcDir(generateNativeImageMetadata.map { metadataDir })
}

tasks.matching { it.name == "processResources" || it.name == "sourcesJar" }.configureEach {
  dependsOn(generateNativeImageMetadata)
}

// There is no Java code, so the Javadoc jar is empty. The sources jar must not carry the library, so it holds the
// license texts that belong to the binary instead.
mavenPublishing {
  configure(JavaLibrary(JavadocJar.Empty(), sourcesJar = true))

  pom {
    name = project.name
    description = "The Dawn native library for $target, used by jwebgpu-ffm on the desktop."
  }
}

tasks.withType<Jar>().matching { it.name == "sourcesJar" }.configureEach {
  exclude("natives/**", "META-INF/native-image/**")
  from(rootProject.layout.projectDirectory.file("LICENSE"))
  from(rootProject.layout.projectDirectory.file("THIRD_PARTY_NOTICES.md"))
}
