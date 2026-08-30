package com.cosmicindustries.umbra.firewall

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rules ORDER BY appName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<AppRule>>

    @Query("SELECT * FROM app_rules WHERE mode = :mode")
    suspend fun getByMode(mode: AppMode): List<AppRule>

    @Query("SELECT * FROM app_rules WHERE packageName = :packageName")
    suspend fun get(packageName: String): AppRule?

    @Upsert
    suspend fun upsert(rule: AppRule)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfAbsent(rules: List<AppRule>)

    @Query("UPDATE app_rules SET mode = :mode, updatedAtMillis = :updatedAtMillis WHERE packageName = :packageName")
    suspend fun setMode(packageName: String, mode: AppMode, updatedAtMillis: Long)

    @Query("DELETE FROM app_rules WHERE packageName NOT IN (:installedPackages)")
    suspend fun pruneUninstalled(installedPackages: List<String>)
}
