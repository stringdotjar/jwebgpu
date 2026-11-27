# Compute

The GPU is not only for pictures. Compute shaders run any calculation that splits into many small, independent jobs:
physics for thousands of particles, image filters, simulations, culling. This guide explains workgroups, storage
buffers, and dispatching, and walks through a complete example that doubles a list of numbers on the GPU and reads the
answer back.

## Table of contents

1. [Why compute on the GPU](#why-compute-on-the-gpu)
2. [The mental model: an exam hall](#the-mental-model-an-exam-hall)
3. [The shader](#the-shader)
4. [Buffers for input, output, and readback](#buffers-for-input-output-and-readback)
5. [The pipeline and the bind group](#the-pipeline-and-the-bind-group)
6. [Dispatching](#dispatching)
7. [Reading the result](#reading-the-result)
8. [Compute every frame](#compute-every-frame)
9. [Limits worth knowing](#limits-worth-knowing)
10. [Common mistakes](#common-mistakes)
11. [Check your understanding](#check-your-understanding)

## Why compute on the GPU

A CPU has a handful of fast cores. A GPU has thousands of slower ones that like doing the same thing to different data.
If you need to update a million particles, a loop on the CPU does them one by one, while the GPU does thousands at the
same time. The price is that the data must live on the GPU, and that the work must not depend on the order in which
the jobs run.

## The mental model: an exam hall

Picture an exam hall with rows of tables:

- Each **invocation** is one student answering one question (one element of your data).
- A **workgroup** is one table of students. Students at the same table can share notes (`var<workgroup>` memory) and
  wait for each other (`workgroupBarrier()`). The table size is fixed in the shader with `@workgroup_size`.
- A **dispatch** says how many tables to fill. Tables work in any order, and students at different tables cannot talk.

If your data has 1,000 elements and a table seats 64, you need `ceil(1000 / 64) = 16` tables, and the last table has
some empty seats, so the shader checks that its index is in range.

## The shader

```java
private static final String WGSL = """
    @group(0) @binding(0) var<storage, read_write> data : array<f32>;

    @compute @workgroup_size(64)
    fn main(@builtin(global_invocation_id) id : vec3u) {
      if (id.x < arrayLength(&data)) {
        data[id.x] = data[id.x] * 2.0;
      }
    }
    """;
```

`global_invocation_id` is the student's seat number across the whole hall. `var<storage, read_write>` is a storage
buffer that the shader can read and write.

## Buffers for input, output, and readback

The shader works on a `STORAGE` buffer. To get the results back to Java, you copy them into a second buffer that can
be mapped, because a mappable buffer cannot also be a storage buffer (see
[Buffers and Textures](buffers-and-textures.md#usages-telling-the-gpu-what-a-resource-is-for)):

```java
private static final int COUNT = 1000;
private static final long BYTES = COUNT * Float.BYTES;

private final float[] numbers = new float[COUNT];

GpuBufferDescriptor sd = new GpuBufferDescriptor();
sd.label = "numbers";
sd.usage = GpuBufferUsage.STORAGE | GpuBufferUsage.COPY_DST | GpuBufferUsage.COPY_SRC;
sd.size = BYTES;
GpuBuffer storage = device.createBuffer(sd);

GpuBufferDescriptor rd = new GpuBufferDescriptor();
rd.label = "numbers readback";
rd.usage = GpuBufferUsage.MAP_READ | GpuBufferUsage.COPY_DST;
rd.size = BYTES;
GpuBuffer readback = device.createBuffer(rd);

for (int i = 0; i < COUNT; i++) {
  numbers[i] = i;
}
queue.writeBuffer(storage, 0, numbers, 0, COUNT);
```

## The pipeline and the bind group

```java
GpuShaderSourceWGSL source = new GpuShaderSourceWGSL();
source.code = WGSL;
GpuShaderModuleDescriptor smd = new GpuShaderModuleDescriptor();
smd.nextInChain = source;
GpuShaderModule shader = device.createShaderModule(smd);

GpuComputePipelineDescriptor cpd = new GpuComputePipelineDescriptor();
cpd.label = "double";
cpd.compute.module = shader;
cpd.compute.entryPoint = "main";
GpuComputePipeline pipeline = device.createComputePipeline(cpd);   // layout is null: automatic.

GpuBindGroupLayout layout = pipeline.getBindGroupLayout(0);
GpuBindGroupDescriptor bgd = new GpuBindGroupDescriptor(1);
bgd.layout = layout;
bgd.entryCount = 1;
bgd.entries[0].binding = 0;
bgd.entries[0].buffer = storage;
bgd.entries[0].offset = 0;
bgd.entries[0].size = BYTES;
GpuBindGroup bindGroup = device.createBindGroup(bgd);
```

For explicit layouts, the entry's `buffer.type` is `GpuBufferBindingType.STORAGE` (or `READ_ONLY_STORAGE`) and its
`visibility` is `GpuShaderStage.COMPUTE`. See
[Bind group layouts and pipeline layouts](shaders-and-pipelines.md#bind-group-layouts-and-pipeline-layouts).

## Dispatching

A compute pass is recorded like a render pass, but instead of drawing you **dispatch** workgroups:

```java
GpuCommandEncoder encoder = device.createCommandEncoder(null);
GpuComputePassEncoder cp = encoder.beginComputePass(null);
cp.setPipeline(pipeline);
cp.setBindGroup(0, bindGroup);
cp.dispatchWorkgroups((COUNT + 63) / 64, 1, 1);   // 16 workgroups of 64.
cp.end();
encoder.copyBufferToBuffer(storage, 0, readback, 0, BYTES);
GpuCommandBuffer commands = encoder.finish(null);
queue.submit(new GpuCommandBuffer[] { commands }, 1);
```

`(COUNT + 63) / 64` is the integer way to round up. The three arguments are workgroup counts in x, y, and z; images
often use two dimensions (`dispatchWorkgroups(width / 8, height / 8, 1)` with `@workgroup_size(8, 8)`).
`dispatchWorkgroupsIndirect(buffer, offset)` reads the three counts from a buffer instead, so a previous pass can decide
how much work there is.

## Reading the result

```java
readback.mapAsync(GpuMapMode.READ, 0, BYTES, GpuCallbackMode.ALLOW_PROCESS_EVENTS, (status, message) -> {
  if (status == GpuMapAsyncStatus.SUCCESS) {
    readback.readMappedRange(0, numbers, 0, COUNT);
    System.out.println("numbers[10] = " + numbers[10]);   // 20.0
  }
  readback.unmap();
});
```

Pump `instance.processEvents()` until the callback has run (on the web, just let the browser call it).

## Compute every frame

Simulations often run a compute pass every frame, and then draw the results. Both can go into one encoder: first the
compute pass, then the render pass that reads the same buffer as a vertex or storage buffer. The GPU runs them in
order, so the draw sees the updated data. As with rendering, keep the descriptors in fields and refill the wrappers:

```java
device.createCommandEncoder(null, encoder);
encoder.beginComputePass(null, computePass);
computePass.setPipeline(simulate);
computePass.setBindGroup(0, particles);
computePass.dispatchWorkgroups(groups, 1, 1);
computePass.end();
encoder.beginRenderPass(pass, renderPass);
// ... draw the particles ...
renderPass.end();
encoder.finish(null, commands);
```

Avoid reading results back every frame if you can: waiting for a map makes the CPU and GPU take turns instead of
working at the same time. When you must, keep two readback buffers and read the one from the previous frame.

## Limits worth knowing

Read them from `device.getLimits(limits)`:

| Limit | Meaning | Default |
|-------|---------|---------|
| `maxComputeInvocationsPerWorkgroup` | Students per table (x times y times z). | 256 |
| `maxComputeWorkgroupSizeX`, `Y`, `Z` | Table size per dimension. | 256, 256, 64 |
| `maxComputeWorkgroupsPerDimension` | Tables per dispatch, per dimension. | 65535 |
| `maxComputeWorkgroupStorageSize` | Bytes of `var<workgroup>` memory per table. | 16384 |
| `maxStorageBufferBindingSize` | Largest storage buffer binding, in bytes. | 128 MiB |
| `maxStorageBuffersPerShaderStage` | Storage buffers one shader stage may use. | 8 |

In [compatibility mode](compatibility-mode.md#compatibility-limits), the default limits are lower, and vertex and
fragment shaders have extra limits on storage buffers and storage textures (possibly zero). Compute shaders are not
affected by those extra limits, which makes compute the safest home for storage buffers on old hardware.

## Common mistakes

- **No range check in the shader.** The last workgroup has empty seats; without `if (id.x < ...)` they write past the
  end of your data.
- **Dispatching elements instead of workgroups.** `dispatchWorkgroups(1000, 1, 1)` with `@workgroup_size(64)` runs
  64,000 invocations. Divide by the workgroup size and round up.
- **Making the storage buffer mappable.** Copy into a separate `MAP_READ` buffer.
- **Forgetting `COPY_SRC` on the storage buffer**, which the copy to the readback buffer needs.
- **Reading back every frame and waiting for it**, which stalls the pipeline.

## Check your understanding

1. You have 5,000 elements and `@workgroup_size(128)`. How many workgroups do you dispatch?
2. Why is the shader's `if (id.x < arrayLength(&data))` needed?
3. Why can the compute pass and the render pass that draws its results share one command encoder?

(Answers: `(5000 + 127) / 128 = 40`; because 40 times 128 is 5,120, so 120 invocations have no element to work on;
commands in a command buffer run in order, so the draw sees what the compute pass wrote.)

**Next:** [Error Handling and Device Loss](errors-and-device-loss.md)
