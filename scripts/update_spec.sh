#!/usr/bin/env bash
# Downloads the pinned webgpu-headers specification files into spec/.
#
# Usage: scripts/update_spec.sh <webgpu-headers commit sha>
#
# The files are copied byte for byte from the given commit of
# https://github.com/webgpu-native/webgpu-headers. Never edit them by hand. After running this script,
# update spec/SPEC_VERSION.md (commit, Dawn commit, date) and review the generator test results.

set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <webgpu-headers commit sha>" >&2
  exit 1
fi

commit="$1"
if ! [[ "$commit" =~ ^[0-9a-f]{40}$ ]]; then
  echo "error: expected a full 40 character commit sha, got '$commit'" >&2
  exit 1
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
spec_dir="$repo_root/spec"
base="https://raw.githubusercontent.com/webgpu-native/webgpu-headers/$commit"

# Each entry is "remote path:local name".
files=(
  "webgpu.json:webgpu.json"
  "schema.json:schema.json"
  "webgpu.h:webgpu.h"
  "LICENSE:LICENSE-webgpu-headers"
)

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

# Download everything first so a failed download never leaves spec/ half updated.
for entry in "${files[@]}"; do
  remote="${entry%%:*}"
  local_name="${entry##*:}"
  echo "downloading $remote"
  curl --fail --silent --show-error --location "$base/$remote" --output "$tmp_dir/$local_name"
done

for entry in "${files[@]}"; do
  local_name="${entry##*:}"
  mv "$tmp_dir/$local_name" "$spec_dir/$local_name"
done

echo "updated $spec_dir to webgpu-headers $commit"
echo "next: edit spec/SPEC_VERSION.md and run ./gradlew :jwebgpu-test:test"
