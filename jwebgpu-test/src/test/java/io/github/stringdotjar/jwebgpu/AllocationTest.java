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

import org.junit.jupiter.api.Test;

import io.github.stringdotjar.jwebgpu.Fakes.FakeTextureView;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Proves that refilling descriptors allocates nothing.
 *
 * <p>The whole point of reusable descriptors is that a game can fill them every frame without creating garbage. The
 * test refills a render pass descriptor 100,000 times and asks the JVM how many bytes this thread allocated.
 */
class AllocationTest {

  private static final int ITERATIONS = 100_000;

  private static com.sun.management.ThreadMXBean bean() {
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    assumeTrue(bean instanceof com.sun.management.ThreadMXBean, "needs the HotSpot thread bean");
    com.sun.management.ThreadMXBean sun = (com.sun.management.ThreadMXBean) bean;
    assumeTrue(sun.isThreadAllocatedMemorySupported(), "thread allocation counters are not supported");
    sun.setThreadAllocatedMemoryEnabled(true);
    return sun;
  }

  private static void fill(GpuRenderPassDescriptor pass, GpuTextureView view, GpuTextureView depth, int i) {
    pass.reset();
    pass.label = "frame";
    pass.colorAttachmentCount = 1;
    GpuRenderPassColorAttachment color = pass.colorAttachments[0];
    color.view = view;
    color.loadOp = GpuLoadOp.CLEAR;
    color.storeOp = GpuStoreOp.STORE;
    color.clearValue.r = i * 0.001;
    color.clearValue.g = 0.25;
    color.clearValue.b = 0.5;
    color.clearValue.a = 1.0;
  }

  private static void fillViewDescriptor(GpuTextureViewDescriptor d, GpuTextureView unused, int i) {
    d.reset();
    d.format = GpuTextureFormat.BGRA8_UNORM;
    d.dimension = GpuTextureViewDimension.D2;
    d.baseMipLevel = i & 3;
    d.aspect = GpuTextureAspect.ALL;
  }

  @Test
  void refillingARenderPassDescriptorAllocatesNothing() {
    com.sun.management.ThreadMXBean bean = bean();
    long thread = Thread.currentThread().threadId();
    GpuRenderPassDescriptor pass = new GpuRenderPassDescriptor();
    GpuTextureViewDescriptor viewDescriptor = new GpuTextureViewDescriptor();
    FakeTextureView view = new FakeTextureView();

    for (int i = 0; i < ITERATIONS; i++) {
      fill(pass, view, view, i);
      fillViewDescriptor(viewDescriptor, view, i);
    }

    long baselineStart = bean.getThreadAllocatedBytes(thread);
    long baselineEnd = bean.getThreadAllocatedBytes(thread);
    long baseline = baselineEnd - baselineStart;

    long before = bean.getThreadAllocatedBytes(thread);
    for (int i = 0; i < ITERATIONS; i++) {
      fill(pass, view, view, i);
      fillViewDescriptor(viewDescriptor, view, i);
    }
    long after = bean.getThreadAllocatedBytes(thread);

    assertEquals(baseline, after - before, "refilling descriptors must not allocate");
    assertEquals(1, pass.colorAttachmentCount);
  }

  @Test
  void theMeasurementNoticesRealAllocations() {
    com.sun.management.ThreadMXBean bean = bean();
    long thread = Thread.currentThread().threadId();
    GpuRenderPassDescriptor[] keep = new GpuRenderPassDescriptor[100];
    long before = bean.getThreadAllocatedBytes(thread);
    for (int i = 0; i < keep.length; i++) {
      keep[i] = new GpuRenderPassDescriptor();
    }
    long after = bean.getThreadAllocatedBytes(thread);
    assertTrue(after - before > 100 * 100, "creating descriptors must show up as allocation");
  }
}
