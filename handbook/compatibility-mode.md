# Compatibility Mode and Backend Selection

Not every GPU can run all of WebGPU. Older graphics cards, PCs that only have Direct3D 11, and phones whose best API is
OpenGL ES 3.1 can still run a slightly smaller version of it, called **compatibility mode**. This guide explains feature
levels, how to request a compatibility adapter, how to upgrade it to the full feature set when the hardware allows it,
how to choose a native backend, and a fallback pattern that tries the best option first.

## Table of contents

1. [Why compatibility mode exists](#why-compatibility-mode-exists)
2. [The mental model: a full kitchen and a camping stove](#the-mental-model-a-full-kitchen-and-a-camping-stove)
3. [Feature levels: core and compatibility](#feature-levels-core-and-compatibility)
4. [Requesting a compatibility adapter](#requesting-a-compatibility-adapter)
5. [Did I get core or compatibility?](#did-i-get-core-or-compatibility)
6. [Upgrading a compatibility adapter to core](#upgrading-a-compatibility-adapter-to-core)
7. [Compatibility limits](#compatibility-limits)
8. [What compatibility mode restricts](#what-compatibility-mode-restricts)
9. [Choosing a backend type](#choosing-a-backend-type)
10. [OpenGL and OpenGL ES](#opengl-and-opengl-es)
11. [A fallback pattern: core first, then compatibility](#a-fallback-pattern-core-first-then-compatibility)
12. [The browser](#the-browser)
13. [Common mistakes](#common-mistakes)
14. [Check your understanding](#check-your-understanding)

## Why compatibility mode exists

WebGPU was designed around modern native APIs: Vulkan, Metal, and Direct3D 12. That is what the full feature set,
called **core**, assumes. But many machines in use today do not have one of those APIs, or have a driver too buggy to
trust:

- Older Windows PCs and virtual machines that only have Direct3D 11.
- Android phones with no Vulkan driver, or a broken one, where OpenGL ES 3.1 is the reliable option.
- Linux machines with old GPUs that only have OpenGL.

Compatibility mode is a version of WebGPU that is a little smaller and a little stricter, so that it can run on those
older APIs too. A program written for compatibility mode runs on more hardware; a program written for core gets every
feature, but only on newer hardware.

## The mental model: a full kitchen and a camping stove

Think of core as a full restaurant kitchen, and compatibility mode as a good camping stove. The camping stove cooks
almost every recipe, but it has fewer burners (lower limits) and a couple of things it cannot do (extra rules). A cook
who knows the stove's rules can make the same meal anywhere.

Two things follow from that picture:

- A recipe that works on the camping stove also works in the full kitchen. A compatibility-mode program is a valid
  core program.
- If you ask for a camping stove and the place happens to have a full kitchen, you can ask to use the full kitchen
  instead. That is the "upgrade to core" described below.

## Feature levels: core and compatibility

The **feature level** is what you ask for when you request an adapter, in `GpuRequestAdapterOptions.featureLevel`:

| `GpuFeatureLevel` | Meaning |
|-------------------|---------|
| `UNDEFINED` | The default. It means `CORE`. |
| `CORE` | The full feature set, which Vulkan, Metal, and Direct3D 12 support. |
| `COMPATIBILITY` | The smaller feature set, which can also be supported on OpenGL ES 3.1 and Direct3D 11. |

If an adapter is returned, it supports at least the feature level you asked for. An implementation is allowed to give
you a core adapter when you asked for compatibility, because core can do everything compatibility can.

## Requesting a compatibility adapter

```java
GpuRequestAdapterOptions options = new GpuRequestAdapterOptions();
options.featureLevel = GpuFeatureLevel.COMPATIBILITY;
instance.requestAdapter(options, GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, adapter, message) -> {
  if (adapter == null) {
    System.out.println("No compatibility adapter: " + status + " " + message);
    return;
  }
  // Keep it.
});
```

Asking for compatibility widens the search: Dawn then also considers adapters that cannot do core, such as a
Direct3D 11 adapter, or an OpenGL or OpenGL ES adapter when the natives include those backends (see
[OpenGL and OpenGL ES](#opengl-and-opengl-es)).

## Did I get core or compatibility?

The feature `GpuFeatureName.CORE_FEATURES_AND_LIMITS` answers the question:

- On an **adapter**, it means the adapter **can** do core. A core adapter always has it. A compatibility adapter has it
  when the hardware underneath could do core anyway (for example a Vulkan adapter returned for a compatibility
  request).
- On a **device**, it means the device **is** core. A device without it is limited to compatibility mode.

```java
boolean adapterCanDoCore = adapter.hasFeature(GpuFeatureName.CORE_FEATURES_AND_LIMITS);
// ... after requesting the device:
boolean deviceIsCore = device.hasFeature(GpuFeatureName.CORE_FEATURES_AND_LIMITS);
```

Check the **device**, not the adapter, when you decide which code paths to use. The device is what you actually draw
with.

## Upgrading a compatibility adapter to core

A device created from a compatibility adapter is a compatibility device, unless you ask for the upgrade. To get core
whenever the hardware allows it, request `CORE_FEATURES_AND_LIMITS` as a device feature, but only if the adapter has it
(asking for a feature the adapter lacks makes the device request fail):

```java
GpuDeviceDescriptor descriptor = new GpuDeviceDescriptor(1);
descriptor.label = "main device";
if (adapter.hasFeature(GpuFeatureName.CORE_FEATURES_AND_LIMITS)) {
  descriptor.requiredFeatures[descriptor.requiredFeatureCount++] = GpuFeatureName.CORE_FEATURES_AND_LIMITS;
}
// Set the callbacks as usual (see "Requesting a device"), then:
adapter.requestDevice(descriptor, GpuCallbackMode.ALLOW_PROCESS_EVENTS, onDevice);
```

This gives you one request that works everywhere: a core device on modern hardware, and a compatibility device on
older hardware. Your code then reads `device.hasFeature(GpuFeatureName.CORE_FEATURES_AND_LIMITS)` to know which one it
got.

## Compatibility limits

Compatibility mode has lower default limits than core, and four extra limits that core does not have. They live in an
extension struct, `GpuCompatibilityModeLimits`, which you chain onto `GpuLimits.nextInChain`:

| Field | What it limits |
|-------|----------------|
| `maxStorageBuffersInVertexStage` | Storage buffers a vertex shader may use. |
| `maxStorageTexturesInVertexStage` | Storage textures a vertex shader may use. |
| `maxStorageBuffersInFragmentStage` | Storage buffers a fragment shader may use. |
| `maxStorageTexturesInFragmentStage` | Storage textures a fragment shader may use. |

On some compatibility hardware these are 0: vertex and fragment shaders there cannot use storage buffers or storage
textures at all. Compute shaders are not affected by these four limits.

**Reading them** from an adapter or a device:

```java
private final GpuLimits limits = new GpuLimits();
private final GpuCompatibilityModeLimits compatLimits = new GpuCompatibilityModeLimits();

void readLimits(GpuDevice device) {
  limits.nextInChain = compatLimits;   // Chain once; the call fills both structs.
  device.getLimits(limits);
  System.out.println("storage buffers in fragment shaders: " + compatLimits.maxStorageBuffersInFragmentStage);
}
```

**Requesting more** than the default, up to what the adapter offers, works like any other limit: fill a `GpuLimits`
with the chained struct and pass it as `requiredLimits`. A field left at its initial value
(`GpuConstants.LIMIT_U32_UNDEFINED`) means "the default".

```java
GpuLimits supported = new GpuLimits();
GpuCompatibilityModeLimits supportedCompat = new GpuCompatibilityModeLimits();
supported.nextInChain = supportedCompat;
adapter.getLimits(supported);

GpuLimits required = new GpuLimits();
GpuCompatibilityModeLimits requiredCompat = new GpuCompatibilityModeLimits();
required.nextInChain = requiredCompat;
requiredCompat.maxStorageBuffersInFragmentStage = supportedCompat.maxStorageBuffersInFragmentStage;
descriptor.requiredLimits = required;
```

Upgrading to core (the section above) also gives the device the core limits.

## What compatibility mode restricts

Besides the lower limits, compatibility mode adds a few validation rules, so that the older APIs can implement
everything. The full, current list is in the WebGPU compatibility mode proposal of the specification; the ones you are
most likely to meet are:

- **Storage resources in vertex and fragment shaders** are limited by the four limits above (possibly to zero).
- **Texture views used in bind groups must match the texture's declared view dimension.** On the older APIs, a texture
  can only be sampled one way, so you say up front how it will be viewed: chain a `GpuTextureBindingViewDimension` onto
  the `GpuTextureDescriptor` (for example `CUBE` for a cube map stored as six layers). `texture.getTextureBindingViewDimension()`
  reads it back.
- **Some lesser-used capabilities are missing or narrower**, for example some multisampling, copy, and blending
  combinations, and a few WGSL built-ins.

Because every rule is a validation rule, breaking one gives you a clear validation error in the uncaptured error
callback (see [Error Handling and Device Loss](errors-and-device-loss.md)), never a silent difference. The easiest way to
make sure a program runs in compatibility mode is to develop and test it on a compatibility device.

```java
GpuTextureBindingViewDimension viewDimension = new GpuTextureBindingViewDimension();
viewDimension.textureBindingViewDimension = GpuTextureViewDimension.CUBE;

GpuTextureDescriptor descriptor = new GpuTextureDescriptor();
descriptor.usage = GpuTextureUsage.TEXTURE_BINDING | GpuTextureUsage.COPY_DST;
descriptor.dimension = GpuTextureDimension.D2;
descriptor.size.width = 512;
descriptor.size.height = 512;
descriptor.size.depthOrArrayLayers = 6;
descriptor.format = GpuTextureFormat.RGBA8_UNORM;
descriptor.nextInChain = viewDimension;
GpuTexture skybox = device.createTexture(descriptor);
```

## Choosing a backend type

On desktop and Android, Dawn can run on top of several native APIs. `GpuRequestAdapterOptions.backendType` lets you
require one:

| `GpuBackendType` | Native API | Feature levels in Dawn |
|------------------|------------|------------------------|
| `UNDEFINED` | Let Dawn pick the best one. | Any |
| `VULKAN` | Vulkan (Linux, Windows, Android) | Core and compatibility |
| `METAL` | Metal (macOS) | Core and compatibility |
| `D3D12` | Direct3D 12 (Windows) | Core and compatibility |
| `D3D11` | Direct3D 11 (Windows) | Typically compatibility |
| `OPENGL` | Desktop OpenGL | Compatibility only |
| `OPENGLES` | OpenGL ES | Compatibility only |
| `NULL` | A fake GPU that draws nothing, for tests | Core and compatibility |
| `WEBGPU` | WebGPU on top of another WebGPU implementation | Rarely needed |

If no adapter of that backend exists (because the machine does not support it, or because the natives were built
without it), the request returns `null`. The backends compiled into the published natives are listed in the
[Platform Guide](platforms.md#natives); a backend that is not compiled in can never be returned.

Most programs leave `backendType` at `UNDEFINED`, which lets Dawn choose, and use it only to work around a broken
driver, to force a fallback path for testing, or to select `NULL` in unit tests. `GpuAdapterInfo.backendType` tells you
which backend an adapter uses.

## OpenGL and OpenGL ES

In Dawn, **OpenGL and OpenGL ES adapters only exist in compatibility mode.** A request with
`backendType = OPENGL` or `OPENGLES` and the default feature level (which means core) finds nothing. Always combine
them with `featureLevel = COMPATIBILITY`:

```java
options.reset();
options.featureLevel = GpuFeatureLevel.COMPATIBILITY;
options.backendType = GpuBackendType.OPENGLES;
instance.requestAdapter(options, GpuCallbackMode.ALLOW_PROCESS_EVENTS, onAdapter);
```

OpenGL and OpenGL ES adapters are available when the natives are built with Dawn's OpenGL backends. If the natives you
use do not include them, such a request simply returns `null`, so a program that tries them as a last resort works the
same either way. To build natives with the OpenGL backends yourself, see
[COMPILING.md](../COMPILING.md#native-libraries-dawn).

## A fallback pattern: core first, then compatibility

The most robust startup tries the best option first and falls back step by step:

1. Core, any backend. Modern machines stop here.
2. Compatibility, any backend. Picks up Direct3D 11-only machines and any other compatibility adapter.
3. Compatibility on OpenGL ES, then on OpenGL, as a last resort (useful only when the natives include them).

Each attempt is asynchronous, so the next one starts from the callback of the previous one. This class keeps its
options and callback in fields and walks through a table of attempts:

```java
package com.example;

import io.github.stringdotjar.jwebgpu.GpuAdapter;
import io.github.stringdotjar.jwebgpu.GpuBackendType;
import io.github.stringdotjar.jwebgpu.GpuCallbackMode;
import io.github.stringdotjar.jwebgpu.GpuFeatureLevel;
import io.github.stringdotjar.jwebgpu.GpuInstance;
import io.github.stringdotjar.jwebgpu.GpuRequestAdapterCallback;
import io.github.stringdotjar.jwebgpu.GpuRequestAdapterOptions;
import io.github.stringdotjar.jwebgpu.GpuRequestAdapterStatus;

/** Requests the best adapter available: core first, then compatibility, then OpenGL ES and OpenGL. */
public final class AdapterSearch {

  private static final GpuFeatureLevel[] LEVELS = {
    GpuFeatureLevel.CORE, GpuFeatureLevel.COMPATIBILITY, GpuFeatureLevel.COMPATIBILITY, GpuFeatureLevel.COMPATIBILITY
  };
  private static final GpuBackendType[] BACKENDS = {
    GpuBackendType.UNDEFINED, GpuBackendType.UNDEFINED, GpuBackendType.OPENGLES, GpuBackendType.OPENGL
  };

  private final GpuRequestAdapterOptions options = new GpuRequestAdapterOptions();
  private final GpuRequestAdapterCallback onAdapter = this::onAdapter;
  private final GpuInstance instance;
  private GpuAdapter adapter;
  private int attempt;

  private boolean finished;

  public AdapterSearch(GpuInstance instance) {
    this.instance = instance;
  }

  public void start() {
    attempt = 0;
    request();
  }

  private void request() {
    options.reset();
    options.featureLevel = LEVELS[attempt];
    options.backendType = BACKENDS[attempt];
    instance.requestAdapter(options, GpuCallbackMode.ALLOW_PROCESS_EVENTS, onAdapter);
  }

  private void onAdapter(GpuRequestAdapterStatus status, GpuAdapter result, String message) {
    if (status == GpuRequestAdapterStatus.SUCCESS && result != null) {
      adapter = result;
      finished = true;
      return;
    }
    attempt++;
    if (attempt < LEVELS.length) {
      request();
    } else {
      finished = true;   // Nothing worked; adapter stays null.
    }
  }

  public GpuAdapter getAdapter() {
    return adapter;
  }

  public boolean isFinished() {
    return finished;
  }
}
```

After it finishes, request the device as usual, including `CORE_FEATURES_AND_LIMITS` when the adapter has it (see
[Upgrading a compatibility adapter to core](#upgrading-a-compatibility-adapter-to-core)). Then let your renderer read
`device.hasFeature(GpuFeatureName.CORE_FEATURES_AND_LIMITS)` and the compatibility limits, and pick its code paths:
for example, read particle positions from a storage buffer in the vertex shader on core, and from a vertex buffer on
compatibility devices whose `maxStorageBuffersInVertexStage` is 0.

There is a simpler alternative when you do not need the OpenGL steps: request `COMPATIBILITY` once, and always ask for
the core upgrade on the device. On modern hardware Dawn returns an adapter that can do core, and the upgrade gives you
a core device; on older hardware you get a compatibility device. Trying core first, as above, makes the choice of the
best adapter explicit and keeps core adapters ahead of compatibility-only ones.

On the web, the same class works: each callback runs from the browser's event loop, and `backendType` is ignored, so
the last two attempts behave like the second one.

## The browser

Browsers understand the feature level too. A browser that supports compatibility mode may return a compatibility
adapter for `featureLevel = COMPATIBILITY`, and one that does not simply returns a core adapter, which is allowed.
`CORE_FEATURES_AND_LIMITS` and `GpuCompatibilityModeLimits` work the same as on the desktop, so the upgrade and the
limit checks above need no special case. `backendType` is ignored, because the browser chooses its own native API.

## Common mistakes

- **Asking for `OPENGL` or `OPENGLES` with the default feature level.** They only exist in compatibility mode; set
  `featureLevel = COMPATIBILITY`.
- **Requesting `CORE_FEATURES_AND_LIMITS` without checking the adapter.** The device request fails on a
  compatibility-only adapter.
- **Deciding code paths from the adapter instead of the device.** Check `device.hasFeature(...)`; a compatibility
  adapter that can do core still gives you a compatibility device unless you requested the upgrade.
- **Assuming storage buffers in vertex or fragment shaders.** Read `GpuCompatibilityModeLimits` first.
- **Expecting a backend that is not compiled into your natives.** The request returns `null`; check the natives table
  in the [Platform Guide](platforms.md#natives).
- **Forgetting `options.reset()` between attempts** when you reuse one options object, which carries old values into
  the next request.

## Check your understanding

1. You request an adapter with `backendType = OPENGLES` and get `null`, although the device supports OpenGL ES. What
   are the two likely reasons?
2. You request a compatibility adapter and it has `CORE_FEATURES_AND_LIMITS`. Is your device core?
3. Where do you find out how many storage buffers a fragment shader may use on a compatibility device?

(Answers: the feature level was left at the default, which means core, and OpenGL ES only exists in compatibility
mode; or the natives were built without the OpenGL ES backend. Only if you request `CORE_FEATURES_AND_LIMITS` in the
device descriptor; check `device.hasFeature(...)` to be sure. In `GpuCompatibilityModeLimits.maxStorageBuffersInFragmentStage`,
filled by `device.getLimits(...)` when the struct is chained onto `GpuLimits.nextInChain`.)

**Next:** ["How Do I...?" Cookbook](cookbook.md)
