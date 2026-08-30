package com.cosmicindustries.umbra.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmicindustries.umbra.UmbraApp
import com.cosmicindustries.umbra.logging.ConnectionEvent
import com.cosmicindustries.umbra.ui.SimpleViewModelFactory
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LogsViewModel(private val app: UmbraApp) : ViewModel() {

    val events: StateFlow<List<ConnectionEvent>> = app.logRepository.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _exportedFile = MutableStateFlow<File?>(null)
    val exportedFile: StateFlow<File?> = _exportedFile

    fun exportCsv() {
        viewModelScope.launch { _exportedFile.value = app.logRepository.exportCsv() }
    }

    fun clearAll() {
        viewModelScope.launch { app.logRepository.clearAll() }
    }

    companion object {
        fun factory(app: UmbraApp) = SimpleViewModelFactory { LogsViewModel(app) }
    }
}
