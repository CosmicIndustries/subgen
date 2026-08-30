# Umbra

An Android app in the spirit of [Rethink](https://github.com/celzero/rethink-app),
merging three real, open-source engines into one running session instead of
picking one:

- **[WireGuard](https://www.wireguard.com/)** — the actual `wireguard-go`
  protocol implementation, driven directly (not through the official app's
  `GoBackend`) so it can share Umbra's own VPN service with the other two
  pieces. See [ARCHITECTURE.md](ARCHITECTURE.md).
- **[ByeByeDPI](https://github.com/hufrea/byedpi)** (`ciadpi`) — a local
  desync proxy. Rather than being a separate per-app routing option, it
  wraps WireGuard's *own* connection to your VPN server in its DPI-evasion
  techniques (UDP fake-packets), for networks that specifically detect and
  throttle or block WireGuard itself.
- **Shizuku** — a privileged-without-root per-app hard-block firewall, the
  same mechanism [ShizuWall](https://github.com/piyushmaurya23/ShizuWall)
  uses. Active independent of whether the tunnel is running.

Per-app rules choose WireGuard / Blocked / Direct for each installed app.
All connection logging is **strictly local** — see [NOTICE.md](NOTICE.md).

## Status

This has real, unusually thorough local verification for a project built
in an environment with no Android SDK, emulator, or device: the actual
committed Go/C WireGuard bridge was locally compiled and linked (against a
real JDK `jni.h` + stub NDK headers) with all its JNI symbols confirmed
present and correctly named, and the SOCKS5 relay logic was unit-tested
against a fake server replicating byedpi's real behavior. CI (GitHub
Actions) does the real Android/NDK/Go cross-compile and has produced a
working, installable debug APK. What's *not* verified is real on-device
behavior — an actual WireGuard peer, actual network conditions, actual
Shizuku pairing. See [BUILDING.md](BUILDING.md) and the "What's actually
been verified vs. not" section of [ARCHITECTURE.md](ARCHITECTURE.md).

## Layout

```
umbra/
  app/src/main/java/com/cosmicindustries/umbra/
    tunnel/    WireGuardBridge (JNI) + WireGuardEngine + config storage
    dpi/       byedpi JNI wrapper and config builder
    vpn/       UmbraVpnService — the one VpnService/TUN everything shares
    firewall/  per-app rules (Room) + Shizuku hard-block enforcement
    logging/   local-only connection log (Room) + CSV export
    data/      settings (DataStore) + the Room database
    ui/        Jetpack Compose screens
  app/src/main/cpp/       CMake build for libbyedpi.so
  app/src/main/go/        Go module for the WireGuard bridge (libwgbridge.so), built by CI
  external/               git submodule: byedpi
```

## Why these specific projects

The DPI-bypass native layer isn't a reimplementation — `umbra/external/`
vendors byedpi's actual upstream source as a git submodule, and
`app/src/main/cpp/CMakeLists.txt` builds it from scratch. The WireGuard
bridge (`app/src/main/go/`) uses the real `golang.zx2c4.com/wireguard`
module as a dependency and adapts wireguard-android's own JNI glue code
rather than reimplementing the protocol. See [NOTICE.md](NOTICE.md) for
exact provenance and licenses, and [ARCHITECTURE.md](ARCHITECTURE.md) for
how all three pieces were grounded — extracting and inspecting real
compiled `.so` files, decompiling library classes, and reading upstream
source directly — rather than guessed from documentation.
