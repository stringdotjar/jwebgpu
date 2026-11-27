# Error Handling and Device Loss

WebGPU is designed so that mistakes give you a message instead of a crash. This guide explains where those messages
go, how to catch errors around specific code with error scopes, which Java exceptions jwebgpu throws, and how to
recover when the GPU device is lost.

## Table of contents

1. [Why WebGPU errors do not throw](#why-webgpu-errors-do-not-throw)
2. [The mental model: a referee with a whistle](#the-mental-model-a-referee-with-a-whistle)
3. [The kinds of GPU errors](#the-kinds-of-gpu-errors)
4. [The uncaptured error callback](#the-uncaptured-error-callback)
5. [Error scopes](#error-scopes)
6. [Labels and debug groups](#labels-and-debug-groups)
7. [Java exceptions jwebgpu throws](#java-exceptions-jwebgpu-throws)
8. [Device loss](#device-loss)
9. [Recovering from a lost device](#recovering-from-a-lost-device)
10. [Common mistakes](#common-mistakes)
11. [Check your understanding](#check-your-understanding)

## Why WebGPU errors do not throw

Most WebGPU work happens later, on the GPU, or in a separate process in the browser. When you call
`device.createBuffer(...)`, the call returns at once, and checking every detail would mean waiting. So WebGPU checks in
the background, and a call that turns out to be invalid gives you an **invalid object** instead of failing: a texture
that silently does nothing and makes every command that uses it invalid too. The error itself is reported separately.

## The mental model: a referee with a whistle

Think of a referee at a game. When a player breaks a rule, play does not stop for a lecture. The referee blows the
whistle and writes it down, and the game goes on. You decide how to listen:

- The **uncaptured error callback** is the referee shouting every foul across the field, so you always hear it.
- An **error scope** is you asking the referee, "tell me about any foul during the next play", and getting the answer
  privately afterwards.

## The kinds of GPU errors

| `GpuErrorType` | Meaning | Typical cause |
|----------------|---------|---------------|
| `VALIDATION` | You broke a rule of the API. | A missing usage flag, a format mismatch, a shader error, a wrong size. |
| `OUT_OF_MEMORY` | The GPU ran out of memory. | A huge texture or buffer, or too many of them. |
| `INTERNAL` | The implementation failed, even though the call was valid. | A driver bug or a shader the driver cannot compile. |
| `UNKNOWN` | Something else. | Rare. |

Validation errors are bugs in your program: fix them. Out-of-memory errors can happen in correct programs: handle them
(for example, by loading smaller textures).

## The uncaptured error callback

Every error that no error scope catches goes to the device's uncaptured error callback. Set it when you request the
device:

```java
descriptor.uncapturedErrorCallback = (device, type, message) -> {
  System.err.println("WebGPU " + type + ": " + message);
};
```

The message is detailed. A typical one names the object by its label, the call, and the rule that was broken, which is
why labeling objects matters.

The callback can run at any time, often inside the call that caused the error, and possibly on another thread, so keep
it short and thread safe (the tests append to a `StringBuilder` inside a `synchronized` block). If you have none, errors
are lost or only printed to the native log.

## Error scopes

An error scope catches errors of one kind around a stretch of code. You push a scope, do the work, and pop it; the pop
reports the first error inside the scope, asynchronously:

```java
device.pushErrorScope(GpuErrorFilter.OUT_OF_MEMORY);
GpuTexture big = device.createTexture(hugeDescriptor);
device.popErrorScope(GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, type, message) -> {
  if (type == GpuErrorType.OUT_OF_MEMORY) {
    // Fall back to a smaller texture.
  }
});
```

- The filter is `VALIDATION`, `OUT_OF_MEMORY`, or `INTERNAL`. Only errors of that kind are caught; others pass through
  to outer scopes or to the uncaptured error callback.
- Scopes nest like a stack. Every `pushErrorScope` needs exactly one `popErrorScope`.
- In the callback, `type` is `NO_ERROR` when nothing went wrong, and `status` is `SUCCESS` when the scope was popped
  correctly.

A validation scope around shader and pipeline creation is a good way to give a clear message when a user-supplied
shader is broken. Combine it with `shaderModule.getCompilationInfo(...)` (see
[Checking compiler messages](shaders-and-pipelines.md#checking-compiler-messages)) to get line numbers.

## Labels and debug groups

- Give every object a `label` in its descriptor (or call `setLabel(...)` later). Error messages use it, so
  "buffer 'player vertices'" tells you more than "a buffer".
- In encoders and passes, `pushDebugGroup(label)`, `popDebugGroup()`, and `insertDebugMarker(label)` name sections of
  your commands. Error messages and GPU debuggers show them.
- Use constant strings for labels in per-frame code, so no new string is built every frame.

## Java exceptions jwebgpu throws

Errors in the GPU work never throw, but misusing the Java side does:

| Exception | When |
|-----------|------|
| `IllegalStateException` | No backend is installed; a second, different backend is installed; you call a method on a released object (the message says it was released); the desktop natives jar for this platform is missing. |
| `NullPointerException` | A required argument, such as a callback, is `null`. |
| `UnsupportedOperationException` | On the web, you used something the browser does not offer (see [What the web does not support](platforms.md#what-the-web-does-not-support)). |

An invalid descriptor (a zero-sized texture, a wrong format) is **not** a Java exception: it is a WebGPU validation
error, reported through the callback.

## Device loss

A device can be **lost**: the driver crashed or was updated, the GPU was reset, the system ran out of video memory, a
laptop switched GPUs, or the browser decided to reclaim the GPU. A lost device stops doing work, and everything created
from it becomes useless.

You hear about it through the device lost callback:

```java
descriptor.deviceLostMode = GpuCallbackMode.ALLOW_PROCESS_EVENTS;
descriptor.deviceLostCallback = (device, reason, message) -> {
  if (reason == GpuDeviceLostReason.DESTROYED || reason == GpuDeviceLostReason.CALLBACK_CANCELLED) {
    return;   // We destroyed or released it ourselves, or the program is shutting down.
  }
  deviceLost = true;   // Let the main loop rebuild everything.
};
```

The reasons are:

| `GpuDeviceLostReason` | Meaning |
|-----------------------|---------|
| `DESTROYED` | You called `device.destroy()`. |
| `CALLBACK_CANCELLED` | The instance or device went away first (usually at shutdown), so the callback is cancelled. |
| `FAILED_CREATION` | The device could not be created in the first place. |
| `UNKNOWN` | Anything else, including driver crashes and resets. This is the one to recover from. |

`device.getLostFuture()` returns the future of the lost event, if you prefer to wait for it with `waitAny`.

## Recovering from a lost device

Recovery means starting over from the adapter step. An adapter can usually create only one device, so request a **new
adapter** too:

1. Note the loss in the callback (set a field), and stop rendering.
2. In your main loop, release every object of the old device (pipelines, bind groups, buffers, textures, the queue,
   the device, and the adapter). `release()` is safe on objects of a lost device.
3. Request a new adapter and a new device, exactly as at startup.
4. Recreate your resources: reload shaders, pipelines, textures, and buffers. Keep the data you need to do this (file
   names, CPU-side copies of small buffers), because the GPU copies are gone.
5. Configure the surface again with the new device.

Programs that load everything through one "create resources" method get recovery almost for free: call it again.

## Common mistakes

- **No uncaptured error callback.** Errors disappear, and you only see a black screen.
- **No labels.** Error messages cannot tell you which object is wrong.
- **Unbalanced error scopes.** Every push needs a pop.
- **Treating every device lost callback as a crash.** At shutdown it runs with `DESTROYED` or `CALLBACK_CANCELLED`.
- **Requesting a new device from the old adapter after a loss.** Request a new adapter first.

## Check your understanding

1. You create a texture with a missing usage flag. Does `device.createTexture(...)` throw?
2. How do you find out whether one particular buffer creation ran out of memory?
3. The device lost callback reports `UNKNOWN`. What do you do?

(Answers: no, it returns an invalid texture, and a `VALIDATION` error goes to the uncaptured error callback (or to an
error scope); wrap it in `pushErrorScope(GpuErrorFilter.OUT_OF_MEMORY)` and `popErrorScope(...)`, and check the type in
the callback; stop rendering, release everything of the old device, request a new adapter and device, and recreate
your resources.)

**Next:** [Garbage-Free Usage](garbage-free.md)
