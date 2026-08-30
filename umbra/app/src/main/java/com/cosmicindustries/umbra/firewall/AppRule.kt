package com.cosmicindustries.umbra.firewall

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-app routing decision, the same shape as ShizuWall's `appFirewallMode`:
 *  - ALLOW_DIRECT: excluded from the TUN entirely, no VPN/proxy/rules.
 *  - VPN_WIREGUARD: routed through the (single, always-on-when-started)
 *    WireGuard tunnel — see [com.cosmicindustries.umbra.tunnel.WireGuardEngine].
 *    Whether that tunnel's own transport is itself wrapped by byedpi is a
 *    global setting, not a per-app choice — see
 *    [com.cosmicindustries.umbra.data.SettingsStore.byedpiWrapEnabled].
 *  - BLOCKED: no network access, enforced by
 *    [com.cosmicindustries.umbra.firewall.ShizukuFirewall] regardless of
 *    whether the tunnel is running.
 *
 * There used to be a separate DPI_BYPASS mode routing specific apps through
 * byedpi directly (no WireGuard), requiring a second, independently-active
 * TUN — which Android's one-VPN-at-a-time constraint made mutually
 * exclusive with WireGuard mode. Dropped in favor of using byedpi to wrap
 * WireGuard's own transport instead, which needs only one TUN/session and
 * lets both run together. See ARCHITECTURE.md.
 */
enum class AppMode {
    ALLOW_DIRECT,
    VPN_WIREGUARD,
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
