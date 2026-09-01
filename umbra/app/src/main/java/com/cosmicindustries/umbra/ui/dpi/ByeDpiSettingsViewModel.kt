package com.cosmicindustries.umbra.ui.dpi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmicindustries.umbra.UmbraApp
import com.cosmicindustries.umbra.ui.SimpleViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Edits are local until [save] is pressed — same pattern as
 * [com.cosmicindustries.umbra.ui.wireguard.WireGuardConfigViewModel], rather
 * than writing to [com.cosmicindustries.umbra.data.SettingsStore] on every
 * slider tick/keystroke. Current values are loaded once on creation, not
 * continuously collected, since this screen is the only writer.
 */
class ByeDpiSettingsViewModel(private val app: UmbraApp) : ViewModel() {

    private val _wrapEnabled = MutableStateFlow(true)
    val wrapEnabled: StateFlow<Boolean> = _wrapEnabled
    private val _udpFakeCount = MutableStateFlow(2)
    val udpFakeCount: StateFlow<Int> = _udpFakeCount
    private val _fakeTtl = MutableStateFlow(8)
    val fakeTtl: StateFlow<Int> = _fakeTtl
    private val _customFakeData = MutableStateFlow("")
    val customFakeData: StateFlow<String> = _customFakeData
    private val _scriptMode = MutableStateFlow(false)
    val scriptMode: StateFlow<Boolean> = _scriptMode
    private val _rawArgs = MutableStateFlow("")
    val rawArgs: StateFlow<String> = _rawArgs

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    init {
        viewModelScope.launch {
            val store = app.settingsStore
            _wrapEnabled.value = store.byedpiWrapEnabled.first()
            _udpFakeCount.value = store.byedpiUdpFakeCount.first()
            _fakeTtl.value = store.byedpiFakeTtl.first()
            _customFakeData.value = store.byedpiCustomFakeData.first()
            _scriptMode.value = store.byedpiScriptMode.first()
            _rawArgs.value = store.byedpiRawArgs.first()
        }
    }

    fun setWrapEnabled(enabled: Boolean) { _wrapEnabled.value = enabled; _saved.value = false }
    fun setUdpFakeCount(count: Int) { _udpFakeCount.value = count; _saved.value = false }
    fun setFakeTtl(ttl: Int) { _fakeTtl.value = ttl; _saved.value = false }
    fun setCustomFakeData(data: String) { _customFakeData.value = data; _saved.value = false }
    fun setScriptMode(enabled: Boolean) { _scriptMode.value = enabled; _saved.value = false }
    fun setRawArgs(args: String) { _rawArgs.value = args; _saved.value = false }

    fun save() {
        viewModelScope.launch {
            val store = app.settingsStore
            store.setByedpiWrapEnabled(_wrapEnabled.value)
            store.setByedpiUdpFakeCount(_udpFakeCount.value)
            store.setByedpiFakeTtl(_fakeTtl.value)
            store.setByedpiCustomFakeData(_customFakeData.value)
            store.setByedpiScriptMode(_scriptMode.value)
            store.setByedpiRawArgs(_rawArgs.value)
            _saved.value = true
        }
    }

    companion object {
        fun factory(app: UmbraApp) = SimpleViewModelFactory { ByeDpiSettingsViewModel(app) }
    }
}
