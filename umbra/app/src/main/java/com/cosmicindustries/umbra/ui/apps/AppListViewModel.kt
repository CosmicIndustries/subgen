package com.cosmicindustries.umbra.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmicindustries.umbra.UmbraApp
import com.cosmicindustries.umbra.firewall.AppMode
import com.cosmicindustries.umbra.firewall.AppRule
import com.cosmicindustries.umbra.ui.SimpleViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppListViewModel(private val app: UmbraApp) : ViewModel() {

    val rules: StateFlow<List<AppRule>> = app.appRuleRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setMode(packageName: String, mode: AppMode) {
        viewModelScope.launch { app.appRuleRepository.setMode(packageName, mode) }
    }

    fun refresh() {
        viewModelScope.launch { app.appRuleRepository.sync() }
    }

    companion object {
        fun factory(app: UmbraApp) = SimpleViewModelFactory { AppListViewModel(app) }
    }
}
