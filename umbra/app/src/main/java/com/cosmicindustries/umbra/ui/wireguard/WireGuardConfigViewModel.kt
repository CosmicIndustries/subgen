package com.cosmicindustries.umbra.ui.wireguard

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmicindustries.umbra.UmbraApp
import com.cosmicindustries.umbra.tunnel.WireGuardConfigImport
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

    private val _importNote = MutableStateFlow<String?>(null)
    val importNote: StateFlow<String?> = _importNote

    fun onTextChanged(text: String) {
        _rawConfig.value = text
        _saved.value = false
        _importNote.value = null
    }

    /** QR contents are already wg-quick config text (same as the official app's QR export). */
    fun onQrScanned(contents: String) {
        onTextChanged(contents)
    }

    fun onFileImported(resolver: ContentResolver, uri: Uri) {
        when (val result = WireGuardConfigImport.read(resolver, uri)) {
            is WireGuardConfigImport.Result.Success -> {
                onTextChanged(result.configText)
                _importNote.value = result.note
            }
            is WireGuardConfigImport.Result.Error -> {
                _error.value = result.message
                _saved.value = false
            }
        }
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
