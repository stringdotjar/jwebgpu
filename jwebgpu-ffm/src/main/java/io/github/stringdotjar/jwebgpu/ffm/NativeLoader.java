/*
 * MIT License
 *
 * Copyright (c) 2026 stringdotjar
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.stringdotjar.jwebgpu.ffm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Finds, extracts and loads the native WebGPU library (Dawn).
 *
 * <p>The library for each platform is packed into the jar as {@code natives/<os>-<arch>/<library name>}, for example
 * {@code natives/linux-x86_64/libwebgpu_dawn.so}. A shared library cannot be loaded from inside a jar, so the loader
 * copies it once into a cache directory and loads that copy. The directory name contains a hash of the file, so
 * different versions never clash, and later runs reuse the copy without writing anything.
 *
 * <p>The library is picked in this order:
 *
 * <ol>
 *   <li>the path given to {@link #load(Path)},
 *   <li>the file named by the system property {@value #PROPERTY},
 *   <li>the copy packed into the jar for the current operating system and CPU.
 * </ol>
 *
 * <p>Loading the same library twice does nothing. Loading a different file after one is loaded fails.
 */
final class NativeLoader {

  /** The system property that names a library file to use instead of the packed one. */
  static final String PROPERTY = "jwebgpu.library.path";

  private static final Object LOCK = new Object();
  private static Path loaded;

  private NativeLoader() {}

  /**
   * Loads the native library.
   *
   * @param library The library file to use, or {@code null} to use the system property or the packed copy.
   * @throws IllegalStateException If the platform is not supported, the file cannot be found, or a different library
   *     was loaded before.
   */
  static void load(Path library) {
    synchronized (LOCK) {
      Path path = library;
      if (path == null) {
        String property = System.getProperty(PROPERTY);
        path = property != null && !property.isEmpty() ? Path.of(property) : extract();
      }
      path = path.toAbsolutePath().normalize();
      if (loaded != null) {
        if (!loaded.equals(path)) {
          throw new IllegalStateException("The native library " + loaded + " is already loaded, so " + path
              + " cannot be loaded as well.");
        }
        return;
      }
      if (!Files.isRegularFile(path)) {
        throw new IllegalStateException("The native library file does not exist: " + path);
      }
      Native.setLibrary(SymbolLookup.libraryLookup(path, Arena.global()));
      loaded = path;
    }
  }

  /**
   * Returns the library file name for an operating system.
   *
   * @param os The operating system, as returned by {@link #os()}.
   * @return The file name, such as {@code libwebgpu_dawn.so}.
   */
  static String libraryName(String os) {
    return switch (os) {
      case "windows" -> "webgpu_dawn.dll";
      case "macos" -> "libwebgpu_dawn.dylib";
      default -> "libwebgpu_dawn.so";
    };
  }

  /**
   * Detects the operating system.
   *
   * @return {@code linux}, {@code windows} or {@code macos}.
   * @throws IllegalStateException If the system is none of those.
   */
  static String os() {
    String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (name.contains("win")) {
      return "windows";
    }
    if (name.contains("mac") || name.contains("darwin")) {
      return "macos";
    }
    if (name.contains("linux")) {
      return "linux";
    }
    throw new IllegalStateException("jwebgpu has no native library for the operating system '" + name + "'.");
  }

  /**
   * Detects the CPU architecture.
   *
   * @return {@code x86_64} or {@code aarch64}.
   * @throws IllegalStateException If the CPU is none of those.
   */
  static String arch() {
    String name = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    return switch (name) {
      case "amd64", "x86_64", "x64" -> "x86_64";
      case "aarch64", "arm64" -> "aarch64";
      default -> throw new IllegalStateException("jwebgpu has no native library for the CPU '" + name + "'.");
    };
  }

  /**
   * Returns the path of the packed library inside the jar.
   *
   * @return A resource path such as {@code natives/linux-x86_64/libwebgpu_dawn.so}.
   */
  static String resource() {
    String os = os();
    return "natives/" + os + "-" + arch() + "/" + libraryName(os);
  }

  private static Path extract() {
    String resource = resource();
    byte[] bytes;
    try (InputStream in = NativeLoader.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("The jar has no native library at " + resource + ". Build it with "
            + "scripts/build_natives.sh, or set -D" + PROPERTY + "=/path/to/library.");
      }
      bytes = in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + resource, e);
    }
    Path dir = cacheDir().resolve(hash(bytes));
    Path target = dir.resolve(libraryName(os()));
    try {
      if (Files.isRegularFile(target) && Files.size(target) == bytes.length) {
        return target;
      }
      Files.createDirectories(dir);
      Path temp = Files.createTempFile(dir, "extract", ".tmp");
      Files.write(temp, bytes);
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      return target;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot extract the native library to " + dir, e);
    }
  }

  private static Path cacheDir() {
    String home = System.getProperty("user.home");
    Path root;
    if (os().equals("windows") && System.getenv("LOCALAPPDATA") != null) {
      root = Path.of(System.getenv("LOCALAPPDATA"), "jwebgpu", "natives");
    } else if (os().equals("macos") && home != null) {
      root = Path.of(home, "Library", "Caches", "jwebgpu", "natives");
    } else if (System.getenv("XDG_CACHE_HOME") != null) {
      root = Path.of(System.getenv("XDG_CACHE_HOME"), "jwebgpu", "natives");
    } else if (home != null) {
      root = Path.of(home, ".cache", "jwebgpu", "natives");
    } else {
      root = Path.of(System.getProperty("java.io.tmpdir"), "jwebgpu-natives");
    }
    try {
      Files.createDirectories(root);
      if (Files.isWritable(root)) {
        return root;
      }
    } catch (IOException e) {
      // Fall through to the temporary directory.
    }
    return Path.of(System.getProperty("java.io.tmpdir"), "jwebgpu-natives");
  }

  private static String hash(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      StringBuilder sb = new StringBuilder(16);
      for (int i = 0; i < 8; i++) {
        sb.append(Character.forDigit((digest[i] >> 4) & 0xF, 16)).append(Character.forDigit(digest[i] & 0xF, 16));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
