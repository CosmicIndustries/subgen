package com.cosmicindustries.umbra

import android.app.Application
import com.cosmicindustries.umbra.data.SettingsStore
import com.cosmicindustries.umbra.firewall.AppRuleRepository
import com.cosmicindustries.umbra.firewall.ShizukuPermissionManager
import com.cosmicindustries.umbra.logging.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Composition root. Umbra deliberately skips Hilt/Dagger in favor of a
 * plain manual service locator: with no Android SDK/emulator available to
 * compile-check annotation processing in the environment this scaffold was
 * built in, hand-wired singletons are far less likely to hide a build break.
 */
class UmbraApp : Application() {

    val settingsStore by lazy { SettingsStore(this) }
    val appRuleRepository by lazy { AppRuleRepository(this) }
    val logRepository by lazy { LogRepository(this) }
    val shizukuPermissionManager by lazy { ShizukuPermissionManager() }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        shizukuPermissionManager.start()
        appScope.launch {
            appRuleRepository.sync()
            val days = settingsStore.logRetentionDays.first()
            logRepository.purgeOlderThanDays(days)
        }
    }
}
