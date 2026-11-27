# Surfaces and Presenting

A surface is how WebGPU puts pictures on the screen: in a desktop window, in an Android view, or in a web page's
`<canvas>`. This guide shows how to create a surface on each platform, how to configure it, and the loop that draws
and presents a frame without creating garbage.

## Table of contents

1. [Why surfaces exist](#why-surfaces-exist)
2. [The mental model: a picture frame with a stack of canvases](#the-mental-model-a-picture-frame-with-a-stack-of-canvases)
3. [Creating a surface on the desktop](#creating-a-surface-on-the-desktop)
4. [Creating a surface on Android](#creating-a-surface-on-android)
5. [Creating a surface on the web](#creating-a-surface-on-the-web)
6. [Configuring the surface](#configuring-the-surface)
7. [The frame loop](#the-frame-loop)
8. [Resizing and lost surfaces](#resizing-and-lost-surfaces)
9. [Drawing without a window](#drawing-without-a-window)
10. [Common mistakes](#common-mistakes)
11. [Check your understanding](#check-your-understanding)

## Why surfaces exist

WebGPU itself knows nothing about windows. Windows belong to the operating system (Win32, X11, Wayland, Cocoa,
Android) or to the browser, and each one hands out a different kind of handle. A **surface** wraps one of those
handles, so the rest of WebGPU can treat "the thing on the screen" like any other texture to draw into.

jwebgpu does not open windows. You open a window with whatever windowing library or toolkit you like, get its native
handle, and give that handle to jwebgpu.

## The mental model: a picture frame with a stack of canvases

Think of the surface as a picture frame on the wall. Behind it is a small stack of blank canvases.

- **Configuring** the surface decides the size and kind of the canvases.
- Each frame, you take the next blank canvas (`getCurrentTexture`), paint on it (a render pass), and put it in the
  frame (`present`). The viewer sees it.
- When the frame on the wall changes size (the window was resized), the canvases no longer fit, and you configure
  again.

## Creating a surface on the desktop

On the desktop you create a surface from a `GpuSurfaceDescriptor` with a chained struct that carries the native window
handle. Which struct depends on the operating system and window system:

| Platform | Extension struct | Fields to fill |
|----------|------------------|----------------|
| Windows | `GpuSurfaceSourceWindowsHWND` | `hinstance` (the module handle) and `hwnd` (the window handle) |
| Linux, X11 (Xlib) | `GpuSurfaceSourceXlibWindow` | `display` (the `Display*`) and `window` (the X window id) |
| Linux, X11 (XCB) | `GpuSurfaceSourceXCBWindow` | `connection` (the `xcb_connection_t*`) and `window` (the XCB window id, an `int`) |
| Linux, Wayland | `GpuSurfaceSourceWaylandSurface` | `display` (the `wl_display*`) and `surface` (the `wl_surface*`) |
| macOS | `GpuSurfaceSourceMetalLayer` | `layer` (a `CAMetalLayer*` attached to the window's view) |

Every handle is a `long`. Your windowing library has functions that return them. For example, on Windows:

```java
GpuSurfaceSourceWindowsHWND source = new GpuSurfaceSourceWindowsHWND();
source.hinstance = moduleHandle;   // From your windowing library.
source.hwnd = windowHandle;        // From your windowing library.

GpuSurfaceDescriptor descriptor = new GpuSurfaceDescriptor();
descriptor.label = "main window";
descriptor.nextInChain = source;
GpuSurface surface = instance.createSurface(descriptor);
```

The Linux libraries that jwebgpu publishes support X11 (Xlib and XCB) and Wayland at the same time and pick one at run
time, depending on the struct you chain. On macOS, Metal draws into a `CAMetalLayer`, so your window's content view
needs one; many windowing libraries can create it for you or give you the view to attach it to.

Create the surface **before** you request the adapter if you can, and pass it as
`GpuRequestAdapterOptions.compatibleSurface`, so the adapter you get is one that can present to it.

## Creating a surface on Android

On Android the window handle is an `ANativeWindow`, which you get from a `Surface` (from a `SurfaceView` or a
`TextureView`). The `AndroidSurface` helper in `io.github.stringdotjar.jwebgpu.jni.android` does the conversion and
releases things in the right order:

```java
public void surfaceCreated(SurfaceHolder holder) {
  target = AndroidSurface.create(instance, holder.getSurface());
  GpuSurface surface = target.getSurface();
  // Configure it in surfaceChanged(...), where you know the size.
}

public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
  configure(target.getSurface(), width, height);
}

public void surfaceDestroyed(SurfaceHolder holder) {
  target.release();
  target = null;
}
```

Think of `AndroidSurface` as a picture frame holder: the wall (the native window) must outlive the frame (the WebGPU
surface), so `release()` takes the frame down first and lets go of the wall afterwards. Android destroys and recreates
the `Surface` when your app goes to the background and comes back, so create a new `AndroidSurface` every time
`surfaceCreated(...)` runs. If you manage the window yourself, the extension struct is
`GpuSurfaceSourceAndroidNativeWindow` with its `window` field.

## Creating a surface on the web

In the browser a surface is a `<canvas>` element. Chain a `TeaVmSurfaceSourceCanvas` from `jwebgpu-teavm`, made from a
CSS selector or from a canvas element you already have:

```java
GpuSurfaceDescriptor descriptor = new GpuSurfaceDescriptor();
descriptor.nextInChain = TeaVmSurfaceSourceCanvas.ofSelector("#canvas");   // Or ofElement(canvasElement).
GpuSurface surface = instance.createSurface(descriptor);
```

That is the only web-specific line; everything after it is the same as on the other platforms. Keep it in your web
launcher, and pass the finished `GpuSurface` to your platform-neutral code.

## Configuring the surface

Before drawing, configure the surface: which device draws into it, the pixel format, the size, and how frames are
shown. Ask the surface what it supports first:

```java
GpuSurfaceCapabilities capabilities = new GpuSurfaceCapabilities();
surface.getCapabilities(adapter, capabilities);
GpuTextureFormat format = capabilities.formats[0];   // The first format is the preferred one.

GpuSurfaceConfiguration configuration = new GpuSurfaceConfiguration();
configuration.device = device;
configuration.format = format;
configuration.usage = GpuTextureUsage.RENDER_ATTACHMENT;
configuration.width = width;
configuration.height = height;
configuration.presentMode = GpuPresentMode.FIFO;
configuration.alphaMode = GpuCompositeAlphaMode.AUTO;
surface.configure(configuration);
```

What the fields mean:

- `format` is the pixel format of the surface textures. Use one from `capabilities.formats` (often `BGRA8_UNORM` or
  `RGBA8_UNORM`), and build your render pipelines with the same format.
- `usage` says what you will do with the textures. `RENDER_ATTACHMENT` (the default) lets you draw into them; add
  `COPY_SRC` if you also want to copy from them, for example for screenshots. `capabilities.usages` lists what is
  allowed.
- `width` and `height` are the size in pixels. Use the window's framebuffer size, which can differ from its size in
  screen points on high-density displays.
- `presentMode` controls vertical sync. `FIFO` waits for the display (no tearing, and always supported), `MAILBOX`
  shows the newest frame without tearing where supported, and `IMMEDIATE` shows frames at once, which may tear.
  `capabilities.presentModes` lists what the surface supports.
- `alphaMode` controls how the window system blends transparent pixels. `AUTO` picks a sensible default, usually
  opaque.

On the web, `width` and `height` set the size of the canvas's drawing buffer, and `presentMode` is ignored.

## The frame loop

Each frame takes the next surface texture, draws into a view of it, submits, and presents. Every object the loop needs
lives in a field, and the refill overloads reuse the wrappers, so after the first frame nothing is allocated:

```java
private final GpuSurfaceTexture frame = new GpuSurfaceTexture();
private final GpuRenderPassDescriptor pass = new GpuRenderPassDescriptor();
private final GpuCommandBuffer[] submit = new GpuCommandBuffer[1];
private GpuTextureView view;
private GpuCommandEncoder encoder;
private GpuRenderPassEncoder passEncoder;
private GpuCommandBuffer commands;

void setUpPass() {
  pass.colorAttachmentCount = 1;
  GpuRenderPassColorAttachment color = pass.colorAttachments[0];
  color.loadOp = GpuLoadOp.CLEAR;
  color.storeOp = GpuStoreOp.STORE;
  color.clearValue.r = 0.1;
  color.clearValue.g = 0.2;
  color.clearValue.b = 0.3;
  color.clearValue.a = 1.0;
}

void drawFrame() {
  instance.processEvents();

  surface.getCurrentTexture(frame);
  GpuSurfaceGetCurrentTextureStatus status = frame.status;
  if (status == GpuSurfaceGetCurrentTextureStatus.OUTDATED || status == GpuSurfaceGetCurrentTextureStatus.LOST) {
    configure(width, height);
    return;
  }
  if (status != GpuSurfaceGetCurrentTextureStatus.SUCCESS_OPTIMAL
      && status != GpuSurfaceGetCurrentTextureStatus.SUCCESS_SUBOPTIMAL) {
    return;   // TIMEOUT or ERROR: skip this frame.
  }

  // The first frame creates the wrappers; every later frame refills them.
  view = view == null ? frame.texture.createView(null) : frame.texture.createView(null, view);
  pass.colorAttachments[0].view = view;
  encoder = encoder == null ? device.createCommandEncoder(null) : device.createCommandEncoder(null, encoder);
  passEncoder = passEncoder == null ? encoder.beginRenderPass(pass) : encoder.beginRenderPass(pass, passEncoder);

  passEncoder.setPipeline(pipeline);
  passEncoder.draw(3, 1, 0, 0);
  passEncoder.end();

  commands = commands == null ? encoder.finish(null) : encoder.finish(null, commands);
  submit[0] = commands;
  queue.submit(submit, 1);
  surface.present();

  commands.release();
  passEncoder.release();
  encoder.release();
  view.release();
  frame.texture.release();
}
```

A few details worth knowing:

- `getCurrentTexture(frame)` refills the `GpuSurfaceTexture` you pass, including the wrapper in `frame.texture`, so it
  allocates nothing after the first frame. Always check `frame.status` before using the texture.
- Releasing the per-frame wrappers at the end gives their native objects back promptly. The wrappers themselves stay
  usable for the next refill.
- `present()` returns a `GpuStatus`. On the web it does nothing, because the browser shows the canvas when your code
  returns to the event loop.
- On the web, drive `drawFrame()` from the browser's frame callback (for example TeaVM's
  `Window.requestAnimationFrame(...)`) instead of a loop.

## Resizing and lost surfaces

When the window changes size, call `configure(...)` again with the new size. A good place is your window system's
resize callback, or the start of the frame when you notice the size changed. Do not configure a surface with a width
or height of 0 (a minimized window); skip drawing until the window has a size again.

`getCurrentTexture` also tells you when the surface needs attention:

| Status | Meaning | What to do |
|--------|---------|------------|
| `SUCCESS_OPTIMAL` | All good. | Draw. |
| `SUCCESS_SUBOPTIMAL` | It works, but the configuration no longer matches the window perfectly. | Draw, then configure again soon. |
| `TIMEOUT` | No texture was ready in time. | Skip this frame. |
| `OUTDATED` | The window changed, so the configuration is out of date. | Configure again, then try the next frame. |
| `LOST` | The surface is gone. | Configure again; if it keeps failing, create a new surface. |
| `ERROR` | Something else went wrong. | Skip the frame and check your uncaptured error callback. |

Before you release a surface, call `surface.unconfigure()`, then `surface.release()`.

## Drawing without a window

You do not need a surface to render. Create a texture with `RENDER_ATTACHMENT` usage, draw into a view of it, and copy
it into a buffer to read the pixels back. That is how the tests check the triangle on machines without a screen, and
it is how you render thumbnails or images on a server. The [Render Passes](render-passes.md) guide walks through it.

## Common mistakes

- **Using a pipeline whose color target format differs from the surface format.** Build pipelines with the format
  you configured.
- **Not checking `frame.status`.** An outdated surface hands you no usable texture.
- **Forgetting to reconfigure after a resize.** The picture stretches, or `getCurrentTexture` reports `OUTDATED`.
- **Configuring a 0 by 0 surface** while the window is minimized.
- **Keeping an `AndroidSurface` after `surfaceDestroyed(...)`.** Release it there and create a new one in
  `surfaceCreated(...)`.
- **Creating a new `GpuSurfaceTexture` or render pass descriptor every frame.** Keep them in fields.

## Check your understanding

1. Why does jwebgpu not open windows itself?
2. `getCurrentTexture` reports `OUTDATED`. What happened, and what do you do?
3. Why are `view`, `encoder`, `passEncoder`, and `commands` fields instead of local variables in the frame loop?

(Answers: windows belong to the operating system or the browser, and every platform has its own handles, so you use
the windowing library you like and pass its handle in; the window changed (usually its size), so configure the surface
again with the new size; so the refill overloads can reuse the same wrappers every frame, which keeps the frame free
of allocations.)

**Next:** [Buffers and Textures](buffers-and-textures.md)
