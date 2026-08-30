package com.cosmicindustries.umbra.dpi

/**
 * Typed builder over byedpi's real CLI flags (extracted from
 * `external/byedpi/main.c`'s help text / `params.h`'s `enum demode`). Only
 * the knobs someone tuning DPI evasion actually touches are modeled;
 * anything else can go through [extraArgs] verbatim.
 *
 * Desync strategy reference (positions use byedpi's `pos_t` mini-language:
 * `offset[:repeats:skip][+flag1[flag2]]`, e.g. "2" or "1,midsld"):
 *  - SPLIT    (-s): split the request in two writes at [splitPosition].
 *  - DISORDER (-d): split, then send the two pieces out of order.
 *  - OOB      (-o): split, send the second piece as TCP out-of-band data.
 *  - FAKE     (-f): send a bogus low-TTL packet before the real one.
 */
data class ByeDpiConfig(
    val listenIp: String = "127.0.0.1",
    val listenPort: Int = 1080,
    val maxConnections: Int = 512,
    val bufferSize: Int = 16384,
    val debugLevel: Int = 0,
    val allowUdp: Boolean = true,
    val resolveDomains: Boolean = true,
    val desyncMode: DesyncMode = DesyncMode.SPLIT,
    val splitPosition: String = "2",
    val fakeTtl: Int = 8,
    val fakeSni: String? = null,
    val hostsWhitelist: String? = null,
    val autoDesync: String? = "torst,tls,1,4",
    val cacheTtlSeconds: Int? = 300,
    val extraArgs: List<String> = emptyList(),
) {
    enum class DesyncMode(val flag: String?) {
        NONE(null),
        SPLIT("-s"),
        DISORDER("-d"),
        OOB("-o"),
        DISOOB("-q"),
        FAKE("-f"),
    }

    /** Renders this config as the argv byedpi's `parse_args()` expects (no argv[0]). */
    fun toArgs(): Array<String> = buildList {
        add("-i"); add(listenIp)
        add("-p"); add(listenPort.toString())
        add("-c"); add(maxConnections.toString())
        add("-b"); add(bufferSize.toString())
        add("-x"); add(debugLevel.toString())
        if (!allowUdp) add("-U")
        if (!resolveDomains) add("-N")

        if (desyncMode.flag != null) {
            add(desyncMode.flag)
            add(splitPosition)
        }
        if (desyncMode == DesyncMode.FAKE) {
            add("-t"); add(fakeTtl.toString())
            fakeSni?.let { add("-n"); add(it) }
        }
        hostsWhitelist?.let { add("-H"); add(it) }
        autoDesync?.let { add("-A"); add(it) }
        cacheTtlSeconds?.let { add("-u"); add(it.toString()) }

        addAll(extraArgs)
    }.toTypedArray()
}
