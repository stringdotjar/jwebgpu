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
package io.github.stringdotjar.jwebgpu;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.stringdotjar.jwebgpu.Fakes.RecordingBackend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the backend registration of {@link JWebGpu} with a fake backend. */
class JWebGpuTest {

  private static final String NOT_INSTALLED = "No jwebgpu backend is installed. Call JWebGpuFfm.install() (desktop), "
      + "JWebGpuJni.install() (Android), or JWebGpuTeaVm.install() (web) first.";

  @BeforeEach
  @AfterEach
  void removeBackend() {
    JWebGpu.uninstall();
  }

  @Test
  void withoutABackendEveryCallExplainsWhatToDo() {
    assertFalse(JWebGpu.isInstalled());
    assertEquals(NOT_INSTALLED, assertThrows(IllegalStateException.class, JWebGpu::backend).getMessage());
    assertEquals(NOT_INSTALLED, assertThrows(IllegalStateException.class, JWebGpu::createInstance).getMessage());
    assertEquals(NOT_INSTALLED,
        assertThrows(IllegalStateException.class, () -> JWebGpu.createInstance(null)).getMessage());
    assertEquals(NOT_INSTALLED,
        assertThrows(IllegalStateException.class, () -> JWebGpu.getProcAddress("wgpuCreateInstance")).getMessage());
    assertEquals(NOT_INSTALLED, assertThrows(IllegalStateException.class,
        () -> JWebGpu.hasInstanceFeature(GpuInstanceFeatureName.TIMED_WAIT_ANY)).getMessage());
  }

  @Test
  void installedBackendReceivesTheGlobalFunctions() {
    RecordingBackend backend = new RecordingBackend("fake");
    JWebGpu.install(backend);
    assertTrue(JWebGpu.isInstalled());
    assertSame(backend, JWebGpu.backend());

    GpuInstanceDescriptor descriptor = new GpuInstanceDescriptor();
    assertSame(backend.instance, JWebGpu.createInstance(descriptor));
    assertSame(descriptor, backend.lastDescriptor);
    assertSame(backend.instance, JWebGpu.createInstance());
    assertNull(backend.lastDescriptor);
    assertEquals(2, backend.createInstanceCalls);

    GpuInstanceLimits limits = new GpuInstanceLimits();
    assertSame(GpuStatus.SUCCESS, JWebGpu.getInstanceLimits(limits));
    assertEquals(64L, limits.timedWaitAnyMaxCount);
    assertTrue(JWebGpu.hasInstanceFeature(GpuInstanceFeatureName.TIMED_WAIT_ANY));
    assertFalse(JWebGpu.hasInstanceFeature(GpuInstanceFeatureName.SHADER_SOURCE_SPIRV));
  }

  @Test
  void objectsDispatchThroughTheirOwnClass() {
    RecordingBackend backend = new RecordingBackend("fake");
    JWebGpu.install(backend);
    GpuInstance instance = JWebGpu.createInstance();
    GpuRequestAdapterCallback callback = (status, adapter, message) -> {
    };
    assertEquals(42L, instance.requestAdapter(null, GpuCallbackMode.ALLOW_PROCESS_EVENTS, callback));
    assertSame(callback, backend.instance.lastCallback);
    assertSame(GpuCallbackMode.ALLOW_PROCESS_EVENTS, backend.instance.lastMode);

    // The global is not consulted again: the instance keeps working after the backend is removed.
    JWebGpu.uninstall();
    instance.processEvents();
    assertEquals(1, backend.instance.processEventsCalls);
  }

  @Test
  void installingTheSameBackendTwiceIsANoOp() {
    RecordingBackend backend = new RecordingBackend("fake");
    JWebGpu.install(backend);
    JWebGpu.install(backend);
    assertSame(backend, JWebGpu.backend());
  }

  @Test
  void installingADifferentBackendFails() {
    RecordingBackend first = new RecordingBackend("first");
    JWebGpu.install(first);
    IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> JWebGpu.install(new RecordingBackend("second")));
    assertTrue(e.getMessage().contains("first"), e.getMessage());
    assertTrue(e.getMessage().contains("second"), e.getMessage());
    assertSame(first, JWebGpu.backend());
  }

  @Test
  void aBackendCanBeInstalledAgainAfterUninstall() {
    JWebGpu.install(new RecordingBackend("first"));
    JWebGpu.uninstall();
    RecordingBackend second = new RecordingBackend("second");
    JWebGpu.install(second);
    assertSame(second, JWebGpu.backend());
  }

  @Test
  void installingNullFails() {
    assertThrows(NullPointerException.class, () -> JWebGpu.install(null));
  }
}
