package com.cosmicindustries.umbra.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmicindustries.umbra.UmbraApp
import com.cosmicindustries.umbra.ui.SimpleViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val app: UmbraApp) : ViewModel() {

    val logRetentionDays: StateFlow<Int> = app.settingsStore.logRetentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 14)
    val startOnBoot: StateFlow<Boolean> = app.settingsStore.startOnBootFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setLogRetentionDays(days: Int) {
        viewModelScope.launch { app.settingsStore.setLogRetentionDays(days) }
    }

    fun setStartOnBoot(enabled: Boolean) {
        viewModelScope.launch { app.settingsStore.setStartOnBoot(enabled) }
    }

    companion object {
        fun factory(app: UmbraApp) = SimpleViewModelFactory { SettingsViewModel(app) }
    }
}
