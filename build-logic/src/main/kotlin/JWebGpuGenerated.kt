import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.jvm.toolchain.JavaToolchainService

/**
 * The `jwebgpuGenerated` extension of the `jwebgpu.generated-sources` plugin.
 *
 * A module calls [emit] once for every kind of source the generator should write into one of its source sets, for
 * example `emit("api")` in the API module and `emit("ffm")` in the desktop backend. The generator writes the files
 * into `build/generated/sources/jwebgpu/<source set>/java` before the module compiles, and the directory is added
 * to the source set.
 */
open class JWebGpuGenerated(private val project: Project, private val generator: Any) {

  /**
   * Generates sources with the generator and adds them to a source set.
   *
   * @param kind The value of the generator's `--emit` option (`api`, `ffm` or `api-checks`).
   * @param sourceSet The name of the source set that receives the sources.
   */
  fun emit(kind: String, sourceSet: String = "main") {
    val specDir = project.rootProject.layout.projectDirectory.dir("spec")
    val outDir = project.layout.buildDirectory.dir("generated/sources/jwebgpu/$sourceSet/java")
    val java = project.extensions.getByType(JavaPluginExtension::class.java)
    val toolchains = project.extensions.getByType(JavaToolchainService::class.java)
    val taskName = "generateJWebGpu${kind.split('-').joinToString("") { it.replaceFirstChar(Char::uppercase) }}Sources"

    val task = project.tasks.register(taskName, JavaExec::class.java) {
      group = "build"
      description = "Generates the '$kind' Java sources from the specification."
      classpath = project.configurations.getByName("jwebgpuGenerator")
      mainClass.set("io.github.stringdotjar.jwebgpu.generator.Generator")
      javaLauncher.set(toolchains.launcherFor(java.toolchain))
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
        project.delete(outDir)
      }
    }

    project.extensions.getByType(SourceSetContainer::class.java).named(sourceSet) {
      this.java.srcDir(task)
    }

    val suffix = sourceSet.replaceFirstChar(Char::uppercase)
    val names = if (sourceSet == "main") {
      listOf("compileJava", "javadoc", "sourcesJar", "checkstyleMain")
    } else {
      listOf("compile${suffix}Java", "checkstyle$suffix")
    }
    project.tasks.matching { it.name in names }.configureEach { dependsOn(task) }
  }
}
