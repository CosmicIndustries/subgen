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

/** Non-secret app settings. Secrets (the WireGuard private key) live in WireGuardConfigStore's encrypted prefs instead. */
class SettingsStore(private val context: Context) {

    val isRunning: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_RUNNING] ?: false }

    /** Set by UmbraVpnService when it fails to start (bad/missing config, establish() denied, etc.); cleared on a successful start. */
    val lastError: Flow<String?> = context.dataStore.data.map { prefs -> prefs[KEY_LAST_ERROR] }

    val logRetentionDays: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_RETENTION_DAYS] ?: 14 }
    val startOnBootFlow: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_START_ON_BOOT] ?: false }

    /** Whether WireGuard's own transport is relayed through byedpi (see WireGuardBridge.wgTurnOnViaByedpi). */
    val byedpiWrapEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_BYEDPI_WRAP_ENABLED] ?: true }
    val byedpiUdpFakeCount: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_BYEDPI_UDP_FAKE_COUNT] ?: 2 }
    /** TTL stamped on byedpi's decoy UDP packets (`-t/--ttl`); byedpi's own default is 8. */
    val byedpiFakeTtl: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_BYEDPI_FAKE_TTL] ?: 8 }
    /** Overrides byedpi's decoy payload (`-l/--fake-data`); blank keeps byedpi's own built-in default. */
    val byedpiCustomFakeData: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_BYEDPI_CUSTOM_FAKE_DATA] ?: "" }

    suspend fun setRunning(running: Boolean) {
        context.dataStore.edit { it[KEY_RUNNING] = running }
    }

    suspend fun setLastError(message: String?) {
        context.dataStore.edit {
            if (message == null) it.remove(KEY_LAST_ERROR) else it[KEY_LAST_ERROR] = message
        }
    }

    suspend fun setLogRetentionDays(days: Int) {
        context.dataStore.edit { it[KEY_RETENTION_DAYS] = days }
    }

    suspend fun setByedpiWrapEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BYEDPI_WRAP_ENABLED] = enabled }
    }

    suspend fun setByedpiUdpFakeCount(count: Int) {
        context.dataStore.edit { it[KEY_BYEDPI_UDP_FAKE_COUNT] = count }
    }

    suspend fun setByedpiFakeTtl(ttl: Int) {
        context.dataStore.edit { it[KEY_BYEDPI_FAKE_TTL] = ttl }
    }

    suspend fun setByedpiCustomFakeData(data: String) {
        context.dataStore.edit { it[KEY_BYEDPI_CUSTOM_FAKE_DATA] = data }
    }

    suspend fun setStartOnBoot(enabled: Boolean) {
        context.dataStore.edit { it[KEY_START_ON_BOOT] = enabled }
    }

    suspend fun startOnBoot(): Boolean = context.dataStore.data.map { it[KEY_START_ON_BOOT] ?: false }.first()

    companion object {
        private val KEY_RUNNING = booleanPreferencesKey("running")
        private val KEY_LAST_ERROR = stringPreferencesKey("last_error")
        private val KEY_RETENTION_DAYS = intPreferencesKey("log_retention_days")
        private val KEY_START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        private val KEY_BYEDPI_WRAP_ENABLED = booleanPreferencesKey("byedpi_wrap_enabled")
        private val KEY_BYEDPI_UDP_FAKE_COUNT = intPreferencesKey("byedpi_udp_fake_count")
        private val KEY_BYEDPI_FAKE_TTL = intPreferencesKey("byedpi_fake_ttl")
        private val KEY_BYEDPI_CUSTOM_FAKE_DATA = stringPreferencesKey("byedpi_custom_fake_data")
    }
}
