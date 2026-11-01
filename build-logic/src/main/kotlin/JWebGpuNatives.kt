import java.io.File
import java.util.Locale

/**
 * Describes where the desktop Dawn libraries live and which one belongs to the machine running the build.
 *
 * Every desktop target has its own module, `jwebgpu-natives-<target>`, whose single resource is the library at
 * `natives/<target>/<library name>`. The target and library names follow the same rules as the desktop backend's
 * loader, so a build script can pick the module the loader would look for.
 */
object JWebGpuNatives {

  /** The desktop targets, one published module each. */
  val TARGETS = listOf(
    "linux-x86_64",
    "linux-aarch64",
    "windows-x86_64",
    "windows-aarch64",
    "macos-x86_64",
    "macos-aarch64"
  )

  /** Returns the name of the module that holds the library of a target. */
  fun moduleName(target: String): String = "jwebgpu-natives-$target"

  /** Returns the file name of the Dawn library for a target. */
  fun libraryName(target: String): String = when {
    target.startsWith("windows") -> "webgpu_dawn.dll"
    target.startsWith("macos") -> "libwebgpu_dawn.dylib"
    else -> "libwebgpu_dawn.so"
  }

  /**
   * Returns where the library of a target is stored in the repository.
   *
   * @param rootDir The root project directory.
   * @param target One of [TARGETS].
   */
  fun libraryFile(rootDir: File, target: String): File =
    File(rootDir, "jwebgpu-natives/$target/src/main/resources/natives/$target/${libraryName(target)}")

  /** Returns the target of the machine running the build, or null if jwebgpu has no library for it. */
  fun hostTarget(): String? {
    val name = System.getProperty("os.name", "").lowercase(Locale.ROOT)
    val os = when {
      name.contains("win") -> "windows"
      name.contains("mac") || name.contains("darwin") -> "macos"
      name.contains("linux") -> "linux"
      else -> return null
    }
    val arch = when (System.getProperty("os.arch", "").lowercase(Locale.ROOT)) {
      "amd64", "x86_64", "x64" -> "x86_64"
      "aarch64", "arm64" -> "aarch64"
      else -> return null
    }
    return "$os-$arch"
  }
}
