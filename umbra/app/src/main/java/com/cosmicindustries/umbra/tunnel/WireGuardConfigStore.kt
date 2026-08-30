package com.cosmicindustries.umbra.tunnel

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wireguard.config.BadConfigException
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.StringReader

/**
 * Stores the raw wg-quick-style config text the user pasted/imported, in an
 * app-private encrypted preference file (the [Interface] block carries the
 * private key). Umbra keeps exactly one WireGuard tunnel config at a time —
 * "one profile" is enough for the merge-with-DPI-bypass use case this app
 * targets; multi-tunnel management is what the standalone WireGuard app is
 * already good at.
 */
class WireGuardConfigStore(context: Context) {

    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "umbra_wireguard_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun saveRaw(rawConfigText: String) {
        prefs.edit().putString(KEY_RAW_CONFIG, rawConfigText).apply()
    }

    fun loadRaw(): String? = prefs.getString(KEY_RAW_CONFIG, null)

    fun clear() {
        prefs.edit().remove(KEY_RAW_CONFIG).apply()
    }

    companion object {
        private const val KEY_RAW_CONFIG = "raw_config"

        @Throws(BadConfigException::class)
        fun parse(rawConfigText: String): Config =
            Config.parse(BufferedReader(StringReader(rawConfigText)))

        /**
         * Rewrites the [Interface] block's `IncludedApplications` /
         * `ExcludedApplications` lines (a wireguard-android-specific
         * extension to the wg-quick format, applied by GoBackend's VpnService
         * when it builds its Builder) to match the current per-app rules.
         *
         * Only one of the two should be non-empty: IncludedApplications is a
         * routed-through-VPN allowlist, ExcludedApplications is a
         * bypass-the-VPN denylist. We always emit IncludedApplications when
         * the caller has a concrete VPN_WIREGUARD app set, since an explicit
         * allowlist is unambiguous; pass an empty [includedPackages] with a
         * non-empty [excludedPackages] to route everything except those.
         */
        fun withAppRouting(
            rawConfigText: String,
            includedPackages: Set<String>,
            excludedPackages: Set<String>,
        ): String {
            val lines = rawConfigText.lines().toMutableList()
            val cleaned = lines.filterNot {
                val key = it.substringBefore('=').trim()
                key.equals("IncludedApplications", ignoreCase = true) ||
                    key.equals("ExcludedApplications", ignoreCase = true)
            }.toMutableList()

            val interfaceIndex = cleaned.indexOfFirst { it.trim().equals("[Interface]", ignoreCase = true) }
            if (interfaceIndex == -1) {
                // Malformed config; let Config.parse() surface the real error.
                return cleaned.joinToString("\n")
            }

            val newLines = buildList {
                if (includedPackages.isNotEmpty()) {
                    add("IncludedApplications = ${includedPackages.joinToString(",")}")
                } else if (excludedPackages.isNotEmpty()) {
                    add("ExcludedApplications = ${excludedPackages.joinToString(",")}")
                }
            }
            cleaned.addAll(interfaceIndex + 1, newLines)
            return cleaned.joinToString("\n")
        }
    }
}
