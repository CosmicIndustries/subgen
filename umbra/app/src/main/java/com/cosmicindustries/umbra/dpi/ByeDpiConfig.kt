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
 *
 * ## Script mode
 *
 * [scriptMode] lets advanced users bypass the simple fields above entirely
 * and paste byedpi's *full* CLI argument surface directly — everything in
 * its README (`--split`, `--disorder`, `--auto`, `--fake-sni`, etc.), not
 * just the UDP-relevant subset this class otherwise exposes. Umbra doesn't
 * validate that content; it's passed to byedpi's own `parse_args()`
 * verbatim (tokenized the same way a shell would split it), which is the
 * authority on whether it's well-formed.
 *
 * The one thing script mode can't override is the listen address: this
 * app's own SOCKS5 client (`socks5udpbind.go`) connects to
 * `listenIp:listenPort` from *this* object's fields, not from whatever the
 * user's script says, so any `-i`/`--ip`/`-p`/`--port` in the pasted
 * script is stripped before [listenIp]/[listenPort] are prepended — a
 * script setting a different bind address would otherwise make byedpi
 * listen somewhere this app never connects to, silently breaking the
 * wrap rather than erroring.
 */
data class ByeDpiConfig(
    val listenIp: String = "127.0.0.1",
    val listenPort: Int = 1080,
    val udpFakeCount: Int = 2,
    val fakeTtl: Int = 8,
    val customFakeData: String = "",
    val debugLevel: Int = 0,
    val scriptMode: Boolean = false,
    val rawArgs: String = "",
) {
    val proxyAddress: String get() = "$listenIp:$listenPort"

    /** Renders this config as the argv byedpi's `parse_args()` expects (no argv[0]). */
    fun toArgs(): Array<String> = buildList {
        add("-i"); add(listenIp)
        add("-p"); add(listenPort.toString())
        add("-x"); add(debugLevel.toString())
        if (scriptMode && rawArgs.isNotBlank()) {
            addAll(stripListenFlags(tokenize(rawArgs)))
        } else if (udpFakeCount > 0) {
            add("-a"); add(udpFakeCount.toString())
            add("-t"); add(fakeTtl.toString())
            if (customFakeData.isNotBlank()) {
                add("-l"); add(":$customFakeData")
            }
        }
    }.toTypedArray()

    companion object {
        private val LISTEN_FLAGS = setOf("-i", "--ip", "-p", "--port")

        /** Drops any listen-address flag (and its following value) from an already-tokenized arg list. */
        private fun stripListenFlags(tokens: List<String>): List<String> {
            val result = mutableListOf<String>()
            var i = 0
            while (i < tokens.size) {
                val tok = tokens[i]
                val eq = tok.indexOf('=')
                val flag = if (eq >= 0) tok.substring(0, eq) else tok
                if (flag in LISTEN_FLAGS) {
                    i += if (eq >= 0) 1 else 2 // "--ip=1.2.3.4" consumes one token; "--ip 1.2.3.4" consumes two
                    continue
                }
                result += tok
                i++
            }
            return result
        }

        /**
         * Shell-like tokenizer: splits on unquoted whitespace, honors single/double
         * quotes and backslash escapes (matching what a user pasting a byedpi
         * command line from a terminal or the README would expect), without
         * pulling in a full shell grammar this app has no other use for.
         */
        internal fun tokenize(input: String): List<String> {
            val tokens = mutableListOf<String>()
            val current = StringBuilder()
            var inToken = false
            var quote: Char? = null
            var i = 0
            while (i < input.length) {
                val c = input[i]
                when {
                    quote != null -> {
                        if (c == '\\' && quote == '"' && i + 1 < input.length) {
                            current.append(input[i + 1]); i++
                        } else if (c == quote) {
                            quote = null
                        } else {
                            current.append(c)
                        }
                    }
                    c == '\'' || c == '"' -> { quote = c; inToken = true }
                    c == '\\' && i + 1 < input.length -> { current.append(input[i + 1]); i++; inToken = true }
                    c.isWhitespace() -> {
                        if (inToken) { tokens += current.toString(); current.clear(); inToken = false }
                    }
                    else -> { current.append(c); inToken = true }
                }
                i++
            }
            if (inToken || current.isNotEmpty()) tokens += current.toString()
            return tokens
        }
    }
}
