package com.cosmicindustries.umbra.ui.dashboard

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmicindustries.umbra.UmbraApp
import com.cosmicindustries.umbra.data.UmbraMode
import com.cosmicindustries.umbra.firewall.AppMode
import com.cosmicindustries.umbra.firewall.ShizukuStatus
import com.cosmicindustries.umbra.tunnel.WireGuardConfigStore
import com.cosmicindustries.umbra.tunnel.WireGuardEngine
import com.cosmicindustries.umbra.vpn.UmbraVpnService
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(private val app: UmbraApp) : ViewModel() {

    private val wireGuardEngine = WireGuardEngine(app)
    private val wireGuardConfigStore = WireGuardConfigStore(app)

    val activeMode: StateFlow<UmbraMode> = app.settingsStore.activeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UmbraMode.OFF)

    val shizukuStatus: StateFlow<ShizukuStatus> = app.shizukuPermissionManager.status

    val wireGuardState: StateFlow<Tunnel.State> = wireGuardEngine.state

    private val _wireGuardError = MutableStateFlow<String?>(null)
    val wireGuardError: StateFlow<String?> = _wireGuardError

    fun requestShizukuPermission() = app.shizukuPermissionManager.requestPermission()

    /** Called once VpnService.prepare() consent (if any was needed) has already succeeded. */
    fun startDpiBypass(context: Context) {
        viewModelScope.launch {
            app.settingsStore.setActiveMode(UmbraMode.DPI_BYPASS)
            val intent = Intent(context, UmbraVpnService::class.java).setAction(UmbraVpnService.ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    fun stopDpiBypass(context: Context) {
        viewModelScope.launch {
            app.settingsStore.setActiveMode(UmbraMode.OFF)
            context.startService(Intent(context, UmbraVpnService::class.java).setAction(UmbraVpnService.ACTION_STOP))
        }
    }

    fun startWireGuard() {
        viewModelScope.launch {
            _wireGuardError.value = null
            val raw = wireGuardConfigStore.loadRaw()
            if (raw == null) {
                _wireGuardError.value = "No WireGuard config saved yet — paste one on the WireGuard tab first."
                return@launch
            }
            try {
                val includedApps = app.appRuleRepository.getByMode(AppMode.VPN_WIREGUARD).map { it.packageName }.toSet()
                val routed = WireGuardConfigStore.withAppRouting(raw, includedApps, emptySet())
                val config = WireGuardConfigStore.parse(routed)
                wireGuardEngine.start(config)
                app.settingsStore.setActiveMode(UmbraMode.WIREGUARD)
            } catch (e: Exception) {
                _wireGuardError.value = "Failed to start WireGuard: ${e.message}"
            }
        }
    }

    fun stopWireGuard() {
        viewModelScope.launch {
            wireGuardEngine.stop()
            app.settingsStore.setActiveMode(UmbraMode.OFF)
        }
    }

    fun hasWireGuardConfig(): Boolean = wireGuardConfigStore.loadRaw() != null

    companion object {
        fun factory(app: UmbraApp) = com.cosmicindustries.umbra.ui.SimpleViewModelFactory { DashboardViewModel(app) }
    }
}
