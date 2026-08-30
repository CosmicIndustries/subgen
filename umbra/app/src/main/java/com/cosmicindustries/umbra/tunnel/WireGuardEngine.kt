package com.cosmicindustries.umbra.tunnel

import com.wireguard.config.Config
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class WireGuardState { DOWN, UP }

/**
 * Drives [WireGuardBridge] directly instead of going through the official
 * `com.wireguard.android:tunnel` library's `GoBackend`/`Backend`/`Tunnel`
 * API. GoBackend insists on owning its own `android.net.VpnService`
 * subclass internally (see the git history of this file / ARCHITECTURE.md
 * for why that made WireGuard and the DPI-bypass engine mutually
 * exclusive) — this engine instead takes a TUN fd that
 * [com.cosmicindustries.umbra.vpn.UmbraVpnService] already owns, so
 * WireGuard-routed apps and Shizuku-blocked apps can be handled by the same
 * single VpnService/session. `com.wireguard.config.Config` (parsing +
 * `toWgUserspaceString()`) is still used from that same Maven artifact —
 * only `GoBackend` itself is no longer needed.
 */
class WireGuardEngine {

    private val _state = MutableStateFlow(WireGuardState.DOWN)
    val state: StateFlow<WireGuardState> = _state

    private var handle: Int = -1

    /**
     * @param tunFd the VpnService's established TUN file descriptor. WireGuardBridge takes
     *   ownership of it (matches `tun.CreateUnmonitoredTUNFromFD`'s contract on the Go side).
     * @param byedpiAddr if non-null (e.g. "127.0.0.1:1080"), WireGuard's own UDP transport is
     *   relayed through byedpi's local SOCKS5 listener instead of a plain UDP socket.
     * @param protect `android.net.VpnService.protect`, called on WireGuard's own outbound
     *   socket(s) so they don't loop back into the tunnel they belong to.
     */
    fun start(
        interfaceName: String,
        tunFd: Int,
        config: Config,
        byedpiAddr: String?,
        protect: (Int) -> Boolean,
    ) {
        check(handle < 0) { "WireGuard is already running (handle=$handle)" }

        val settings = config.toWgUserspaceString()
        handle = if (byedpiAddr != null) {
            WireGuardBridge.wgTurnOnViaByedpi(interfaceName, tunFd, settings, byedpiAddr)
        } else {
            WireGuardBridge.wgTurnOn(interfaceName, tunFd, settings)
        }
        check(handle >= 0) { "wgTurnOn failed (byedpiAddr=$byedpiAddr)" }

        val v4 = WireGuardBridge.wgGetSocketV4(handle)
        val v6 = WireGuardBridge.wgGetSocketV6(handle)
        if (v4 >= 0) protect(v4)
        if (v6 >= 0) protect(v6)

        _state.value = WireGuardState.UP
    }

    fun stop() {
        if (handle >= 0) {
            WireGuardBridge.wgTurnOff(handle)
            handle = -1
        }
        _state.value = WireGuardState.DOWN
    }

    fun isRunning(): Boolean = handle >= 0
}
