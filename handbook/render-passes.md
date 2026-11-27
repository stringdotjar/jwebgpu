# Render Passes: A Full Triangle Walkthrough

This guide puts everything together: it draws a red triangle on a blue background, step by step, into a texture, then
reads the pixels back to prove it worked. Along the way it explains command encoders, render passes, color
attachments, vertex and index buffers, and depth buffers, and it ends with the garbage-free version for a window.

## Table of contents

1. [Why passes exist](#why-passes-exist)
2. [The plan](#the-plan)
3. [Step 1: a device](#step-1-a-device)
4. [Step 2: a texture to draw into](#step-2-a-texture-to-draw-into)
5. [Step 3: the shader and the pipeline](#step-3-the-shader-and-the-pipeline)
6. [Step 4: record and submit](#step-4-record-and-submit)
7. [Step 5: read the pixels back](#step-5-read-the-pixels-back)
8. [Step 6: clean up](#step-6-clean-up)
9. [Drawing from vertex and index buffers](#drawing-from-vertex-and-index-buffers)
10. [Adding a depth buffer](#adding-a-depth-buffer)
11. [More things a pass can do](#more-things-a-pass-can-do)
12. [The same frame, every frame, without garbage](#the-same-frame-every-frame-without-garbage)
13. [Common mistakes](#common-mistakes)
14. [Check your understanding](#check-your-understanding)

## Why passes exist

A GPU draws fastest when it knows, up front, which textures it is drawing into and what to do with them at the start
and the end (clear them? keep the old contents? keep the result?). A **render pass** is that promise: "for a while, I
will draw into these attachments". Inside the pass you set pipelines and resources and issue draws; outside it you can
copy and compute.

In the kitchen analogy, a pass is one cooking session at one counter: you clear the counter (load op), cook several
dishes on it (draws), and box up the result (store op).

## The plan

1. Get a device.
2. Create a 64 by 64 texture to draw into, and a view of it.
3. Create a shader and a render pipeline.
4. Record a render pass that clears to blue and draws three vertices, then a copy of the texture into a buffer, and
   submit it.
5. Map the buffer and check the pixels: red in the middle, blue in the corner.
6. Release everything.

The code below is one class. It uses the `GpuStartup` class from
[Instances, Adapters, and Devices](instances-adapters-and-devices.md#a-startup-class-that-works-on-every-platform) to
get the device, and the pieces are explained step by step.

## Step 1: a device

```java
public static void main(String[] args) throws InterruptedException {
  JWebGpuFfm.install();
  GpuInstance instance = JWebGpu.createInstance();
  GpuStartup startup = new GpuStartup(instance);
  startup.start();
  while (!startup.isFinished()) {
    instance.processEvents();
    Thread.sleep(1);
  }
  if (startup.getFailure() != null) {
    System.out.println(startup.getFailure());
    return;
  }
  new Triangle(instance, startup.getDevice(), startup.getQueue()).run();
}
```

## Step 2: a texture to draw into

```java
private static final int SIZE = 64;
private static final int ROW_BYTES = 256;   // 64 pixels * 4 bytes, already a multiple of 256.

GpuTextureDescriptor td = new GpuTextureDescriptor();
td.label = "target";
td.usage = GpuTextureUsage.RENDER_ATTACHMENT | GpuTextureUsage.COPY_SRC;
td.dimension = GpuTextureDimension.D2;
td.size.width = SIZE;
td.size.height = SIZE;
td.size.depthOrArrayLayers = 1;
td.format = GpuTextureFormat.RGBA8_UNORM;
GpuTexture texture = device.createTexture(td);
GpuTextureView view = texture.createView(null);
```

`RENDER_ATTACHMENT` lets the pass draw into it, and `COPY_SRC` lets us copy it into a buffer afterwards. For a window,
you would use the surface texture instead (see [Surfaces and Presenting](surfaces-and-presenting.md)).

## Step 3: the shader and the pipeline

This shader needs no vertex buffer: it picks the three corners from a constant array, using the index of the vertex
being drawn.

```java
private static final String WGSL = """
    @vertex fn vs(@builtin(vertex_index) i : u32) -> @builtin(position) vec4f {
      var p = array<vec2f, 3>(vec2f(0.0, 0.8), vec2f(-0.8, -0.8), vec2f(0.8, -0.8));
      return vec4f(p[i], 0.0, 1.0);
    }
    @fragment fn fs() -> @location(0) vec4f { return vec4f(1.0, 0.0, 0.0, 1.0); }
    """;

GpuShaderSourceWGSL wgsl = new GpuShaderSourceWGSL();
wgsl.code = WGSL;
GpuShaderModuleDescriptor smd = new GpuShaderModuleDescriptor();
smd.label = "triangle shader";
smd.nextInChain = wgsl;
GpuShaderModule shader = device.createShaderModule(smd);

GpuRenderPipelineDescriptor rpd = new GpuRenderPipelineDescriptor();
rpd.vertex.module = shader;
rpd.vertex.entryPoint = "vs";
rpd.primitive.topology = GpuPrimitiveTopology.TRIANGLE_LIST;
GpuFragmentState fragment = new GpuFragmentState();
fragment.module = shader;
fragment.entryPoint = "fs";
fragment.targetCount = 1;
fragment.targets[0].format = GpuTextureFormat.RGBA8_UNORM;   // Same format as the texture.
fragment.targets[0].writeMask = GpuColorWriteMask.ALL;
rpd.fragment = fragment;
GpuRenderPipeline pipeline = device.createRenderPipeline(rpd);
```

The positions are in **clip space**: x and y go from -1 (left, bottom) to 1 (right, top), whatever the size of the
texture.

## Step 4: record and submit

First a buffer to copy the pixels into, then the commands:

```java
GpuBufferDescriptor bd = new GpuBufferDescriptor();
bd.label = "readback";
bd.usage = GpuBufferUsage.MAP_READ | GpuBufferUsage.COPY_DST;
bd.size = (long) ROW_BYTES * SIZE;
GpuBuffer readback = device.createBuffer(bd);

GpuCommandEncoder encoder = device.createCommandEncoder(null);

GpuRenderPassDescriptor pass = new GpuRenderPassDescriptor();
pass.colorAttachmentCount = 1;
GpuRenderPassColorAttachment color = pass.colorAttachments[0];
color.view = view;
color.loadOp = GpuLoadOp.CLEAR;     // Start from the clear color...
color.storeOp = GpuStoreOp.STORE;   // ...and keep the result.
color.clearValue.r = 0.0;
color.clearValue.g = 0.0;
color.clearValue.b = 1.0;
color.clearValue.a = 1.0;

GpuRenderPassEncoder rp = encoder.beginRenderPass(pass);
rp.setPipeline(pipeline);
rp.draw(3, 1, 0, 0);   // 3 vertices, 1 instance, starting at vertex 0 and instance 0.
rp.end();

GpuTexelCopyTextureInfo src = new GpuTexelCopyTextureInfo();
src.texture = texture;
src.aspect = GpuTextureAspect.ALL;
GpuTexelCopyBufferInfo dst = new GpuTexelCopyBufferInfo();
dst.buffer = readback;
dst.layout.bytesPerRow = ROW_BYTES;
dst.layout.rowsPerImage = SIZE;
GpuExtent3D extent = new GpuExtent3D();
extent.width = SIZE;
extent.height = SIZE;
encoder.copyTextureToBuffer(src, dst, extent);

GpuCommandBuffer commands = encoder.finish(null);
queue.submit(new GpuCommandBuffer[] { commands }, 1);
```

The pieces:

- **The encoder** records commands. Nothing has happened on the GPU yet.
- **`beginRenderPass`** starts the pass with its attachments. The `loadOp` says what the attachment starts as
  (`CLEAR` to the `clearValue`, or `LOAD` to keep what was there), and the `storeOp` says whether to keep the result
  (`STORE`) or throw it away (`DISCARD`, for things like a depth buffer you no longer need).
- **`end()`** closes the pass. Every pass must be ended before the encoder records anything else.
- **`finish`** seals the recording into a command buffer, and **`submit`** hands it to the GPU. The GPU now draws and
  copies, in order.

## Step 5: read the pixels back

```java
private final byte[] pixels = new byte[ROW_BYTES * SIZE];
private boolean mapped;

readback.mapAsync(GpuMapMode.READ, 0, bd.size, GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, message) -> {
  if (status == GpuMapAsyncStatus.SUCCESS) {
    readback.readMappedRange(0, pixels, 0, pixels.length);
  } else {
    System.out.println("Map failed (" + status + "): " + message);
  }
  readback.unmap();
  mapped = true;
});
while (!mapped) {
  instance.processEvents();
  Thread.sleep(1);
}

int center = (SIZE / 2) * ROW_BYTES + (SIZE / 2) * 4;
System.out.println("center: " + (pixels[center] & 0xFF) + ", " + (pixels[center + 1] & 0xFF) + ", "
    + (pixels[center + 2] & 0xFF));   // 255, 0, 0: red.
int corner = 1 * ROW_BYTES + 1 * 4;
System.out.println("corner: " + (pixels[corner] & 0xFF) + ", " + (pixels[corner + 1] & 0xFF) + ", "
    + (pixels[corner + 2] & 0xFF));   // 0, 0, 255: blue.
```

The `& 0xFF` turns Java's signed bytes into 0 to 255. Each row starts `ROW_BYTES` after the previous one, and each
pixel is 4 bytes (red, green, blue, alpha, because the format is `RGBA8_UNORM`; a `BGRA8_UNORM` surface stores blue
first).

## Step 6: clean up

```java
commands.release();
rp.release();
encoder.release();
readback.release();
pipeline.release();
shader.release();
view.release();
texture.release();
queue.release();
device.release();
// And finally the adapter and the instance.
```

## Drawing from vertex and index buffers

Real programs keep vertices in buffers. Upload the positions, describe the buffer in the pipeline (see
[Render pipelines](shaders-and-pipelines.md#render-pipelines)), and bind it in the pass:

```java
private static final float[] CORNERS = { 0.0f, 0.8f, -0.8f, -0.8f, 0.8f, -0.8f };
private static final short[] INDICES = { 0, 1, 2, 0 };   // Padded to 8 bytes, a multiple of 4.

queue.writeBuffer(vertices, 0, CORNERS, 0, CORNERS.length);
queue.writeBuffer(indices, 0, INDICES, 0, INDICES.length);

rp.setPipeline(pipeline);
rp.setVertexBuffer(0, vertices, 0, GpuConstants.WHOLE_SIZE);
rp.setIndexBuffer(indices, GpuIndexFormat.UINT16, 0, GpuConstants.WHOLE_SIZE);
rp.drawIndexed(3, 1, 0, 0, 0);
```

The vertex buffer needs `VERTEX | COPY_DST` usage and the index buffer `INDEX | COPY_DST`. `GpuConstants.WHOLE_SIZE`
means "from the offset to the end of the buffer". Index buffers let several triangles share vertices: a square is 4
vertices and 6 indices instead of 6 vertices.

## Adding a depth buffer

For 3D, a depth buffer keeps near surfaces in front of far ones. Create a depth texture the size of the color target,
give the pipeline a depth state (see [Blending and depth](shaders-and-pipelines.md#blending-and-depth)), and attach it
to the pass:

```java
GpuTextureDescriptor dd = new GpuTextureDescriptor();
dd.usage = GpuTextureUsage.RENDER_ATTACHMENT;
dd.dimension = GpuTextureDimension.D2;
dd.size.width = width;
dd.size.height = height;
dd.format = GpuTextureFormat.DEPTH24_PLUS;
GpuTexture depthTexture = device.createTexture(dd);
GpuTextureView depthView = depthTexture.createView(null);

GpuRenderPassDepthStencilAttachment depth = new GpuRenderPassDepthStencilAttachment();
depth.view = depthView;
depth.depthLoadOp = GpuLoadOp.CLEAR;
depth.depthStoreOp = GpuStoreOp.DISCARD;   // Not needed after the pass.
depth.depthClearValue = 1.0f;              // 1 is the far plane.
pass.depthStencilAttachment = depth;
```

Recreate the depth texture whenever the window is resized, because it must match the color target's size.

## More things a pass can do

| Call | What it does |
|------|--------------|
| `setViewport(x, y, width, height, minDepth, maxDepth)` | Draws into part of the target (split screen, minimaps). |
| `setScissorRect(x, y, width, height)` | Discards pixels outside a rectangle. |
| `setBlendConstant(color)` | The color used by the `CONSTANT` blend factors. |
| `setStencilReference(reference)` | The value stencil tests compare against. |
| `draw(vertexCount, instanceCount, firstVertex, firstInstance)` | Draws vertices; instances repeat the draw, for example for many identical objects. |
| `drawIndexed(indexCount, instanceCount, firstIndex, baseVertex, firstInstance)` | Draws through the index buffer. |
| `drawIndirect(buffer, offset)`, `drawIndexedIndirect(buffer, offset)` | Takes the draw arguments from a buffer, for draws decided by a compute shader. |
| `pushDebugGroup(label)`, `popDebugGroup()`, `insertDebugMarker(label)` | Names sections of the pass, which GPU debuggers and error messages show. |
| `executeBundles(bundles, count)` | Replays draws recorded once into a `GpuRenderBundle`. |

You can draw many times in one pass, switching pipelines and bind groups between draws. Starting a new pass is more
expensive than another draw, so group work that goes into the same targets into one pass.

## The same frame, every frame, without garbage

The walkthrough creates its objects as it goes, which is fine once. A frame loop keeps every descriptor and array in a
field and refills the wrappers. Here is the drawing part of a frame, as the unit tests run it 10,000 times without
allocating a single byte:

```java
private final GpuCommandBuffer[] submit = new GpuCommandBuffer[1];
private final float[] data = { 0f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f };

void frame() {
  device.createCommandEncoder(null, encoder);
  encoder.beginRenderPass(pass, passEncoder);
  passEncoder.setPipeline(pipeline);
  passEncoder.setBindGroup(0, bindGroup);
  passEncoder.setVertexBuffer(0, vertices, 0, GpuConstants.WHOLE_SIZE);
  passEncoder.draw(3, 1, 0, 0);
  passEncoder.end();
  encoder.finish(null, commands);
  queue.writeBuffer(vertices, 0, data, 0, data.length);
  submit[0] = commands;
  queue.submit(submit, 1);
  commands.release();
  passEncoder.release();
  encoder.release();
}
```

The wrappers `encoder`, `passEncoder`, and `commands` were created once during setup (by one normal call each) and are
refilled from then on. The [Surfaces and Presenting](surfaces-and-presenting.md#the-frame-loop) guide shows the same
loop drawing into a window, and [Garbage-Free Usage](garbage-free.md) explains the rules behind it.

## Common mistakes

- **Forgetting `end()`** on a pass before `finish()`. The command buffer is invalid, and the uncaptured error callback
  says so.
- **Recording on an encoder after `finish()`.** An encoder is used once; create (or refill) a new one each frame.
- **A pipeline format that differs from the attachment format.**
- **A depth texture whose size differs from the color target**, usually after a resize.
- **Reading the pixels without waiting for the map callback.**
- **`loadOp = LOAD` on the first pass of a surface texture.** Its contents are undefined; clear it instead.

## Check your understanding

1. What do `loadOp` and `storeOp` decide, and which values does a typical color attachment use?
2. Why does the walkthrough copy the texture into a buffer instead of mapping the texture?
3. Which three wrappers does the per-frame code refill, and why are they released at the end of each frame?

(Answers: what the attachment starts as at the beginning of the pass and whether the result is kept at the end, so
`CLEAR` and `STORE`; textures cannot be mapped, only buffers can, so pixels travel to Java through a `MAP_READ` buffer;
the command encoder, the render pass encoder, and the command buffer, released so their native objects are freed
promptly, while the Java wrappers stay ready for the next refill.)

**Next:** [Compute](compute.md)
