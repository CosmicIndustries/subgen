package com.cosmicindustries.umbra.tunnel

import android.content.Context
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin wrapper around the official `com.wireguard.android:tunnel` library
 * (GoBackend, wraps wireguard-go over JNI).
 *
 * IMPORTANT: GoBackend manages its OWN android.net.VpnService instance
 * (`GoBackend.VpnService`, merged into the manifest from the tunnel AAR) —
 * it does not hand the TUN file descriptor to a caller-supplied VpnService.
 * Android only ever has one VPN interface established at a time, so this
 * engine and [com.cosmicindustries.umbra.vpn.UmbraVpnService] (which drives
 * the ByeDPI/hev-socks5-tunnel path) are mutually exclusive "modes" the user
 * picks on the dashboard, not two simultaneously-active tunnels. See
 * ARCHITECTURE.md for why: wireguard-go's low-level wgTurnOn/wgTurnOff JNI
 * entry points are hardcoded (by the prebuilt libwg-go.so) to only be
 * callable from GoBackend's own class, so embedding wireguard-go inside our
 * own VpnService would require rebuilding wireguard-go from source with our
 * own JNI bridge, which is out of scope here.
 *
 * Per-app routing for WireGuard mode is done through the config text itself:
 * wireguard-android extends the standard wg-quick [Interface] block with
 * non-standard `ExcludedApplications` / `IncludedApplications` keys (a
 * comma-separated package list) that GoBackend's VpnService reads directly
 * when building its Builder — see [WireGuardConfigStore.withAppRouting].
 */
class WireGuardEngine(context: Context) {

    private val backend: Backend = GoBackend(context.applicationContext)
    private val tunnel = UmbraTunnel()

    private val _state = MutableStateFlow(Tunnel.State.DOWN)
    val state: StateFlow<Tunnel.State> = _state

    fun start(config: Config) {
        _state.value = backend.setState(tunnel, Tunnel.State.UP, config)
    }

    fun stop() {
        _state.value = backend.setState(tunnel, Tunnel.State.DOWN, null)
    }

    fun refreshState() {
        _state.value = backend.getState(tunnel)
    }

    fun statistics(): Statistics? = try {
        backend.getStatistics(tunnel)
    } catch (_: Exception) {
        null
    }

    fun backendVersion(): String = try {
        backend.getVersion()
    } catch (_: Exception) {
        "unknown"
    }

    /** Tunnel identity GoBackend uses for its notification + wakelock bookkeeping. */
    private inner class UmbraTunnel : Tunnel {
        override fun getName(): String = TUNNEL_NAME

        override fun onStateChange(newState: Tunnel.State) {
            _state.value = newState
        }
    }

    companion object {
        const val TUNNEL_NAME = "umbra"
    }
}
