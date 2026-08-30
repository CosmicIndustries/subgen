# Umbra

An Android app in the spirit of [Rethink](https://github.com/celzero/rethink-app),
built around three real, open-source engines instead of one:

- **[WireGuard](https://www.wireguard.com/)** — the official
  `com.wireguard.android:tunnel` library (GoBackend), for a real encrypted
  tunnel.
- **[ByeByeDPI](https://github.com/hufrea/byedpi)** (`ciadpi`) +
  **[hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)** — a
  local desync/fragmentation proxy plus a TUN↔SOCKS5 bridge, for evading
  DPI-based blocking/throttling on networks that interfere with plain
  TCP/TLS traffic.
- **Shizuku** — a privileged-without-root per-app hard-block firewall, the
  same mechanism [ShizuWall](https://github.com/piyushmaurya23/ShizuWall)
  uses.

Per-app rules choose which of those (or neither) each installed app's
traffic goes through. All connection logging is **strictly local** — see
[NOTICE.md](NOTICE.md).

## Status

This is a from-scratch scaffold: a complete Gradle project, real
dependencies, and Kotlin/C implementations wired together against verified
upstream APIs — but it has **not been compiled or run**. The environment
this was built in has no Android SDK, emulator, or physical device. See
[BUILDING.md](BUILDING.md) before expecting an installable APK, and
[ARCHITECTURE.md](ARCHITECTURE.md) for the design decisions and their
trade-offs (most importantly: why WireGuard mode and DPI-bypass mode are
mutually exclusive rather than simultaneous).

## Layout

```
umbra/
  app/src/main/java/com/cosmicindustries/umbra/
    tunnel/    WireGuard (GoBackend) integration
    dpi/       byedpi + hev-socks5-tunnel JNI wrappers and config builders
    vpn/       UmbraVpnService (DPI-bypass mode's VpnService)
    firewall/  per-app rules (Room) + Shizuku hard-block enforcement
    logging/   local-only connection log (Room) + CSV export
    data/      settings (DataStore) + the Room database
    ui/        Jetpack Compose screens
  app/src/main/cpp/       CMake build for the native byedpi/hev-socks5-tunnel libs
  external/               git submodules: byedpi, hev-socks5-tunnel (+ its own submodules)
```

## Why these specific projects

The DPI-bypass native layer isn't a reimplementation — `umbra/external/`
vendors the actual upstream sources as git submodules, and
`app/src/main/cpp/CMakeLists.txt` builds them from scratch (see
[NOTICE.md](NOTICE.md) for the exact commits and licenses). This was
grounded by extracting and inspecting the real `libbyedpi.so` /
`libhev-socks5-tunnel.so` / `libwg-go.so` from a ByeByeDPI build and the
official WireGuard Android app to confirm their actual JNI contracts before
writing the Kotlin/C glue around them, rather than guessing at
undocumented internals.
