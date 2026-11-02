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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks that loading Dawn from its natives jar works in a small heap.
 *
 * <p>The library is about 10 MB. It used to be read into one byte array, which crashed applications that run with a
 * heap of a few megabytes. A child JVM with a 16 MB heap now installs the backend twice against a fresh cache folder:
 * the first run extracts the library and the second finds it in the cache.
 */
class NativeLoaderTest {

  @TempDir
  Path cache;

  @Test
  void theLibraryLoadsInASixteenMegabyteHeap() throws IOException, InterruptedException {
    assumeTrue(System.getProperty(NativeLoader.PROPERTY) == null, "a library path is set, so nothing is extracted");
    assumeTrue(NativeLoader.class.getClassLoader().getResource(NativeLoader.resource()) != null,
        "no natives jar for this machine on the class path");
    for (int run = 1; run <= 2; run++) {
      String output = runSmallHeap();
      assertTrue(output.contains("INSTALLED"), "run " + run + " failed:\n" + output);
    }
  }

  private String runSmallHeap() throws IOException, InterruptedException {
    String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    ProcessBuilder builder = new ProcessBuilder(List.of(java, "-Xmx16m", "--enable-native-access=ALL-UNNAMED",
        "-Duser.home=" + cache, "-cp", System.getProperty("java.class.path"), SmallHeapInstall.class.getName()));
    builder.environment().put("XDG_CACHE_HOME", cache.toString());
    builder.environment().put("LOCALAPPDATA", cache.toString());
    builder.redirectErrorStream(true);
    Process process = builder.start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(process.waitFor(60, TimeUnit.SECONDS), "the child JVM did not finish");
    assertEquals(0, process.exitValue(), output);
    return output;
  }

  /** The child JVM's program: installs the FFM backend and reports it. */
  static final class SmallHeapInstall {

    private SmallHeapInstall() {}

    public static void main(String[] args) {
      JWebGpuFfm.install();
      System.out.println("INSTALLED");
    }
  }
}
