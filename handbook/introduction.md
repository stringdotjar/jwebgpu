# What jwebgpu Is: The Mental Model

This guide explains what jwebgpu is, who it is for, and the handful of ideas that WebGPU and these bindings are built
on. Read it first. Every other guide in this handbook assumes you know the picture painted here.

## Table of contents

1. [jwebgpu in one paragraph](#jwebgpu-in-one-paragraph)
2. [Why WebGPU, and why bindings](#why-webgpu-and-why-bindings)
3. [The one analogy to remember: a restaurant kitchen](#the-one-analogy-to-remember-a-restaurant-kitchen)
4. [The five big ideas](#the-five-big-ideas)
5. [How a frame feels from the inside](#how-a-frame-feels-from-the-inside)
6. [The rules of the house](#the-rules-of-the-house)
7. [What runs where: platforms and modules](#what-runs-where-platforms-and-modules)
8. [A map of this handbook](#a-map-of-this-handbook)
9. [Check your understanding](#check-your-understanding)

## jwebgpu in one paragraph

jwebgpu lets a Java program use the graphics card (the GPU) through [WebGPU](https://www.w3.org/TR/webgpu/), the
modern, portable GPU API. You write your rendering code once, against one platform-neutral API (`jwebgpu-api`), and a
small backend module connects it to the platform: on desktop and Android it calls Google's
[Dawn](https://dawn.googlesource.com/dawn) through the standard `webgpu.h` C interface, and in the browser it calls the
browser's own `navigator.gpu`. The bindings are generated from pinned copies of the WebGPU specifications, so the three
platforms never drift apart, and they are designed so that code that runs every frame creates no garbage.

## Why WebGPU, and why bindings

Graphics cards are fast because they do thousands of small jobs at the same time. Getting work to them is hard,
though: every operating system has its own API (Direct3D on Windows, Metal on Apple platforms, Vulkan on Linux and
Android, OpenGL almost everywhere), and each one has its own rules and quirks.

WebGPU is one API that sits on top of all of them. It was designed for the web, so it is safe (a mistake gives you a
clear error message instead of a crash), portable, and much simpler than Vulkan or Direct3D 12, while still being
modern and fast. Dawn is the WebGPU implementation inside Chrome, and it also works outside the browser as a normal
native library.

Java cannot call C libraries or browser APIs by itself, so it needs **bindings**: Java classes that forward each call
to the real implementation. jwebgpu is those bindings, for every platform at once.

## The one analogy to remember: a restaurant kitchen

The easiest way to understand how the objects of WebGPU fit together is to picture a restaurant kitchen. Your program
is the restaurant owner. The GPU is the kitchen, full of fast cooks who never talk back but follow written orders
exactly.

| In the kitchen... | In jwebgpu... | What it does |
|-------------------|---------------|--------------|
| The phone book of kitchens in town | `GpuInstance` | The starting point. You create one and use it to find a GPU. |
| One particular kitchen | `GpuAdapter` | A real GPU (or a software one). It tells you what it can do. |
| Your contract with that kitchen | `GpuDevice` | Your private connection to the adapter. Everything you create comes from it. |
| The order window | `GpuQueue` | Where finished orders are handed in. The kitchen works through them in order. |
| Writing an order ticket | `GpuCommandEncoder` | Records commands. Nothing happens on the GPU while you write. |
| A finished, sealed ticket | `GpuCommandBuffer` | A recorded list of commands, ready to submit. |
| A recipe card | `GpuRenderPipeline`, `GpuComputePipeline` | The shaders and fixed settings for one kind of work. |
| The pantry | `GpuBuffer`, `GpuTexture` | Memory on the GPU: vertices, colors, images, results. |
| The tray of ingredients for one dish | `GpuBindGroup` | Which buffers, textures, and samplers a recipe uses. |
| One cooking session at one counter | A render pass or compute pass | A stretch of drawing into some textures, or of computing. |
| The serving hatch | `GpuSurface` | Where finished pictures go to be shown in a window or canvas. |

Keep this picture in mind. Almost every question has an answer in it:

- You never cook yourself. You **write orders** (encode commands) and **hand them in** (submit them to the queue).
- Writing an order is cheap and happens on your thread. Cooking happens later, on the GPU, at its own pace.
- If you want something back from the kitchen (pixels, results), you have to **wait for it**, because the kitchen is
  busy. That is why some calls are asynchronous and finish in a callback.

## The five big ideas

### 1. Start at the instance, end at a device

Everything begins with `JWebGpu.createInstance()`. From the instance you request an adapter, and from the adapter you
request a device. Once you have a device, you rarely touch the instance or the adapter again, except to pump events.
See [Instances, Adapters, and Devices](instances-adapters-and-devices.md).

```java
GpuInstance instance = JWebGpu.createInstance();
instance.requestAdapter(null, GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, adapter, message) -> {
  // Keep the adapter, then request a device from it.
});
```

### 2. Describe things with descriptors

WebGPU objects are created from **descriptors**: plain objects with public fields that describe what you want. In
jwebgpu, descriptors are mutable Java classes that you create once and fill in:

```java
GpuBufferDescriptor descriptor = new GpuBufferDescriptor();
descriptor.label = "vertices";
descriptor.usage = GpuBufferUsage.VERTEX | GpuBufferUsage.COPY_DST;
descriptor.size = 1024;
GpuBuffer vertices = device.createBuffer(descriptor);
```

The constructor of every descriptor fills in the defaults of the specification, and `reset()` puts them back. See
[Architecture for Users](architecture.md).

### 3. Record, then submit

You do not draw directly. You record commands into an encoder, finish it into a command buffer, and submit that to the
queue:

```java
GpuCommandEncoder encoder = device.createCommandEncoder(null);
// ... record a render pass or a compute pass ...
GpuCommandBuffer commands = encoder.finish(null);
queue.submit(new GpuCommandBuffer[] { commands }, 1);
```

(Per-frame code keeps the array in a field instead of creating it every time; see idea 5.) See
[Render Passes](render-passes.md).

### 4. Some answers arrive later

Requesting an adapter or a device, reading a buffer back, and a few other calls are **asynchronous**: they return at
once and call your callback when the answer is ready. On desktop and Android you decide when callbacks may run (usually
when you call `instance.processEvents()`); in the browser they run when the browser's event loop gets to them. See
[Asynchronous Calls and Callbacks](async-and-callbacks.md).

### 5. Per-frame code creates no garbage

Java's garbage collector is fast, but a collection in the middle of a frame can still cause a visible stutter. jwebgpu
is designed so that a frame of rendering allocates nothing on the Java heap: you keep descriptors in fields and refill
them, and every method that returns an object has an overload that refills an existing wrapper instead of creating a
new one:

```java
device.createCommandEncoder(null, encoder);   // Reuses the encoder wrapper. Nothing is allocated.
```

See [Garbage-Free Usage](garbage-free.md).

## How a frame feels from the inside

Here is what a typical frame of a program that draws to a window does, in order:

1. **Pump events.** `instance.processEvents()` lets finished asynchronous work (a buffer read, a lost device) call
   back into your program. (In the browser this is not needed.)
2. **Get this frame's picture.** `surface.getCurrentTexture(frame)` hands you the texture that the window will show
   next, and you create a view of it.
3. **Update data.** `queue.writeBuffer(...)` uploads anything that changed, such as a camera matrix.
4. **Record.** Create an encoder, begin a render pass that clears and draws into the view, set the pipeline and the
   bind groups, draw, and end the pass.
5. **Submit.** Finish the encoder and submit the command buffer to the queue.
6. **Present.** `surface.present()` shows the picture.
7. **Release** the per-frame wrappers, so the native objects behind them are freed promptly.

The [Surfaces and Presenting](surfaces-and-presenting.md) and [Render Passes](render-passes.md) guides build this
loop step by step.

## The rules of the house

- **Install a backend first.** Call `JWebGpuFfm.install()` (desktop), `JWebGpuJni.install()` (Android), or
  `JWebGpuTeaVm.install()` (web) once, before anything else. jwebgpu never finds a backend by itself.
- **Release what you create.** Every WebGPU object has `release()`. Release objects when you are done with them;
  `release()` is safe to call twice.
- **Keep the backend out of your rendering code.** Only the launcher of each platform names a backend class. The
  rest of your program uses `jwebgpu-api` only, so it runs everywhere.
- **Do not allocate per frame.** Create descriptors, arrays, and callbacks once, keep them in fields, and refill them.
- **Do not block in the browser.** The web cannot wait, so continue your work in callbacks there.

## What runs where: platforms and modules

| Module | What it is | Where it runs |
|--------|------------|---------------|
| `jwebgpu-api` | The platform-neutral API. Your rendering code uses only this. | Everywhere |
| `jwebgpu-ffm` | The desktop backend, built on Java's Foreign Function and Memory API. | Windows, macOS, Linux |
| `jwebgpu-natives-<target>` | The Dawn library for one desktop operating system and CPU. | Desktop |
| `jwebgpu-jni` | The Android backend (an AAR with its native libraries). | Android 8.0 and newer, 64-bit |
| `jwebgpu-teavm` | The web backend, which wraps `navigator.gpu` through TeaVM. | Browsers with WebGPU |

The [Getting Started](getting-started.md) guide shows which ones to add for each platform, and the
[Platform Guide](platforms.md) covers the differences.

## A map of this handbook

- **Part 1, Foundations:** this guide, [Getting Started](getting-started.md), and
  [Architecture for Users](architecture.md).
- **Part 2, Talking to the GPU:** [Instances, Adapters, and Devices](instances-adapters-and-devices.md),
  [Asynchronous Calls and Callbacks](async-and-callbacks.md), and [Surfaces and Presenting](surfaces-and-presenting.md).
- **Part 3, Drawing and computing:** [Buffers and Textures](buffers-and-textures.md),
  [Shaders and Pipelines](shaders-and-pipelines.md), [Render Passes](render-passes.md), and [Compute](compute.md).
- **Part 4, Robust and fast programs:** [Error Handling and Device Loss](errors-and-device-loss.md) and
  [Garbage-Free Usage](garbage-free.md).
- **Part 5, Platforms:** the [Platform Guide](platforms.md) and
  [Compatibility Mode and Backend Selection](compatibility-mode.md).

## Check your understanding

1. In the kitchen analogy, what is the difference between a `GpuCommandEncoder` and a `GpuCommandBuffer`?
2. Why does requesting an adapter finish in a callback instead of returning the adapter directly?
3. Which part of your program should call `JWebGpuFfm.install()`, and why only that part?

(Answers: the encoder is the order ticket you are still writing, and the command buffer is the finished ticket you hand
in with `queue.submit(...)`; finding and starting a GPU can take time, and the browser cannot block, so the answer
arrives later; the desktop launcher, because everything else should use only `jwebgpu-api` so it runs on every
platform.)

**Next:** [Getting Started](getting-started.md)
