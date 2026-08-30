cmake_minimum_required(VERSION 3.22.1)
project(umbra_native C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Vendored as a git submodule under umbra/external/ and built from its real
# upstream sources (never from a pre-built .so) so Umbra's own
# licensing/provenance stays clean. See umbra/NOTICE.md. (The WireGuard
# transport itself is a separate Go module built by CI directly into
# app/src/main/jniLibs/ — see app/src/main/go/ — since Go's own toolchain,
# not CMake, drives that build.)
get_filename_component(EXTERNAL_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../external" ABSOLUTE)
set(BYEDPI_DIR "${EXTERNAL_DIR}/byedpi")

if(NOT EXISTS "${BYEDPI_DIR}/proxy.c")
    message(FATAL_ERROR
        "umbra/external/byedpi is empty — the git submodules were not "
        "initialized. Run: git submodule update --init --recursive")
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
#
# byedpi runs here purely as a local SOCKS5 (+ UDP ASSOCIATE) listener that
# app/src/main/go's custom conn.Bind talks to, to wrap WireGuard's own
# transport in byedpi's desync techniques — see ARCHITECTURE.md.
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
