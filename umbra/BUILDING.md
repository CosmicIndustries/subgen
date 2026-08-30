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

### Installing a new release APK over an old one

Every build is signed with the same pinned debug key
(`app/debug.keystore`, wired via `signingConfigs.debug` in
`app/build.gradle.kts`), so newer releases install as a normal update over
older ones. **If you installed a release before this was added** (builds
15 and earlier), your device has an APK signed with a one-off key that
GitHub Actions generated fresh for that specific CI run — installing a
newer, differently-signed APK over it fails with a signature mismatch
(often surfacing as a generic "app not installed" toast rather than
naming the real reason). Uninstall the old Umbra app once; every build
from here on shares one signature and updates normally.

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

The WireGuard tab has three ways to load a config — Umbra keeps exactly
one profile, and currently supports a single `[Peer]` block if you enable
DPI-bypass wrapping (see below):

- **Paste** the config text directly into the text field, same as before.
- **Scan QR** — the same QR format the official WireGuard app exports:
  the raw wg-quick text encoded directly into the code. Uses
  `zxing-android-embedded` (same library the official app uses,
  confirmed by decompiling it), so it also requests camera permission
  the first time.
- **Import file** — pick a `.conf` file, or a `.zip` containing one
  (common when a provider bundles multiple peer configs together; Umbra
  extracts the first `.conf` entry alphabetically and tells you if it
  skipped others). Detected by the zip file's own magic bytes, not by
  filename or the content provider's reported MIME type, so it works
  even if your file manager reports `.conf` as `text/plain` or
  `application/octet-stream`.

### Testing the import paths without a real VPN provider

`scripts/generate-example-config.sh` generates a real, syntactically
valid wg-quick config — a real client keypair, but a placeholder
`[Peer]` (fake server key + `vpn.example.com` endpoint, since the script
has no server to actually talk to) — plus `.zip` and (if `qrencode` is
installed) `.conf.png` versions of the same file, so you can exercise
Scan QR and Import file against something real before wiring up an
actual provider:

```sh
./scripts/generate-example-config.sh ./example-wg
```

Edit the output's `PublicKey`/`Endpoint` to your real server's values
before expecting the tunnel to actually connect — the script only
verifies the *import paths* work, not connectivity.

## 7. DPI-bypass tuning

The DPI Bypass tab wraps WireGuard's own transport through byedpi and
exposes the parts of byedpi's config that actually affect a UDP relay
(the rest of byedpi's CLI is TCP-only and doesn't apply — see
`ARCHITECTURE.md`):

- **Fake UDP packets** (`-a/--udp-fake`): how many decoy datagrams
  precede each real one.
- **Decoy TTL** (`-t/--ttl`, only shown once fake packets > 0): the value
  worth actually tuning per-network. byedpi's own default is 8. If
  wrapping makes no observable difference, try values between 3 and 12 —
  too high and the decoy behaves just like a real packet (no bypass
  effect); too low and it never reaches whatever's inspecting traffic on
  the way to your VPN server.
- **Custom decoy payload** (`-l/--fake-data`, advanced/optional): override
  byedpi's built-in decoy bytes. Leave blank unless you have a specific
  reason to change it.

This targets networks that specifically detect and block/throttle
WireGuard's own traffic pattern, not general website blocking.
