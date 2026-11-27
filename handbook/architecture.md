# Architecture for Users: The API, the Backends, and the Names

This guide explains how jwebgpu is put together from the point of view of someone who uses it: how one API reaches
three platforms, why WebGPU objects are Java wrappers, and the naming rules that let you translate any WebGPU example
(from MDN, the specification, or `webgpu.h`) into jwebgpu code. For the inside story (the generator and how each
backend marshals calls), read [ARCHITECTURE.md](../ARCHITECTURE.md).

## Table of contents

1. [One API, three backends](#one-api-three-backends)
2. [Why the bindings are generated](#why-the-bindings-are-generated)
3. [Objects are typed wrappers](#objects-are-typed-wrappers)
4. [Descriptors: forms you fill in](#descriptors-forms-you-fill-in)
5. [Arrays inside descriptors: capacity and count](#arrays-inside-descriptors-capacity-and-count)
6. [Extension structs and chains](#extension-structs-and-chains)
7. [Enums, bit flags, and sentinel values](#enums-bit-flags-and-sentinel-values)
8. [Output structs: objects the API fills for you](#output-structs-objects-the-api-fills-for-you)
9. [Translating a WebGPU example](#translating-a-webgpu-example)
10. [Lifetimes: release and addRef](#lifetimes-release-and-addref)
11. [Check your understanding](#check-your-understanding)

## One API, three backends

```
                    your program
                         |
                    jwebgpu-api            <- platform-neutral; safe on desktop, Android, and web
         _______________|_______________
        |               |               |
  jwebgpu-ffm      jwebgpu-jni      jwebgpu-teavm
 (desktop, FFM)   (Android, JNI)   (web, TeaVM)
        |               |               |
      Dawn            Dawn         navigator.gpu
```

`jwebgpu-api` holds one abstract class per WebGPU object (`GpuDevice`, `GpuQueue`, `GpuBuffer`, and so on), plus the
descriptors, enums, flags, and callbacks. It contains no FFM, JNI, or JavaScript types, so it compiles and runs on every
platform. Each backend implements those classes with its own technology.

The hotel analogy from [Getting Started](getting-started.md#installing-a-backend) still applies: `JWebGpu` is the front
desk, and the backend is the hotel behind it. You talk to the front desk once, to get an instance. After that, every
object you get back was made by the backend itself, so each call goes straight to the backend's code. Only one backend
is ever installed, so the Java just-in-time compiler (and TeaVM) can inline those calls: the abstraction costs nothing
per frame.

## Why the bindings are generated

WebGPU has roughly 200 functions and 150 structs. Writing three binding layers by hand would let them drift apart.
Instead, jwebgpu generates the API and every backend at build time from pinned copies of the official
machine-readable specifications (in `spec/` of the repository): `webgpu.json`, the source of truth behind `webgpu.h`,
and the browser's WebIDL. What this means for you:

- **The API is complete.** Every function, struct, enum, and constant of `webgpu.h` at the pinned version has a Java
  counterpart.
- **The Javadoc comes from the specification.** Hover over a field in your IDE and you see what the specification
  says about it.
- **The platforms behave the same.** Desktop and Android share Dawn, and the browser follows the same specification.
  The few calls a browser cannot offer are listed in the [Platform Guide](platforms.md#what-the-web-does-not-support).

## Objects are typed wrappers

Every WebGPU object is a Java object of its own type (`GpuTexture`, `GpuBuffer`) instead of a raw number:

- **The web has no pointers.** On desktop and Android a WebGPU object is a C pointer; in the browser it is a
  JavaScript object. A wrapper hides the difference, so one API works everywhere.
- **The compiler catches mistakes.** Passing a buffer where a texture is expected is a compile error, not a GPU crash.
- **The API is discoverable.** `texture.createView(descriptor)` is easier to find than a free function.
- **Release is safe.** `release()` can be called twice, and a released object throws an `IllegalStateException`
  whose message says it was released, instead of crashing the process.

Methods drop the object's name from the C function: `wgpuDeviceCreateBuffer(device, &desc)` becomes
`device.createBuffer(descriptor)`. Every method that returns an object also has an overload that **refills an existing
wrapper**, such as `device.createCommandEncoder(descriptor, encoder)`, so per-frame code allocates nothing. See
[Garbage-Free Usage](garbage-free.md).

## Descriptors: forms you fill in

A descriptor is like a paper form: it has a field for every option, most fields come pre-filled with sensible
defaults, and you fill in only what you need. In jwebgpu, descriptors are mutable classes with public fields:

```java
GpuTextureDescriptor descriptor = new GpuTextureDescriptor();
descriptor.label = "color target";
descriptor.usage = GpuTextureUsage.RENDER_ATTACHMENT | GpuTextureUsage.COPY_SRC;
descriptor.dimension = GpuTextureDimension.D2;
descriptor.size.width = 256;
descriptor.size.height = 256;
descriptor.format = GpuTextureFormat.RGBA8_UNORM;
GpuTexture texture = device.createTexture(descriptor);
```

The rules:

- The constructor applies the defaults of the C header (for example `mipLevelCount = 1` and `sampleCount = 1` on a
  texture). `reset()` restores them, so one descriptor can be reused for many objects.
- A nested struct that is always present is a `final` field that already exists (`descriptor.size`,
  `pipelineDescriptor.vertex`, `colorAttachment.clearValue`). You fill it in; you never replace it.
- A nested struct that is optional is a nullable field (`pipelineDescriptor.fragment`,
  `passDescriptor.depthStencilAttachment`). You create it once and assign it.
- A field marked `@Nullable` may be left `null`. Many `create...` methods also accept a `null` descriptor for "all
  defaults", such as `texture.createView(null)` and `device.createCommandEncoder(null)`.
- Descriptors are copied when you pass them. After `device.createTexture(descriptor)` returns, you are free to change
  or reset the descriptor.

## Arrays inside descriptors: capacity and count

Lists inside a descriptor (color attachments, bind group entries, vertex buffers, required features) are a
**fixed-capacity array plus an `int` count**. The array never grows by itself, which is what keeps descriptors
garbage-free. The constructor takes the capacities, and the no-argument constructor picks a small default:

```java
GpuRenderPassDescriptor pass = new GpuRenderPassDescriptor();        // Room for 1 color attachment.
GpuRenderPassDescriptor mrt = new GpuRenderPassDescriptor(4);        // Room for 4.
GpuBindGroupDescriptor group = new GpuBindGroupDescriptor();         // Room for 4 entries.
GpuDeviceDescriptor device = new GpuDeviceDescriptor(2);             // Room for 2 required features.
```

When the elements are structs, the constructor creates them too, so you fill `pass.colorAttachments[0]` directly. When
they are objects or enums (such as `GpuPipelineLayoutDescriptor.bindGroupLayouts` or
`GpuDeviceDescriptor.requiredFeatures`), you store your values in the array. Then set the count to how many are in use:

```java
pass.colorAttachmentCount = 1;
GpuRenderPassColorAttachment color = pass.colorAttachments[0];
color.view = view;
color.loadOp = GpuLoadOp.CLEAR;
color.storeOp = GpuStoreOp.STORE;
```

Only the first `count` elements are sent; the rest of the array is ignored.

## Extension structs and chains

WebGPU extends descriptors without changing them by **chaining** extra structs onto them, like stapling an extra page
to a form. In jwebgpu:

- An extensible descriptor has a `nextInChain` field.
- An extension struct extends `GpuChainedStruct`, which has its own `next` field, so you can staple more than one page.

The most common chain is the shader source, which is how a shader module learns that its code is WGSL:

```java
GpuShaderSourceWGSL wgsl = new GpuShaderSourceWGSL();
wgsl.code = source;
GpuShaderModuleDescriptor descriptor = new GpuShaderModuleDescriptor();
descriptor.nextInChain = wgsl;
GpuShaderModule shader = device.createShaderModule(descriptor);
```

Other chains you will meet: the window handle of a surface (`GpuSurfaceSourceWindowsHWND` and friends, see
[Surfaces and Presenting](surfaces-and-presenting.md)), the compatibility limits on `GpuLimits`
(`GpuCompatibilityModeLimits`, see [Compatibility Mode](compatibility-mode.md)), and the texture binding view dimension
on a texture descriptor (`GpuTextureBindingViewDimension`). The Javadoc of each descriptor names the extension structs
that can be chained onto it.

## Enums, bit flags, and sentinel values

- **Enums** are Java enums named after the C enum, with UPPER_SNAKE_CASE constants: `GpuTextureFormat.RGBA8_UNORM`,
  `GpuLoadOp.CLEAR`. A constant whose name would start with a digit gets a `D` in front (`GpuTextureDimension.D2`,
  `GpuTextureViewDimension.D2_ARRAY`). Each constant has the native number in `value`, and the static `of(int)` turns
  a number back into a constant, or returns `null` for a number the specification does not know.
- **Enums returned by the API may be `null`.** A status or type that the native library reports with an unknown
  number arrives as `null`, which is why callback parameters such as `status` are `@Nullable`. Compare with `==`
  against the constant you expect, and treat `null` like a failure.
- **Bit flags** are classes of `long` constants that you combine with `|`: `GpuBufferUsage.VERTEX | GpuBufferUsage.COPY_DST`,
  `GpuShaderStage.VERTEX | GpuShaderStage.FRAGMENT`, `GpuColorWriteMask.ALL`.
- **Sentinel values** live in `GpuConstants`: `WHOLE_SIZE` ("the rest of the buffer"), `LIMIT_U32_UNDEFINED` ("no
  particular limit"), `DEPTH_SLICE_UNDEFINED`, `MIP_LEVEL_COUNT_UNDEFINED`, and so on.

## Output structs: objects the API fills for you

Some calls answer by filling an object you pass in, instead of returning a new one. You create the object once and
let the call refill it as often as you like:

```java
GpuAdapterInfo info = new GpuAdapterInfo();
adapter.getInfo(info);

GpuLimits limits = new GpuLimits();
adapter.getLimits(limits);

GpuSupportedFeatures features = new GpuSupportedFeatures();
adapter.getFeatures(features);
for (int i = 0; i < features.featureCount; i++) {
  System.out.println(features.features[i]);
}
```

When an output array is too small for the answer, the backend replaces it with a bigger one (a one-time allocation),
so a reused output struct stops allocating after the first call. Calls like these return a `GpuStatus` where the
specification has one; `GpuStatus.SUCCESS` means the object was filled.

## Translating a WebGPU example

Most WebGPU tutorials are written in JavaScript. The translation is mechanical:

| JavaScript | jwebgpu |
|------------|---------|
| `await navigator.gpu.requestAdapter()` | `instance.requestAdapter(options, mode, callback)` |
| `device.createBuffer({ size: 64, usage: GPUBufferUsage.VERTEX })` | Fill a `GpuBufferDescriptor`, then `device.createBuffer(descriptor)` |
| `format: "rgba8unorm"` | `format = GpuTextureFormat.RGBA8_UNORM` |
| `colorAttachments: [{ view, loadOp: "clear" }]` | `colorAttachmentCount = 1`, then fill `colorAttachments[0]` |
| `code: wgsl` on the shader descriptor | Chain a `GpuShaderSourceWGSL` onto `nextInChain` |
| `device.queue` | `device.getQueue()` |
| `pass.setVertexBuffer(0, buffer)` | `pass.setVertexBuffer(0, buffer, 0, GpuConstants.WHOLE_SIZE)` |
| `pass.draw(3)` | `pass.draw(3, 1, 0, 0)` (every argument is explicit) |
| `buffer.destroy()` | `buffer.destroy()`, and `buffer.release()` when you drop it |

Examples written for `webgpu.h` in C map even more directly: `WGPUTextureFormat_RGBA8Unorm` is
`GpuTextureFormat.RGBA8_UNORM`, `wgpuQueueSubmit(queue, 1, &commands)` is `queue.submit(array, 1)`, and a callback info
struct becomes a callback mode plus a lambda.

## Lifetimes: release and addRef

Native WebGPU objects are reference counted. In jwebgpu:

- `release()` gives up your reference. When the last reference goes, the native object is freed. Calling `release()`
  again does nothing, and `isReleased()` tells you whether the wrapper is empty.
- `addRef()` adds a reference, for when two owners share one object; each owner then calls `release()` once.
- `destroy()` (on buffers, textures, query sets, and devices) frees the GPU memory right away, even while other
  references exist. It is the "I know I am done with this" button for big resources. You still call `release()`
  afterwards to drop the wrapper's reference.
- A refill overload releases the handle the wrapper held before, then stores the new one.

Release objects in roughly the reverse order you created them: per-frame objects every frame, and long-lived objects
(pipelines, buffers, the device, the adapter, the instance) when the program shuts down.

## Check your understanding

1. You want three color attachments in a render pass. What do you change compared with the default descriptor?
2. A callback gives you `status == null`. What does that mean, and how should you treat it?
3. Where does the WGSL source go in a `GpuShaderModuleDescriptor`, and why is it not a plain field?

(Answers: create it with `new GpuRenderPassDescriptor(3)`, fill `colorAttachments[0]` to `[2]`, and set
`colorAttachmentCount = 3`; the native library reported a number the specification does not know, so treat it as a
failure; in a `GpuShaderSourceWGSL` chained onto `nextInChain`, because the shader source is an extension struct, which
lets the same descriptor also take other kinds of source, such as SPIR-V on desktop.)

**Next:** [Instances, Adapters, and Devices](instances-adapters-and-devices.md)
