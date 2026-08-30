# Building Umbra

This has not been built in this environment (no Android SDK/emulator was
available where it was written — see `README.md`). These are the steps to
get it into Android Studio and onto a device.

## 1. Clone with submodules

```sh
git clone --recurse-submodules <repo-url>
cd <repo>/umbra
# if you already cloned without --recurse-submodules:
git submodule update --init --recursive
```

`external/byedpi` and `external/hev-socks5-tunnel` (plus its own nested
submodules) must be populated before the native build will find any
sources — `CMakeLists.txt` fails fast with a clear message if they're
missing.

## 2. Open in Android Studio

Use a recent Android Studio (Ladybug/2024.2+) with:
- Android SDK Platform 35
- NDK 27.2.12479018 (pinned in `app/build.gradle.kts`; Studio will offer to
  install it on first sync if missing)
- CMake 3.22.1+

Open the `umbra/` directory (not the repo root) as the project.

## 3. First sync

Expect to fix at least a few things on the first sync/build — this
scaffold was written without a compiler in the loop (see
`ARCHITECTURE.md`'s "What hasn't been verified" section). Start with the
native build (`app/src/main/cpp`) since that's the part most likely to
need a small adjustment.

## 4. Shizuku setup (optional, for the hard-block firewall)

The per-app "Blocked" mode requires the separate
[Shizuku](https://shizuku.rikka.app/) app running on the device:

- **Rooted device**: open Shizuku, tap "Start (root)".
- **Non-rooted device**: `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`
  (or use Shizuku's wireless-ADB pairing flow on Android 11+), then keep
  the Shizuku app's service running (it can start on boot).

Umbra works without it — you just won't get OS-level hard blocking for
apps tagged "Blocked"; WireGuard-mode and DPI-bypass-mode per-app routing
don't need Shizuku at all.

## 5. WireGuard config

On the WireGuard tab, paste a standard `wg-quick`-style config (the same
text you'd get from a `.conf` file or QR code from your VPN provider or
`wg genkey`/`wg pubkey` setup). Umbra keeps exactly one profile.

## 6. DPI-bypass tuning

The DPI tab exposes byedpi's desync strategy (split/disorder/oob/fake) and
split position. Defaults are conservative; see
`external/byedpi/README.md` upstream for what each strategy actually does
on the wire if you need to tune against a specific network's DPI.
