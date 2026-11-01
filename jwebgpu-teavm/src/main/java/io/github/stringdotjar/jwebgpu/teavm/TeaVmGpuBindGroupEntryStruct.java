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
package io.github.stringdotjar.jwebgpu.teavm;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

import io.github.stringdotjar.jwebgpu.GpuBindGroupEntry;
import io.github.stringdotjar.jwebgpu.GpuChainedStruct;
import io.github.stringdotjar.jwebgpu.GpuConstants;
import io.github.stringdotjar.jwebgpu.GpuExternalTextureBindingEntry;

/**
 * Fills the browser's {@code GPUBindGroupEntry} from a {@link GpuBindGroupEntry}.
 *
 * <p>The C struct has one member for every kind of resource (buffer, sampler, texture view) and uses the one that is
 * not null. The browser has a single {@code resource} member instead, which holds a sampler, a texture view, an
 * external texture, or a {@code {buffer, offset, size}} range for a buffer. This class is written by hand because of
 * that difference. A buffer range object is cached, and a size of {@code WHOLE_SIZE} leaves the browser's optional
 * {@code size} out.
 */
final class TeaVmGpuBindGroupEntryStruct {

  private final JSObject obj = TeaVmJs.object();
  private final JSObject range = TeaVmJs.object();

  TeaVmGpuBindGroupEntryStruct() {}

  @JSBody(params = { "o", "v" }, script = "o.binding = v >>> 0;")
  private static native void putBinding(JSObject o, int v);

  @JSBody(params = { "o", "v" }, script = "o.resource = v;")
  private static native void putResource(JSObject o, JSObject v);

  @JSBody(params = { "r", "buffer", "offset", "size", "whole" },
      script = "r.buffer = buffer; r.offset = offset; r.size = whole ? undefined : size;")
  private static native void fillRange(JSObject r, JSObject buffer, double offset, double size, boolean whole);

  JSObject obj() {
    return obj;
  }

  JSObject fill(GpuBindGroupEntry e) {
    write(obj, e);
    return obj;
  }

  void write(JSObject t, GpuBindGroupEntry e) {
    putBinding(t, e.binding);
    if (e.buffer != null) {
      fillRange(range, TeaVmGpuBuffer.js(e.buffer), (double) e.offset, (double) e.size,
          e.size == GpuConstants.WHOLE_SIZE);
      putResource(t, range);
    } else if (e.sampler != null) {
      putResource(t, TeaVmGpuSampler.js(e.sampler));
    } else if (e.textureView != null) {
      putResource(t, TeaVmGpuTextureView.js(e.textureView));
    } else {
      for (GpuChainedStruct c = e.nextInChain; c != null; c = c.next) {
        if (c instanceof GpuExternalTextureBindingEntry x) {
          putResource(t, TeaVmGpuExternalTexture.js(x.externalTexture));
          return;
        }
      }
      throw new IllegalArgumentException("A bind group entry needs a buffer, a sampler or a texture view.");
    }
  }
}
