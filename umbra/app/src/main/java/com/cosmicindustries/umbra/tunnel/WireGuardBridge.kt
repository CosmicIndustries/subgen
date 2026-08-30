package com.cosmicindustries.umbra.tunnel

/**
 * JNI bridge onto `libwgbridge.so`, built from `app/src/main/go/` — a Go
 * module vendoring the same `golang.zx2c4.com/wireguard` device/tun/ipc
 * packages the official WireGuard Android app uses, adapted from its
 * `tunnel/tools/libwg-go/api-android.go` (Apache-2.0) to bind to this class
 * instead of `com.wireguard.android.backend.GoBackend`, plus one addition:
 * [wgTurnOnViaByedpi] swaps WireGuard's default UDP socket for a custom
 * `conn.Bind` that relays through byedpi's local SOCKS5 listener (see
 * `app/src/main/go/socks5udpbind.go`), so byedpi's own desync techniques
 * get applied to WireGuard's own transport.
 *
 * Every method here returns a `handle` (>= 0) identifying the running
 * tunnel, or -1 on failure — mirrors the real GoBackend/libwg-go contract
 * exactly, verified by locally compiling and linking this exact Go+C
 * source against a real JDK jni.h (see ARCHITECTURE.md).
 */
object WireGuardBridge {
    init {
        System.loadLibrary("wgbridge")
    }

    /** Plain WireGuard, no DPI-bypass wrapping. [settings] is UAPI text (see [com.wireguard.config.Config.toWgUserspaceString]). */
    external fun wgTurnOn(interfaceName: String, tunFd: Int, settings: String): Int

    /** Same as [wgTurnOn], but routes WireGuard's own UDP transport through byedpi at [byedpiAddr] (e.g. "127.0.0.1:1080"). */
    external fun wgTurnOnViaByedpi(interfaceName: String, tunFd: Int, settings: String, byedpiAddr: String): Int

    external fun wgTurnOff(handle: Int)

    external fun wgGetSocketV4(handle: Int): Int

    external fun wgGetSocketV6(handle: Int): Int

    external fun wgGetConfig(handle: Int): String?

    external fun wgVersion(): String?
}
