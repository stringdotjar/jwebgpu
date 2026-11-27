# Garbage-Free Usage

jwebgpu is designed so that a frame of rendering allocates nothing on the Java heap. This guide explains why that
matters, the habits that get you there, how the refill overloads and the scratch memory work, and how to measure that
your own frames are garbage-free.

## Table of contents

1. [Why garbage matters](#why-garbage-matters)
2. [The mental model: a workshop with labeled drawers](#the-mental-model-a-workshop-with-labeled-drawers)
3. [Habit 1: create descriptors once, refill them](#habit-1-create-descriptors-once-refill-them)
4. [Habit 2: refill wrappers instead of creating them](#habit-2-refill-wrappers-instead-of-creating-them)
5. [Habit 3: keep arrays and callbacks in fields](#habit-3-keep-arrays-and-callbacks-in-fields)
6. [Habit 4: upload from reused arrays or buffers](#habit-4-upload-from-reused-arrays-or-buffers)
7. [Habit 5: reuse output structs](#habit-5-reuse-output-structs)
8. [Scratch memory and trimScratch](#scratch-memory-and-trimscratch)
9. [What happens on the web](#what-happens-on-the-web)
10. [Measuring allocations](#measuring-allocations)
11. [The checklist](#the-checklist)
12. [Check your understanding](#check-your-understanding)

## Why garbage matters

Every `new` in Java creates an object on the heap. When objects are no longer used, they become garbage, and the
garbage collector eventually cleans them up. Modern collectors are fast, but cleaning still costs time, and if it
happens in the middle of a frame, that frame is late: the animation stutters. At 60 frames per second a frame has
16.6 milliseconds; at 120, only 8.3.

Code that runs every frame runs thousands of times a minute, so even one small allocation per frame turns into a steady
stream of garbage. Code that runs once (loading, startup) can allocate freely.

## The mental model: a workshop with labeled drawers

Think of a workshop. A wasteful worker buys a new screwdriver for every screw and throws it away afterwards; soon the
floor is covered and someone has to stop the work to sweep. A tidy worker buys each tool once, keeps it in a labeled
drawer, and puts it back after use. jwebgpu gives you the drawers:

- Descriptors are tools you keep and refill.
- Wrapper objects (encoders, passes, command buffers, views) can be refilled with a new native object instead of being
  replaced.
- The backends keep their own scratch memory, so passing a descriptor to a call creates nothing either.

## Habit 1: create descriptors once, refill them

Descriptors are mutable on purpose. Keep them in fields, set them up once, and change only what differs per frame:

```java
private final GpuRenderPassDescriptor pass = new GpuRenderPassDescriptor();

void setUp() {
  pass.label = "main pass";
  pass.colorAttachmentCount = 1;
  pass.colorAttachments[0].loadOp = GpuLoadOp.CLEAR;
  pass.colorAttachments[0].storeOp = GpuStoreOp.STORE;
  pass.colorAttachments[0].clearValue.a = 1.0;
}

void frame() {
  pass.colorAttachments[0].view = view;   // The only thing that changes.
  // ...
}
```

Nested structs and arrays inside descriptors are preallocated, so filling them allocates nothing. `reset()` restores
the defaults when you reuse one descriptor for different objects.

## Habit 2: refill wrappers instead of creating them

Every method that returns an object has a second overload that takes an existing wrapper (`into`) and refills it:

| Creates a wrapper | Refills one |
|-------------------|-------------|
| `device.createCommandEncoder(descriptor)` | `device.createCommandEncoder(descriptor, encoder)` |
| `encoder.beginRenderPass(descriptor)` | `encoder.beginRenderPass(descriptor, passEncoder)` |
| `encoder.beginComputePass(descriptor)` | `encoder.beginComputePass(descriptor, computePassEncoder)` |
| `encoder.finish(descriptor)` | `encoder.finish(descriptor, commands)` |
| `texture.createView(descriptor)` | `texture.createView(descriptor, view)` |
| `device.createBindGroup(descriptor)` | `device.createBindGroup(descriptor, bindGroup)` |
| `device.getQueue()` | `device.getQueue(queue)` |

The refill overload stores the new native handle in the wrapper and releases the handle the wrapper held before, then
returns the same wrapper. No Java object is created.

The wrappers are abstract API classes, so you get the first one from a normal call. Two patterns work:

```java
// Pattern A: create on the first frame, refill afterwards.
encoder = encoder == null ? device.createCommandEncoder(null) : device.createCommandEncoder(null, encoder);

// Pattern B: create once during setup, release, and always refill in the frame.
encoder = device.createCommandEncoder(null);
encoder.release();
// ... later, every frame:
device.createCommandEncoder(null, encoder);
```

A released wrapper can be refilled, so releasing the per-frame wrappers at the end of each frame (which gives their
native objects back promptly) and refilling them at the start of the next works on every backend.

The surface texture is refilled for you: `surface.getCurrentTexture(frame)` reuses the wrapper already in
`frame.texture`.

## Habit 3: keep arrays and callbacks in fields

Some calls take arrays. Create them once:

```java
private final GpuCommandBuffer[] submit = new GpuCommandBuffer[1];
private final int[] dynamicOffsets = new int[1];

submit[0] = commands;
queue.submit(submit, 1);

dynamicOffsets[0] = objectIndex * 256;
passEncoder.setBindGroup(0, objectGroup, dynamicOffsets, 1);
```

`passEncoder.setBindGroup(0, group)` without offsets uses a shared empty array, so it allocates nothing either.

Lambdas that capture variables (including `this`) create a new object every time the expression runs. Store callbacks
you pass repeatedly in `final` fields:

```java
private final GpuQueueWorkDoneCallback onWorkDone = (status, message) -> gpuIdle = true;
private volatile boolean gpuIdle;

queue.onSubmittedWorkDone(GpuCallbackMode.ALLOW_PROCESS_EVENTS, onWorkDone);
```

On desktop and Android, registering a callback stores it in a preallocated table and passes a slot number to native
code, so no wrapper object is created per call.

Watch the code inside callbacks and around them, too: string concatenation (`"error: " + message`) and boxing
(`Integer` from an `int`) allocate. That is fine for error paths that run rarely, not for code that runs every frame.

## Habit 4: upload from reused arrays or buffers

`queue.writeBuffer(...)` and `queue.writeTexture(...)` accept arrays (`byte[]`, `short[]`, `int[]`, `float[]`) with an
offset and a count, or a `ByteBuffer`. Keep the source in a field and refill it:

```java
private final float[] uniforms = new float[16];

void frame() {
  camera.writeMatrix(uniforms);   // Fill the array in place.
  queue.writeBuffer(uniformBuffer, 0, uniforms, 0, uniforms.length);
}
```

Uploads cost no allocation on any backend:

- An **array** is copied once into native scratch memory (desktop, Android) or into a reusable staging buffer (web).
- A **direct `ByteBuffer`** is passed without any copy. On desktop, the backend remembers the last direct buffer each
  thread used, so uploading from the **same** buffer again allocates nothing, while a brand-new direct buffer pays for
  one small temporary object. Keep one direct buffer in a field and reuse it.
- A **heap `ByteBuffer`** is copied through its array.

## Habit 5: reuse output structs

Calls that answer by filling an object (`getInfo`, `getLimits`, `getFeatures`, `getCapabilities`,
`getCurrentTexture`) fill the object you pass. Keep it in a field. When an array inside it is too small, the backend
replaces it once with a bigger one; after that, the same struct is refilled without allocation.

## Scratch memory and trimScratch

On desktop and Android, every thread that calls jwebgpu owns a block of native **scratch memory**. A call writes its
descriptors, strings, and arrays into that block (at the byte offsets of the C structs), passes addresses to Dawn, and
reuses the block for the next call. That is how passing a descriptor creates nothing.

The block grows when one call needs more than it holds (for example, a very large upload from an array), and it never
shrinks by itself. After a rare, huge upload, give the memory back on the same thread:

```java
JWebGpuFfm.trimScratch();   // Desktop.
JWebGpuJni.trimScratch();   // Android.
```

Do not call it from inside a callback. For large, frequent uploads, prefer a direct `ByteBuffer`, which skips the
scratch block entirely.

## What happens on the web

The web backend follows the same rules with different tools: each descriptor type has one cached JavaScript object that
is overwritten on every call, enum values are interned strings, and a Java string is converted once and reused as long
as the same `String` object is passed again (so prefer constant labels). Array uploads go through one reusable direct
staging buffer that grows only when an upload is bigger than any before. The one thing that is created per upload is a
small view object on the JavaScript side, never a Java object.

## Measuring allocations

On a HotSpot JVM you can count the bytes the current thread allocates, which is exactly how jwebgpu's own tests prove
that a frame allocates nothing:

```java
ThreadMXBean bean = ManagementFactory.getThreadMXBean();
com.sun.management.ThreadMXBean hotspot = (com.sun.management.ThreadMXBean) bean;
hotspot.setThreadAllocatedMemoryEnabled(true);
long thread = Thread.currentThread().threadId();

for (int i = 0; i < 30_000; i++) {
  frame();   // Warm up, so the JIT has compiled everything.
}
long before = hotspot.getThreadAllocatedBytes(thread);
for (int i = 0; i < 10_000; i++) {
  frame();
}
long after = hotspot.getThreadAllocatedBytes(thread);
System.out.println("bytes per frame: " + (after - before) / 10_000.0);
```

Measure after a warm-up, because the first runs of any Java code allocate while classes load and the JIT compiles. A
profiler with allocation tracking (such as JDK Flight Recorder) shows **where** the remaining allocations come from.

## The checklist

| Check | Where to read more |
|-------|--------------------|
| No `new` in per-frame code. | This guide |
| Descriptors live in fields and are refilled. | [Habit 1](#habit-1-create-descriptors-once-refill-them) |
| Encoders, passes, command buffers, and views use the refill overloads. | [Habit 2](#habit-2-refill-wrappers-instead-of-creating-them) |
| The submit array and dynamic offsets live in fields. | [Habit 3](#habit-3-keep-arrays-and-callbacks-in-fields) |
| Repeated callbacks are stored in `final` fields. | [Habit 3](#habit-3-keep-arrays-and-callbacks-in-fields) |
| Uploads come from reused arrays or one reused direct `ByteBuffer`. | [Habit 4](#habit-4-upload-from-reused-arrays-or-buffers) |
| Output structs are reused. | [Habit 5](#habit-5-reuse-output-structs) |
| `device.getQueue()` is called once. | [The queue](instances-adapters-and-devices.md#the-queue) |
| Pipelines and bind groups are created at load time. | [Shaders and Pipelines](shaders-and-pipelines.md) |
| The allocation counter shows 0 bytes per frame after warm-up. | [Measuring allocations](#measuring-allocations) |

## Check your understanding

1. Why does `queue.submit(new GpuCommandBuffer[] { commands }, 1)` belong in setup code but not in a frame?
2. What does `encoder.finish(null, commands)` do with the native handle that `commands` held before?
3. You upload a 64 MB texture once from a `byte[]` at startup. What can you do afterwards to give memory back?

(Answers: it creates a new array every time it runs, so in a frame it creates garbage; keep the array in a field
instead; it releases it and stores the new command buffer's handle in the same wrapper; call `JWebGpuFfm.trimScratch()`
(or `JWebGpuJni.trimScratch()` on Android) on the same thread, outside any callback, or upload from a direct
`ByteBuffer` next time.)

**Next:** [Platform Guide](platforms.md)
