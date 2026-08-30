package com.cosmicindustries.umbra.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.cosmicindustries.umbra.R
import com.cosmicindustries.umbra.data.SettingsStore
import com.cosmicindustries.umbra.dpi.ByeDpiConfig
import com.cosmicindustries.umbra.dpi.ByeDpiEngine
import com.cosmicindustries.umbra.dpi.TunnelConfig
import com.cosmicindustries.umbra.firewall.AppMode
import com.cosmicindustries.umbra.firewall.AppRuleRepository
import com.cosmicindustries.umbra.firewall.ShizukuFirewall
import com.cosmicindustries.umbra.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Owns the TUN interface for "DPI-bypass mode": apps tagged
 * [AppMode.DPI_BYPASS] get their traffic desynced by byedpi via
 * hev-socks5-tunnel (see [ByeDpiEngine]); apps tagged [AppMode.BLOCKED] are
 * hard-blocked through [ShizukuFirewall] regardless of this service's state;
 * everything else is left off the TUN and goes direct.
 *
 * This is deliberately a *different* VpnService than WireGuard mode, which
 * is driven entirely by GoBackend's own auto-managed VpnService (see
 * WireGuardEngine's kdoc for why the two can't share one TUN). Only one can
 * be the system's active VPN at a time; the dashboard enforces that as a
 * mode switch rather than the OS silently tearing one down when the other
 * starts.
 */
class UmbraVpnService : VpnService() {

    private lateinit var byeDpiEngine: ByeDpiEngine
    private lateinit var appRuleRepository: AppRuleRepository
    private lateinit var settingsStore: SettingsStore
    private val shizukuFirewall = ShizukuFirewall()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tunInterface: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        byeDpiEngine = ByeDpiEngine(this)
        appRuleRepository = AppRuleRepository(this)
        settingsStore = SettingsStore(this)
        createNotificationChannel()
    }

    private suspend fun currentByeDpiConfig(): ByeDpiConfig {
        val mode = runCatching {
            ByeDpiConfig.DesyncMode.valueOf(settingsStore.byeDpiDesyncMode.first())
        }.getOrDefault(ByeDpiConfig.DesyncMode.SPLIT)
        return ByeDpiConfig(
            desyncMode = mode,
            splitPosition = settingsStore.byeDpiSplitPosition.first(),
            fakeSni = settingsStore.byeDpiFakeSni.first().ifBlank { null },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        startTunnel()
        return START_STICKY
    }

    private fun startTunnel() {
        scope.launch {
            appRuleRepository.sync()
            val dpiBypassApps = appRuleRepository.getByMode(AppMode.DPI_BYPASS)
            val blockedApps = appRuleRepository.getByMode(AppMode.BLOCKED)

            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .addAddress(TunnelConfig.DEFAULT_IPV4, 24)
                .addAddress(TunnelConfig.DEFAULT_IPV6, 64)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("2606:4700:4700::1111")
                .setMtu(TunnelConfig.DEFAULT_MTU)
                .setBlocking(false)

            var includedAny = false
            for (rule in dpiBypassApps) {
                try {
                    builder.addAllowedApplication(rule.packageName)
                    includedAny = true
                } catch (_: PackageManager.NameNotFoundException) {
                    // App was uninstalled between sync() and now; skip it.
                }
            }
            // Never route our own process's traffic into the tunnel: byedpi's
            // own outbound connections must go direct or we'd loop back on ourselves.
            runCatching { builder.addDisallowedApplication(packageName) }

            if (!includedAny) {
                stopTunnel()
                return@launch
            }

            val established = builder.establish()
            if (established == null) {
                // Another VPN (or the always-on lockdown VPN) owns the interface.
                stopTunnel()
                return@launch
            }
            tunInterface = established

            try {
                byeDpiEngine.start(established.fd, currentByeDpiConfig())
            } catch (e: Exception) {
                stopTunnel()
                return@launch
            }

            shizukuFirewall.applyAll(blockedRules = blockedApps, unblockedRules = emptyList())
            updateNotification(active = true)
        }
    }

    private fun stopTunnel() {
        byeDpiEngine.stop()
        tunInterface?.let { runCatching { it.close() } }
        tunInterface = null
        updateNotification(active = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        stopTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        scope.cancel()
        tunInterface?.let { runCatching { it.close() } }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.vpn_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(active: Boolean = false): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, UmbraVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_vpn_ic)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(
                if (active) getString(R.string.vpn_notification_text_active) else getString(R.string.vpn_notification_text_idle),
            )
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.vpn_notification_stop), stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(active: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(active))
    }

    companion object {
        const val ACTION_START = "com.cosmicindustries.umbra.action.START"
        const val ACTION_STOP = "com.cosmicindustries.umbra.action.STOP"
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "umbra_tunnel"
    }
}
