package com.cosmicindustries.umbra.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cosmicindustries.umbra.R
import com.cosmicindustries.umbra.data.SettingsStore
import com.cosmicindustries.umbra.dpi.ByeDpiConfig
import com.cosmicindustries.umbra.dpi.ByeDpiEngine
import com.cosmicindustries.umbra.firewall.AppMode
import com.cosmicindustries.umbra.firewall.AppRule
import com.cosmicindustries.umbra.firewall.AppRuleRepository
import com.cosmicindustries.umbra.firewall.ShizukuFirewall
import com.cosmicindustries.umbra.tunnel.WireGuardConfigStore
import com.cosmicindustries.umbra.tunnel.WireGuardEngine
import com.cosmicindustries.umbra.ui.MainActivity
import com.wireguard.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The single unified VpnService: one TUN, WireGuard as the tunnel for every
 * [AppMode.VPN_WIREGUARD] app (optionally with its own transport wrapped by
 * byedpi — see [com.cosmicindustries.umbra.tunnel.WireGuardBridge]),
 * [AppMode.BLOCKED] apps hard-blocked via [ShizukuFirewall] independent of
 * whether the tunnel is even running, and [AppMode.ALLOW_DIRECT] apps left
 * off the TUN entirely. See ARCHITECTURE.md for why this replaced the
 * earlier two-mutually-exclusive-modes design.
 */
class UmbraVpnService : VpnService() {

    private val wireGuardEngine = WireGuardEngine()
    private val byeDpiEngine = ByeDpiEngine()
    private val shizukuFirewall = ShizukuFirewall()
    private lateinit var appRuleRepository: AppRuleRepository
    private lateinit var settingsStore: SettingsStore
    private lateinit var wireGuardConfigStore: WireGuardConfigStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appRuleRepository = AppRuleRepository(this)
        settingsStore = SettingsStore(this)
        wireGuardConfigStore = WireGuardConfigStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAll()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification(active = false))
        startAll()
        return START_STICKY
    }

    private fun startAll() {
        scope.launch {
            Log.d(TAG, "startAll: syncing installed apps")
            appRuleRepository.sync()
            when (val outcome = prepareLaunch()) {
                is LaunchOutcome.Failed -> {
                    Log.w(TAG, "startAll: prepareLaunch failed: ${outcome.message}")
                    fail(outcome.message)
                }
                is LaunchOutcome.Ready -> finishLaunch(outcome)
            }
        }
    }

    /** Outcome of everything that can fail before a VPN interface actually exists. */
    private sealed interface LaunchOutcome {
        data class Ready(
            val config: Config,
            val byedpiAddr: String?,
            val builder: Builder,
            val blockedApps: List<AppRule>,
            val unblockedApps: List<AppRule>,
        ) : LaunchOutcome
        data class Failed(val message: String) : LaunchOutcome
    }

    /** Loads/parses the WireGuard config, starts byedpi if enabled, and builds the app-scoped [Builder]. */
    private suspend fun prepareLaunch(): LaunchOutcome {
        val rawConfig = wireGuardConfigStore.loadRaw()
            ?: return LaunchOutcome.Failed("No WireGuard config saved — paste one on the WireGuard tab first.")
        val config = try {
            WireGuardConfigStore.parse(rawConfig)
        } catch (e: Exception) {
            return LaunchOutcome.Failed("Invalid WireGuard config: ${e.message}")
        }

        val wireguardApps = appRuleRepository.getByMode(AppMode.VPN_WIREGUARD)
        val directApps = appRuleRepository.getByMode(AppMode.ALLOW_DIRECT)
        val blockedApps = appRuleRepository.getByMode(AppMode.BLOCKED)
        Log.d(
            TAG,
            "prepareLaunch: ${wireguardApps.size} WireGuard app(s), ${directApps.size} direct, " +
                "${blockedApps.size} blocked",
        )

        // Start byedpi (if wanted) before establish(): if it fails, nothing
        // owning a TUN fd exists yet, so there's nothing to leak or unwind.
        val byedpiAddr = if (settingsStore.byedpiWrapEnabled.first()) {
            val byedpiConfig = ByeDpiConfig(
                udpFakeCount = settingsStore.byedpiUdpFakeCount.first(),
                fakeTtl = settingsStore.byedpiFakeTtl.first(),
                customFakeData = settingsStore.byedpiCustomFakeData.first(),
                scriptMode = settingsStore.byedpiScriptMode.first(),
                rawArgs = settingsStore.byedpiRawArgs.first(),
            )
            try {
                byeDpiEngine.start(byedpiConfig)
                Log.d(TAG, "prepareLaunch: byedpi started at ${byedpiConfig.proxyAddress}")
            } catch (e: Exception) {
                return LaunchOutcome.Failed("byedpi failed to start: ${e.message}")
            }
            byedpiConfig.proxyAddress
        } else {
            null
        }

        val builder = buildTun(config)
        val includedAny = includeWireGuardApps(builder, wireguardApps)
        // Umbra's own traffic (this process's UID) is never explicitly excluded here:
        // Android's VpnService.Builder forbids mixing addAllowedApplication with
        // addDisallowedApplication on the same instance — once includeWireGuardApps() above
        // has added at least one app, the Builder is in allow-list mode and a later
        // addDisallowedApplication call would just throw. Allow-list semantics alone already
        // keep every non-listed app, Umbra included, off the tunnel; WireGuardEngine's own
        // protect() calls are the belt-and-suspenders for WireGuard's specific outbound socket.

        if (!includedAny) {
            byeDpiEngine.stop()
            return LaunchOutcome.Failed(
                "No apps are routed through WireGuard yet — set at least one app to \"WireGuard\" on the App List tab.",
            )
        }
        return LaunchOutcome.Ready(config, byedpiAddr, builder, blockedApps, directApps + wireguardApps)
    }

    /** Adds each still-installed app to [builder]'s allow-list; returns whether at least one was added. */
    private fun includeWireGuardApps(builder: Builder, wireguardApps: List<AppRule>): Boolean {
        var includedAny = false
        for (packageName in wireguardApps.map { it.packageName }) {
            try {
                builder.addAllowedApplication(packageName)
                includedAny = true
                Log.d(TAG, "includeWireGuardApps: added $packageName")
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "includeWireGuardApps: $packageName not found (uninstalled?): ${e.message}")
            }
        }
        return includedAny
    }

    /** Establishes the TUN and starts WireGuard now that [outcome] confirms everything else is ready. */
    private suspend fun finishLaunch(outcome: LaunchOutcome.Ready) {
        val established = outcome.builder.establish()
        if (established == null) {
            Log.w(TAG, "finishLaunch: Builder.establish() returned null")
            byeDpiEngine.stop()
            fail("Android denied establishing the VPN (another VPN may already own it).")
            return
        }
        // From here, WireGuardBridge owns the raw fd (mirrors upstream
        // GoBackend's own ParcelFileDescriptor.detachFd() use, and its own
        // error paths already close it on failure — see app/src/main/go/api.go)
        // — this service must not also try to close it.
        val tunFd = established.detachFd()

        try {
            wireGuardEngine.start(
                interfaceName = "umbra0",
                tunFd = tunFd,
                config = outcome.config,
                byedpiAddr = outcome.byedpiAddr,
                protect = ::protect,
            )
            Log.d(TAG, "finishLaunch: WireGuard tunnel up")
        } catch (e: Exception) {
            Log.w(TAG, "finishLaunch: WireGuard failed to start", e)
            byeDpiEngine.stop()
            fail("WireGuard failed to start: ${e.message}")
            return
        }

        // Every app not currently AppMode.BLOCKED is passed as unblockedRules, not just the
        // ones this launch is actively routing — otherwise an app blocked in an earlier
        // session (or by a debloat preset) whose mode was later changed away from BLOCKED
        // would stay firewalled at the OS level forever, since nothing else in this codebase
        // ever calls ShizukuFirewall.unblock() for it. unblockCommandsFor()'s commands are
        // idempotent, so re-unblocking an already-unblocked app is a harmless no-op.
        Log.d(
            TAG,
            "finishLaunch: applying Shizuku firewall — ${outcome.blockedApps.size} blocked, " +
                "${outcome.unblockedApps.size} unblocked",
        )
        shizukuFirewall.applyAll(blockedRules = outcome.blockedApps, unblockedRules = outcome.unblockedApps)
        settingsStore.setLastError(null)
        settingsStore.setRunning(true)
        updateNotification(active = true)
    }

    /** Builds the Builder's address/route/DNS/MTU from the parsed config's own [Interface]/[Peer] blocks. */
    private fun buildTun(config: Config): Builder {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(config.`interface`.mtu.orElse(DEFAULT_MTU))
            .setBlocking(false)

        for (address in config.`interface`.addresses) {
            builder.addAddress(address.address, address.mask)
        }
        val dnsServers = config.`interface`.dnsServers
        if (dnsServers.isEmpty()) {
            builder.addDnsServer(FALLBACK_DNS)
        } else {
            for (dns in dnsServers) builder.addDnsServer(dns)
        }
        for (peer in config.peers) {
            for (allowedIp in peer.allowedIps) {
                builder.addRoute(allowedIp.address, allowedIp.mask)
            }
        }
        return builder
    }

    private suspend fun fail(message: String) {
        Log.w(TAG, "fail: $message")
        settingsStore.setLastError(message)
        settingsStore.setRunning(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopAll() {
        Log.d(TAG, "stopAll")
        // Tear down the native engines synchronously, not via scope.launch: onDestroy()
        // cancels `scope` right after stopSelf(), which could cancel a launched
        // coroutine before wgTurnOff()/jniStopProxy() ever ran, leaking the
        // tunnel/proxy in the background. Neither call is suspend or slow (they're
        // JNI calls, not I/O), so there's no reason to hop off this thread for them.
        wireGuardEngine.stop()
        byeDpiEngine.stop()
        scope.launch { settingsStore.setRunning(false) }
        updateNotification(active = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        stopAll()
        super.onRevoke()
    }

    override fun onDestroy() {
        scope.cancel()
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

    private fun buildNotification(active: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, UmbraVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
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
        private const val TAG = "Umbra/VpnService"
        const val ACTION_START = "com.cosmicindustries.umbra.action.START"
        const val ACTION_STOP = "com.cosmicindustries.umbra.action.STOP"
        private const val DEFAULT_MTU = 1420
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "umbra_tunnel"

        // NOSONAR: this is Cloudflare's public DNS resolver (1.1.1.1), a well-known,
        // publicly documented address — not an internal/sensitive host — used only
        // as a fallback when the user's own WireGuard config specifies no DNS server.
        private const val FALLBACK_DNS = "1.1.1.1" // NOSONAR
    }
}
