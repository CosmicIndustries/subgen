package com.cosmicindustries.umbra.dpi

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the DPI-bypass path end to end: starts byedpi as a local SOCKS5
 * desync proxy, then starts hev-socks5-tunnel to bridge the TUN device
 * (owned by [com.cosmicindustries.umbra.vpn.UmbraVpnService]) into it.
 *
 *   TUN fd --[hev-socks5-tunnel]--> 127.0.0.1:byedpiPort --[byedpi]--> internet
 */
class ByeDpiEngine(private val context: Context) {

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private var config: ByeDpiConfig = ByeDpiConfig()

    /** Starts byedpi + the tun2socks bridge for [tunFd]. Call from the VPN service after `establish()`. */
    fun start(tunFd: Int, config: ByeDpiConfig = this.config) {
        this.config = config
        check(ByeDpiProxy.jniStartProxy(config.toArgs()) == 0) { "byedpi failed to start" }

        val configFile = File(context.cacheDir, "hev-socks5-tunnel.yaml")
        val tunnelConfig = TunnelConfig(
            socksAddress = config.listenIp,
            socksPort = config.listenPort,
        )
        configFile.writeText(tunnelConfig.toYaml())

        check(TProxyService.TProxyStartService(configFile.absolutePath, tunFd)) {
            "hev-socks5-tunnel failed to start"
        }
        _running.value = true
    }

    fun stop() {
        if (!_running.value) return
        TProxyService.TProxyStopService()
        ByeDpiProxy.jniStopProxy()
        _running.value = false
    }

    /** [txPackets, txBytes, rxPackets, rxBytes], or null if not running. */
    fun stats(): LongArray? = if (TProxyService.TProxyIsRunning()) TProxyService.TProxyGetStats() else null
}
