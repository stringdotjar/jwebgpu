# Instances, Adapters, and Devices

Before you can draw anything, you need three objects: an instance, an adapter, and a device. This guide explains what
each one is for, how to request them with the options that matter, how to read what a GPU can do, and how to shut
everything down again.

## Table of contents

1. [Why three objects?](#why-three-objects)
2. [The instance](#the-instance)
3. [Requesting an adapter](#requesting-an-adapter)
4. [Reading what an adapter can do](#reading-what-an-adapter-can-do)
5. [Requesting a device](#requesting-a-device)
6. [A startup class that works on every platform](#a-startup-class-that-works-on-every-platform)
7. [The queue](#the-queue)
8. [Shutting down](#shutting-down)
9. [Common mistakes](#common-mistakes)
10. [Check your understanding](#check-your-understanding)

## Why three objects?

Back to the restaurant kitchen from the [Introduction](introduction.md#the-one-analogy-to-remember-a-restaurant-kitchen):

- The **instance** is the phone book. It knows every kitchen in town (every GPU, and software ones too).
- An **adapter** is one kitchen you looked up. You can ask it questions ("how big is your oven?", "do you make
  sushi?"), but you cannot order from it yet.
- A **device** is your contract with that kitchen. You say up front which special services you need (features) and
  how big your orders may get (limits), and the kitchen agrees. From then on, everything you create comes from the
  device.

Why not just one object? Because a computer can have several GPUs (a fast discrete card and a power-saving integrated
one, or a software fallback), and because a program may want to inspect them before it commits. The device is also
the unit of isolation: if one device is lost, other devices and the adapter are not.

## The instance

```java
GpuInstance instance = JWebGpu.createInstance();
```

That is all most programs need. An instance descriptor lets you ask for instance features:

```java
GpuInstanceDescriptor descriptor = new GpuInstanceDescriptor(1);
descriptor.requiredFeatureCount = 1;
descriptor.requiredFeatures[0] = GpuInstanceFeatureName.TIMED_WAIT_ANY;
GpuInstance instance = JWebGpu.createInstance(descriptor);
```

`TIMED_WAIT_ANY` lets `instance.waitAny(...)` wait with a timeout above zero on desktop and Android (see
[Asynchronous Calls and Callbacks](async-and-callbacks.md#waiting-for-a-future-with-waitany)). You can check what the
platform offers with `JWebGpu.hasInstanceFeature(GpuInstanceFeatureName.TIMED_WAIT_ANY)` or fill a
`GpuSupportedInstanceFeatures` with `JWebGpu.getInstanceFeatures(...)`. The browser has no instance features.

The instance also tells you which optional WGSL language features the shader compiler supports:
`instance.hasWGSLLanguageFeature(...)` and `instance.getWGSLLanguageFeatures(...)`.

Create one instance per program and keep it for the program's whole life. You will call `instance.processEvents()` on
it every frame.

## Requesting an adapter

```java
GpuRequestAdapterOptions options = new GpuRequestAdapterOptions();
options.powerPreference = GpuPowerPreference.HIGH_PERFORMANCE;
instance.requestAdapter(options, GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, adapter, message) -> {
  if (status == GpuRequestAdapterStatus.SUCCESS && adapter != null) {
    // Keep it.
  } else {
    System.out.println("No adapter: " + status + " " + message);
  }
});
```

Passing `null` instead of options means "any adapter, with the defaults". The options are:

| Field | What it does |
|-------|--------------|
| `powerPreference` | `HIGH_PERFORMANCE` prefers a discrete GPU, `LOW_POWER` an integrated one, `UNDEFINED` lets the implementation choose. It is a hint. |
| `featureLevel` | `CORE` (the default) or `COMPATIBILITY`, which also accepts older GPUs. See [Compatibility Mode](compatibility-mode.md). |
| `backendType` | Requires a particular native API (`VULKAN`, `METAL`, `D3D12`, `D3D11`, `OPENGL`, `OPENGLES`, or `NULL`, a fake GPU for tests). `UNDEFINED` lets Dawn pick. Ignored by the browser. See [Backend selection](compatibility-mode.md#choosing-a-backend-type). |
| `forceFallbackAdapter` | Requires a "fallback" adapter, usually a software renderer. Useful for tests on machines without a GPU. |
| `compatibleSurface` | Requires an adapter that can present to this surface. Pass the surface you want to draw into, if you already have it. Ignored by the browser. |

If no adapter matches, the callback gets `null` for the adapter and a status other than `SUCCESS` (often
`UNAVAILABLE`), with a message that explains why. Always handle that case: a machine without a usable GPU, or with an
old driver, is common.

## Reading what an adapter can do

Once you have an adapter, you can ask it three kinds of questions. Each answer fills an object you pass in, so create
those objects once.

**Who are you?**

```java
GpuAdapterInfo info = new GpuAdapterInfo();
if (adapter.getInfo(info) == GpuStatus.SUCCESS) {
  System.out.println(info.vendor + " " + info.device + " (" + info.backendType + ", " + info.adapterType + ")");
}
```

`adapterType` is `DISCRETE_GPU`, `INTEGRATED_GPU`, `CPU`, or `UNKNOWN`, and `backendType` is the native API behind the
adapter (the browser does not report it).

**What optional features do you have?**

```java
if (adapter.hasFeature(GpuFeatureName.TIMESTAMP_QUERY)) {
  // We can measure GPU time.
}

GpuSupportedFeatures features = new GpuSupportedFeatures();
adapter.getFeatures(features);
for (int i = 0; i < features.featureCount; i++) {
  System.out.println("feature: " + features.features[i]);
}
```

Features are things a GPU may or may not offer, such as `SHADER_F16`, `TEXTURE_COMPRESSION_BC`, or
`DEPTH32_FLOAT_STENCIL8`. An adapter having a feature is not enough: you must also **request** it on the device.

**How big can things get?**

```java
GpuLimits limits = new GpuLimits();
adapter.getLimits(limits);
System.out.println("largest 2D texture: " + limits.maxTextureDimension2D);
System.out.println("largest buffer: " + limits.maxBufferSize);
```

Limits are numbers such as the largest texture, the number of bind groups, or the largest buffer. A device starts with
the default limits of the specification, which every adapter supports, and you can ask for more (up to what the
adapter reports).

## Requesting a device

```java
GpuDeviceDescriptor descriptor = new GpuDeviceDescriptor(1);
descriptor.label = "main device";
descriptor.defaultQueue.label = "main queue";

// Optional features, only if the adapter has them.
if (adapter.hasFeature(GpuFeatureName.TIMESTAMP_QUERY)) {
  descriptor.requiredFeatures[descriptor.requiredFeatureCount++] = GpuFeatureName.TIMESTAMP_QUERY;
}

// Optional limits. Every field of a new GpuLimits means "the default", so set only what you need.
GpuLimits required = new GpuLimits();
required.maxBufferSize = limits.maxBufferSize;
descriptor.requiredLimits = required;

// Tell me when the device is lost, and about errors I did not catch.
descriptor.deviceLostMode = GpuCallbackMode.ALLOW_PROCESS_EVENTS;
descriptor.deviceLostCallback = (device, reason, message) -> System.out.println("Device lost: " + reason + " " + message);
descriptor.uncapturedErrorCallback = (device, type, message) -> System.out.println("GPU error: " + type + " " + message);

adapter.requestDevice(descriptor, GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, device, message) -> {
  if (status == GpuRequestDeviceStatus.SUCCESS && device != null) {
    // Keep it, and get its queue.
  } else {
    System.out.println("No device: " + status + " " + message);
  }
});
```

What the fields do:

- `requiredFeatures` and `requiredFeatureCount` list the features you need. Asking for one the adapter does not have
  makes the request fail, so check with `adapter.hasFeature(...)` first. The capacity comes from the constructor
  (`new GpuDeviceDescriptor(1)` has room for one).
- `requiredLimits` asks for limits above the defaults. Asking for more than the adapter offers makes the request fail.
- `deviceLostCallback` runs when the device stops working (driver crash, GPU reset, `device.destroy()`, or the program
  shutting down). **Set `deviceLostMode` too whenever you set the callback**; it says when the callback may run (see
  [Asynchronous Calls and Callbacks](async-and-callbacks.md)).
- `uncapturedErrorCallback` receives every validation, out-of-memory, or internal error that no error scope caught. It
  is the single most useful debugging aid in WebGPU, so always set one. See
  [Error Handling and Device Loss](errors-and-device-loss.md).

After you have the device, `device.hasFeature(...)`, `device.getFeatures(...)`, and `device.getLimits(...)` tell you
what it actually got, and `device.getAdapterInfo(...)` describes the adapter behind it.

## A startup class that works on every platform

The browser cannot block, so the portable way to start up is a chain of callbacks: the adapter callback requests the
device, and the device callback says "ready". On desktop and Android you pump `instance.processEvents()` from your main
loop until the chain finishes; on the web the browser runs the callbacks for you and `processEvents()` does nothing,
so the same code works there too.

```java
package com.example;

import io.github.stringdotjar.jwebgpu.GpuAdapter;
import io.github.stringdotjar.jwebgpu.GpuCallbackMode;
import io.github.stringdotjar.jwebgpu.GpuDevice;
import io.github.stringdotjar.jwebgpu.GpuDeviceDescriptor;
import io.github.stringdotjar.jwebgpu.GpuDeviceLostReason;
import io.github.stringdotjar.jwebgpu.GpuErrorType;
import io.github.stringdotjar.jwebgpu.GpuInstance;
import io.github.stringdotjar.jwebgpu.GpuPowerPreference;
import io.github.stringdotjar.jwebgpu.GpuQueue;
import io.github.stringdotjar.jwebgpu.GpuRequestAdapterOptions;
import io.github.stringdotjar.jwebgpu.GpuRequestAdapterStatus;
import io.github.stringdotjar.jwebgpu.GpuRequestDeviceStatus;

/** Requests an adapter and a device, one callback after the other. */
public final class GpuStartup {

  private final GpuRequestAdapterOptions options = new GpuRequestAdapterOptions();
  private final GpuDeviceDescriptor deviceDescriptor = new GpuDeviceDescriptor();
  private final GpuInstance instance;
  private GpuAdapter adapter;
  private GpuDevice device;
  private GpuQueue queue;
  private String failure;

  private boolean finished;

  public GpuStartup(GpuInstance instance) {
    this.instance = instance;
  }

  public void start() {
    options.powerPreference = GpuPowerPreference.HIGH_PERFORMANCE;
    instance.requestAdapter(options, GpuCallbackMode.ALLOW_PROCESS_EVENTS, this::onAdapter);
  }

  private void onAdapter(GpuRequestAdapterStatus status, GpuAdapter result, String message) {
    if (status != GpuRequestAdapterStatus.SUCCESS || result == null) {
      fail("No adapter (" + status + "): " + message);
      return;
    }
    adapter = result;
    deviceDescriptor.label = "main device";
    deviceDescriptor.deviceLostMode = GpuCallbackMode.ALLOW_PROCESS_EVENTS;
    deviceDescriptor.deviceLostCallback = this::onDeviceLost;
    deviceDescriptor.uncapturedErrorCallback = this::onError;
    adapter.requestDevice(deviceDescriptor, GpuCallbackMode.ALLOW_PROCESS_EVENTS, this::onDevice);
  }

  private void onDevice(GpuRequestDeviceStatus status, GpuDevice result, String message) {
    if (status != GpuRequestDeviceStatus.SUCCESS || result == null) {
      fail("No device (" + status + "): " + message);
      return;
    }
    device = result;
    queue = device.getQueue();
    finished = true;
  }

  private void onDeviceLost(GpuDevice lost, GpuDeviceLostReason reason, String message) {
    System.out.println("Device lost (" + reason + "): " + message);
  }

  private void onError(GpuDevice source, GpuErrorType type, String message) {
    System.out.println("GPU error (" + type + "): " + message);
  }

  private void fail(String why) {
    failure = why;
    finished = true;
  }

  public GpuAdapter getAdapter() {
    return adapter;
  }

  public GpuDevice getDevice() {
    return device;
  }

  public GpuQueue getQueue() {
    return queue;
  }

  public String getFailure() {
    return failure;
  }

  public boolean isFinished() {
    return finished;
  }
}
```

A desktop main loop then looks like this:

```java
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
GpuDevice device = startup.getDevice();
```

On the web, do not loop. Instead, check `isFinished()` from your frame callback (for example from
`requestAnimationFrame`), or call your "ready" code directly at the end of `onDevice(...)`.

## The queue

```java
GpuQueue queue = device.getQueue();
```

Every device has exactly one queue. You hand finished command buffers to it with `queue.submit(...)`, and it also
uploads data directly: `queue.writeBuffer(...)` and `queue.writeTexture(...)`. `queue.onSubmittedWorkDone(...)` calls
you back when the GPU has finished everything submitted so far. `getQueue()` returns a new wrapper each time, so call
it once and keep the queue in a field (or use `device.getQueue(queue)` to refill one).

## Shutting down

Release in the reverse order of creation. Long-lived objects first (pipelines, buffers, textures), then:

```java
queue.release();
device.release();
adapter.release();
instance.release();
```

When the device goes away, its device lost callback runs one last time (with a reason such as `DESTROYED` or
`CALLBACK_CANCELLED`), so do not treat every call of it as an emergency. You can also call `device.destroy()` to free
all GPU memory of the device at once, which loses the device on purpose.

## Common mistakes

- **Not handling a `null` adapter.** Machines without a usable GPU exist. Show a clear message instead of a
  `NullPointerException`.
- **Requesting a feature without checking it.** The device request fails. Check `adapter.hasFeature(...)` first.
- **Setting `deviceLostCallback` without `deviceLostMode`.** Always set both.
- **No `uncapturedErrorCallback`.** Errors then only show up in the native log, if at all. Set one from day one.
- **Calling `device.getQueue()` every frame.** Each call creates a new wrapper. Keep the queue in a field.
- **Blocking on the web.** A `while` loop that waits for a callback never ends in the browser, because the callback can
  only run after your code returns.

## Check your understanding

1. Your program needs `SHADER_F16` if available, and works without it otherwise. How do you request the device?
2. What does a new `GpuLimits` mean when you pass it as `requiredLimits` without changing any field?
3. Why does the startup class request the device from inside the adapter callback instead of after `start()` returns?

(Answers: check `adapter.hasFeature(GpuFeatureName.SHADER_F16)`, add it to `requiredFeatures` and increase
`requiredFeatureCount` only if it is there, then remember whether the device has it with `device.hasFeature(...)`;
every field is "undefined", which means "the default limit"; because the adapter only exists once its callback has
run, and on the web that happens after `start()` has already returned.)

**Next:** [Asynchronous Calls and Callbacks](async-and-callbacks.md)
