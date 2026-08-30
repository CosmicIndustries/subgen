package com.cosmicindustries.umbra.ui.dpi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmicindustries.umbra.UmbraApp
import com.cosmicindustries.umbra.ui.SimpleViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ByeDpiSettingsViewModel(private val app: UmbraApp) : ViewModel() {

    val wrapEnabled: StateFlow<Boolean> = app.settingsStore.byedpiWrapEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val udpFakeCount: StateFlow<Int> = app.settingsStore.byedpiUdpFakeCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 2)

    fun setWrapEnabled(enabled: Boolean) = viewModelScope.launch { app.settingsStore.setByedpiWrapEnabled(enabled) }
    fun setUdpFakeCount(count: Int) = viewModelScope.launch { app.settingsStore.setByedpiUdpFakeCount(count) }

    companion object {
        fun factory(app: UmbraApp) = SimpleViewModelFactory { ByeDpiSettingsViewModel(app) }
    }
}
