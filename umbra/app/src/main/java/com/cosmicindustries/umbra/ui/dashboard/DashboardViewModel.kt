package com.cosmicindustries.umbra.ui.dashboard

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmicindustries.umbra.UmbraApp
import com.cosmicindustries.umbra.firewall.ShizukuStatus
import com.cosmicindustries.umbra.tunnel.WireGuardConfigStore
import com.cosmicindustries.umbra.vpn.UmbraVpnService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The actual Builder/WireGuard-engine/byedpi wiring all lives inside
 * [UmbraVpnService] now (VpnService.Builder can only be constructed from a
 * VpnService instance) — this ViewModel just starts/stops it via Intent and
 * observes the state/error it reports back through [UmbraApp.settingsStore].
 */
class DashboardViewModel(private val app: UmbraApp) : ViewModel() {

    private val wireGuardConfigStore = WireGuardConfigStore(app)

    val isRunning: StateFlow<Boolean> = app.settingsStore.isRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val lastError: StateFlow<String?> = app.settingsStore.lastError
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val shizukuStatus: StateFlow<ShizukuStatus> = app.shizukuPermissionManager.status

    fun requestShizukuPermission() = app.shizukuPermissionManager.requestPermission()

    fun hasWireGuardConfig(): Boolean = wireGuardConfigStore.loadRaw() != null

    /** Called once VpnService.prepare() consent (if any was needed) has already succeeded. */
    fun start(context: Context) {
        viewModelScope.launch { app.settingsStore.setLastError(null) }
        val intent = Intent(context, UmbraVpnService::class.java).setAction(UmbraVpnService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.startService(Intent(context, UmbraVpnService::class.java).setAction(UmbraVpnService.ACTION_STOP))
    }

    companion object {
        fun factory(app: UmbraApp) = com.cosmicindustries.umbra.ui.SimpleViewModelFactory { DashboardViewModel(app) }
    }
}
