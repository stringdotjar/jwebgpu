# Vendored specifications

This directory will hold the machine-readable WebGPU specifications that the generator reads. The files
are vendored (copied into the repository) and pinned to exact upstream commits, so every build produces
the same bindings no matter when it runs.

**Status:** placeholder. No specification files have been vendored yet.

## Planned contents

| File | Upstream | Used for |
|------|----------|----------|
| `webgpu.yml` | [webgpu-headers](https://github.com/webgpu-native/webgpu-headers) (the source of truth for `webgpu.h`) | The Java API, the FFM layer and the JNI layer |
| `webgpu.idl` | The WebGPU WebIDL published with the [W3C WebGPU specification](https://www.w3.org/TR/webgpu/) | The browser (TeaVM) layer |
| `dawn.json` (only if needed) | [Dawn](https://dawn.googlesource.com/dawn) | Dawn-specific extensions that are not in `webgpu.yml` |

## Pinned versions

| File | Upstream commit | Date vendored |
|------|-----------------|---------------|
| (none yet) | | |

## How to update a specification

1. Pick the upstream commit or release tag you want to move to. Prefer a commit that matches the Dawn
   version the native libraries are built from.
2. Download the file from that exact commit and replace the copy in this directory. Do not edit vendored
   files by hand.
3. Update the table above with the new commit and date.
4. Run `./gradlew compileJava :jwebgpu-test:test` and review the generated differences reported by the
   golden-file tests. Update the golden files only after you have read the changes.
5. Commit the specification update on its own, separate from any generator changes.
