package com.cosmicindustries.umbra.ui.wireguard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmicindustries.umbra.UmbraApp
import com.cosmicindustries.umbra.tunnel.WireGuardConfigStore
import com.cosmicindustries.umbra.ui.SimpleViewModelFactory
import com.wireguard.config.BadConfigException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WireGuardConfigViewModel(app: UmbraApp) : ViewModel() {
    private val store = WireGuardConfigStore(app)

    private val _rawConfig = MutableStateFlow(store.loadRaw() ?: "")
    val rawConfig: StateFlow<String> = _rawConfig

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    fun onTextChanged(text: String) {
        _rawConfig.value = text
        _saved.value = false
    }

    fun save() {
        viewModelScope.launch {
            try {
                WireGuardConfigStore.parse(_rawConfig.value)
                store.saveRaw(_rawConfig.value)
                _error.value = null
                _saved.value = true
            } catch (e: BadConfigException) {
                _error.value = e.message ?: "Invalid WireGuard config"
                _saved.value = false
            }
        }
    }

    companion object {
        fun factory(app: UmbraApp) = SimpleViewModelFactory { WireGuardConfigViewModel(app) }
    }
}
