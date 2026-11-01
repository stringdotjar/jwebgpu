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
package io.github.stringdotjar.jwebgpu.samples.web;

import io.github.stringdotjar.jwebgpu.GpuAdapter;
import io.github.stringdotjar.jwebgpu.GpuAdapterInfo;
import io.github.stringdotjar.jwebgpu.GpuCallbackMode;
import io.github.stringdotjar.jwebgpu.GpuDevice;
import io.github.stringdotjar.jwebgpu.GpuDeviceDescriptor;
import io.github.stringdotjar.jwebgpu.GpuInstance;
import io.github.stringdotjar.jwebgpu.GpuQueue;
import io.github.stringdotjar.jwebgpu.JWebGpu;
import io.github.stringdotjar.jwebgpu.teavm.JWebGpuTeaVm;

import java.util.function.Consumer;

/**
 * The shared GPU objects of the sample pages: the instance, the adapter, the device and its queue.
 *
 * <p>The browser cannot block, so {@link #open(Report, Consumer)} asks for the adapter and the device with callbacks and
 * calls {@code ready} once everything is there.
 */
final class Context {

  final Report report;
  final GpuAdapterInfo info = new GpuAdapterInfo();

  GpuInstance instance;
  GpuAdapter adapter;
  GpuDevice device;
  GpuQueue queue;
  int expectedErrors;

  Context(Report report) {
    this.report = report;
  }

  static void open(Report report, Consumer<Context> ready) {
    JWebGpuTeaVm.install();
    Context ctx = new Context(report);
    ctx.instance = JWebGpu.createInstance();
    ctx.instance.requestAdapter(null, GpuCallbackMode.ALLOW_SPONTANEOUS, (status, result, message) -> {
      if (result == null) {
        report.fail("No adapter (" + status + "): " + message);
        report.finish();
        return;
      }
      ctx.adapter = result;
      ctx.adapter.getInfo(ctx.info);
      Report.log("Adapter: " + ctx.info.vendor + " / " + ctx.info.architecture + " (" + ctx.info.backendType + ")");
      ctx.requestDevice(ready);
    });
  }

  private void requestDevice(Consumer<Context> ready) {
    GpuDeviceDescriptor descriptor = new GpuDeviceDescriptor();
    descriptor.uncapturedErrorCallback = (d, type, message) -> {
      if (expectedErrors > 0) {
        expectedErrors--;
        Report.log("Expected uncaptured error (" + type + "): " + message);
      } else {
        report.fail("WebGPU error (" + type + "): " + message);
      }
    };
    descriptor.deviceLostCallback = (d, reason, message) -> Report.log("Device lost (" + reason + "): " + message);
    adapter.requestDevice(descriptor, GpuCallbackMode.ALLOW_SPONTANEOUS, (status, result, message) -> {
      if (result == null) {
        report.fail("No device (" + status + "): " + message);
        report.finish();
        return;
      }
      device = result;
      queue = device.getQueue();
      ready.accept(this);
    });
  }
}
