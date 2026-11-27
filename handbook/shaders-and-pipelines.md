# Shaders and Pipelines

Shaders are the small programs the GPU runs, and pipelines bundle a shader with all the fixed settings it needs. Bind
groups then connect a pipeline to your buffers, textures, and samplers. This guide covers all three, with the layouts
that tie them together.

## Table of contents

1. [Why pipelines exist](#why-pipelines-exist)
2. [The mental model: recipe cards and ingredient trays](#the-mental-model-recipe-cards-and-ingredient-trays)
3. [Shader modules and WGSL](#shader-modules-and-wgsl)
4. [Checking compiler messages](#checking-compiler-messages)
5. [Render pipelines](#render-pipelines)
6. [Blending and depth](#blending-and-depth)
7. [Compute pipelines](#compute-pipelines)
8. [Bind group layouts and pipeline layouts](#bind-group-layouts-and-pipeline-layouts)
9. [Bind groups](#bind-groups)
10. [Creating pipelines in the background](#creating-pipelines-in-the-background)
11. [Override constants](#override-constants)
12. [Common mistakes](#common-mistakes)
13. [Check your understanding](#check-your-understanding)

## Why pipelines exist

Turning shader code and drawing settings into something a GPU can run is expensive: the driver compiles the shader and
bakes in the vertex layout, blending, depth testing, and the output format. Older APIs did this lazily, in the middle of
a draw call, which caused stutters. WebGPU makes you do it up front: you create a **pipeline** once, at load time, and
switching between ready-made pipelines while drawing is cheap.

## The mental model: recipe cards and ingredient trays

- A **shader module** is a cookbook: a file of WGSL code with one or more entry points (recipes).
- A **pipeline** is a laminated recipe card: "use the `vs` recipe for shaping and the `fs` recipe for coloring, cut
  the vegetables this way (vertex layout), serve on this kind of plate (color format)". It never changes once printed.
- A **bind group layout** is the shape of the ingredient tray the recipe expects: "slot 0 holds a uniform buffer, slot
  1 a texture, slot 2 a sampler".
- A **bind group** is a filled tray: this buffer, this texture, this sampler.

At draw time you pick a recipe card (`setPipeline`) and slide in trays (`setBindGroup`). Trays can be swapped between
draws without printing a new card.

## Shader modules and WGSL

WebGPU's shading language is **WGSL**. A shader module is created from WGSL source, chained onto the descriptor:

```java
private static final String WGSL = """
    struct Uniforms { tint : vec4f };
    @group(0) @binding(0) var<uniform> uniforms : Uniforms;

    @vertex fn vs(@location(0) position : vec2f) -> @builtin(position) vec4f {
      return vec4f(position, 0.0, 1.0);
    }

    @fragment fn fs() -> @location(0) vec4f {
      return uniforms.tint;
    }
    """;

GpuShaderSourceWGSL source = new GpuShaderSourceWGSL();
source.code = WGSL;
GpuShaderModuleDescriptor descriptor = new GpuShaderModuleDescriptor();
descriptor.label = "tinted shader";
descriptor.nextInChain = source;
GpuShaderModule shader = device.createShaderModule(descriptor);
```

Java text blocks (`"""`) are a comfortable way to keep short shaders next to the code that uses them; longer ones can
be loaded from resources. The module can be released once every pipeline that uses it has been created.

On desktop and Android, Dawn also accepts SPIR-V through `GpuShaderSourceSPIRV` (with `code` as an `int[]` and
`codeSize` in words) when the instance offers the `SHADER_SOURCE_SPIRV` feature. The browser only accepts WGSL, so
portable programs use WGSL.

## Checking compiler messages

A shader with a mistake still gives you a module, but every pipeline built from it fails, and the error goes to the
uncaptured error callback. To see the compiler's messages with line numbers, ask for them:

```java
shader.getCompilationInfo(GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, info) -> {
  for (int i = 0; i < info.messageCount; i++) {
    GpuCompilationMessage m = info.messages[i];
    System.out.println(m.type + " at line " + m.lineNum + ", column " + m.linePos + ": " + m.message);
  }
});
```

`type` is `ERROR`, `WARNING`, or `INFO`. Calling this during development, right after creating each module, catches
typos early.

## Render pipelines

A render pipeline describes the whole drawing process: the vertex stage, how vertices form shapes, and the fragment
stage that colors pixels.

```java
GpuRenderPipelineDescriptor descriptor = new GpuRenderPipelineDescriptor();
descriptor.label = "tinted triangles";

// Vertex stage: one buffer of vec2f positions.
descriptor.vertex.module = shader;
descriptor.vertex.entryPoint = "vs";
descriptor.vertex.bufferCount = 1;
GpuVertexBufferLayout buffer = descriptor.vertex.buffers[0];
buffer.arrayStride = 2 * Float.BYTES;   // Bytes from one vertex to the next.
buffer.stepMode = GpuVertexStepMode.VERTEX;
buffer.attributeCount = 1;
GpuVertexAttribute position = buffer.attributes[0];
position.format = GpuVertexFormat.FLOAT32X2;
position.offset = 0;
position.shaderLocation = 0;            // Matches @location(0) in the shader.

// How vertices become shapes.
descriptor.primitive.topology = GpuPrimitiveTopology.TRIANGLE_LIST;
descriptor.primitive.cullMode = GpuCullMode.NONE;

// Fragment stage: one color target in the surface format.
GpuFragmentState fragment = new GpuFragmentState();
fragment.module = shader;
fragment.entryPoint = "fs";
fragment.targetCount = 1;
GpuColorTargetState target = fragment.targets[0];
target.format = surfaceFormat;
target.writeMask = GpuColorWriteMask.ALL;
descriptor.fragment = fragment;

GpuRenderPipeline pipeline = device.createRenderPipeline(descriptor);
```

Things to notice:

- `vertex` is always there (a `final` field); `fragment` is optional (a nullable field) because depth-only passes need
  no fragment stage. Create the `GpuFragmentState` once and assign it.
- A vertex buffer layout has room for 4 attributes by default, and the vertex state of a render pipeline descriptor
  has room for 1 buffer. Because `vertex` is a `final` field, you cannot pass it a bigger capacity; instead, replace
  its array once, at setup, with a bigger one whose elements you create yourself:

  ```java
  descriptor.vertex.buffers = new GpuVertexBufferLayout[] { new GpuVertexBufferLayout(), new GpuVertexBufferLayout() };
  descriptor.vertex.bufferCount = 2;
  ```
- `layout` is `null` here, which means **automatic layout**: WebGPU works out the bind group layouts from the shader.
  `pipeline.getBindGroupLayout(0)` gives you the layout of group 0, ready for a bind group. Automatic layouts are
  convenient for one pipeline; when several pipelines share bind groups, use explicit layouts (below).
- A shader that draws without vertex buffers (it computes positions from `@builtin(vertex_index)`) simply leaves
  `bufferCount` at 0.

## Blending and depth

**Blending** mixes the new color with what is already in the target, which is how transparency works. Classic alpha
blending:

```java
GpuBlendState blend = new GpuBlendState();
blend.color.operation = GpuBlendOperation.ADD;
blend.color.srcFactor = GpuBlendFactor.SRC_ALPHA;
blend.color.dstFactor = GpuBlendFactor.ONE_MINUS_SRC_ALPHA;
blend.alpha.operation = GpuBlendOperation.ADD;
blend.alpha.srcFactor = GpuBlendFactor.ONE;
blend.alpha.dstFactor = GpuBlendFactor.ONE_MINUS_SRC_ALPHA;
target.blend = blend;
```

**Depth testing** keeps near things in front of far things in 3D. The pipeline describes the test, and the render pass
provides the depth texture (see [Render Passes](render-passes.md#adding-a-depth-buffer)):

```java
GpuDepthStencilState depth = new GpuDepthStencilState();
depth.format = GpuTextureFormat.DEPTH24_PLUS;
depth.depthWriteEnabled = GpuOptionalBool.TRUE;
depth.depthCompare = GpuCompareFunction.LESS;
descriptor.depthStencil = depth;
```

**Multisampling** (smoother edges) is `descriptor.multisample.count = 4`, with a 4-sample render target and a resolve
target in the render pass.

## Compute pipelines

A compute pipeline has only one stage:

```java
GpuComputePipelineDescriptor descriptor = new GpuComputePipelineDescriptor();
descriptor.label = "double the numbers";
descriptor.compute.module = shader;
descriptor.compute.entryPoint = "main";
GpuComputePipeline pipeline = device.createComputePipeline(descriptor);
```

The [Compute](compute.md) guide shows how to use it.

## Bind group layouts and pipeline layouts

An explicit bind group layout spells out the tray: which binding numbers exist, what kind of resource each one holds,
and which shader stages see it.

```java
GpuBindGroupLayoutDescriptor descriptor = new GpuBindGroupLayoutDescriptor(3);
descriptor.label = "material";
descriptor.entryCount = 3;

GpuBindGroupLayoutEntry uniforms = descriptor.entries[0];
uniforms.binding = 0;
uniforms.visibility = GpuShaderStage.VERTEX | GpuShaderStage.FRAGMENT;
uniforms.buffer.type = GpuBufferBindingType.UNIFORM;

GpuBindGroupLayoutEntry image = descriptor.entries[1];
image.binding = 1;
image.visibility = GpuShaderStage.FRAGMENT;
image.texture.sampleType = GpuTextureSampleType.FLOAT;
image.texture.viewDimension = GpuTextureViewDimension.D2;

GpuBindGroupLayoutEntry filter = descriptor.entries[2];
filter.binding = 2;
filter.visibility = GpuShaderStage.FRAGMENT;
filter.sampler.type = GpuSamplerBindingType.FILTERING;

GpuBindGroupLayout materialLayout = device.createBindGroupLayout(descriptor);
```

Each entry has a `buffer`, a `texture`, a `sampler`, and a `storageTexture` part, all already created; fill in the one
that applies and leave the others alone. Then a pipeline layout lists the bind group layouts by group number:

```java
GpuPipelineLayoutDescriptor layoutDescriptor = new GpuPipelineLayoutDescriptor();
layoutDescriptor.bindGroupLayoutCount = 1;
layoutDescriptor.bindGroupLayouts[0] = materialLayout;   // @group(0)
GpuPipelineLayout layout = device.createPipelineLayout(layoutDescriptor);

pipelineDescriptor.layout = layout;
```

Every pipeline created with this layout accepts the same bind groups.

## Bind groups

A bind group fills the tray with real resources. Binding numbers match the layout and the shader:

```java
GpuBindGroupDescriptor descriptor = new GpuBindGroupDescriptor(3);
descriptor.label = "brick material";
descriptor.layout = materialLayout;   // Or pipeline.getBindGroupLayout(0) with an automatic layout.
descriptor.entryCount = 3;

descriptor.entries[0].binding = 0;
descriptor.entries[0].buffer = uniformBuffer;
descriptor.entries[0].offset = 0;
descriptor.entries[0].size = 16;

descriptor.entries[1].binding = 1;
descriptor.entries[1].textureView = brickView;

descriptor.entries[2].binding = 2;
descriptor.entries[2].sampler = sampler;

GpuBindGroup material = device.createBindGroup(descriptor);
```

An entry sets exactly one of `buffer` (with `offset` and `size` in bytes), `textureView`, or `sampler`. When drawing:

```java
passEncoder.setBindGroup(0, material);
```

Bind groups are cheap to switch but not free to create, so create them at load time and keep them. If a buffer's
contents change, you do not need a new bind group: the bind group points at the buffer, and `queue.writeBuffer(...)`
updates what it sees.

**Dynamic offsets** let one bind group point at different slices of one big uniform buffer: set
`hasDynamicOffset = true` on the layout entry, and pass the offsets when binding, from an `int[]` you keep in a field:
`passEncoder.setBindGroup(0, group, offsets, 1)`.

## Creating pipelines in the background

Pipeline creation can take a while (many milliseconds for a big shader). The asynchronous versions compile without
blocking your thread, which helps loading screens:

```java
device.createRenderPipelineAsync(descriptor, GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, result, message) -> {
  if (status == GpuCreatePipelineAsyncStatus.SUCCESS) {
    pipeline = result;
  } else {
    System.out.println("Pipeline failed (" + status + "): " + message);
  }
});
```

`device.createComputePipelineAsync(...)` works the same way.

## Override constants

WGSL `override` declarations are constants you set when creating the pipeline, without editing the shader. Each stage
has a `constants` array and a `constantCount`. You create the fragment state yourself, so give it a capacity:

```java
GpuFragmentState fragment = new GpuFragmentState(1, 1);   // Room for 1 constant and 1 target.
fragment.constantCount = 1;
fragment.constants[0].key = "brightness";
fragment.constants[0].value = 1.5;
```

The vertex stage of a render pipeline and the compute stage of a compute pipeline are `final` fields that start with
room for no constants, so replace their array once, at setup:
`descriptor.compute.constants = new GpuConstantEntry[] { new GpuConstantEntry() };`.

## Common mistakes

- **The color target format does not match the render pass target.** The pipeline's `targets[0].format` must equal
  the format of the view you draw into (for a window, the configured surface format).
- **`shaderLocation` or `@binding` numbers that do not match the shader.** The uncaptured error callback names the
  mismatch.
- **Forgetting to set a count** (`bufferCount`, `attributeCount`, `targetCount`, `entryCount`). A count of 0 means
  "none", whatever is in the array.
- **Creating pipelines or bind groups every frame.** Create them once, at load time.
- **Using `pipeline.getBindGroupLayout(...)` of one pipeline for bind groups of another.** Automatic layouts are tied
  to their pipeline; share explicit layouts instead.

## Check your understanding

1. Why does WebGPU make you create pipelines up front instead of setting state while drawing?
2. Your uniform buffer changes every frame. Do you need a new bind group every frame?
3. What does `layout = null` on a pipeline descriptor mean, and when should you avoid it?

(Answers: compiling shaders and baking state is expensive, and doing it in the middle of drawing causes stutters;
no, the bind group refers to the buffer, so `queue.writeBuffer(...)` is enough; it means an automatic layout derived
from the shader, which you should avoid when several pipelines need to share bind groups.)

**Next:** [Render Passes](render-passes.md)
