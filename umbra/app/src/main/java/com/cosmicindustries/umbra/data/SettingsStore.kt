package com.cosmicindustries.umbra.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "umbra_settings")

enum class UmbraMode { OFF, WIREGUARD, DPI_BYPASS }

/** Non-secret app settings (retention days, active mode, start-on-boot). Secrets live in WireGuardConfigStore's encrypted prefs instead. */
class SettingsStore(private val context: Context) {

    val activeMode: Flow<UmbraMode> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_MODE]?.let { runCatching { UmbraMode.valueOf(it) }.getOrNull() } ?: UmbraMode.OFF
    }

    val logRetentionDays: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_RETENTION_DAYS] ?: 14 }
    val startOnBootFlow: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_START_ON_BOOT] ?: false }

    val byeDpiDesyncMode: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_DPI_DESYNC_MODE] ?: "SPLIT" }
    val byeDpiSplitPosition: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_DPI_SPLIT_POS] ?: "2" }
    val byeDpiFakeSni: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_DPI_FAKE_SNI] ?: "" }

    suspend fun setActiveMode(mode: UmbraMode) {
        context.dataStore.edit { it[KEY_ACTIVE_MODE] = mode.name }
    }

    suspend fun setLogRetentionDays(days: Int) {
        context.dataStore.edit { it[KEY_RETENTION_DAYS] = days }
    }

    suspend fun setByeDpiDesyncMode(mode: String) {
        context.dataStore.edit { it[KEY_DPI_DESYNC_MODE] = mode }
    }

    suspend fun setByeDpiSplitPosition(position: String) {
        context.dataStore.edit { it[KEY_DPI_SPLIT_POS] = position }
    }

    suspend fun setByeDpiFakeSni(sni: String) {
        context.dataStore.edit { it[KEY_DPI_FAKE_SNI] = sni }
    }

    suspend fun setStartOnBoot(enabled: Boolean) {
        context.dataStore.edit { it[KEY_START_ON_BOOT] = enabled }
    }

    suspend fun startOnBoot(): Boolean = context.dataStore.data.map { it[KEY_START_ON_BOOT] ?: false }.first()

    companion object {
        private val KEY_ACTIVE_MODE = stringPreferencesKey("active_mode")
        private val KEY_RETENTION_DAYS = intPreferencesKey("log_retention_days")
        private val KEY_START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        private val KEY_DPI_DESYNC_MODE = stringPreferencesKey("dpi_desync_mode")
        private val KEY_DPI_SPLIT_POS = stringPreferencesKey("dpi_split_position")
        private val KEY_DPI_FAKE_SNI = stringPreferencesKey("dpi_fake_sni")
    }
}
