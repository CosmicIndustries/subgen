package com.cosmicindustries.umbra.dpi

/**
 * hev-socks5-tunnel's YAML config schema, trimmed to the keys Umbra needs
 * (full reference: `external/hev-socks5-tunnel/conf/main.yml`). Written to a
 * cache file whose path is handed to [TProxyService.TProxyStartService].
 */
data class TunnelConfig(
    val tunName: String = "tun0",
    val mtu: Int = DEFAULT_MTU,
    val tunnelIpv4: String = DEFAULT_IPV4,
    val tunnelIpv6: String = DEFAULT_IPV6,
    val socksAddress: String = "127.0.0.1",
    val socksPort: Int,
    val socksUdpMode: String = "udp",
) {
    fun toYaml(): String = buildString {
        appendLine("tunnel:")
        appendLine("  name: $tunName")
        appendLine("  mtu: $mtu")
        appendLine("  multi-queue: false")
        appendLine("  ipv4: $tunnelIpv4")
        appendLine("  ipv6: '$tunnelIpv6'")
        appendLine("  icmp: 'off'")
        appendLine()
        appendLine("socks5:")
        appendLine("  port: $socksPort")
        appendLine("  address: $socksAddress")
        appendLine("  udp: '$socksUdpMode'")
    }

    companion object {
        // 198.18.0.0/15 is the RFC 2544 benchmarking range: unroutable, so it
        // never collides with a real home/office LAN the way 10.x/192.168.x
        // might. hev-socks5-tunnel's own sample config uses the same
        // addresses; UmbraVpnService's VpnService.Builder MUST assign the
        // exact same ones (see UmbraVpnService.TUNNEL_IPV4/TUNNEL_IPV6)
        // since hev-socks5-tunnel's internal lwip stack needs to recognize
        // packets addressed to this host on the fd VpnService hands it.
        const val DEFAULT_IPV4 = "198.18.0.1"
        const val DEFAULT_IPV6 = "fc00::1"
        const val DEFAULT_MTU = 8500
    }
}
