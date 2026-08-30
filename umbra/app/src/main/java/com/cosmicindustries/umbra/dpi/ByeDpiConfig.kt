package com.cosmicindustries.umbra.dpi

/**
 * byedpi's CLI flags (extracted from `external/byedpi/main.c`'s help
 * text), trimmed to what's relevant for byedpi's role in this app: a
 * local SOCKS5 (+ UDP ASSOCIATE) listener that [WireGuardEngine] wraps its
 * own transport through (see [WireGuardBridge.wgTurnOnViaByedpi] and
 * `app/src/main/go/socks5udpbind.go`).
 *
 * That's a UDP flow, so only byedpi's UDP-specific desync knob applies —
 * `--split`/`--disorder`/`--fake`/etc. are TCP-segment-framing techniques
 * with no meaning for a UDP relay. `-a/--udp-fake <count>` sends that many
 * decoy datagrams ahead of each real one, the UDP analogue of byedpi's
 * fake-packet TCP technique.
 *
 * byedpi defaults to accepting both SOCKS4 and SOCKS5 when no mode flag is
 * passed (`if (!params.mode) params.mode |= (MODE_SOCKS4 | MODE_SOCKS5);`
 * in main.c) — no explicit "--socks5" flag exists or is needed.
 */
data class ByeDpiConfig(
    val listenIp: String = "127.0.0.1",
    val listenPort: Int = 1080,
    val udpFakeCount: Int = 2,
    val debugLevel: Int = 0,
) {
    val proxyAddress: String get() = "$listenIp:$listenPort"

    /** Renders this config as the argv byedpi's `parse_args()` expects (no argv[0]). */
    fun toArgs(): Array<String> = buildList {
        add("-i"); add(listenIp)
        add("-p"); add(listenPort.toString())
        add("-x"); add(debugLevel.toString())
        if (udpFakeCount > 0) {
            add("-a"); add(udpFakeCount.toString())
        }
    }.toTypedArray()
}
