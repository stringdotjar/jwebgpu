# Glossary

Plain-language definitions of the terms used throughout this handbook, in alphabetical order. Each entry links to the
guide that covers it.

## Table of contents

[A](#a) | [B](#b) | [C](#c) | [D](#d) | [E](#e) | [F](#f) | [G](#g) | [I](#i) | [J](#j) | [L](#l) | [M](#m) |
[N](#n) | [O](#o) | [P](#p) | [Q](#q) | [R](#r) | [S](#s) | [T](#t) | [U](#u) | [V](#v) | [W](#w)

## A

**Adapter (`GpuAdapter`).** One GPU (or software renderer) found through the instance. You read its info, features,
and limits, and request a device from it. [Instances, Adapters, and Devices](instances-adapters-and-devices.md)

**`ALLOW_PROCESS_EVENTS`.** The callback mode that runs callbacks inside `instance.processEvents()` or `waitAny(...)`, on
the calling thread. [Asynchronous Calls and Callbacks](async-and-callbacks.md#the-three-callback-modes)

**`ALLOW_SPONTANEOUS`.** The callback mode that lets a callback run at any time, on any thread.
[Asynchronous Calls and Callbacks](async-and-callbacks.md#the-three-callback-modes)

**Allocation.** Creating a Java object with `new`. Allocations in per-frame code create garbage.
[Garbage-Free Usage](garbage-free.md)

**Attachment.** A texture view that a render pass draws into: a color attachment, or a depth-stencil attachment.
[Render Passes](render-passes.md)

## B

**Backend.** The platform-specific module that implements `jwebgpu-api`: `jwebgpu-ffm` (desktop), `jwebgpu-jni`
(Android), or `jwebgpu-teavm` (web). [Architecture for Users](architecture.md)

**Backend type (`GpuBackendType`).** The native API under a Dawn adapter: Vulkan, Metal, Direct3D 12, Direct3D 11,
OpenGL, OpenGL ES, or Null. [Compatibility Mode](compatibility-mode.md#choosing-a-backend-type)

**Bind group (`GpuBindGroup`).** A set of resources (buffers, texture views, samplers) that a pipeline uses, bound with
`setBindGroup(...)`. [Shaders and Pipelines](shaders-and-pipelines.md#bind-groups)

**Bind group layout (`GpuBindGroupLayout`).** The shape of a bind group: which binding numbers hold which kinds of
resources. [Shaders and Pipelines](shaders-and-pipelines.md#bind-group-layouts-and-pipeline-layouts)

**Bit flags.** `long` constants combined with `|`, such as `GpuBufferUsage.VERTEX | GpuBufferUsage.COPY_DST`.
[Architecture for Users](architecture.md#enums-bit-flags-and-sentinel-values)

**Blending.** Mixing a new color with the color already in the target, for transparency.
[Shaders and Pipelines](shaders-and-pipelines.md#blending-and-depth)

**Buffer (`GpuBuffer`).** A block of bytes in GPU memory. [Buffers and Textures](buffers-and-textures.md)

## C

**Callback mode (`GpuCallbackMode`).** When an asynchronous callback may run: `WAIT_ANY_ONLY`, `ALLOW_PROCESS_EVENTS`, or
`ALLOW_SPONTANEOUS`. [Asynchronous Calls and Callbacks](async-and-callbacks.md)

**Chain (`nextInChain`).** A linked list of extension structs attached to a descriptor, such as a WGSL source on a shader
module descriptor. [Architecture for Users](architecture.md#extension-structs-and-chains)

**Clip space.** The coordinates a vertex shader outputs: x and y from -1 to 1 across the target.
[Render Passes](render-passes.md#step-3-the-shader-and-the-pipeline)

**Command buffer (`GpuCommandBuffer`).** A finished recording of commands, ready to submit to the queue.
[Render Passes](render-passes.md)

**Command encoder (`GpuCommandEncoder`).** Records passes and copies into a command buffer.
[Render Passes](render-passes.md)

**Compatibility mode.** A slightly smaller, stricter version of WebGPU that also runs on Direct3D 11, OpenGL, and
OpenGL ES. [Compatibility Mode](compatibility-mode.md)

**Compute pass.** A section of commands that dispatches compute shaders. [Compute](compute.md)

**Core.** The full WebGPU feature set, for Vulkan, Metal, and Direct3D 12 class hardware.
[Compatibility Mode](compatibility-mode.md#feature-levels-core-and-compatibility)

**`CORE_FEATURES_AND_LIMITS`.** The feature that says an adapter can do core, or that a device is core. Requesting it
upgrades a compatibility adapter's device to core. [Compatibility Mode](compatibility-mode.md#did-i-get-core-or-compatibility)

## D

**Dawn.** Google's implementation of WebGPU, used inside Chrome, which jwebgpu calls on desktop and Android.
[Introduction](introduction.md)

**Depth buffer.** A texture that stores how far away each pixel is, so near surfaces hide far ones.
[Render Passes](render-passes.md#adding-a-depth-buffer)

**Descriptor.** A mutable object with public fields that describes something to create, such as a
`GpuBufferDescriptor`. Create it once and refill it. [Architecture for Users](architecture.md#descriptors-forms-you-fill-in)

**Device (`GpuDevice`).** Your connection to an adapter, from which you create every resource.
[Instances, Adapters, and Devices](instances-adapters-and-devices.md#requesting-a-device)

**Device loss.** When a device stops working (driver crash, GPU reset, `destroy()`).
[Error Handling and Device Loss](errors-and-device-loss.md#device-loss)

**Direct `ByteBuffer`.** A `ByteBuffer` whose memory lives outside the Java heap. Uploads from one are passed without a
copy. [Garbage-Free Usage](garbage-free.md#habit-4-upload-from-reused-arrays-or-buffers)

**Dispatch.** Starting a number of compute workgroups with `dispatchWorkgroups(...)`. [Compute](compute.md#dispatching)

## E

**Error scope.** A way to catch GPU errors of one kind around some calls, with `pushErrorScope(...)` and
`popErrorScope(...)`. [Error Handling and Device Loss](errors-and-device-loss.md#error-scopes)

**Extension struct (`GpuChainedStruct`).** An extra struct chained onto a descriptor's `nextInChain` to add options.
[Architecture for Users](architecture.md#extension-structs-and-chains)

## F

**Feature (`GpuFeatureName`).** An optional capability of an adapter, which you must request on the device to use.
[Instances, Adapters, and Devices](instances-adapters-and-devices.md#reading-what-an-adapter-can-do)

**Feature level (`GpuFeatureLevel`).** Core or compatibility, chosen when requesting an adapter.
[Compatibility Mode](compatibility-mode.md#feature-levels-core-and-compatibility)

**FFM.** Java's Foreign Function and Memory API, which the desktop backend uses to call Dawn.
[Platform Guide](platforms.md#desktop)

**Format (`GpuTextureFormat`).** How a texture stores each pixel, such as `RGBA8_UNORM` or `DEPTH24_PLUS`.
[Buffers and Textures](buffers-and-textures.md#creating-a-texture)

**Future.** The id of an asynchronous operation, returned by calls such as `requestAdapter(...)`, used with `waitAny`.
[Asynchronous Calls and Callbacks](async-and-callbacks.md#waiting-for-a-future-with-waitany)

## G

**Garbage.** Objects that are no longer used and wait for the garbage collector. Per-frame garbage causes stutters.
[Garbage-Free Usage](garbage-free.md)

## I

**Instance (`GpuInstance`).** The root object, created with `JWebGpu.createInstance()`, used to find adapters, create
surfaces, and pump events. [Instances, Adapters, and Devices](instances-adapters-and-devices.md#the-instance)

**Invocation.** One run of a shader for one vertex, pixel, or compute element. [Compute](compute.md)

## J

**JNI.** The Java Native Interface, which the Android backend uses to call Dawn. [Platform Guide](platforms.md#android)

## L

**Label.** A name you give an object in its descriptor, used in error messages.
[Error Handling and Device Loss](errors-and-device-loss.md#labels-and-debug-groups)

**Limits (`GpuLimits`).** Maximum sizes and counts, such as the largest texture.
[Instances, Adapters, and Devices](instances-adapters-and-devices.md#reading-what-an-adapter-can-do)

**Load op and store op.** What an attachment starts as at the beginning of a pass (`CLEAR` or `LOAD`) and whether the
result is kept (`STORE` or `DISCARD`). [Render Passes](render-passes.md#step-4-record-and-submit)

## M

**Mapping.** Making a buffer's memory readable or writable from Java, with `mapAsync(...)`.
[Buffers and Textures](buffers-and-textures.md#reading-a-buffer-back-with-mapasync)

## N

**Natives jar.** A `jwebgpu-natives-<target>` module that holds the Dawn library for one desktop platform.
[Getting Started](getting-started.md#adding-the-dependencies)

**Null backend.** A fake Dawn backend that accepts every call and draws nothing, for tests.
[Platform Guide](platforms.md#natives)

## O

**Output struct.** An object that a call fills in for you, such as `GpuAdapterInfo` or `GpuLimits`.
[Architecture for Users](architecture.md#output-structs-objects-the-api-fills-for-you)

## P

**Pipeline.** A render or compute pipeline: shaders plus fixed settings, created once.
[Shaders and Pipelines](shaders-and-pipelines.md)

**Pipeline layout (`GpuPipelineLayout`).** The list of bind group layouts a pipeline uses.
[Shaders and Pipelines](shaders-and-pipelines.md#bind-group-layouts-and-pipeline-layouts)

**Present.** Showing a finished surface texture on the screen with `surface.present()`.
[Surfaces and Presenting](surfaces-and-presenting.md#the-frame-loop)

**Present mode.** How frames are synchronized with the display: `FIFO` (vsync), `MAILBOX`, or `IMMEDIATE`.
[Surfaces and Presenting](surfaces-and-presenting.md#configuring-the-surface)

**`processEvents()`.** The instance call that runs every ready `ALLOW_PROCESS_EVENTS` callback. Does nothing on the web.
[Asynchronous Calls and Callbacks](async-and-callbacks.md#pumping-events-with-processevents)

## Q

**Queue (`GpuQueue`).** Where you submit command buffers and upload data. One per device.
[Instances, Adapters, and Devices](instances-adapters-and-devices.md#the-queue)

## R

**Refill overload.** The version of a method that stores a new object in an existing wrapper instead of creating one,
such as `device.createCommandEncoder(descriptor, encoder)`. [Garbage-Free Usage](garbage-free.md#habit-2-refill-wrappers-instead-of-creating-them)

**Release.** Giving up your reference to a WebGPU object with `release()`. Safe to call twice.
[Architecture for Users](architecture.md#lifetimes-release-and-addref)

**Render pass.** A section of commands that draws into a set of attachments. [Render Passes](render-passes.md)

## S

**Sampler (`GpuSampler`).** Tells a shader how to filter a texture and what to do at its edges.
[Buffers and Textures](buffers-and-textures.md#samplers)

**Scratch memory.** Per-thread native memory that the desktop and Android backends reuse to pass descriptors without
allocating. [Garbage-Free Usage](garbage-free.md#scratch-memory-and-trimscratch)

**Secure context.** A page served over HTTPS or from `localhost`, which WebGPU requires in the browser.
[Platform Guide](platforms.md#web)

**Shader module (`GpuShaderModule`).** Compiled shader code with one or more entry points.
[Shaders and Pipelines](shaders-and-pipelines.md#shader-modules-and-wgsl)

**Storage buffer.** A buffer that shaders can read and write, used heavily in compute. [Compute](compute.md)

**Surface (`GpuSurface`).** A window, Android view, or canvas that WebGPU can present to.
[Surfaces and Presenting](surfaces-and-presenting.md)

## T

**TeaVM.** The Java to JavaScript and WebAssembly compiler that the web backend is built for.
[Platform Guide](platforms.md#web)

**Texture (`GpuTexture`).** An image in GPU memory. [Buffers and Textures](buffers-and-textures.md#creating-a-texture)

**Texture view (`GpuTextureView`).** A window onto part of a texture, which passes and shaders use.
[Buffers and Textures](buffers-and-textures.md#texture-views)

## U

**Uncaptured error callback.** The device callback that receives every GPU error no error scope caught.
[Error Handling and Device Loss](errors-and-device-loss.md#the-uncaptured-error-callback)

**Uniform buffer.** A small, read-only buffer of shader parameters, such as a camera matrix.
[Shaders and Pipelines](shaders-and-pipelines.md#bind-groups)

**Usage.** The bit flags that list everything a buffer or texture will be used for.
[Buffers and Textures](buffers-and-textures.md#usages-telling-the-gpu-what-a-resource-is-for)

## V

**Validation error.** A GPU error that means the program broke a rule of the API.
[Error Handling and Device Loss](errors-and-device-loss.md#the-kinds-of-gpu-errors)

**Vertex buffer.** A buffer of per-vertex data, such as positions and colors.
[Render Passes](render-passes.md#drawing-from-vertex-and-index-buffers)

## W

**`waitAny(...)`.** The instance call that waits for one of several futures. Only polls on the web.
[Asynchronous Calls and Callbacks](async-and-callbacks.md#waiting-for-a-future-with-waitany)

**WasmGC.** WebAssembly with garbage collection, one of TeaVM's two targets. [Platform Guide](platforms.md#web)

**WGSL.** The WebGPU Shading Language. [Shaders and Pipelines](shaders-and-pipelines.md#shader-modules-and-wgsl)

**Workgroup.** A group of compute invocations that run together and can share memory. [Compute](compute.md)

**Wrapper.** The Java object (such as `GpuTexture`) that stands for a native or browser WebGPU object.
[Architecture for Users](architecture.md#objects-are-typed-wrappers)
