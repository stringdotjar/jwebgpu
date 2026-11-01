# Scripts

This directory holds helper scripts that are run by hand or by CI. Planned and existing scripts:

- `build_natives.sh`: builds the Dawn native library for desktop platforms.
- `build_android_natives.sh`: builds the Dawn native library and the JNI glue for Android ABIs.
- `update_spec.sh`: downloads the pinned specification files into `spec/` (usage: `scripts/update_spec.sh <commit sha>`).
