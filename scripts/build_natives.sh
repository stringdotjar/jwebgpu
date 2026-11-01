#!/usr/bin/env bash
#
# Builds Google Dawn as one shared library that exposes the webgpu.h C API, and copies it into the
# desktop backend's bundled-native resources. Run it from anywhere:
#
#   ./scripts/build_natives.sh linux-x86_64
#
# Targets:
#   linux-x86_64, linux-aarch64      Vulkan (X11 and Wayland surfaces) and Null.
#   windows-x86_64, windows-aarch64  Direct3D 12, Direct3D 11, Vulkan and Null.
#   macos-x86_64, macos-aarch64      Metal and Null.
#   android-arm64-v8a, android-x86_64
#                                    Vulkan and Null, cross compiled with the Android NDK from Linux
#                                    or macOS. Only 64-bit ABIs are built, because the generated
#                                    struct layouts assume 64-bit pointers.
#   fetch                            Only downloads the Dawn sources and their dependencies.
#
# A desktop target must be built on its own platform, except the two macOS targets, which can be cross
# compiled from either Mac architecture.
#
# Prerequisites (see COMPILING.md for the full list):
#   - git, python3, cmake 3.22+, and Ninja.
#   - Linux: g++ 13+ (or clang 16+) and the X11 development headers (libx11-dev and libx11-xcb-dev).
#   - Windows: Visual Studio 2022 Build Tools, run from a shell where cl.exe is on PATH
#     (for example after vcvarsall.bat, or the ilammy/msvc-dev-cmd action in CI).
#   - macOS: Xcode command line tools.
#   - Android: the Android NDK (r28 or newer), found through JWEBGPU_ANDROID_NDK, ANDROID_NDK_HOME,
#     ANDROID_NDK_ROOT, or ~/Android/Sdk/ndk/28.2.13676358.
#
# Environment variables (all optional):
#   JWEBGPU_DAWN_CACHE  Where Dawn sources and build trees live. Default: ~/.cache/jwebgpu/dawn
#   JWEBGPU_JOBS        Number of parallel compile jobs. Default: the number of CPU cores.
#   JWEBGPU_X11         ON or OFF. Linux only. Default: ON. Turn it off if libx11-xcb-dev is missing.
#   JWEBGPU_WAYLAND     ON or OFF. Linux only. Default: ON. It needs no extra packages.
#   JWEBGPU_OUT_DIR     Where the finished library is copied. Default: the target's folder under
#                       jwebgpu-ffm/src/main/resources/natives.
#   JWEBGPU_MAX_GLIBC   Linux only. Fails the build if the library needs a newer glibc than this
#                       (for example 2.28). Default: no limit.
#   JWEBGPU_ANDROID_API Lowest Android API level the library supports. Default: 26
#   JWEBGPU_ANDROID_NDK Path to the Android NDK. See the prerequisites above.
#   MACOSX_DEPLOYMENT_TARGET  Oldest macOS release the library supports. Default: 12.0
#
# The Dawn revision is pinned in scripts/dawn.version. The first run downloads about 1.3 GB of sources
# and the build takes roughly 25 to 60 minutes. Later runs reuse the cache.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
# shellcheck source=scripts/dawn.version
. "${SCRIPT_DIR}/dawn.version"

CACHE_DIR="${JWEBGPU_DAWN_CACHE:-${HOME}/.cache/jwebgpu/dawn}"
# Environment variables are not tilde-expanded by the shell, so do it here.
CACHE_DIR="${CACHE_DIR/#\~/${HOME}}"
SRC_DIR="${CACHE_DIR}/src"
TARGET="${1:-}"

die() {
  echo "build_natives.sh: $*" >&2
  exit 1
}

log() {
  echo "==> $*"
}

usage() {
  sed -n '2,/^set -euo/p' "${BASH_SOURCE[0]}" | sed -e '$d' -e 's/^# \{0,1\}//' -e '/^#$/s/#//' >&2
  exit 2
}

# Converts a path to the form native tools expect. Git Bash on Windows needs drive letters (C:/...).
native_path() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -m "$1"
  else
    echo "$1"
  fi
}

cpu_count() {
  if [ -n "${JWEBGPU_JOBS:-}" ]; then
    echo "${JWEBGPU_JOBS}"
  elif command -v nproc >/dev/null 2>&1; then
    nproc
  elif command -v sysctl >/dev/null 2>&1; then
    sysctl -n hw.ncpu
  else
    echo "${NUMBER_OF_PROCESSORS:-4}"
  fi
}

find_python() {
  local candidate
  for candidate in python3 python; do
    if command -v "${candidate}" >/dev/null 2>&1 && "${candidate}" -c 'import sys' >/dev/null 2>&1; then
      echo "${candidate}"
      return
    fi
  done
  die "python3 not found; install Python 3 and re-run."
}

# Downloads Dawn at the pinned commit, then its third-party dependencies. Safe to run again.
fetch_sources() {
  command -v git >/dev/null 2>&1 || die "git not found."
  mkdir -p "${SRC_DIR}"
  if [ ! -d "${SRC_DIR}/.git" ]; then
    log "Creating the Dawn checkout in ${SRC_DIR}"
    git -C "${SRC_DIR}" init -q
    git -C "${SRC_DIR}" remote add origin "${DAWN_REPO_URL}"
  fi
  git -C "${SRC_DIR}" config core.longpaths true

  local current
  current="$(git -C "${SRC_DIR}" rev-parse HEAD 2>/dev/null || true)"
  if [ "${current}" != "${DAWN_COMMIT}" ]; then
    log "Fetching Dawn ${DAWN_COMMIT}"
    git -C "${SRC_DIR}" fetch --depth 1 origin "${DAWN_COMMIT}"
    git -C "${SRC_DIR}" checkout -q --force FETCH_HEAD
  fi

  log "Fetching Dawn third-party dependencies"
  "$(find_python)" "${SRC_DIR}/tools/fetch_dawn_dependencies.py" --directory "$(native_path "${SRC_DIR}")"
}

# Prints the cmake generator arguments, one per line.
generator_args() {
  local ninja_bin=""
  if command -v ninja >/dev/null 2>&1; then
    ninja_bin="$(command -v ninja)"
  elif [ -x "${HOME}/.cache/jwebgpu/venv/bin/ninja" ]; then
    ninja_bin="${HOME}/.cache/jwebgpu/venv/bin/ninja"
  fi
  [ -n "${ninja_bin}" ] || die "Ninja not found; install it (for example 'pip install ninja')."
  echo "-GNinja"
  echo "-DCMAKE_MAKE_PROGRAM=$(native_path "${ninja_bin}")"
}

# Name of the library file that Dawn produces and that we ship, per OS.
library_name() {
  case "$1" in
    linux) echo "libwebgpu_dawn.so" ;;
    windows) echo "webgpu_dawn.dll" ;;
    macos) echo "libwebgpu_dawn.dylib" ;;
    android) echo "libwebgpu_dawn.so" ;;
  esac
}

# Prints the Android NDK directory, or fails with a hint.
android_ndk() {
  local candidate
  for candidate in "${JWEBGPU_ANDROID_NDK:-}" "${ANDROID_NDK_HOME:-}" "${ANDROID_NDK_ROOT:-}" \
    "${HOME}/Android/Sdk/ndk/28.2.13676358"; do
    if [ -n "${candidate}" ] && [ -f "${candidate}/build/cmake/android.toolchain.cmake" ]; then
      echo "${candidate}"
      return
    fi
  done
  die "Android NDK not found; set JWEBGPU_ANDROID_NDK to the NDK directory."
}

# Prints the directory of the NDK's LLVM tools for this host.
android_llvm_bin() {
  local ndk="$1" dir
  for dir in "${ndk}"/toolchains/llvm/prebuilt/*/bin; do
    if [ -x "${dir}/llvm-nm" ]; then
      echo "${dir}"
      return
    fi
  done
  die "No LLVM tools found in ${ndk}."
}

# Fails if the library exports no wgpu functions. On Linux and macOS it also fails if anything else is
# exported. On Windows, Dawn itself marks a few extra C++ entry points for export, so extras only warn.
verify_exports() {
  local os="$1" lib="$2" symbols bad count
  case "${os}" in
    linux) symbols="$(nm -D --defined-only "${lib}" | awk '{print $3}')" ;;
    android) symbols="$("${ANDROID_LLVM_BIN}/llvm-nm" -D --defined-only "${lib}" | awk '{print $3}')" ;;
    macos) symbols="$(nm -gU "${lib}" | awk '{print $3}' | sed 's/^_//')" ;;
    *)
      if command -v dumpbin >/dev/null 2>&1; then
        symbols="$(dumpbin //exports "$(native_path "${lib}")" | tr -d '\r' | awk 'NF == 4 && $1 ~ /^[0-9]+$/ {print $4}')"
      else
        log "dumpbin not found; skipping the export check."
        return 0
      fi
      ;;
  esac
  count="$(echo "${symbols}" | grep -c '^wgpu' || true)"
  bad="$(echo "${symbols}" | grep -v '^wgpu' || true)"
  log "Exported wgpu functions: ${count}"
  [ "${count}" -gt 200 ] || die "Expected more than 200 exported wgpu functions, found ${count}."
  grep -qx 'wgpuCreateInstance' <<<"${symbols}" || die "wgpuCreateInstance is not exported."
  if [ -n "${bad}" ]; then
    echo "${bad}" | head -n 20 >&2
    if [ "${os}" = "windows" ]; then
      log "Warning: the library exports symbols that are not part of webgpu.h."
    else
      die "The library exports symbols that are not part of webgpu.h."
    fi
  fi
}

report_linux_dependencies() {
  local lib="$1"
  log "Shared library dependencies (NEEDED):"
  readelf -d "${lib}" | awk '/NEEDED/ {print "    " $5}'
  if command -v objdump >/dev/null 2>&1; then
    local glibc
    glibc="$(objdump -T "${lib}" | grep -o 'GLIBC_[0-9.]*' | sed 's/^GLIBC_//' | sort -uV | tail -n 1)"
    log "Highest glibc symbol version required: ${glibc}"
    if [ -n "${JWEBGPU_MAX_GLIBC:-}" ] && [ -n "${glibc}" ]; then
      local newest
      newest="$(printf '%s\n%s\n' "${glibc}" "${JWEBGPU_MAX_GLIBC}" | sort -V | tail -n 1)"
      [ "${newest}" = "${JWEBGPU_MAX_GLIBC}" ] ||
        die "The library needs glibc ${glibc}, newer than the allowed ${JWEBGPU_MAX_GLIBC}."
    fi
  fi
}

# Checks that the window systems that were switched on really made it into the Linux build. The CMake
# cache records what Dawn was configured with. X11 also leaves the name of the library Dawn loads at run
# time with dlopen in the binary, so that is checked as well.
verify_linux_surfaces() {
  local lib="$1" build_dir="$2" x11="$3" wayland="$4" cache
  cache="${build_dir}/CMakeCache.txt"
  if [ "${x11}" = "ON" ]; then
    grep -q '^DAWN_USE_X11:BOOL=ON$' "${cache}" || die "X11 was switched on, but Dawn was configured without it."
    grep -aq 'libX11-xcb.so' "${lib}" || die "X11 was switched on, but the library has no X11 support."
    log "X11 (Xlib and XCB) surface support: present"
  fi
  if [ "${wayland}" = "ON" ]; then
    grep -q '^DAWN_USE_WAYLAND:BOOL=ON$' "${cache}" || die "Wayland was switched on, but Dawn was configured without it."
    log "Wayland surface support: present"
  fi
}

build() {
  local os arch cmake_arch
  case "${TARGET}" in
    linux-x86_64 | linux-aarch64 | windows-x86_64 | windows-aarch64 | macos-x86_64 | macos-aarch64) ;;
    android-arm64-v8a | android-x86_64) ;;
    android-*) die "Only the 64-bit Android ABIs (arm64-v8a and x86_64) are supported." ;;
    *) usage ;;
  esac
  os="${TARGET%%-*}"
  arch="${TARGET#*-}"

  if [ "${os}" = "linux" ]; then
    local host_arch
    host_arch="$(uname -m)"
    [ "${host_arch}" = "${arch}" ] || die "Cannot build ${TARGET} on a ${host_arch} host; use a matching machine."
  fi

  command -v cmake >/dev/null 2>&1 || die "cmake not found; install cmake 3.22+ and re-run."
  fetch_sources

  local build_dir="${CACHE_DIR}/build/${TARGET}"
  local lib_name
  lib_name="$(library_name "${os}")"

  local args=(
    -S "$(native_path "${SRC_DIR}")"
    -B "$(native_path "${build_dir}")"
    -DCMAKE_BUILD_TYPE=Release
    -DDAWN_FETCH_DEPENDENCIES=OFF
    -DDAWN_BUILD_MONOLITHIC_LIBRARY=SHARED
    -DDAWN_ENABLE_NULL=ON
    -DDAWN_ENABLE_DESKTOP_GL=OFF
    -DDAWN_ENABLE_OPENGLES=OFF
    -DDAWN_BUILD_SAMPLES=OFF
    -DDAWN_BUILD_TESTS=OFF
    -DDAWN_BUILD_NODE_BINDINGS=OFF
    -DDAWN_BUILD_BENCHMARKS=OFF
    -DDAWN_BUILD_PROTOBUF=OFF
    -DDAWN_USE_GLFW=OFF
    -DTINT_BUILD_CMD_TOOLS=OFF
    -DTINT_BUILD_TESTS=OFF
    -DTINT_BUILD_IR_BINARY=OFF
    -DTINT_BUILD_GLSL_WRITER=OFF
    -DTINT_BUILD_GLSL_VALIDATOR=OFF
    -DTINT_BUILD_SPV_READER=OFF
    -DDAWN_SUPPORTS_CXX_MODULES=OFF
    -DCMAKE_CXX_SCAN_FOR_MODULES=OFF
  )
  while IFS= read -r line; do args+=("${line}"); done < <(generator_args)

  local x11="${JWEBGPU_X11:-ON}" wayland="${JWEBGPU_WAYLAND:-ON}"
  case "${os}" in
    android)
      local ndk map_file
      ndk="$(android_ndk)"
      ANDROID_LLVM_BIN="$(android_llvm_bin "${ndk}")"
      map_file="$(native_path "${SCRIPT_DIR}/natives/linux-exports.map")"
      # Same export rules as Linux. The static C++ runtime means apps do not need libc++_shared.so,
      # so the library cannot clash with another library's copy of it.
      local compile_flags="-fvisibility=hidden -ffunction-sections -fdata-sections"
      args+=(
        "-DCMAKE_TOOLCHAIN_FILE=$(native_path "${ndk}/build/cmake/android.toolchain.cmake")"
        "-DANDROID_ABI=${arch}"
        "-DANDROID_PLATFORM=android-${JWEBGPU_ANDROID_API:-26}"
        -DANDROID_STL=c++_static
        -DDAWN_ENABLE_VULKAN=ON
        -DDAWN_ENABLE_D3D11=OFF
        -DDAWN_ENABLE_D3D12=OFF
        -DDAWN_ENABLE_METAL=OFF
        -DDAWN_USE_X11=OFF
        -DDAWN_USE_WAYLAND=OFF
        -DTINT_BUILD_HLSL_WRITER=OFF
        -DTINT_BUILD_MSL_WRITER=OFF
        "-DCMAKE_C_FLAGS=${compile_flags}"
        "-DCMAKE_CXX_FLAGS=${compile_flags} -fvisibility-inlines-hidden"
        "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,--strip-all -Wl,--version-script=${map_file}"
      )
      log "Android: ABI ${arch}, API ${JWEBGPU_ANDROID_API:-26}, NDK ${ndk}"
      ;;
    linux)
      local map_file
      map_file="$(native_path "${SCRIPT_DIR}/natives/linux-exports.map")"
      # Hidden visibility plus a version script keep every C++ internal symbol private. Static
      # libstdc++ and libgcc mean the library needs only glibc at run time.
      local compile_flags="-fvisibility=hidden -ffunction-sections -fdata-sections"
      args+=(
        -DDAWN_ENABLE_VULKAN=ON
        -DDAWN_ENABLE_D3D11=OFF
        -DDAWN_ENABLE_D3D12=OFF
        -DDAWN_ENABLE_METAL=OFF
        -DDAWN_USE_X11="${x11}"
        -DDAWN_USE_WAYLAND="${wayland}"
        -DTINT_BUILD_HLSL_WRITER=OFF
        -DTINT_BUILD_MSL_WRITER=OFF
        "-DCMAKE_C_FLAGS=${compile_flags}"
        "-DCMAKE_CXX_FLAGS=${compile_flags} -fvisibility-inlines-hidden"
        "-DCMAKE_SHARED_LINKER_FLAGS=-static-libstdc++ -static-libgcc -Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,--strip-all -Wl,--version-script=${map_file}"
      )
      log "Linux surfaces: X11=${x11}, Wayland=${wayland}"
      ;;
    windows)
      # Static MSVC runtime, so users do not need the Visual C++ redistributable. Only the wgpu
      # functions are marked __declspec(dllexport) by webgpu.h, so no export list is needed.
      args+=(
        -DDAWN_ENABLE_VULKAN=ON
        -DDAWN_ENABLE_D3D11=ON
        -DDAWN_ENABLE_D3D12=ON
        -DDAWN_ENABLE_METAL=OFF
        -DTINT_BUILD_HLSL_WRITER=ON
        -DTINT_BUILD_MSL_WRITER=OFF
        -DCMAKE_MSVC_RUNTIME_LIBRARY=MultiThreaded
        # Dependencies with older CMake files ignore CMAKE_MSVC_RUNTIME_LIBRARY unless this policy is on,
        # and abseil picks its runtime with its own switch. Without both, objects built against the DLL
        # runtime (/MD) fail to link with the rest of Dawn (/MT).
        -DCMAKE_POLICY_DEFAULT_CMP0091=NEW
        -DABSL_MSVC_STATIC_RUNTIME=ON
      )
      ;;
    macos)
      local exports_file
      exports_file="$(native_path "${SCRIPT_DIR}/natives/macos-exports.txt")"
      case "${arch}" in
        x86_64) cmake_arch="x86_64" ;;
        aarch64) cmake_arch="arm64" ;;
      esac
      args+=(
        -DDAWN_ENABLE_VULKAN=OFF
        -DDAWN_ENABLE_D3D11=OFF
        -DDAWN_ENABLE_D3D12=OFF
        -DDAWN_ENABLE_METAL=ON
        -DTINT_BUILD_HLSL_WRITER=OFF
        -DTINT_BUILD_MSL_WRITER=ON
        "-DCMAKE_OSX_ARCHITECTURES=${cmake_arch}"
        "-DCMAKE_OSX_DEPLOYMENT_TARGET=${MACOSX_DEPLOYMENT_TARGET:-12.0}"
        "-DCMAKE_C_FLAGS=-fvisibility=hidden -ffunction-sections -fdata-sections"
        "-DCMAKE_CXX_FLAGS=-fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections"
        "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-dead_strip -Wl,-x -Wl,-exported_symbols_list,${exports_file}"
      )
      ;;
  esac

  log "Configuring Dawn for ${TARGET} in ${build_dir}"
  cmake "${args[@]}"

  log "Building webgpu_dawn with $(cpu_count) jobs"
  cmake --build "$(native_path "${build_dir}")" --parallel "$(cpu_count)" --target webgpu_dawn

  local built
  built="$(find "${build_dir}" -name "${lib_name}" -type f | head -n 1)"
  [ -n "${built}" ] || die "Build finished but ${lib_name} was not found under ${build_dir}."

  verify_exports "${os}" "${built}"
  if [ "${os}" = "linux" ]; then
    report_linux_dependencies "${built}"
    verify_linux_surfaces "${built}" "${build_dir}" "${x11}" "${wayland}"
  fi

  # Desktop libraries go into the FFM backend's resources. Android libraries go into the JNI
  # backend's jniLibs folder, one subfolder per ABI, which is where the Android build expects them.
  local default_out="${REPO_ROOT}/jwebgpu-ffm/src/main/resources/natives/${TARGET}"
  if [ "${os}" = "android" ]; then
    default_out="${REPO_ROOT}/jwebgpu-jni/src/main/jniLibs/${arch}"
  fi
  local out_dir="${JWEBGPU_OUT_DIR:-${default_out}}"
  mkdir -p "${out_dir}"
  cp -f "${built}" "${out_dir}/${lib_name}"
  log "Done: ${out_dir}/${lib_name} ($(wc -c < "${out_dir}/${lib_name}") bytes)"
}

case "${TARGET}" in
  "" | -h | --help | help) usage ;;
  fetch) fetch_sources ;;
  *) build ;;
esac
