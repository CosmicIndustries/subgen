cmake_minimum_required(VERSION 3.22.1)
# ASM is required for hev-task-system's per-arch context-switch routine
# (third-part/hev-task-system/src/kern/task/hev-task-execute.S).
project(umbra_native C ASM)

set(CMAKE_C_STANDARD 11)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Both native components are vendored as git submodules under umbra/external/
# and built from their real upstream sources (never from a pre-built .so) so
# that Umbra's own licensing/provenance stays clean. See umbra/NOTICE.md.
get_filename_component(EXTERNAL_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../external" ABSOLUTE)
set(BYEDPI_DIR "${EXTERNAL_DIR}/byedpi")
set(HEV_DIR "${EXTERNAL_DIR}/hev-socks5-tunnel")

if(NOT EXISTS "${BYEDPI_DIR}/proxy.c")
    message(FATAL_ERROR
        "umbra/external/byedpi is empty — the git submodules were not "
        "initialized. Run: git submodule update --init --recursive")
endif()
if(NOT EXISTS "${HEV_DIR}/src/hev-socks5-tunnel.c")
    message(FATAL_ERROR
        "umbra/external/hev-socks5-tunnel is empty — the git submodules were "
        "not initialized. Run: git submodule update --init --recursive")
endif()

# ---------------------------------------------------------------------------
# libbyedpi.so — the ciadpi desync engine (hufrea/byedpi, MIT).
#
# We compile byedpi's own sources unmodified (its CLI arg parser, desync
# state machine and event loop) and add one small JNI shim of our own
# (byedpi_jni.c) that calls the same public entry points the upstream
# `main()` in main.c calls: parse_args() -> init() -> run(). Those three
# functions are plain (non-static) C functions with no header declaration
# upstream, so byedpi_jni.c declares them itself. `run()` blocks servicing
# the desync proxy loop, so it's driven from a pthread; jniStopProxy signals
# that thread with SIGTERM, which byedpi's own on_cancel() handler (wired up
# inside run()) already knows how to turn into a clean shutdown of the
# listening socket — see external/byedpi/proxy.c.
# ---------------------------------------------------------------------------
file(GLOB BYEDPI_SOURCES "${BYEDPI_DIR}/*.c")
list(FILTER BYEDPI_SOURCES EXCLUDE REGEX ".*/win_service\\.c$")

add_library(byedpi SHARED
    ${BYEDPI_SOURCES}
    byedpi_jni.c
)
target_include_directories(byedpi PRIVATE ${BYEDPI_DIR})
target_compile_definitions(byedpi PRIVATE STR_MODE)
target_compile_options(byedpi PRIVATE -Wall -Wno-unused-parameter)
find_library(log-lib log)
target_link_libraries(byedpi ${log-lib})

# ---------------------------------------------------------------------------
# libhev-socks5-tunnel.so — TUN <-> SOCKS5 bridge (heiher/hev-socks5-tunnel
# + its third-part/{yaml,lwip,hev-task-system} and src/core submodules, all
# MIT/BSD). This is what redirects a per-app-routed slice of the TUN
# device's raw IP packets into byedpi's local SOCKS5 listener.
#
# Upstream ships a ready-made Android JNI binding (src/hev-jni.c) that binds
# to a configurable Java class via the PKGNAME/CLSNAME compile-time macros
# instead of hand-written Java_* symbols — we just point those macros at our
# own package instead of writing new glue. See dpi/TProxyService.kt.
#
# Building matches upstream's own Android.mk one-for-one (same recursive
# glob per component, same include paths, same config defines) translated
# from ndk-build to CMake so the whole project stays on one build system.
# ---------------------------------------------------------------------------
file(GLOB YAML_SOURCES "${HEV_DIR}/third-part/yaml/src/*.c")
add_library(hev_yaml STATIC ${YAML_SOURCES})
target_include_directories(hev_yaml PRIVATE "${HEV_DIR}/third-part/yaml/src")
target_compile_definitions(hev_yaml PRIVATE
    YAML_VERSION_MAJOR=0
    YAML_VERSION_MINOR=2
    YAML_VERSION_PATCH=5
    YAML_VERSION_STRING="0.2.5"
)

file(GLOB_RECURSE LWIP_SOURCES "${HEV_DIR}/third-part/lwip/src/*.c")
add_library(hev_lwip STATIC ${LWIP_SOURCES})
target_include_directories(hev_lwip PRIVATE
    "${HEV_DIR}/third-part/lwip/src/include"
    "${HEV_DIR}/third-part/lwip/src/ports/include"
)
target_compile_definitions(hev_lwip PRIVATE FD_SET_DEFINED SOCKLEN_T_DEFINED)

file(GLOB_RECURSE TASK_SYSTEM_SOURCES
    "${HEV_DIR}/third-part/hev-task-system/src/*.c"
    "${HEV_DIR}/third-part/hev-task-system/src/*.S"
)
add_library(hev_task_system STATIC ${TASK_SYSTEM_SOURCES})
target_include_directories(hev_task_system PRIVATE
    "${HEV_DIR}/third-part/hev-task-system/src"
    "${HEV_DIR}/third-part/hev-task-system/include"
)
target_compile_definitions(hev_task_system PRIVATE
    ENABLE_STACK_OVERFLOW_DETECTION
    ENABLE_MEMALLOC_SLICE
    ENABLE_IO_SPLICE_SYSCALL
    CONFIG_STACK_BACKEND=STACK_MMAP
    CONFIG_STACK_OVERFLOW_DETECTION=1
    CONFIG_MEMALLOC_SLICE_ALIGN=16
    CONFIG_MEMALLOC_SLICE_MAX_SIZE=4096
    CONFIG_MEMALLOC_SLICE_MAX_COUNT=1000
    CONFIG_SCHED_CLOCK=CLOCK_NONE
)
target_compile_options(hev_task_system PRIVATE -fvisibility=hidden)

# Recursive glob over src/ also picks up src/core/src/**/*.c (the
# hev-socks5-core submodule lives nested inside src/) and every
# hev-tunnel-<platform>.c variant; the non-Linux ones compile to nothing on
# Android because they're guarded by their own #ifdef, exactly like
# upstream's own Android.mk relies on.
file(GLOB_RECURSE HEV_TUNNEL_SOURCES "${HEV_DIR}/src/*.c")

add_library(hev-socks5-tunnel SHARED ${HEV_TUNNEL_SOURCES})
target_include_directories(hev-socks5-tunnel PRIVATE
    "${HEV_DIR}/src"
    "${HEV_DIR}/src/misc"
    "${HEV_DIR}/src/core/include"
    "${HEV_DIR}/third-part/yaml/include"
    "${HEV_DIR}/third-part/lwip/src/include"
    "${HEV_DIR}/third-part/lwip/src/ports/include"
    "${HEV_DIR}/third-part/hev-task-system/include"
)
target_compile_definitions(hev-socks5-tunnel PRIVATE
    ANDROID
    FD_SET_DEFINED
    SOCKLEN_T_DEFINED
    ENABLE_LIBRARY
    PKGNAME=com/cosmicindustries/umbra/dpi
    CLSNAME=TProxyService
)
target_compile_options(hev-socks5-tunnel PRIVATE -Wno-unused-parameter)
target_link_options(hev-socks5-tunnel PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
target_link_libraries(hev-socks5-tunnel
    hev_yaml
    hev_lwip
    hev_task_system
    ${log-lib}
)
