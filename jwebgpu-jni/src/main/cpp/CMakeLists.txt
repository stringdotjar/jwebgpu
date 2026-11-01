# Builds the native glue library (libjwebgpu_jni) of the JNI backend.
#
# Android Gradle Plugin runs this file for each ABI. The unit tests of the repository run it for the desktop through
# the Gradle task buildJniHostGlue. Inputs (pass them with -D):
#
#   JWEBGPU_GLUE_DIR  The directory with the generated jwebgpu_jni.c (build/generated/sources/jwebgpu/main/cpp).
#   WEBGPU_INCLUDE    The directory that contains webgpu.h. Defaults to the vendored spec/ directory.
#   DAWN_LIBRARY      The Dawn shared library to link against. On Android the default is
#                     src/main/jniLibs/<abi>/libwebgpu_dawn.so.
cmake_minimum_required(VERSION 3.22)
project(jwebgpu_jni C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_VISIBILITY_PRESET hidden)

if(NOT JWEBGPU_GLUE_DIR)
  message(FATAL_ERROR "Pass -DJWEBGPU_GLUE_DIR=<directory with the generated jwebgpu_jni.c>")
endif()

if(NOT WEBGPU_INCLUDE)
  set(WEBGPU_INCLUDE "${CMAKE_CURRENT_SOURCE_DIR}/../../../../spec")
endif()

if(NOT DAWN_LIBRARY)
  if(ANDROID)
    set(DAWN_LIBRARY "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libwebgpu_dawn.so")
  else()
    message(FATAL_ERROR "Pass -DDAWN_LIBRARY=<path of libwebgpu_dawn>")
  endif()
endif()

if(NOT EXISTS "${DAWN_LIBRARY}")
  message(FATAL_ERROR "The Dawn library does not exist: ${DAWN_LIBRARY}")
endif()

add_library(webgpu_dawn SHARED IMPORTED)
set_target_properties(webgpu_dawn PROPERTIES IMPORTED_LOCATION "${DAWN_LIBRARY}")
if(NOT ANDROID)
  set_target_properties(webgpu_dawn PROPERTIES IMPORTED_NO_SONAME FALSE)
endif()

add_library(jwebgpu_jni SHARED
  "${JWEBGPU_GLUE_DIR}/jwebgpu_jni.c"
  "${CMAKE_CURRENT_SOURCE_DIR}/native_helpers.c")

target_include_directories(jwebgpu_jni PRIVATE "${WEBGPU_INCLUDE}")
target_compile_options(jwebgpu_jni PRIVATE -Wall -Wextra -Werror -fvisibility=hidden)

if(ANDROID)
  target_link_libraries(jwebgpu_jni PRIVATE webgpu_dawn android log)
  # Support devices with 16 KB memory pages.
  target_link_options(jwebgpu_jni PRIVATE -Wl,-z,max-page-size=16384)
else()
  find_package(JNI REQUIRED)
  target_include_directories(jwebgpu_jni PRIVATE ${JNI_INCLUDE_DIRS})
  target_link_libraries(jwebgpu_jni PRIVATE webgpu_dawn)
  # Find libwebgpu_dawn next to the glue library if it is not loaded yet.
  get_filename_component(DAWN_DIR "${DAWN_LIBRARY}" DIRECTORY)
  set_target_properties(jwebgpu_jni PROPERTIES BUILD_RPATH "${DAWN_DIR}")
endif()
