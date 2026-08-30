package com.cosmicindustries.umbra.logging

import android.content.Context
import com.cosmicindustries.umbra.data.UmbraDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow

/**
 * All connection logging lives on-device only: a local Room table, a
 * local-file CSV export, and a local retention purge. Nothing in this class
 * (or anywhere else in Umbra) makes a network call — see NOTICE.md.
 */
class LogRepository(private val context: Context) {
    private val dao = UmbraDatabase.get(context).connectionEventDao()

    fun observeRecent(packageName: String? = null, limit: Int = 500): Flow<List<ConnectionEvent>> =
        dao.observeRecent(packageName, limit)

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun record(event: ConnectionEvent) = dao.insert(event)

    suspend fun purgeOlderThanDays(days: Int) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        dao.purgeOlderThan(cutoff)
    }

    suspend fun clearAll() = dao.clearAll()

    /** Writes a CSV to app-private external files (never uploaded); returns the file for the caller to share manually. */
    suspend fun exportCsv(): File {
        val events = dao.getAllForExport()
        val dir = File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(System.currentTimeMillis())
        val file = File(dir, "umbra-log-$stamp.csv")
        file.bufferedWriter().use { writer ->
            writer.appendLine("timestamp,package,uid,destHost,destPort,protocol,engine,bytesSent,bytesReceived,blocked")
            for (e in events) {
                writer.appendLine(
                    listOf(
                        e.timestampMillis, e.packageName, e.uid, e.destHost.orEmpty(),
                        e.destPort, e.protocol, e.engine.name, e.bytesSent, e.bytesReceived, e.blocked,
                    ).joinToString(","),
                )
            }
        }
        return file
    }
}
