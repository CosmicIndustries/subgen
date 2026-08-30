package com.cosmicindustries.umbra.logging

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionEventDao {
    @Insert
    suspend fun insert(event: ConnectionEvent)

    @Query(
        "SELECT * FROM connection_events " +
            "WHERE (:packageName IS NULL OR packageName = :packageName) " +
            "ORDER BY timestampMillis DESC LIMIT :limit",
    )
    fun observeRecent(packageName: String?, limit: Int = 500): Flow<List<ConnectionEvent>>

    @Query("SELECT * FROM connection_events ORDER BY timestampMillis DESC")
    suspend fun getAllForExport(): List<ConnectionEvent>

    @Query("DELETE FROM connection_events WHERE timestampMillis < :olderThanMillis")
    suspend fun purgeOlderThan(olderThanMillis: Long)

    @Query("DELETE FROM connection_events")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM connection_events")
    fun observeCount(): Flow<Int>
}
