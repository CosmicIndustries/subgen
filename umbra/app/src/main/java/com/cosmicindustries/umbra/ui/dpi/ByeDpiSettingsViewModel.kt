package com.cosmicindustries.umbra.ui.dpi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmicindustries.umbra.UmbraApp
import com.cosmicindustries.umbra.dpi.ByeDpiConfig
import com.cosmicindustries.umbra.ui.SimpleViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ByeDpiSettingsViewModel(private val app: UmbraApp) : ViewModel() {

    val desyncMode: StateFlow<String> = app.settingsStore.byeDpiDesyncMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "SPLIT")
    val splitPosition: StateFlow<String> = app.settingsStore.byeDpiSplitPosition
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "2")
    val fakeSni: StateFlow<String> = app.settingsStore.byeDpiFakeSni
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setDesyncMode(mode: String) = viewModelScope.launch { app.settingsStore.setByeDpiDesyncMode(mode) }
    fun setSplitPosition(position: String) = viewModelScope.launch { app.settingsStore.setByeDpiSplitPosition(position) }
    fun setFakeSni(sni: String) = viewModelScope.launch { app.settingsStore.setByeDpiFakeSni(sni) }

    /** Builds the [ByeDpiConfig] UmbraVpnService will use next time the DPI-bypass tunnel starts. */
    fun currentConfig(): ByeDpiConfig = ByeDpiConfig(
        desyncMode = runCatching { ByeDpiConfig.DesyncMode.valueOf(desyncMode.value) }
            .getOrDefault(ByeDpiConfig.DesyncMode.SPLIT),
        splitPosition = splitPosition.value,
        fakeSni = fakeSni.value.ifBlank { null },
    )

    companion object {
        fun factory(app: UmbraApp) = SimpleViewModelFactory { ByeDpiSettingsViewModel(app) }
    }
}
