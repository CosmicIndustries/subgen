package com.cosmicindustries.umbra.firewall

import android.content.Context
import org.json.JSONObject

/**
 * Bulk-block presets for known Android bloat/telemetry system packages, sourced from the
 * Universal Android Debloater Next Generation project's package database
 * (https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation,
 * `resources/assets/uad_lists.json`, GPL-3.0). Only the package identifiers and their
 * "removal" risk tier are extracted into this app's own `assets/debloat_packages.json` —
 * none of that project's per-package descriptive text is bundled, so nothing beyond a bare
 * package-name/tier mapping is redistributed here. See NOTICE.md.
 *
 * UAD-ng classifies each package into one of four tiers; a fourth one, "Unsafe" (packages
 * UAD itself warns can bootloop a device if touched), is deliberately never exposed as a
 * bulk preset here — those still require setting a mode by hand, same as any other app.
 */
enum class DebloatTier {
    /** Safe to block for virtually everyone — the tier UAD-ng itself recommends by default. */
    RECOMMENDED,

    /** Fine for an experienced user; may remove a feature you actually use. */
    ADVANCED,

    /** Only for someone who knows exactly what the package does. */
    EXPERT,
}

/**
 * Loads `assets/debloat_packages.json` (generated from UAD-ng's list — see the enum doc
 * above) once and caches it for the process lifetime; the file only changes with an app
 * update, never at runtime.
 */
object DebloatList {
    private const val ASSET_PATH = "debloat_packages.json"

    @Volatile
    private var cache: Map<DebloatTier, Set<String>>? = null

    /**
     * Package names in [tier] and every less-aggressive tier below it — cumulative, matching
     * how UAD-ng's own UI treats these tiers (applying [DebloatTier.EXPERT] also covers
     * [DebloatTier.RECOMMENDED] and [DebloatTier.ADVANCED]).
     */
    fun packagesUpTo(context: Context, tier: DebloatTier): Set<String> {
        val byTier = load(context)
        return DebloatTier.entries
            .filter { it.ordinal <= tier.ordinal }
            .fold(emptySet<String>()) { acc, t -> acc + byTier[t].orEmpty() }
    }

    private fun load(context: Context): Map<DebloatTier, Set<String>> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val json = context.applicationContext.assets.open(ASSET_PATH).use {
                it.readBytes().toString(Charsets.UTF_8)
            }
            val obj = JSONObject(json)
            val result = mapOf(
                DebloatTier.RECOMMENDED to obj.stringSet("recommended"),
                DebloatTier.ADVANCED to obj.stringSet("advanced"),
                DebloatTier.EXPERT to obj.stringSet("expert"),
            )
            cache = result
            return result
        }
    }

    private fun JSONObject.stringSet(key: String): Set<String> {
        val arr = optJSONArray(key) ?: return emptySet()
        return buildSet(arr.length()) {
            for (i in 0 until arr.length()) add(arr.getString(i))
        }
    }
}
