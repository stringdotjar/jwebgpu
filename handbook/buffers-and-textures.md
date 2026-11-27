# Buffers and Textures

Buffers and textures are the GPU's memory: vertices, indices, uniforms, images, and results all live in them. This
guide explains usages, how to create both kinds of resources, how to upload data, how to read data back to Java, and
how texture views, samplers, and copies fit in.

## Table of contents

1. [Why the GPU has its own memory](#why-the-gpu-has-its-own-memory)
2. [Usages: telling the GPU what a resource is for](#usages-telling-the-gpu-what-a-resource-is-for)
3. [Creating a buffer](#creating-a-buffer)
4. [Uploading to a buffer](#uploading-to-a-buffer)
5. [Reading a buffer back with mapAsync](#reading-a-buffer-back-with-mapasync)
6. [Buffers that start mapped](#buffers-that-start-mapped)
7. [Creating a texture](#creating-a-texture)
8. [Uploading to a texture](#uploading-to-a-texture)
9. [Texture views](#texture-views)
10. [Samplers](#samplers)
11. [Copies between buffers and textures](#copies-between-buffers-and-textures)
12. [Freeing memory](#freeing-memory)
13. [Common mistakes](#common-mistakes)
14. [Check your understanding](#check-your-understanding)

## Why the GPU has its own memory

A graphics card has its own memory, close to its cores, and on most machines your Java arrays cannot be read by the GPU
directly. So data travels: you upload it from Java into a GPU resource, the GPU works on it, and if you want results
back, they travel the other way. WebGPU makes those trips explicit, which is why there are separate calls for writing,
copying, and mapping.

There are two kinds of resources:

- A **buffer** is a plain block of bytes, like a `byte[]` on the GPU. Vertices, indices, uniforms, storage data.
- A **texture** is an image with a size, a pixel format, and optionally mip levels and layers, which the GPU can filter
  and sample. Pictures, render targets, depth buffers.

## Usages: telling the GPU what a resource is for

Every buffer and texture is created with a **usage**: a set of bit flags that lists everything you will ever do with it.
The GPU uses this to put the resource in the right kind of memory, and WebGPU rejects anything you did not declare.
It is like labeling moving boxes: "kitchen, fragile" tells the movers where it goes and how to carry it.

| `GpuBufferUsage` | Lets you... |
|------------------|-------------|
| `VERTEX` | Bind it as a vertex buffer. |
| `INDEX` | Bind it as an index buffer. |
| `UNIFORM` | Bind it as a uniform buffer (small, read-only shader data). |
| `STORAGE` | Bind it as a storage buffer (large, read or write shader data). |
| `INDIRECT` | Take draw or dispatch arguments from it. |
| `COPY_SRC`, `COPY_DST` | Copy from it, or into it (`queue.writeBuffer(...)` needs `COPY_DST`). |
| `MAP_READ`, `MAP_WRITE` | Map it to read it from Java, or to write it from Java. |
| `QUERY_RESOLVE` | Receive the results of a query set. |

| `GpuTextureUsage` | Lets you... |
|-------------------|-------------|
| `TEXTURE_BINDING` | Sample it in a shader. |
| `STORAGE_BINDING` | Use it as a storage texture in a shader. |
| `RENDER_ATTACHMENT` | Draw into it in a render pass. |
| `COPY_SRC`, `COPY_DST` | Copy from it, or into it (`queue.writeTexture(...)` needs `COPY_DST`). |

Combine them with `|`: `GpuBufferUsage.VERTEX | GpuBufferUsage.COPY_DST`.

`MAP_READ` may only be combined with `COPY_DST`, and `MAP_WRITE` only with `COPY_SRC`. That is why reading results back
always uses a separate "readback" buffer that you copy into.

## Creating a buffer

```java
GpuBufferDescriptor descriptor = new GpuBufferDescriptor();
descriptor.label = "triangle vertices";
descriptor.usage = GpuBufferUsage.VERTEX | GpuBufferUsage.COPY_DST;
descriptor.size = 6 * Float.BYTES;   // In bytes.
GpuBuffer vertices = device.createBuffer(descriptor);
```

- `size` is in bytes. Keep it a multiple of 4; writes and copies work in 4-byte steps.
- `label` shows up in error messages, which makes debugging much easier. Label everything.
- `buffer.getSize()` and `buffer.getUsage()` read the values back later.

## Uploading to a buffer

The simplest upload is `queue.writeBuffer(...)`. It copies your data into the buffer before any work you submit after
it runs:

```java
private final float[] corners = { 0.0f, 0.8f, -0.8f, -0.8f, 0.8f, -0.8f };

queue.writeBuffer(vertices, 0, corners, 0, corners.length);
```

There is an overload for each array type (`byte[]`, `short[]`, `int[]`, and `float[]`): the second offset is the index
of the first element, and the count is in **elements, not bytes**. The `bufferOffset` (the second argument) is in bytes
and must be a multiple of 4, and the total size written must be a multiple of 4 bytes.

You can also upload from a `ByteBuffer`. The bytes from its position up to its limit are used, and the position and
limit are not changed:

```java
ByteBuffer indexData = ByteBuffer.allocateDirect(8).order(ByteOrder.LITTLE_ENDIAN);
indexData.putShort((short) 0).putShort((short) 1).putShort((short) 2).putShort((short) 0).flip();
queue.writeBuffer(indices, 0, indexData);
```

GPUs read numbers in **little-endian** byte order, and Java's `ByteBuffer` defaults to big-endian, so always call
`.order(ByteOrder.LITTLE_ENDIAN)` on buffers you fill with multi-byte values. (The array overloads take care of this
for you.) A **direct** `ByteBuffer` is passed without a copy on desktop, Android, and the web, so it is the fastest
choice for large or frequent uploads; keep it in a field and refill it.

## Reading a buffer back with mapAsync

To read GPU data from Java, copy it into a buffer with `MAP_READ | COPY_DST` usage, then **map** it. Mapping is
asynchronous, because the GPU may still be writing:

```java
private final float[] results = new float[64];
private final GpuBufferMapCallback onMapped = this::onMapped;

void startReadback() {
  readback.mapAsync(GpuMapMode.READ, 0, readback.getSize(), GpuCallbackMode.ALLOW_PROCESS_EVENTS, onMapped);
}

private void onMapped(GpuMapAsyncStatus status, String message) {
  if (status != GpuMapAsyncStatus.SUCCESS) {
    System.out.println("Map failed (" + status + "): " + message);
    return;
  }
  readback.readMappedRange(0, results, 0, results.length);
  readback.unmap();
  // Use the results.
}
```

While a buffer is mapped, the GPU cannot use it, so `unmap()` as soon as you are done. `buffer.getMapState()` tells you
whether a buffer is `UNMAPPED`, `PENDING`, or `MAPPED`.

There are two ways to get at mapped memory:

- **`readMappedRange(...)` and `writeMappedRange(...)`** copy between the mapping and a Java array or `ByteBuffer`.
  They work on **every** platform, including the web. Prefer them.
- **`getMappedRange(offset, size)` and `getConstMappedRange(offset, size)`** return a `ByteBuffer` that is a view of the
  mapped memory itself (the "const" one is read-only). They avoid one copy on desktop and Android, but they **throw on
  the web**, and the `ByteBuffer` becomes invalid after `unmap()`. Never keep it.

## Buffers that start mapped

To fill a buffer once at creation (static geometry, for example), create it already mapped. This works with any usage,
even without `MAP_WRITE`:

```java
GpuBufferDescriptor descriptor = new GpuBufferDescriptor();
descriptor.usage = GpuBufferUsage.VERTEX;
descriptor.size = corners.length * Float.BYTES;
descriptor.mappedAtCreation = true;
GpuBuffer vertices = device.createBuffer(descriptor);
vertices.writeMappedRange(0, corners, 0, corners.length);
vertices.unmap();
```

## Creating a texture

```java
GpuTextureDescriptor descriptor = new GpuTextureDescriptor();
descriptor.label = "sprite sheet";
descriptor.usage = GpuTextureUsage.TEXTURE_BINDING | GpuTextureUsage.COPY_DST;
descriptor.dimension = GpuTextureDimension.D2;
descriptor.size.width = 256;
descriptor.size.height = 256;
descriptor.size.depthOrArrayLayers = 1;
descriptor.format = GpuTextureFormat.RGBA8_UNORM;
GpuTexture texture = device.createTexture(descriptor);
```

The important fields:

- `dimension` is `D1`, `D2`, or `D3`. Most textures are `D2`.
- `size` is the width, height, and either the depth (for `D3`) or the number of array layers (for `D2` arrays and cube
  maps, which have 6 layers).
- `format` is the pixel format. `RGBA8_UNORM` (8 bits per channel, read as 0.0 to 1.0) is the everyday choice;
  `RGBA8_UNORM_SRGB` stores colors in sRGB; `RGBA16_FLOAT` and `RGBA32_FLOAT` hold high dynamic range values;
  `DEPTH24_PLUS` and `DEPTH32_FLOAT` are depth buffers.
- `mipLevelCount` (default 1) and `sampleCount` (default 1, or 4 for multisampling).

`texture.getWidth()`, `getHeight()`, `getFormat()`, and the other getters read the values back.

## Uploading to a texture

`queue.writeTexture(...)` uploads pixels. It needs to know where in the texture the pixels go, how your data is laid
out, and how big the region is:

```java
private final GpuTexelCopyTextureInfo destination = new GpuTexelCopyTextureInfo();
private final GpuTexelCopyBufferLayout layout = new GpuTexelCopyBufferLayout();
private final GpuExtent3D extent = new GpuExtent3D();

void upload(GpuTexture texture, byte[] pixels, int width, int height) {
  destination.texture = texture;
  destination.aspect = GpuTextureAspect.ALL;
  layout.offset = 0;
  layout.bytesPerRow = width * 4;   // 4 bytes per RGBA8 pixel.
  layout.rowsPerImage = height;
  extent.width = width;
  extent.height = height;
  extent.depthOrArrayLayers = 1;
  queue.writeTexture(destination, pixels, 0, pixels.length, layout, extent);
}
```

`destination.mipLevel` and `destination.origin` choose the mip level and the corner to start at. `writeTexture`
accepts any `bytesPerRow` that fits the data. (Copies on the GPU are stricter, see below.)

## Texture views

Shaders and render passes never use a texture directly. They use a **view**: a window onto part of a texture, with a
format and dimension. For the whole texture, pass `null`:

```java
GpuTextureView view = texture.createView(null);
```

A `GpuTextureViewDescriptor` picks a part: `baseMipLevel` and `mipLevelCount`, `baseArrayLayer` and
`arrayLayerCount`, `aspect` (for example `DEPTH_ONLY`), and `dimension` (for example `CUBE` to view six layers as a
cube map).

## Samplers

A sampler tells a shader how to read a texture between pixels and at its edges:

```java
GpuSamplerDescriptor descriptor = new GpuSamplerDescriptor();
descriptor.magFilter = GpuFilterMode.LINEAR;       // Smooth when magnified. NEAREST keeps pixel art crisp.
descriptor.minFilter = GpuFilterMode.LINEAR;
descriptor.mipmapFilter = GpuMipmapFilterMode.LINEAR;
descriptor.addressModeU = GpuAddressMode.REPEAT;   // Tile horizontally. CLAMP_TO_EDGE stops at the border.
descriptor.addressModeV = GpuAddressMode.REPEAT;
GpuSampler sampler = device.createSampler(descriptor);
```

`device.createSampler(null)` gives you the defaults (nearest filtering, clamped edges). Samplers are small and can be
shared by many textures.

## Copies between buffers and textures

A command encoder can copy on the GPU, as part of the commands you submit:

- `encoder.copyBufferToBuffer(source, sourceOffset, destination, destinationOffset, size)`
- `encoder.copyBufferToTexture(sourceInfo, destinationInfo, size)`
- `encoder.copyTextureToBuffer(sourceInfo, destinationInfo, size)`
- `encoder.copyTextureToTexture(sourceInfo, destinationInfo, size)`
- `encoder.clearBuffer(buffer, offset, size)` fills a range with zeros.

Reading rendered pixels back is a copy from the texture into a `MAP_READ` buffer, then a map:

```java
GpuTexelCopyTextureInfo source = new GpuTexelCopyTextureInfo();
source.texture = target;
source.aspect = GpuTextureAspect.ALL;

GpuTexelCopyBufferInfo destination = new GpuTexelCopyBufferInfo();
destination.buffer = readback;
destination.layout.bytesPerRow = 256;   // Must be a multiple of 256 for copies between textures and buffers.
destination.layout.rowsPerImage = 64;

GpuExtent3D extent = new GpuExtent3D();
extent.width = 64;
extent.height = 64;
encoder.copyTextureToBuffer(source, destination, extent);
```

For copies between textures and buffers on the GPU, **`bytesPerRow` must be a multiple of 256**. A 64-pixel-wide
RGBA8 image has 256 bytes per row, which happens to fit; a 100-pixel-wide one (400 bytes) needs rows padded to 512
bytes, and you skip the padding when you read the pixels.

## Freeing memory

- `release()` drops your reference; the memory is freed when nothing uses it anymore.
- `destroy()` on a buffer or texture frees its GPU memory right away. Use it for big resources you know you are done
  with, then `release()` the wrapper.

## Common mistakes

- **Missing a usage flag.** `queue.writeBuffer(...)` needs `COPY_DST`, drawing into a texture needs
  `RENDER_ATTACHMENT`, sampling needs `TEXTURE_BINDING`. The uncaptured error callback names the missing usage.
- **Counting bytes where elements are expected.** The array overloads count elements; `bufferOffset` and buffer sizes
  are in bytes.
- **Big-endian `ByteBuffer`s.** Use `.order(ByteOrder.LITTLE_ENDIAN)`.
- **Reading a buffer that is not mapped**, or forgetting `unmap()` before the GPU uses it again.
- **`bytesPerRow` not a multiple of 256** in a texture-to-buffer copy.
- **Using `getMappedRange(...)` in code that also runs on the web.** Use `readMappedRange(...)` and
  `writeMappedRange(...)`.

## Check your understanding

1. You want to draw from a buffer and update it from Java every frame. Which usage flags does it need?
2. Why can a `MAP_READ` buffer not also be a `STORAGE` buffer, and how do you get compute results back anyway?
3. Your texture is 300 pixels wide in `RGBA8_UNORM`. What `bytesPerRow` do you use to copy it into a buffer?

(Answers: `VERTEX | COPY_DST`, uploading with `queue.writeBuffer(...)`; `MAP_READ` may only be combined with `COPY_DST`,
so you compute into a `STORAGE | COPY_SRC` buffer, copy it into a `MAP_READ | COPY_DST` buffer, and map that one;
300 times 4 is 1200 bytes, rounded up to the next multiple of 256, which is 1280.)

**Next:** [Shaders and Pipelines](shaders-and-pipelines.md)
