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

import io.github.stringdotjar.jwebgpu.GpuBindGroupLayoutEntry;
import io.github.stringdotjar.jwebgpu.GpuBufferBindingType;
import io.github.stringdotjar.jwebgpu.GpuChainedStruct;
import io.github.stringdotjar.jwebgpu.GpuExternalTextureBindingLayout;
import io.github.stringdotjar.jwebgpu.GpuSamplerBindingType;
import io.github.stringdotjar.jwebgpu.GpuStorageTextureAccess;
import io.github.stringdotjar.jwebgpu.GpuTextureSampleType;

/**
 * Fills the browser's {@code GPUBindGroupLayoutEntry} from a {@link GpuBindGroupLayoutEntry}.
 *
 * <p>The C struct always has the four binding layouts (buffer, sampler, texture, storage texture) and marks the unused
 * ones with the value {@code BindingNotUsed}. The browser has only the member of the layout that the entry uses, and
 * the other members must be absent. This class is written by hand because of that difference.
 */
final class TeaVmGpuBindGroupLayoutEntryStruct {

  private final JSObject obj = TeaVmJs.object();
  private final JSObject external = TeaVmJs.object();
  private final TeaVmGpuBufferBindingLayoutStruct buffer = new TeaVmGpuBufferBindingLayoutStruct();
  private final TeaVmGpuSamplerBindingLayoutStruct sampler = new TeaVmGpuSamplerBindingLayoutStruct();
  private final TeaVmGpuTextureBindingLayoutStruct texture = new TeaVmGpuTextureBindingLayoutStruct();
  private final TeaVmGpuStorageTextureBindingLayoutStruct storage = new TeaVmGpuStorageTextureBindingLayoutStruct();

  TeaVmGpuBindGroupLayoutEntryStruct() {}

  @JSBody(params = { "o", "v" }, script = "o.binding = v >>> 0;")
  private static native void putBinding(JSObject o, int v);

  @JSBody(params = { "o", "v" }, script = "o.visibility = v >>> 0;")
  private static native void putVisibility(JSObject o, int v);

  @JSBody(params = { "o", "v" }, script = "o.buffer = v == null ? undefined : v;")
  private static native void putBuffer(JSObject o, JSObject v);

  @JSBody(params = { "o", "v" }, script = "o.sampler = v == null ? undefined : v;")
  private static native void putSampler(JSObject o, JSObject v);

  @JSBody(params = { "o", "v" }, script = "o.texture = v == null ? undefined : v;")
  private static native void putTexture(JSObject o, JSObject v);

  @JSBody(params = { "o", "v" }, script = "o.storageTexture = v == null ? undefined : v;")
  private static native void putStorageTexture(JSObject o, JSObject v);

  @JSBody(params = { "o", "v" }, script = "o.externalTexture = v == null ? undefined : v;")
  private static native void putExternalTexture(JSObject o, JSObject v);

  JSObject obj() {
    return obj;
  }

  JSObject fill(GpuBindGroupLayoutEntry e) {
    write(obj, e);
    return obj;
  }

  void write(JSObject t, GpuBindGroupLayoutEntry e) {
    putBinding(t, e.binding);
    putVisibility(t, (int) e.visibility);
    putBuffer(t, e.buffer.type != GpuBufferBindingType.BINDING_NOT_USED ? buffer.fill(e.buffer) : null);
    putSampler(t, e.sampler.type != GpuSamplerBindingType.BINDING_NOT_USED ? sampler.fill(e.sampler) : null);
    putTexture(t, e.texture.sampleType != GpuTextureSampleType.BINDING_NOT_USED ? texture.fill(e.texture) : null);
    putStorageTexture(t, e.storageTexture.access != GpuStorageTextureAccess.BINDING_NOT_USED
        ? storage.fill(e.storageTexture) : null);
    JSObject ext = null;
    for (GpuChainedStruct c = e.nextInChain; c != null; c = c.next) {
      if (c instanceof GpuExternalTextureBindingLayout) {
        ext = external;
      } else {
        throw TeaVmJs.unsupported("An extension struct is chained onto GpuBindGroupLayoutEntry that the browser "
            + "has no equivalent for.");
      }
    }
    putExternalTexture(t, ext);
  }
}
