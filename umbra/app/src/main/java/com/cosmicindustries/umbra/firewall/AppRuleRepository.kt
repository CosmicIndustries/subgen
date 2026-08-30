package com.cosmicindustries.umbra.firewall

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.cosmicindustries.umbra.data.UmbraDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Source of truth for per-app rules. Seeds itself from [PackageManager] at
 * first run (and on every [sync] call) rather than from any bundled list —
 * the installed-app set is the device owner's, not something to ship
 * hardcoded in the repo.
 */
class AppRuleRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = UmbraDatabase.get(appContext).appRuleDao()
    private val packageManager = appContext.packageManager

    fun observeAll(): Flow<List<AppRule>> = dao.observeAll()

    suspend fun getByMode(mode: AppMode): List<AppRule> = dao.getByMode(mode)

    suspend fun setMode(packageName: String, mode: AppMode) {
        dao.setMode(packageName, mode, System.currentTimeMillis())
    }

    /** Re-scans installed apps: adds any new ones as [AppMode.ALLOW_DIRECT], prunes uninstalled ones. */
    suspend fun sync() {
        val installed = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val now = System.currentTimeMillis()
        val rules = installed.map { info -> info.toDefaultRule(now) }
        dao.insertAllIfAbsent(rules)
        dao.pruneUninstalled(installed.map { it.packageName })
    }

    private fun ApplicationInfo.toDefaultRule(now: Long): AppRule = AppRule(
        packageName = packageName,
        uid = uid,
        appName = packageManager.getApplicationLabel(this).toString(),
        isSystemApp = (flags and ApplicationInfo.FLAG_SYSTEM) != 0,
        mode = AppMode.ALLOW_DIRECT,
        updatedAtMillis = now,
    )
}
