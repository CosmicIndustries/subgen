package com.cosmicindustries.umbra.firewall

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-app routing decision, the same shape as ShizuWall's `appFirewallMode`
 * generalized to Umbra's extra engines:
 *  - ALLOW_DIRECT: excluded from the TUN entirely, no VPN/proxy/rules.
 *  - VPN_WIREGUARD: routed through the WireGuard tunnel (WireGuard mode only).
 *  - DPI_BYPASS: routed through byedpi via hev-socks5-tunnel (DPI-bypass mode only).
 *  - BLOCKED: no network access, enforced by [com.cosmicindustries.umbra.firewall.ShizukuFirewall]
 *    regardless of which VPN mode (or no VPN mode) is currently active.
 */
enum class AppMode {
    ALLOW_DIRECT,
    VPN_WIREGUARD,
    DPI_BYPASS,
    BLOCKED,
}

@Entity(tableName = "app_rules")
data class AppRule(
    @PrimaryKey val packageName: String,
    val uid: Int,
    val appName: String,
    val isSystemApp: Boolean,
    val mode: AppMode,
    val updatedAtMillis: Long,
)
