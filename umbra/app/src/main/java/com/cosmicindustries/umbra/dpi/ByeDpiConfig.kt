package com.cosmicindustries.umbra.dpi

/**
 * byedpi's CLI flags (extracted from `external/byedpi/main.c`'s help
 * text), trimmed to what's relevant for byedpi's role in this app: a
 * local SOCKS5 (+ UDP ASSOCIATE) listener that [WireGuardEngine] wraps its
 * own transport through (see [WireGuardBridge.wgTurnOnViaByedpi] and
 * `app/src/main/go/socks5udpbind.go`).
 *
 * That's a UDP flow, so only byedpi's UDP-specific desync knobs apply —
 * `--split`/`--disorder`/`--oob`/`--tlsrec`/etc. are TCP-segment-framing
 * techniques with no meaning for a UDP relay; confirmed by reading
 * `external/byedpi/desync.c`'s `desync_udp()` directly, which only ever
 * reads `udp_fake_count`, `fake_data`, `fake_offset`, and `ttl` off its
 * `desync_params` — every other field is TCP-path-only. The three exposed
 * here are the ones that actually change UDP behavior:
 *
 * - `-a/--udp-fake <count>`: how many decoy datagrams precede each real
 *   one, the UDP analogue of byedpi's fake-packet TCP technique.
 * - `-t/--ttl <num>`: the TTL stamped on those decoys (byedpi's own
 *   default is 8, `DEFAULT_TTL` in desync.c). This is the knob the
 *   upstream README calls out as the one to actually tune per-network
 *   ("necessary to pick a value where the packet passes through DPI but
 *   does not reach the server") — too high and the decoy reaches the real
 *   destination same as a genuine packet (no bypass effect); too low and
 *   it expires before whatever's doing the inspection ever sees it.
 * - `-l/--fake-data <:string>`: overrides the decoy payload (byedpi's own
 *   built-in default is a fixed `fake_udp` constant in main.c). The
 *   leading `:` is byedpi's own convention (`ftob()` in main.c) for "this
 *   is a literal string, not a file path" — a bare path would try to
 *   `fopen()` it, which can't work from inside this app's sandbox anyway.
 *
 * byedpi defaults to accepting both SOCKS4 and SOCKS5 when no mode flag is
 * passed (`if (!params.mode) params.mode |= (MODE_SOCKS4 | MODE_SOCKS5);`
 * in main.c) — no explicit "--socks5" flag exists or is needed.
 */
data class ByeDpiConfig(
    val listenIp: String = "127.0.0.1",
    val listenPort: Int = 1080,
    val udpFakeCount: Int = 2,
    val fakeTtl: Int = 8,
    val customFakeData: String = "",
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
            add("-t"); add(fakeTtl.toString())
            if (customFakeData.isNotBlank()) {
                add("-l"); add(":$customFakeData")
            }
        }
    }.toTypedArray()
}
