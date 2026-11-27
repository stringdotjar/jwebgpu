# The jwebgpu Handbook

Welcome! This handbook is the main guide to using jwebgpu, the Java bindings for WebGPU that run the same rendering
code on desktop (Windows, macOS, and Linux), on Android, and in the browser. It explains every part of the bindings in
plain language, with analogies, working code examples, and short quizzes, ordered from what you need on day one to
what is good to know later.

## Table of contents

1. [How to use this handbook](#how-to-use-this-handbook)
2. [Suggested reading paths](#suggested-reading-paths)
3. [Part 1: Foundations](#part-1-foundations)
4. [Part 2: Talking to the GPU](#part-2-talking-to-the-gpu)
5. [Part 3: Drawing and computing](#part-3-drawing-and-computing)
6. [Part 4: Robust and fast programs](#part-4-robust-and-fast-programs)
7. [Part 5: Platforms](#part-5-platforms)
8. [Appendices](#appendices)
9. [Other documentation](#other-documentation)

## How to use this handbook

- **Every guide stands on its own.** Each one starts with its own table of contents and links to related guides when
  it touches their topics, so you can jump straight to what you need.
- **Guides follow the same shape:** why the feature exists, the mental model (usually an analogy), how to use it with
  real code, common mistakes, and a short "Check your understanding" quiz with answers.
- **Code examples use the real jwebgpu API.** Every class, field, and method shown exists in `jwebgpu-api` or in a
  backend module. Imports are shown in longer examples; shorter snippets assume the usual
  `io.github.stringdotjar.jwebgpu` imports.
- **The [Cookbook](cookbook.md)** answers quick "How do I...?" questions in one or two lines each, the
  [Glossary](glossary.md) defines every term, and [Troubleshooting](troubleshooting.md) collects the common errors.
- **The Javadoc goes deeper.** Every generated class carries the documentation of the WebGPU specification, and the
  names match the browser's WebGPU API, so the [MDN WebGPU pages](https://developer.mozilla.org/en-US/docs/Web/API/WebGPU_API)
  apply almost one to one. This handbook is the map; the Javadoc is the street-level view.

## Suggested reading paths

**Brand new to WebGPU (or to GPU programming):** read Part 1 in order, then [Instances, Adapters, and Devices](instances-adapters-and-devices.md),
[Asynchronous Calls and Callbacks](async-and-callbacks.md), and [Render Passes](render-passes.md). You can then draw a
triangle and build from there. Dip into everything else as you need it.

**Coming from WebGPU in JavaScript or from `webgpu.h` in C:** skim [Architecture for Users](architecture.md), which
shows how the names map, then read [Garbage-Free Usage](garbage-free.md), which covers the biggest difference: jwebgpu
is designed so that per-frame code creates no garbage.

**Targeting old or low-end hardware:** [Compatibility Mode and Backend Selection](compatibility-mode.md), then the
[Platform Guide](platforms.md).

**Experienced and in a hurry:** the [Cookbook](cookbook.md), then [Architecture for Users](architecture.md).

## Part 1: Foundations

The core ideas everything else builds on. Read these first, in order.

| # | Guide | What you will learn |
|---|-------|---------------------|
| 1 | [What jwebgpu Is: The Mental Model](introduction.md) | The kitchen analogy, the objects of WebGPU, the rules of the house, and the modules. |
| 2 | [Getting Started: Dependencies, Backends, and a First Program](getting-started.md) | Maven Central coordinates, natives jars, installing a backend, and native access. |
| 3 | [Architecture for Users: The API, the Backends, and the Names](architecture.md) | The front desk analogy, typed wrappers, descriptors, chains, enums, and flags. |

## Part 2: Talking to the GPU

How a program finds a GPU, waits for it, and shows pictures on the screen.

| # | Guide | What you will learn |
|---|-------|---------------------|
| 4 | [Instances, Adapters, and Devices](instances-adapters-and-devices.md) | Requesting an adapter, reading its info, features and limits, and requesting a device. |
| 5 | [Asynchronous Calls and Callbacks](async-and-callbacks.md) | Callback modes, `processEvents`, `waitAny`, futures, threads, and the browser. |
| 6 | [Surfaces and Presenting](surfaces-and-presenting.md) | Window handles on desktop, Android surfaces, web canvases, configuring, and the present loop. |

## Part 3: Drawing and computing

Resources, programs, and the commands that use them.

| # | Guide | What you will learn |
|---|-------|---------------------|
| 7 | [Buffers and Textures](buffers-and-textures.md) | Usages, uploads, mapping and reading back, textures, views, samplers, and copies. |
| 8 | [Shaders and Pipelines](shaders-and-pipelines.md) | WGSL modules, compilation messages, render and compute pipelines, layouts, and bind groups. |
| 9 | [Render Passes: A Full Triangle Walkthrough](render-passes.md) | Encoders, passes, attachments, vertex and index buffers, depth, and submitting. |
| 10 | [Compute](compute.md) | Storage buffers, workgroups, dispatching, and reading results back. |

## Part 4: Robust and fast programs

Handling what goes wrong, and keeping frames free of garbage.

| # | Guide | What you will learn |
|---|-------|---------------------|
| 11 | [Error Handling and Device Loss](errors-and-device-loss.md) | Uncaptured errors, error scopes, released objects, and recovering from a lost device. |
| 12 | [Garbage-Free Usage](garbage-free.md) | Reusable descriptors, refill overloads, callbacks in fields, uploads, and scratch memory. |

## Part 5: Platforms

What is the same everywhere, and what is not.

| # | Guide | What you will learn |
|---|-------|---------------------|
| 13 | [Platform Guide: Desktop, Android, and Web](platforms.md) | Requirements, natives, GraalVM, Android ABIs, TeaVM, and what the browser does not support. |
| 14 | [Compatibility Mode and Backend Selection](compatibility-mode.md) | Feature levels, backend types, OpenGL and OpenGL ES, compatibility limits, and a fallback pattern. |

## Appendices

| Guide | What it is |
|-------|------------|
| ["How Do I...?" Cookbook](cookbook.md) | Quick answers to common questions, grouped by topic. |
| [Glossary](glossary.md) | Plain-language definitions of every term in the handbook. |
| [Troubleshooting](troubleshooting.md) | Common error messages and symptoms, and how to fix them. |

## Other documentation

- **The Javadoc.** Every package has a guide in its `package-info.java`, and every generated class carries the text of
  the WebGPU specification.
- **[`README.md`](../README.md)**: the project overview and the quick start.
- **[`ARCHITECTURE.md`](../ARCHITECTURE.md)**: how the generator and the backends work inside, for contributors.
- **[`COMPILING.md`](../COMPILING.md)**: building jwebgpu, the Dawn natives, and the tests.
- **[`CONTRIBUTING.md`](../CONTRIBUTING.md)**: how to contribute.

Have fun making something great!
