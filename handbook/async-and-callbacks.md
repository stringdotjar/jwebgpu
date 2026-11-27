# Asynchronous Calls and Callbacks

Some WebGPU calls cannot answer right away: finding a GPU, starting a device, reading a buffer back, compiling a
pipeline in the background. This guide explains how those calls report back, what the three callback modes mean, how
`processEvents` and `waitAny` work, which thread runs your callback, and how the browser differs.

## Table of contents

1. [Why some calls are asynchronous](#why-some-calls-are-asynchronous)
2. [The mental model: a restaurant pager](#the-mental-model-a-restaurant-pager)
3. [Which calls are asynchronous](#which-calls-are-asynchronous)
4. [The three callback modes](#the-three-callback-modes)
5. [Pumping events with processEvents](#pumping-events-with-processevents)
6. [Waiting for a future with waitAny](#waiting-for-a-future-with-waitany)
7. [Threads and callbacks](#threads-and-callbacks)
8. [The browser](#the-browser)
9. [Callbacks without garbage](#callbacks-without-garbage)
10. [A pattern that works everywhere](#a-pattern-that-works-everywhere)
11. [Common mistakes](#common-mistakes)
12. [Check your understanding](#check-your-understanding)

## Why some calls are asynchronous

The GPU works on its own schedule, far away from your thread. When you ask it for something that it has to do first
(read pixels you just drew, or finish everything you submitted), the honest answer is "not yet". A call that waited
would freeze your thread, and in a browser, freezing the only thread is not allowed at all. So WebGPU gives you the
answer later, in a callback.

## The mental model: a restaurant pager

At some restaurants you order at the counter and get a pager. You sit down, and the pager buzzes when your food is
ready. WebGPU's asynchronous calls work the same way:

- The call (ordering) returns immediately. It gives you a **future**, a number that identifies your order (the pager).
- Your **callback** is what you do when the pager buzzes.
- The **callback mode** is the pager's rule about when it may buzz: only when you look at it, whenever you check in at
  the counter, or any time at all.

## Which calls are asynchronous

| Call | Callback | What it answers |
|------|----------|-----------------|
| `instance.requestAdapter(options, mode, callback)` | `GpuRequestAdapterCallback` | An adapter, or why there is none. |
| `adapter.requestDevice(descriptor, mode, callback)` | `GpuRequestDeviceCallback` | A device, or why there is none. |
| `buffer.mapAsync(mapMode, offset, size, mode, callback)` | `GpuBufferMapCallback` | The buffer can now be read or written from Java. |
| `queue.onSubmittedWorkDone(mode, callback)` | `GpuQueueWorkDoneCallback` | The GPU has finished everything submitted so far. |
| `device.popErrorScope(mode, callback)` | `GpuPopErrorScopeCallback` | The first error inside the scope, if any. |
| `shaderModule.getCompilationInfo(mode, callback)` | `GpuCompilationInfoCallback` | The compiler's messages for a shader. |
| `device.createRenderPipelineAsync(...)`, `device.createComputePipelineAsync(...)` | The matching `GpuCreate...AsyncCallback` | A pipeline compiled in the background. |

Each of them returns a `long`: the id of its future, which you only need for `waitAny`. Two more callbacks live on the
device descriptor instead: the **device lost** callback (with its own `deviceLostMode`) and the **uncaptured error**
callback. See [Error Handling and Device Loss](errors-and-device-loss.md).

Every callback is a functional interface, so a lambda or a method reference works:

```java
buffer.mapAsync(GpuMapMode.READ, 0, size, GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, message) -> {
  if (status == GpuMapAsyncStatus.SUCCESS) {
    // Read the data, then unmap.
  }
});
```

## The three callback modes

`GpuCallbackMode` decides **when** a callback is allowed to run:

| Mode | When the callback may run | Use it when |
|------|---------------------------|-------------|
| `WAIT_ANY_ONLY` | Only inside `instance.waitAny(...)` for that future. | You want to wait for this one answer at a precise point. |
| `ALLOW_PROCESS_EVENTS` | Inside `instance.processEvents()`, or inside `waitAny(...)`. | Almost always. Callbacks run on your thread, at a moment you choose. |
| `ALLOW_SPONTANEOUS` | At any time, on any thread, even before the call that started it returns. | You want the answer as early as possible and your callback is thread safe. |

`ALLOW_PROCESS_EVENTS` is the easy choice for most programs: your main loop calls `processEvents()` once per frame,
and every callback that is ready runs right there, on the main thread, between your other work. No locks are needed.

## Pumping events with processEvents

```java
// Once per frame, at the start of the frame.
instance.processEvents();
```

`processEvents()` never waits. It runs the callbacks of everything that has finished, then returns. If nothing has
finished, it returns at once. Call it regularly: a callback registered with `ALLOW_PROCESS_EVENTS` never runs if you
never call it.

During startup, before you have a frame loop, a small loop does the job:

```java
while (!done) {
  instance.processEvents();
  Thread.sleep(1);
}
```

(The sleep keeps the loop from spinning a CPU core at 100% while it waits. Do not write this loop on the web; see
[The browser](#the-browser).)

## Waiting for a future with waitAny

Sometimes you really do want to wait, for example in a command-line tool that renders one image and exits. `waitAny`
waits until one of the futures you give it has completed, and runs its callback:

```java
long id = instance.requestAdapter(null, GpuCallbackMode.WAIT_ANY_ONLY, (status, adapter, message) -> {
  // Runs inside waitAny(...).
});

GpuFutureWaitInfo[] waits = { new GpuFutureWaitInfo() };
waits[0].future.id = id;
GpuWaitStatus result = instance.waitAny(waits, 1, 10_000_000_000L);   // Up to 10 seconds, in nanoseconds.
if (result == GpuWaitStatus.SUCCESS && waits[0].completed) {
  // The callback has run.
}
```

Rules to know:

- The timeout is in **nanoseconds**. A timeout of 0 only checks ("polls") and never waits.
- A timeout above 0 needs the instance feature `TIMED_WAIT_ANY`, requested when you create the instance (see
  [The instance](instances-adapters-and-devices.md#the-instance)).
- `completed` on each `GpuFutureWaitInfo` tells you which futures finished. The result is `SUCCESS` when at least one
  did, and `TIMED_OUT` when none did in time.
- Keep the `GpuFutureWaitInfo` array in a field if you wait repeatedly; creating it allocates.

## Threads and callbacks

With `WAIT_ANY_ONLY` and `ALLOW_PROCESS_EVENTS`, a callback runs on the thread that calls `waitAny(...)` or
`processEvents()`. That is the simple, safe case.

With `ALLOW_SPONTANEOUS`, Dawn may run the callback on one of its own worker threads, a thread that Java has never seen.
The backends take care of the plumbing (the Android backend attaches such a thread to the Java virtual machine as a
daemon thread), but **your** code must be ready for it:

- Do not touch objects that your main thread is using at the same time without synchronization.
- Hand the result over safely, for example through a `volatile` field or a `synchronized` block, and let the main
  thread act on it.
- Keep the callback short.

The uncaptured error callback has no mode at all. It can run whenever an error happens, often inside the call that
caused it, and possibly on another thread, so treat it like an `ALLOW_SPONTANEOUS` callback.

On desktop, an exception that a callback throws is caught by the backend and handed to the thread's uncaught exception
handler, because an exception must never travel into native code. Your program keeps running, so log problems inside
your callbacks instead of relying on exceptions to stop anything.

Calling jwebgpu from inside a callback is fine. You can request the device from the adapter callback, or start the next
`mapAsync(...)` from a map callback.

## The browser

In the browser, every asynchronous call is a JavaScript promise underneath, and promises always finish from the
browser's event loop, after your current code has returned. So on the web:

- The **callback mode is ignored**. Callbacks always run from the event loop, on the one thread there is.
- `processEvents()` **does nothing**. It is safe to call, so portable code can keep calling it.
- `waitAny(...)` can **only poll**: a timeout of 0 works, and a timeout above 0 throws, because the browser cannot block.
- A loop that waits for a callback **never ends**: the callback can only run after your code returns to the browser.

The practical rule: write startup as a chain of callbacks (as in
[the startup class](instances-adapters-and-devices.md#a-startup-class-that-works-on-every-platform)), and drive your
frames from the browser's frame callback instead of a `while` loop.

## Callbacks without garbage

A lambda that captures variables is a new object every time the expression runs. Once at startup, that does not
matter. Every frame, it does. Store callbacks you use repeatedly in `final` fields:

```java
private final GpuBufferMapCallback onMapped = this::onMapped;
private volatile boolean mapped;

void requestReadback() {
  readback.mapAsync(GpuMapMode.READ, 0, size, GpuCallbackMode.ALLOW_PROCESS_EVENTS, onMapped);
}

private void onMapped(GpuMapAsyncStatus status, String message) {
  mapped = status == GpuMapAsyncStatus.SUCCESS;
}
```

On desktop and Android the backend keeps callbacks in a preallocated table and passes only a slot number to native code,
so registering a stored callback allocates nothing. See [Garbage-Free Usage](garbage-free.md).

## A pattern that works everywhere

Combine the pieces:

1. Use `ALLOW_PROCESS_EVENTS` for your asynchronous calls.
2. Call `instance.processEvents()` at the start of every frame (it does nothing on the web, and runs ready callbacks
   everywhere else).
3. Let callbacks only record what happened (set a field), and let your frame code act on it.
4. Never write a loop that waits for a callback in code that also runs on the web.

That gives you one code path, single-threaded callbacks on desktop and Android, and correct behavior in the browser.

## Common mistakes

- **Never calling `processEvents()`.** Callbacks with `ALLOW_PROCESS_EVENTS` then never run, and the program seems to
  hang.
- **Using `WAIT_ANY_ONLY` and then only calling `processEvents()`.** Those callbacks only run inside `waitAny(...)`.
- **A `waitAny` timeout above 0 without `TIMED_WAIT_ANY`.** Request the instance feature first. On the web, only a
  timeout of 0 works.
- **Touching main-thread state from an `ALLOW_SPONTANEOUS` callback without synchronization.**
- **Creating a capturing lambda every frame** for `mapAsync(...)` or `onSubmittedWorkDone(...)`. Keep it in a field.

## Check your understanding

1. You register a map callback with `ALLOW_PROCESS_EVENTS`, but it never runs. What did you forget?
2. Why is a `while (!done) { instance.processEvents(); }` loop fine on desktop but wrong on the web?
3. Which mode do you pick if the callback must run on your main thread, and why?

(Answers: to call `instance.processEvents()` (or `waitAny(...)`); on the web callbacks only run after your code returns
to the browser, so the loop keeps `done` from ever becoming true; `ALLOW_PROCESS_EVENTS`, because then callbacks only
run inside `processEvents()` or `waitAny(...)`, which you call from the main thread.)

**Next:** [Surfaces and Presenting](surfaces-and-presenting.md)
