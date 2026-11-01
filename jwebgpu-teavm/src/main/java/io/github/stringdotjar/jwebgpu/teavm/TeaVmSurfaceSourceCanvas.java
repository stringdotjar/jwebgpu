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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

import io.github.stringdotjar.jwebgpu.GpuChainedStruct;

/**
 * Tells the web backend which canvas a surface is made of.
 *
 * <p>A browser draws to a canvas element instead of a native window, so this extension struct plays the role that
 * the window structs (Xlib, Win32, Metal and so on) play on desktop. Chain it onto a surface descriptor:
 *
 * <pre>{@code
 * GpuSurfaceDescriptor descriptor = new GpuSurfaceDescriptor();
 * descriptor.nextInChain = TeaVmSurfaceSourceCanvas.ofSelector("#canvas");
 * GpuSurface surface = instance.createSurface(descriptor);
 * }</pre>
 *
 * <p>The canvas is found by a CSS selector (resolved when the surface is created) or taken as an element the
 * program already has. It is a JavaScript {@code HTMLCanvasElement} or {@code OffscreenCanvas}.
 *
 * <p>This struct is not part of {@code webgpu.h}; its tag is a private number that is only meaningful to this backend.
 */
public final class TeaVmSurfaceSourceCanvas extends GpuChainedStruct {

  /** The tag of this extension struct. It is outside the range that the native specification uses. */
  public static final int STYPE = 0x7E570001;

  /** The canvas element, or {@code null} if {@link #selector} names it. */
  public @Nullable JSObject canvas;

  /** The CSS selector of the canvas, or {@code null} if {@link #canvas} holds it. */
  public @Nullable String selector;

  /** Creates the struct with no canvas. Set {@link #canvas} or {@link #selector} before using it. */
  public TeaVmSurfaceSourceCanvas() {
    reset();
  }

  @JSBody(params = "selector", script = "return document.querySelector(selector);")
  private static native JSObject query(String selector);

  /**
   * Creates a struct that finds the canvas by a CSS selector.
   *
   * @param selector The selector, such as {@code #canvas}.
   * @return The struct.
   */
  public static @NotNull TeaVmSurfaceSourceCanvas ofSelector(@NotNull String selector) {
    TeaVmSurfaceSourceCanvas s = new TeaVmSurfaceSourceCanvas();
    s.selector = selector;
    return s;
  }

  /**
   * Creates a struct that uses a canvas element.
   *
   * @param canvas The {@code HTMLCanvasElement} or {@code OffscreenCanvas}.
   * @return The struct.
   */
  public static @NotNull TeaVmSurfaceSourceCanvas ofElement(@NotNull JSObject canvas) {
    TeaVmSurfaceSourceCanvas s = new TeaVmSurfaceSourceCanvas();
    s.canvas = canvas;
    return s;
  }

  /** Restores the defaults: no canvas, no selector, and no next struct. */
  public void reset() {
    next = null;
    canvas = null;
    selector = null;
  }

  @Override
  public int sType() {
    return STYPE;
  }

  /**
   * Finds the canvas element.
   *
   * @return The element.
   * @throws IllegalStateException If neither a canvas nor a selector is set, or the selector matches nothing.
   */
  JSObject resolve() {
    if (canvas != null) {
      return canvas;
    }
    if (selector == null) {
      throw new IllegalStateException("Set either the canvas or the selector of the surface source.");
    }
    JSObject found = query(selector);
    if (TeaVmAsync.isNothing(found)) {
      throw new IllegalStateException("No element matches the selector '" + selector + "'.");
    }
    return found;
  }
}
