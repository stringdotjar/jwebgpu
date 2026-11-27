# "How Do I...?" Cookbook

Short answers to common questions, grouped by topic. Each answer links to the guide that explains it fully.

## Table of contents

1. [Setup](#setup)
2. [Adapters and devices](#adapters-and-devices)
3. [Callbacks](#callbacks)
4. [Surfaces](#surfaces)
5. [Buffers and textures](#buffers-and-textures)
6. [Shaders and pipelines](#shaders-and-pipelines)
7. [Drawing](#drawing)
8. [Compute](#compute)
9. [Errors](#errors)
10. [Performance](#performance)
11. [Compatibility and backends](#compatibility-and-backends)

## Setup

**...add jwebgpu to a desktop project?**
`jwebgpu-api` and `jwebgpu-ffm` as `implementation`, plus one `jwebgpu-natives-<target>` per platform as
`runtimeOnly`, all from `io.github.stringdotjar` on Maven Central. ([Getting Started](getting-started.md#adding-the-dependencies))

**...start the backend?**
`JWebGpuFfm.install();` (desktop), `JWebGpuJni.install();` (Android), or `JWebGpuTeaVm.install();` (web), once, before
anything else. ([Getting Started](getting-started.md#installing-a-backend))

**...get rid of the native access warning?**
Run with `--enable-native-access=ALL-UNNAMED`. ([Getting Started](getting-started.md#allowing-native-access-on-the-desktop))

**...use my own build of Dawn?**
`JWebGpuFfm.install(Path.of("/path/to/libwebgpu_dawn.so"));` or `-Djwebgpu.library.path=...`.
([Platform Guide](platforms.md#loading-the-library))

**...check whether the browser has WebGPU?**
`JWebGpuTeaVm.isSupported()`. ([Platform Guide](platforms.md#web))

## Adapters and devices

**...get an adapter?**
`instance.requestAdapter(options, GpuCallbackMode.ALLOW_PROCESS_EVENTS, callback);` and pump
`instance.processEvents()` until the callback runs. ([Instances, Adapters, and Devices](instances-adapters-and-devices.md#requesting-an-adapter))

**...prefer the fast GPU on a laptop?**
`options.powerPreference = GpuPowerPreference.HIGH_PERFORMANCE;`

**...run tests without a GPU?**
`options.backendType = GpuBackendType.NULL;` (desktop and Android). ([Platform Guide](platforms.md#natives))

**...print the GPU's name?**
`adapter.getInfo(info);` then `info.description`, `info.vendor`, and `info.backendType`.

**...check for an optional feature?**
`adapter.hasFeature(GpuFeatureName.TIMESTAMP_QUERY)`, then add it to `requiredFeatures` and increase
`requiredFeatureCount`. ([Requesting a device](instances-adapters-and-devices.md#requesting-a-device))

**...read the GPU's limits?**
`adapter.getLimits(limits);` or `device.getLimits(limits);` into a `GpuLimits` you keep.

## Callbacks

**...make callbacks run on my thread?**
Use `GpuCallbackMode.ALLOW_PROCESS_EVENTS` and call `instance.processEvents()` every frame.
([Asynchronous Calls and Callbacks](async-and-callbacks.md#the-three-callback-modes))

**...wait for one answer with a timeout?**
Create the instance with `GpuInstanceFeatureName.TIMED_WAIT_ANY`, use `WAIT_ANY_ONLY`, and call
`instance.waitAny(waits, 1, timeoutNanos)` with the returned future id. Not on the web.
([Asynchronous Calls and Callbacks](async-and-callbacks.md#waiting-for-a-future-with-waitany))

**...know when the GPU has finished my work?**
`queue.onSubmittedWorkDone(GpuCallbackMode.ALLOW_PROCESS_EVENTS, callback);`

## Surfaces

**...draw into a window on Windows?**
Chain a `GpuSurfaceSourceWindowsHWND` with `hinstance` and `hwnd` onto a `GpuSurfaceDescriptor` and call
`instance.createSurface(...)`. ([Surfaces and Presenting](surfaces-and-presenting.md#creating-a-surface-on-the-desktop))

**...draw into an Android `SurfaceView`?**
`AndroidSurface.create(instance, holder.getSurface())` in `surfaceCreated(...)`, and `release()` in
`surfaceDestroyed(...)`. ([Surfaces and Presenting](surfaces-and-presenting.md#creating-a-surface-on-android))

**...draw into a canvas?**
`descriptor.nextInChain = TeaVmSurfaceSourceCanvas.ofSelector("#canvas");`
([Surfaces and Presenting](surfaces-and-presenting.md#creating-a-surface-on-the-web))

**...pick the surface format?**
`surface.getCapabilities(adapter, capabilities);` then `capabilities.formats[0]`.

**...turn vsync off?**
`configuration.presentMode = GpuPresentMode.IMMEDIATE;` (or `MAILBOX` where supported), then `surface.configure(...)`.

**...handle a window resize?**
Call `surface.configure(...)` again with the new size, and recreate size-dependent textures such as the depth buffer.
([Surfaces and Presenting](surfaces-and-presenting.md#resizing-and-lost-surfaces))

**...take a screenshot?**
Configure the surface with `RENDER_ATTACHMENT | COPY_SRC`, copy the frame's texture into a `MAP_READ` buffer before
presenting, and map it. ([Buffers and Textures](buffers-and-textures.md#copies-between-buffers-and-textures))

## Buffers and textures

**...upload vertices?**
`queue.writeBuffer(buffer, 0, floats, 0, floats.length);` into a buffer with `VERTEX | COPY_DST`.
([Buffers and Textures](buffers-and-textures.md#uploading-to-a-buffer))

**...read a buffer back?**
Copy it into a `MAP_READ | COPY_DST` buffer, `mapAsync(GpuMapMode.READ, ...)`, then `readMappedRange(...)` and
`unmap()`. ([Buffers and Textures](buffers-and-textures.md#reading-a-buffer-back-with-mapasync))

**...fill a buffer once, at creation?**
`descriptor.mappedAtCreation = true;`, then `writeMappedRange(...)` and `unmap()`.

**...upload an image?**
`queue.writeTexture(destination, pixels, 0, pixels.length, layout, extent);` with `bytesPerRow = width * 4` for RGBA8.
([Buffers and Textures](buffers-and-textures.md#uploading-to-a-texture))

**...keep pixel art crisp?**
A sampler with `magFilter = GpuFilterMode.NEAREST`. ([Buffers and Textures](buffers-and-textures.md#samplers))

**...free a big texture right away?**
`texture.destroy();` then `texture.release();`

## Shaders and pipelines

**...load a WGSL shader?**
Chain a `GpuShaderSourceWGSL` with `code` onto `GpuShaderModuleDescriptor.nextInChain`.
([Shaders and Pipelines](shaders-and-pipelines.md#shader-modules-and-wgsl))

**...see shader compile errors with line numbers?**
`shader.getCompilationInfo(mode, callback)` and print `info.messages[i]`.
([Shaders and Pipelines](shaders-and-pipelines.md#checking-compiler-messages))

**...make things transparent?**
Set a `GpuBlendState` with `SRC_ALPHA` and `ONE_MINUS_SRC_ALPHA` on `targets[0].blend`.
([Shaders and Pipelines](shaders-and-pipelines.md#blending-and-depth))

**...share bind groups between pipelines?**
Create explicit bind group layouts and one `GpuPipelineLayout`, and set it as `layout` on each pipeline.
([Shaders and Pipelines](shaders-and-pipelines.md#bind-group-layouts-and-pipeline-layouts))

**...compile pipelines without freezing a loading screen?**
`device.createRenderPipelineAsync(...)`. ([Shaders and Pipelines](shaders-and-pipelines.md#creating-pipelines-in-the-background))

## Drawing

**...clear the screen to a color?**
`loadOp = GpuLoadOp.CLEAR` and `clearValue` on the color attachment. ([Render Passes](render-passes.md#step-4-record-and-submit))

**...draw with an index buffer?**
`setIndexBuffer(indices, GpuIndexFormat.UINT16, 0, GpuConstants.WHOLE_SIZE)` and `drawIndexed(...)`.
([Render Passes](render-passes.md#drawing-from-vertex-and-index-buffers))

**...add a depth buffer?**
A `DEPTH24_PLUS` texture, a `GpuDepthStencilState` on the pipeline, and a `GpuRenderPassDepthStencilAttachment` on the
pass. ([Render Passes](render-passes.md#adding-a-depth-buffer))

**...draw many copies of one mesh?**
`draw(vertexCount, instanceCount, 0, 0)` with a second vertex buffer whose `stepMode` is `GpuVertexStepMode.INSTANCE`.

**...draw into only part of the target?**
`passEncoder.setViewport(...)` or `setScissorRect(...)`.

## Compute

**...run a compute shader?**
`beginComputePass(null)`, `setPipeline(...)`, `setBindGroup(...)`, `dispatchWorkgroups((count + 63) / 64, 1, 1)`,
`end()`. ([Compute](compute.md#dispatching))

**...get compute results back?**
Copy the `STORAGE | COPY_SRC` buffer into a `MAP_READ | COPY_DST` buffer and map it. ([Compute](compute.md#reading-the-result))

## Errors

**...see GPU errors?**
Set `uncapturedErrorCallback` on the device descriptor. ([Error Handling](errors-and-device-loss.md#the-uncaptured-error-callback))

**...catch an out-of-memory error for one call?**
`device.pushErrorScope(GpuErrorFilter.OUT_OF_MEMORY);`, the call, then `device.popErrorScope(...)`.
([Error Handling](errors-and-device-loss.md#error-scopes))

**...know when the device is lost?**
Set `deviceLostCallback` and `deviceLostMode` on the device descriptor. ([Error Handling](errors-and-device-loss.md#device-loss))

**...make error messages clearer?**
Set `label` on every descriptor, and use `pushDebugGroup(...)` in passes.

## Performance

**...avoid garbage every frame?**
Keep descriptors, arrays, and callbacks in fields, and use the refill overloads such as
`device.createCommandEncoder(null, encoder)`. ([Garbage-Free Usage](garbage-free.md))

**...upload big data fast?**
From one reused direct `ByteBuffer` in little-endian order. ([Garbage-Free Usage](garbage-free.md#habit-4-upload-from-reused-arrays-or-buffers))

**...give scratch memory back after a huge upload?**
`JWebGpuFfm.trimScratch();` (or `JWebGpuJni.trimScratch();`) on the same thread. ([Garbage-Free Usage](garbage-free.md#scratch-memory-and-trimscratch))

**...prove a frame allocates nothing?**
Count `getThreadAllocatedBytes(...)` around many frames after a warm-up. ([Garbage-Free Usage](garbage-free.md#measuring-allocations))

## Compatibility and backends

**...run on older GPUs?**
`options.featureLevel = GpuFeatureLevel.COMPATIBILITY;` ([Compatibility Mode](compatibility-mode.md#requesting-a-compatibility-adapter))

**...get core when the hardware can, and compatibility otherwise?**
Request `COMPATIBILITY`, and add `GpuFeatureName.CORE_FEATURES_AND_LIMITS` to the device's required features when the
adapter has it. ([Compatibility Mode](compatibility-mode.md#upgrading-a-compatibility-adapter-to-core))

**...know whether my device is core?**
`device.hasFeature(GpuFeatureName.CORE_FEATURES_AND_LIMITS)`.

**...read the compatibility limits?**
Chain a `GpuCompatibilityModeLimits` onto `GpuLimits.nextInChain` and call `getLimits(...)`.
([Compatibility Mode](compatibility-mode.md#compatibility-limits))

**...force Vulkan, Direct3D 11, or OpenGL ES?**
`options.backendType = GpuBackendType.VULKAN;` (or `D3D11`, or `OPENGLES` together with `featureLevel = COMPATIBILITY`).
([Compatibility Mode](compatibility-mode.md#choosing-a-backend-type))

**...try core first and fall back to compatibility?**
Chain adapter requests from their callbacks. ([Compatibility Mode](compatibility-mode.md#a-fallback-pattern-core-first-then-compatibility))
