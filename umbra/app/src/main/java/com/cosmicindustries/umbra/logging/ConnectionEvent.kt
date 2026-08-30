package com.cosmicindustries.umbra.logging

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TrafficEngine {
    WIREGUARD,
    DIRECT,
    BLOCKED,
}

/**
 * A single logged connection attempt. Strictly local: this table is never
 * written anywhere off-device (no analytics SDK, no crash reporter with
 * breadcrumbs, no sync). See [LogRepository] and NOTICE.md.
 */
@Entity(tableName = "connection_events")
data class ConnectionEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val packageName: String,
    val uid: Int,
    val destHost: String?,
    val destPort: Int,
    val protocol: String,
    val engine: TrafficEngine,
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val blocked: Boolean = false,
)
