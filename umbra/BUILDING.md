# Building Umbra

CI (`.github/workflows/build-umbra.yml`) builds this on every push and
publishes a debug APK as a GitHub release asset — that's the fastest way to
get an installable build. These are the steps to build it yourself in
Android Studio.

## 1. Clone with submodules

```sh
git clone --recurse-submodules <repo-url>
cd <repo>/umbra
# if you already cloned without --recurse-submodules:
git submodule update --init --recursive
```

`external/byedpi` must be populated before the native build will find any
sources — `CMakeLists.txt` fails fast with a clear message if it's missing.

## 2. Install a Go toolchain

The WireGuard bridge (`app/src/main/go/`) is a separate Go module built by
cross-compiling with the NDK's clang, not through Gradle/CMake. Install Go
1.24.7 (or newer 1.24.x), then build each ABI's `.so` before opening the
project in Android Studio (Gradle won't do this for you — see CI's "Build
WireGuard Go bridge" step in `build-umbra.yml` for the exact commands):

```sh
NDK=$ANDROID_HOME/ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin
mkdir -p app/src/main/jniLibs/arm64-v8a
CC="$NDK/aarch64-linux-android26-clang" CGO_ENABLED=1 GOOS=android GOARCH=arm64 \
  go build -C app/src/main/go -buildmode=c-shared \
  -o "$PWD/app/src/main/jniLibs/arm64-v8a/libwgbridge.so" .
# repeat for armeabi-v7a (armv7a-linux-androideabi26-clang, GOARCH=arm)
# and x86_64 (x86_64-linux-android26-clang, GOARCH=amd64) if you need those ABIs
```

## 3. Open in Android Studio

Use a recent Android Studio (Ladybug/2024.2+) with:
- Android SDK Platform 35
- NDK 27.2.12479018 (pinned in `app/build.gradle.kts`; Studio will offer to
  install it on first sync if missing)
- CMake 3.22.1+

Open the `umbra/` directory (not the repo root) as the project.

## 4. First sync

CI has already gotten this to a green, installable build (see the repo's
Actions tab / releases), so a clean sync should work. If you're modifying
the native or Go layers, see `ARCHITECTURE.md`'s "What's actually been
verified vs. not" section for the pieces most likely to need adjustment on
real-device testing.

## 5. Shizuku setup (optional, for the hard-block firewall)

The per-app "Blocked" mode requires the separate
[Shizuku](https://shizuku.rikka.app/) app running on the device:

- **Rooted device**: open Shizuku, tap "Start (root)".
- **Non-rooted device**: `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`
  (or use Shizuku's wireless-ADB pairing flow on Android 11+), then keep
  the Shizuku app's service running (it can start on boot).

Umbra works without it — you just won't get OS-level hard blocking for
apps tagged "Blocked"; the WireGuard tunnel itself doesn't need Shizuku.

## 6. WireGuard config

On the WireGuard tab, paste a standard `wg-quick`-style config (the same
text you'd get from a `.conf` file or QR code from your VPN provider or
`wg genkey`/`wg pubkey` setup). Umbra keeps exactly one profile, and
currently supports a single `[Peer]` block if you enable DPI-bypass
wrapping (see below).

## 7. DPI-bypass tuning

The DPI Bypass tab has one switch — wrap WireGuard's own transport through
byedpi — and one knob, the number of fake UDP packets byedpi sends ahead of
each real one (`-a/--udp-fake`). This targets networks that specifically
detect and block/throttle WireGuard's own traffic pattern, not general
website blocking.
