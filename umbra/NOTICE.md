# Third-party notices

Umbra's own code (everything outside `external/` and the vendored parts of
`app/src/main/go/`) is MIT-licensed. It depends on / vendors the following:

## Vendored as a git submodule (`umbra/external/`), built from source

| Project | Path | License | Used for |
|---|---|---|---|
| [hufrea/byedpi](https://github.com/hufrea/byedpi) | `external/byedpi` | MIT | The `ciadpi` desync engine (`dpi/ByeDpiProxy.kt` + `app/src/main/cpp/byedpi_jni.c`), run as a local SOCKS5 (+ UDP ASSOCIATE) listener |

None of it is modified — Umbra's CMake build compiles byedpi's sources
as-is and links a small amount of Umbra-authored glue
(`app/src/main/cpp/byedpi_jni.c`) against them.

## Go module (`app/src/main/go/`), adapted from source

| Project | License | Used for |
|---|---|---|
| [golang.zx2c4.com/wireguard](https://git.zx2c4.com/wireguard-go/) (wireguard-go) | MIT | The actual WireGuard protocol implementation — device/tun/ipc/conn packages, used unmodified as a Go module dependency |
| [WireGuard for Android](https://git.zx2c4.com/wireguard-android/) `tunnel/tools/libwg-go/{api-android.go,jni.c}` | Apache-2.0 | `app/src/main/go/api.go` and `app/src/main/go/jni.c` are adapted from these two files: same `wgTurnOn`/`wgTurnOff`/`wgGetSocketV4`/`wgGetSocketV6`/`wgGetConfig`/`wgVersion` JNI contract, retargeted from `com.wireguard.android.backend.GoBackend` to Umbra's own `WireGuardBridge` class, plus one addition (`wgTurnOnViaByedpi`) |

`app/src/main/go/socks5udpbind.go` (the `conn.Bind` implementation that
relays WireGuard's transport through byedpi) is new code written for this
project, not adapted from either upstream.

## Maven dependencies

| Project | License | Used for |
|---|---|---|
| [WireGuard for Android](https://git.zx2c4.com/wireguard-android/) (`com.wireguard.android:tunnel`) | Apache-2.0 | Only `com.wireguard.config.Config` (parsing + `toWgUserspaceString()`) — `GoBackend` itself is not used, see `ARCHITECTURE.md` |
| [Shizuku](https://github.com/RikkaApps/Shizuku) (`dev.rikka.shizuku:api`, `:provider`) | Apache-2.0 | Privileged-without-root command execution for the hard-block firewall (`firewall/ShizukuFirewall.kt`) |
| [ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded) (`com.journeyapps:zxing-android-embedded`) | Apache-2.0 | QR-code WireGuard config import (`ui/wireguard/WireGuardConfigScreen.kt`'s "Scan QR" button) — the same library the official WireGuard Android app uses, confirmed by decompiling its APK |
| AndroidX (Room, DataStore, Security-Crypto, Compose, Navigation, WorkManager, …) | Apache-2.0 | Standard Android app infrastructure |
| Kotlin / kotlinx.coroutines | Apache-2.0 | Language + async runtime |

## Provenance note

The exact JNI contracts this app's Kotlin/C/Go code calls into (byedpi's
`Java_..._jniStartProxy`/`jniStopProxy`/`jniForceClose` naming convention,
and WireGuard's `wgTurnOn`/`wgTurnOff`/etc. cgo-exported C functions plus
the separate `jni.c` shim that gives them real `Java_...` symbol names)
were confirmed by extracting and inspecting the real compiled
`libbyedpi.so` and `libwg-go.so`/`libwg.so` from a ByeByeDPI build and the
official WireGuard Android app, and by reading wireguard-android's actual
`tunnel/tools/libwg-go/{api-android.go,jni.c,Makefile}` and
`tunnel/src/main/java/com/wireguard/android/backend/GoBackend.java`
sources directly, rather than assumed from documentation alone. See
`ARCHITECTURE.md`.

## Data handling

Umbra makes no network calls of its own outside the user-configured
WireGuard/byedpi tunnels. Connection logs (`logging/`) are stored in a
local Room database and never transmitted; CSV export writes to
app-private external storage for the user to share manually if they choose
to. There is no analytics, crash-reporting, or telemetry SDK anywhere in
this codebase.
