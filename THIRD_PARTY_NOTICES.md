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

## Dawn

- **Source:** <https://dawn.googlesource.com/dawn>
- **License:** Apache License 2.0 (with additional notices for its own dependencies)
- **Use:** The native WebGPU implementation that the desktop and Android backends load at runtime. Dawn's source
  code is not part of this repository. Whoever distributes a Dawn binary must also ship Dawn's license and the
  notices of Dawn's dependencies alongside it.
