package com.cosmicindustries.umbra.dpi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns byedpi as a plain local SOCKS5 (+ UDP ASSOCIATE) listener. Nothing
 * else in this app connects to it except
 * [com.cosmicindustries.umbra.tunnel.WireGuardEngine], via
 * [WireGuardBridge.wgTurnOnViaByedpi]'s custom `conn.Bind` — byedpi never
 * touches the TUN device directly (see ARCHITECTURE.md for why the earlier
 * design, bridging it onto the TUN via hev-socks5-tunnel, was replaced).
 */
class ByeDpiEngine {

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    fun start(config: ByeDpiConfig) {
        check(ByeDpiProxy.jniStartProxy(config.toArgs()) == 0) { "byedpi failed to start" }
        _running.value = true
    }

    fun stop() {
        if (!_running.value) return
        ByeDpiProxy.jniStopProxy()
        _running.value = false
    }
}
