# Scripts

This directory holds helper scripts that are run by hand or by CI. Planned and existing scripts:

- `build_natives.sh`: builds the Dawn native library for desktop and Android targets (usage: `scripts/build_natives.sh <target>`, see COMPILING.md).
- `dawn.version`: pins the Dawn revision that every native library is built from.
- `natives/`: linker export lists that keep the native library's public surface to the `wgpu*` functions.
- `update_spec.sh`: downloads the pinned specification files into `spec/` (usage: `scripts/update_spec.sh <webgpu-headers commit sha> [<gpuweb commit sha>]`; the second commit refreshes the browser WebIDL).
- `run_browser_test.mjs`: serves a folder on localhost and runs pages in a headless Chrome with a software WebGPU adapter, reporting what they print (usage: `node scripts/run_browser_test.mjs <folder> <page.html>...`). It skips itself when there is no Chrome.
