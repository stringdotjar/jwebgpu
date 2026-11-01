# Third-party notices

jwebgpu itself is released under the [MIT License](LICENSE). The files and projects below are third-party
material that is vendored into this repository or that jwebgpu is built on.

## webgpu-headers

- **Files:** `spec/webgpu.json`, `spec/schema.json` and `spec/webgpu.h`
- **Source:** <https://github.com/webgpu-native/webgpu-headers>, pinned to commit
  `a1dc77409bb2b8bd80540cbf4b8db219e123e909` (see [spec/SPEC_VERSION.md](spec/SPEC_VERSION.md))
- **License:** BSD 3-Clause, Copyright (c) 2019, "WebGPU native" developers. The full text is in
  [spec/LICENSE-webgpu-headers](spec/LICENSE-webgpu-headers).
- **Use:** The generator reads `webgpu.json` to produce the bindings. `webgpu.h` is only used by tests, as an
  independent reference that the generator's model must match.

The vendored files keep their original copyright and license headers.

## WebGPU specification (WebIDL)

- **Files:** `spec/webgpu.idl` and `spec/LICENSE-gpuweb`
- **Source:** <https://github.com/gpuweb/gpuweb>, pinned to commit `4e829b310ba7381034b93b974eb1a6aabfb464f7`
  (see [spec/SPEC_VERSION.md](spec/SPEC_VERSION.md)). The IDL is cut out of `spec/index.bs` and `spec/sections/copies.bs` of that commit.
- **License:** W3C Software and Document License, and the GPU for the Web 3-Clause BSD License. The full text is in
  [spec/LICENSE-gpuweb](spec/LICENSE-gpuweb).
- **Use:** The generator reads `webgpu.idl` to produce the web backend, which wraps the browser's own WebGPU objects.
  The browser itself is not part of this repository.

The vendored file keeps the copyright notice of the specification in its header.

## Dawn

- **Source:** <https://dawn.googlesource.com/dawn>, pinned to the commit in [scripts/dawn.version](scripts/dawn.version)
- **License:** BSD 3-Clause, Copyright 2017-2026 The Dawn & Tint Authors. (Dawn's repository also contains a few
  Apache License 2.0 files, such as code generator art templates and Android tooling; none of them are compiled into
  the library.)
- **Use:** The native WebGPU implementation that the desktop and Android backends load at runtime. Dawn's source
  code is not part of this repository. CI builds it into one shared library per platform with
  [scripts/build_natives.sh](scripts/build_natives.sh). Tint, the WGSL shader compiler, lives in the Dawn repository
  and is covered by the same license.

Whoever distributes a Dawn binary must reproduce the license texts of everything compiled into it, which are the
components below. Take the full text from the matching file in the Dawn checkout (`LICENSE` at its root, and
`third_party/<name>/LICENSE`), not from this summary.

### Components inside the Dawn library

These are the projects that the build linked into the library at the pinned commit (Linux x86_64 with the Vulkan and
Null backends; the Windows and macOS builds add the platform items marked below).

| Component | License | How it is included | License file in the Dawn checkout |
|-----------|---------|--------------------|-----------------------------------|
| Dawn and Tint | BSD 3-Clause | Compiled in | `LICENSE` |
| Abseil (`abseil-cpp`) | Apache License 2.0 | Compiled in (static libraries) | `third_party/abseil-cpp/LICENSE` |
| SPIRV-Tools (validator and optimizer) | Apache License 2.0 | Compiled in (static libraries) | `third_party/spirv-tools/src/LICENSE` |
| SPIRV-Headers | MIT (Khronos) | Headers compiled in | `third_party/spirv-headers/src/LICENSE` |
| Vulkan-Headers | Apache License 2.0 or MIT | Headers compiled in | `third_party/vulkan-headers/src/LICENSE.md` |
| Vulkan-Utility-Libraries | Apache License 2.0 | Headers compiled in | `third_party/vulkan-utility-libraries/src/LICENSE.md` |
| webgpu-headers | BSD 3-Clause | Headers compiled in | `third_party/webgpu-headers/src/LICENSE` |
| RenderDoc `renderdoc_app.h` | MIT | Single header compiled in | `third_party/renderdoc/LICENSE.md` |
| DirectX-Headers (Windows only) | MIT | Headers compiled in | `third_party/directx-headers/LICENSE` |
| GCC runtime (libstdc++ and libgcc, Linux only) | GPL 3.0 with the GCC Runtime Library Exception | Linked statically | Part of the GCC distribution |
| Microsoft Visual C++ runtime (Windows only) | Microsoft Visual Studio redistributable terms | Linked statically (`/MT`) | Part of Visual Studio |

Notes:

- The build tools Dawn needs only while compiling (Jinja2, MarkupSafe, glslang, and the like) are not part of the
  binary. glslang, GLFW, protobuf and googletest are not even built, because the matching Dawn options are switched
  off.
- The Vulkan loader (`libvulkan`), the system graphics drivers, X11, Wayland, `d3dcompiler_47.dll` and the Metal
  frameworks are not bundled. The library finds them on the user's machine at run time.
