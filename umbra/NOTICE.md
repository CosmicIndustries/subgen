# Third-party notices

Umbra's own code (everything outside `external/`) is MIT-licensed. It
depends on / vendors the following:

## Vendored as git submodules (`umbra/external/`), built from source

| Project | Path | License | Used for |
|---|---|---|---|
| [hufrea/byedpi](https://github.com/hufrea/byedpi) | `external/byedpi` | MIT | The `ciadpi` desync engine (`dpi/ByeDpiProxy.kt` + `app/src/main/cpp/byedpi_jni.c`) |
| [heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) | `external/hev-socks5-tunnel` | MIT | TUN↔SOCKS5 bridge (`dpi/TProxyService.kt`); ships its own Android JNI glue (`src/hev-jni.c`), used as-is |
| heiher/hev-socks5-core | `external/hev-socks5-tunnel/src/core` | MIT | SOCKS5 protocol implementation, nested submodule of the above |
| heiher/hev-task-system | `external/hev-socks5-tunnel/third-part/hev-task-system` | MIT | Cooperative task scheduler the tunnel runs on |
| heiher/yaml | `external/hev-socks5-tunnel/third-part/yaml` | MIT | Parses the tunnel's YAML config |
| heiher/lwip (lwIP fork) | `external/hev-socks5-tunnel/third-part/lwip` | BSD-3-Clause (original lwIP license, Swedish Institute of Computer Science) | Userspace TCP/IP stack backing the tunnel |

None of these are modified — Umbra's CMake build compiles their unmodified
sources and links a small amount of Umbra-authored glue
(`app/src/main/cpp/byedpi_jni.c`) against them.

## Maven dependencies

| Project | License | Used for |
|---|---|---|
| [WireGuard for Android](https://git.zx2c4.com/wireguard-android/) (`com.wireguard.android:tunnel`) | Apache-2.0 | The WireGuard tunnel (`tunnel/WireGuardEngine.kt`) — wraps `wireguard-go`, itself MIT-licensed |
| [Shizuku](https://github.com/RikkaApps/Shizuku) (`dev.rikka.shizuku:api`, `:provider`) | Apache-2.0 | Privileged-without-root command execution for the hard-block firewall (`firewall/ShizukuFirewall.kt`) |
| AndroidX (Room, DataStore, Security-Crypto, Compose, Navigation, WorkManager, …) | Apache-2.0 | Standard Android app infrastructure |
| Kotlin / kotlinx.coroutines | Apache-2.0 | Language + async runtime |

## Provenance note

The exact JNI contracts this app's Kotlin/C code calls into (byedpi's
`Java_..._jniStartProxy`/`jniStopProxy`/`jniForceClose` naming convention,
hev-socks5-tunnel's `PKGNAME`/`CLSNAME`-configurable `RegisterNatives`
binding, and WireGuard's `GoBackend`-only `wgTurnOn`/`wgTurnOff` symbols)
were confirmed by extracting and inspecting the real compiled
`libbyedpi.so`, `libhev-socks5-tunnel.so`, and `libwg-go.so` from a
ByeByeDPI build and the official WireGuard Android app, rather than
assumed from documentation alone. See `ARCHITECTURE.md`.

## Data handling

Umbra makes no network calls of its own outside the user-configured
WireGuard/byedpi tunnels. Connection logs (`logging/`) are stored in a
local Room database and never transmitted; CSV export writes to
app-private external storage for the user to share manually if they choose
to. There is no analytics, crash-reporting, or telemetry SDK anywhere in
this codebase.
