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
package io.github.stringdotjar.jwebgpu.samples.android;

import android.os.Debug;
import android.util.Log;
import android.view.Surface;

import io.github.stringdotjar.jwebgpu.GpuAdapter;
import io.github.stringdotjar.jwebgpu.GpuAdapterInfo;
import io.github.stringdotjar.jwebgpu.GpuBackendType;
import io.github.stringdotjar.jwebgpu.GpuBuffer;
import io.github.stringdotjar.jwebgpu.GpuBufferDescriptor;
import io.github.stringdotjar.jwebgpu.GpuBufferUsage;
import io.github.stringdotjar.jwebgpu.GpuCallbackMode;
import io.github.stringdotjar.jwebgpu.GpuCommandBuffer;
import io.github.stringdotjar.jwebgpu.GpuCommandEncoder;
import io.github.stringdotjar.jwebgpu.GpuCompositeAlphaMode;
import io.github.stringdotjar.jwebgpu.GpuDevice;
import io.github.stringdotjar.jwebgpu.GpuDeviceDescriptor;
import io.github.stringdotjar.jwebgpu.GpuExtent3D;
import io.github.stringdotjar.jwebgpu.GpuInstance;
import io.github.stringdotjar.jwebgpu.GpuMapAsyncStatus;
import io.github.stringdotjar.jwebgpu.GpuMapMode;
import io.github.stringdotjar.jwebgpu.GpuPresentMode;
import io.github.stringdotjar.jwebgpu.GpuQueue;
import io.github.stringdotjar.jwebgpu.GpuRequestAdapterOptions;
import io.github.stringdotjar.jwebgpu.GpuSurface;
import io.github.stringdotjar.jwebgpu.GpuSurfaceCapabilities;
import io.github.stringdotjar.jwebgpu.GpuSurfaceConfiguration;
import io.github.stringdotjar.jwebgpu.GpuSurfaceGetCurrentTextureStatus;
import io.github.stringdotjar.jwebgpu.GpuSurfaceTexture;
import io.github.stringdotjar.jwebgpu.GpuTexelCopyBufferInfo;
import io.github.stringdotjar.jwebgpu.GpuTexelCopyTextureInfo;
import io.github.stringdotjar.jwebgpu.GpuTexture;
import io.github.stringdotjar.jwebgpu.GpuTextureAspect;
import io.github.stringdotjar.jwebgpu.GpuTextureDescriptor;
import io.github.stringdotjar.jwebgpu.GpuTextureDimension;
import io.github.stringdotjar.jwebgpu.GpuTextureFormat;
import io.github.stringdotjar.jwebgpu.GpuTextureUsage;
import io.github.stringdotjar.jwebgpu.GpuTextureView;
import io.github.stringdotjar.jwebgpu.JWebGpu;
import io.github.stringdotjar.jwebgpu.jni.JWebGpuJni;
import io.github.stringdotjar.jwebgpu.jni.android.AndroidSurface;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Runs the on-device checks of the Android backend and reports them to logcat (tag {@code jwebgpu}).
 *
 * <p>The checks, in order:
 *
 * <ol>
 *   <li>install the backend, create an instance, ask for a Vulkan adapter and a device,
 *   <li>draw a triangle off screen and read the pixels back,
 *   <li>render 10,000 frames and count how many objects the thread allocated (it must be none),
 *   <li>time the cost of single calls,
 *   <li>draw the triangle on the screen for about five seconds and count allocations of that path too.
 * </ol>
 *
 * <p>The last line, {@code ALL DONE}, tells a script that the run has ended.
 */
final class DeviceRun extends Thread {

  private static final String TAG = "jwebgpu";
  private static final int SIZE = 64;
  private static final int ROW_BYTES = 256;
  private static final int FRAMES = 10_000;
  private static final int WARMUP = 20_000;
  private static final int CALLS = 200_000;

  private final Surface surface;
  private final int width;
  private final int height;
  private volatile boolean stop;
  private GpuInstance instance;
  private GpuDevice device;
  private GpuAdapter adapter;
  private boolean failed;

  DeviceRun(Surface surface, int width, int height) {
    super("jwebgpu-run");
    this.surface = surface;
    this.width = width;
    this.height = height;
  }

  void stopAndWait() {
    stop = true;
    try {
      join(15_000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void run() {
    try {
      log("start: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + ", Android "
          + android.os.Build.VERSION.RELEASE + ", " + android.os.Build.SUPPORTED_ABIS[0]);
      JWebGpuJni.install();
      instance = JWebGpu.createInstance();
      adapter = requestAdapter();
      device = requestDevice();
      Debug.startAllocCounting();
      offscreen();
      allocationsAndTimings();
      onscreen();
      device.release();
      adapter.release();
      instance.release();
      check(true, "run completed");
    } catch (Throwable t) {
      failed = true;
      Log.e(TAG, "RUN FAILED", t);
    } finally {
      log(failed ? "RESULT FAILED" : "RESULT OK");
      log("ALL DONE");
    }
  }

  private GpuAdapter requestAdapter() {
    GpuRequestAdapterOptions options = new GpuRequestAdapterOptions();
    options.backendType = GpuBackendType.VULKAN;
    GpuAdapter[] result = new GpuAdapter[1];
    String[] message = new String[1];
    instance.requestAdapter(options, GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, found, text) -> {
      result[0] = found;
      message[0] = status + " " + text;
    });
    waitFor(() -> result[0] != null || message[0] != null);
    if (result[0] == null) {
      throw new IllegalStateException("no adapter: " + message[0]);
    }
    GpuAdapterInfo info = new GpuAdapterInfo();
    result[0].getInfo(info);
    log("adapter: " + info.backendType + " / " + info.adapterType + " / " + info.vendor + " / " + info.device
        + " / " + info.description);
    return result[0];
  }

  private GpuDevice requestDevice() {
    GpuDeviceDescriptor descriptor = new GpuDeviceDescriptor();
    descriptor.label = "sample device";
    descriptor.deviceLostMode = GpuCallbackMode.ALLOW_SPONTANEOUS;
    descriptor.deviceLostCallback = (lost, reason, text) -> log("device lost: " + reason + " " + text);
    descriptor.uncapturedErrorCallback = (source, type, text) -> {
      failed = true;
      Log.e(TAG, "WebGPU error " + type + ": " + text);
    };
    GpuDevice[] result = new GpuDevice[1];
    String[] message = new String[1];
    adapter.requestDevice(descriptor, GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, found, text) -> {
      result[0] = found;
      message[0] = status + " " + text;
    });
    waitFor(() -> result[0] != null || message[0] != null);
    if (result[0] == null) {
      throw new IllegalStateException("no device: " + message[0]);
    }
    return result[0];
  }

  private GpuTexture offscreenTexture() {
    GpuTextureDescriptor td = new GpuTextureDescriptor();
    td.usage = GpuTextureUsage.RENDER_ATTACHMENT | GpuTextureUsage.COPY_SRC;
    td.dimension = GpuTextureDimension.D2;
    td.size.width = SIZE;
    td.size.height = SIZE;
    td.size.depthOrArrayLayers = 1;
    td.format = GpuTextureFormat.RGBA8_UNORM;
    return device.createTexture(td);
  }

  private void offscreen() {
    GpuTexture texture = offscreenTexture();
    GpuTextureView view = texture.createView(null);
    Renderer renderer = new Renderer(device, GpuTextureFormat.RGBA8_UNORM, view);
    renderer.frame(view, 0f);

    GpuBufferDescriptor bd = new GpuBufferDescriptor();
    bd.usage = GpuBufferUsage.MAP_READ | GpuBufferUsage.COPY_DST;
    bd.size = (long) ROW_BYTES * SIZE;
    GpuBuffer readback = device.createBuffer(bd);
    GpuQueue queue = device.getQueue();

    GpuCommandEncoder encoder = device.createCommandEncoder(null);
    GpuTexelCopyTextureInfo source = new GpuTexelCopyTextureInfo();
    source.texture = texture;
    source.aspect = GpuTextureAspect.ALL;
    GpuTexelCopyBufferInfo destination = new GpuTexelCopyBufferInfo();
    destination.buffer = readback;
    destination.layout.bytesPerRow = ROW_BYTES;
    destination.layout.rowsPerImage = SIZE;
    GpuExtent3D extent = new GpuExtent3D();
    extent.width = SIZE;
    extent.height = SIZE;
    encoder.copyTextureToBuffer(source, destination, extent);
    GpuCommandBuffer copy = encoder.finish(null);
    queue.submit(new GpuCommandBuffer[] { copy }, 1);

    GpuMapAsyncStatus[] mapped = new GpuMapAsyncStatus[1];
    readback.mapAsync(GpuMapMode.READ, 0, bd.size, GpuCallbackMode.ALLOW_PROCESS_EVENTS,
        (status, message) -> mapped[0] = status);
    waitFor(() -> mapped[0] != null);
    ByteBuffer pixels = readback.getConstMappedRange(0, bd.size);
    int center = (SIZE / 2) * ROW_BYTES + (SIZE / 2) * 4;
    int centerRed = pixels.get(center) & 0xFF;
    int cornerBlue = pixels.get(2) & 0xFF;
    log("offscreen center pixel r,g,b,a = " + (pixels.get(center) & 0xFF) + "," + (pixels.get(center + 1) & 0xFF)
        + "," + (pixels.get(center + 2) & 0xFF) + "," + (pixels.get(center + 3) & 0xFF) + ", corner blue = "
        + cornerBlue);
    check(mapped[0] == GpuMapAsyncStatus.SUCCESS, "the readback buffer mapped");
    check(centerRed > 240, "the triangle is red in the middle");
    check(cornerBlue > 90 && cornerBlue < 115, "the clear color is blue in the corner");
    readback.unmap();

    copy.release();
    encoder.release();
    readback.release();
    renderer.release();
    view.release();
    texture.release();
    queue.release();
    log("OFFSCREEN OK");
  }

  private void allocationsAndTimings() {
    GpuTexture texture = offscreenTexture();
    GpuTextureView view = texture.createView(null);
    Renderer renderer = new Renderer(device, GpuTextureFormat.RGBA8_UNORM, view);

    for (int i = 0; i < WARMUP; i++) {
      renderer.frame(view, (i & 255) / 255f);
      if ((i & 255) == 0) {
        instance.processEvents();
      }
    }
    instance.processEvents();

    long baseObjects = Debug.getThreadAllocCount();
    long baseBytes = Debug.getThreadAllocSize();
    long emptyObjects = Debug.getThreadAllocCount() - baseObjects;
    long emptyBytes = Debug.getThreadAllocSize() - baseBytes;

    Object[] keep = new Object[100];
    long controlStart = Debug.getThreadAllocCount();
    for (int i = 0; i < keep.length; i++) {
      keep[i] = new long[8];
    }
    log("allocation meter control: saw " + (Debug.getThreadAllocCount() - controlStart - emptyObjects)
        + " objects for " + keep.length + " arrays");

    long objects = Debug.getThreadAllocCount();
    long bytes = Debug.getThreadAllocSize();
    long start = System.nanoTime();
    for (int i = 0; i < FRAMES; i++) {
      renderer.frame(view, (i & 255) / 255f);
    }
    long elapsed = System.nanoTime() - start;
    long usedObjects = Debug.getThreadAllocCount() - objects - emptyObjects;
    long usedBytes = Debug.getThreadAllocSize() - bytes - emptyBytes;
    log(String.format(Locale.ROOT, "ALLOCATIONS over %d offscreen frames: %d objects, %d bytes (%.1f us per frame)",
        FRAMES, usedObjects, usedBytes, elapsed / 1000.0 / FRAMES));
    check(usedObjects == 0 && usedBytes == 0, "rendering calls allocate nothing");
    instance.processEvents();

    for (int round = 0; round < 3; round++) {
      report("setViewport (float arguments)", renderer.timeViewportCalls(view, CALLS), CALLS);
      report("setPipeline (object argument)", renderer.timePipelineCalls(view, CALLS), CALLS);
      report("getSize (getter)", renderer.timeGetterCalls(CALLS), CALLS);
      report("writeBuffer (16 bytes from a float array)", renderer.timeUploads(CALLS), CALLS);
    }
    instance.processEvents();
    renderer.release();
    view.release();
    texture.release();
  }

  private void onscreen() {
    if (!surface.isValid()) {
      log("ONSCREEN SKIPPED: the surface is not valid (is the screen on?)");
      return;
    }
    AndroidSurface target = AndroidSurface.create(instance, surface);
    GpuSurface gpuSurface = target.getSurface();
    GpuSurfaceCapabilities caps = new GpuSurfaceCapabilities();
    gpuSurface.getCapabilities(adapter, caps);
    GpuTextureFormat format = caps.formats[0];
    for (int i = 0; i < caps.formatCount; i++) {
      if (caps.formats[i] == GpuTextureFormat.RGBA8_UNORM || caps.formats[i] == GpuTextureFormat.BGRA8_UNORM) {
        format = caps.formats[i];
        break;
      }
    }
    log("surface " + width + "x" + height + ", format " + format + ", " + caps.formatCount + " formats, "
        + caps.presentModeCount + " present modes");
    GpuSurfaceConfiguration config = new GpuSurfaceConfiguration();
    config.device = device;
    config.format = format;
    config.usage = GpuTextureUsage.RENDER_ATTACHMENT;
    config.width = width;
    config.height = height;
    config.alphaMode = GpuCompositeAlphaMode.AUTO;
    config.presentMode = GpuPresentMode.FIFO;
    gpuSurface.configure(config);

    GpuSurfaceTexture current = new GpuSurfaceTexture();
    GpuTextureView view = null;
    Renderer renderer = null;
    int shown = 0;
    long objects = 0;
    long bytes = 0;
    long start = System.nanoTime();
    for (int i = 0; i < 360 && !stop; i++) {
      if (i == 60) {
        objects = Debug.getThreadAllocCount();
        bytes = Debug.getThreadAllocSize();
      }
      gpuSurface.getCurrentTexture(current);
      if (current.status != GpuSurfaceGetCurrentTextureStatus.SUCCESS_OPTIMAL
          && current.status != GpuSurfaceGetCurrentTextureStatus.SUCCESS_SUBOPTIMAL) {
        continue;
      }
      if (view == null) {
        view = current.texture.createView(null);
        renderer = new Renderer(device, format, view);
      } else {
        current.texture.createView(null, view);
      }
      renderer.frame(view, (i % 120) / 120f);
      gpuSurface.present();
      shown++;
    }
    long elapsed = System.nanoTime() - start;
    long usedObjects = Debug.getThreadAllocCount() - objects;
    long usedBytes = Debug.getThreadAllocSize() - bytes;
    log(String.format(Locale.ROOT, "ONSCREEN frames presented: %d in %.2f s (%.1f fps); allocations after warm-up: "
        + "%d objects, %d bytes", shown, elapsed / 1e9, shown * 1e9 / elapsed, usedObjects, usedBytes));
    check(shown > 100 || stop, "frames were presented");
    check(usedObjects <= 4, "the on-screen frame path allocates (almost) nothing");
    if (view != null) {
      view.release();
    }
    if (current.texture != null) {
      current.texture.release();
    }
    if (renderer != null) {
      renderer.release();
    }
    gpuSurface.unconfigure();
    target.release();
    log("ONSCREEN OK");
  }

  private void report(String name, long nanos, int calls) {
    log(String.format(Locale.ROOT, "TIMING %-44s %7.1f ns per call", name, nanos / (double) calls));
  }

  private void waitFor(BooleanSupplier done) {
    long deadline = System.nanoTime() + 20_000_000_000L;
    while (!done.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new IllegalStateException("timed out waiting for a callback");
      }
      instance.processEvents();
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("interrupted", e);
      }
    }
  }

  private void check(boolean condition, String what) {
    if (!condition) {
      failed = true;
      Log.e(TAG, "CHECK FAILED: " + what);
    } else {
      log("check ok: " + what);
    }
  }

  private static void log(String message) {
    Log.i(TAG, message);
  }
}
