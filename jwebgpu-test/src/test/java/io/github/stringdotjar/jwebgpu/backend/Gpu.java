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
package io.github.stringdotjar.jwebgpu.backend;

import io.github.stringdotjar.jwebgpu.GpuAdapter;
import io.github.stringdotjar.jwebgpu.GpuBackendType;
import io.github.stringdotjar.jwebgpu.GpuCallbackMode;
import io.github.stringdotjar.jwebgpu.GpuDevice;
import io.github.stringdotjar.jwebgpu.GpuDeviceDescriptor;
import io.github.stringdotjar.jwebgpu.GpuInstance;
import io.github.stringdotjar.jwebgpu.GpuInstanceDescriptor;
import io.github.stringdotjar.jwebgpu.GpuInstanceFeatureName;
import io.github.stringdotjar.jwebgpu.GpuRequestAdapterOptions;
import io.github.stringdotjar.jwebgpu.JWebGpu;
import io.github.stringdotjar.jwebgpu.ffm.JWebGpuFfm;
import io.github.stringdotjar.jwebgpu.jni.JWebGpuJni;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test helpers that start a backend and bring up an instance, an adapter and a device.
 *
 * <p>The same tests run against two backends. The system property {@code jwebgpu.backend} picks the one that is
 * installed: {@code ffm} (the default) or {@code jni}, which runs the Android backend on the desktop JVM through the
 * {@code testJni} Gradle task.
 *
 * <p>Tests that need the native library call {@link #require()}, which skips the test (a JUnit assumption) when the
 * library is not available on this machine.
 */
final class Gpu {

  /** Messages of the uncaptured error callback, appended by the device that {@link #device} creates. */
  static final StringBuilder ERRORS = new StringBuilder();

  private Gpu() {}

  /** Installs the backend, or skips the calling test if the native library is missing. */
  static void require() {
    String property = System.getProperty("jwebgpu.library.path");
    boolean jni = "jni".equals(System.getProperty("jwebgpu.backend"));
    if (jni) {
      String glue = System.getProperty("jwebgpu.jni.library.path");
      assumeTrue(property != null && Files.isRegularFile(Path.of(property)), "no Dawn library for the JNI run");
      assumeTrue(glue != null && Files.isRegularFile(Path.of(glue)), "the JNI glue library was not built");
      JWebGpuJni.install();
      return;
    }
    if (property != null && !property.isEmpty()) {
      assumeTrue(Files.isRegularFile(Path.of(property)), "the native library " + property + " does not exist");
    }
    try {
      JWebGpuFfm.install();
    } catch (IllegalStateException e) {
      assumeTrue(false,
          "no jwebgpu-natives jar holds a library for this machine; build it with scripts/build_natives.sh");
    }
  }

  static GpuInstance instance() {
    GpuInstanceDescriptor descriptor = new GpuInstanceDescriptor(1);
    descriptor.requiredFeatureCount = 1;
    descriptor.requiredFeatures[0] = GpuInstanceFeatureName.TIMED_WAIT_ANY;
    return JWebGpu.createInstance(descriptor);
  }

  static GpuAdapter adapter(GpuInstance instance, GpuBackendType backend) {
    GpuRequestAdapterOptions options = new GpuRequestAdapterOptions();
    options.backendType = backend;
    GpuAdapter[] result = new GpuAdapter[1];
    boolean[] done = new boolean[1];
    String[] message = new String[1];
    instance.requestAdapter(options, GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, adapter, msg) -> {
      result[0] = adapter;
      message[0] = status + " " + msg;
      done[0] = true;
    });
    await(instance, done);
    if (result[0] == null) {
      fail("no adapter: " + message[0]);
    }
    return result[0];
  }

  static GpuDevice device(GpuInstance instance, GpuAdapter adapter) {
    GpuDeviceDescriptor descriptor = new GpuDeviceDescriptor();
    descriptor.label = "test device";
    descriptor.deviceLostMode = GpuCallbackMode.ALLOW_SPONTANEOUS;
    descriptor.deviceLostCallback = (device, reason, msg) -> {
    };
    descriptor.uncapturedErrorCallback = (device, type, msg) -> {
      synchronized (ERRORS) {
        ERRORS.append(type).append(": ").append(msg).append('\n');
      }
    };
    GpuDevice[] result = new GpuDevice[1];
    boolean[] done = new boolean[1];
    String[] message = new String[1];
    adapter.requestDevice(descriptor, GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, device, msg) -> {
      result[0] = device;
      message[0] = status + " " + msg;
      done[0] = true;
    });
    await(instance, done);
    if (result[0] == null) {
      fail("no device: " + message[0]);
    }
    return result[0];
  }

  static void await(GpuInstance instance, boolean[] done) {
    long deadline = System.nanoTime() + 20_000_000_000L;
    while (!done[0]) {
      instance.processEvents();
      if (System.nanoTime() > deadline) {
        fail("timed out waiting for a callback");
      }
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        fail("interrupted");
      }
    }
  }

  static String errors() {
    synchronized (ERRORS) {
      return ERRORS.toString();
    }
  }
}
