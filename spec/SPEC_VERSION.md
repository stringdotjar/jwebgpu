# Vendored specifications

This directory holds the machine-readable WebGPU specification that the generator reads. The files are
vendored (copied into the repository) and pinned to an exact upstream commit, so every build produces the
same bindings no matter when it runs.

## What is vendored

| File | Used for |
|------|----------|
| `webgpu.json` | The primary input. The machine-readable twin of upstream's `webgpu.yml`, generated from it by upstream's own tooling. The generator turns it into the Java API, the FFM layer and the JNI layer. |
| `schema.json` | The JSON Schema that describes the structure of the specification. Kept as a reference for the shape of `webgpu.json`. |
| `webgpu.h` | The C header that upstream generates from the same specification. It is not read by the generator. Tests parse it as an independent reference that the generator's model must match exactly. |
| `LICENSE-webgpu-headers` | The BSD 3-Clause license of the files above. See also `THIRD_PARTY_NOTICES.md` in the repository root. |

The browser WebIDL for the TeaVM layer is not vendored yet.

## Pinned versions

| Item | Value |
|------|-------|
| Upstream repository | [webgpu-native/webgpu-headers](https://github.com/webgpu-native/webgpu-headers) |
| Upstream commit | `a1dc77409bb2b8bd80540cbf4b8db219e123e909` |
| Matching Dawn commit | `20734e3deac87a3f5c389ebdb808227be6832b44` |
| Date vendored | 2026-10-31 |

This is the webgpu-headers commit that Dawn's `DEPS` file tracks at the Dawn commit above. Dawn's own
`webgpu.h` (which is built from Dawn's `dawn.json`) is a strict superset of this header: every function,
struct, enum and constant here appears in Dawn's header with the same signature, layout and values, and Dawn
only adds extra entries. The generator therefore targets the upstream specification, and the natives are
built from Dawn at the matching commit.

The upstream code generator (`gen/` in webgpu-headers, written in Go) defines how `webgpu.h` is derived from
the specification: callback info structs, futures, `AddRef` and `Release` methods, struct chaining, enum value
numbering and more. The generator's resolver mirrors those rules. When updating, re-read `gen/gen.go` and
`gen/main.go` at the new commit and check whether any rule changed.

## How to update

1. Pick the webgpu-headers commit you want to move to. Prefer the commit that the target Dawn version tracks
   (look at the `webgpu-headers` entry in Dawn's `DEPS` file).
2. Run `scripts/update_spec.sh <full 40 character commit sha>`. It downloads `webgpu.json`, `schema.json`,
   `webgpu.h` and the license from that exact commit and replaces the copies in this directory. Do not edit
   vendored files by hand.
3. Update the tables above (upstream commit, Dawn commit, date).
4. Run `./gradlew compileJava :jwebgpu-test:test`. The conformance test compares the resolved model with
   `webgpu.h` and fails if they disagree, which usually means that upstream changed a generation rule.
   To also see what Dawn's own header adds (it is not vendored), pass the path of a Dawn-built `webgpu.h`:
   `./gradlew :jwebgpu-test:test -Pjwebgpu.dawnHeader=/path/to/webgpu.h`. The extras are printed, and the test fails
   only if a declaration that both headers share differs.
5. Commit the specification update on its own, separate from any generator changes.
