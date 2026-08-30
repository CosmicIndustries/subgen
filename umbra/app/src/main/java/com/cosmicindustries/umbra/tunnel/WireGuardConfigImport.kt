package com.cosmicindustries.umbra.tunnel

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Reads a wg-quick config from a file the user picked via [ActivityResultContracts.OpenDocument]
 * (`WireGuardConfigScreen`'s "Import file" button), accepting either a plain `.conf` or a `.zip`
 * containing one. Sniffed by the zip local-file-header magic bytes (`PK\x03\x04`) rather than
 * trusting the picked document's reported MIME type or filename, since content providers are
 * inconsistent about both — the same detection the real WireGuard Android app effectively gets
 * for free from `ZipFile`/`ZipInputStream` throwing on non-zip input (confirmed present in its
 * decompiled classes, alongside "application/zip"/"text/plain" as the two MIME types it offers
 * the document picker).
 */
object WireGuardConfigImport {

    sealed interface Result {
        data class Success(val configText: String, val note: String? = null) : Result
        data class Error(val message: String) : Result
    }

    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04) // "PK\x03\x04"

    fun read(resolver: ContentResolver, uri: Uri): Result {
        val bytes = try {
            resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Result.Error("Could not open the selected file")
        } catch (e: Exception) {
            return Result.Error("Could not read the selected file: ${e.message}")
        }

        if (bytes.isEmpty()) return Result.Error("Selected file is empty")

        return if (bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals(ZIP_MAGIC)) {
            extractFromZip(bytes)
        } else {
            Result.Success(bytes.toString(Charsets.UTF_8))
        }
    }

    private fun extractFromZip(bytes: ByteArray): Result {
        val confEntries = mutableListOf<Pair<String, ByteArray>>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && name.substringAfterLast('/').endsWith(".conf", ignoreCase = true)) {
                        confEntries += name to zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            return Result.Error("Could not read zip file: ${e.message}")
        }

        if (confEntries.isEmpty()) return Result.Error("No .conf file found inside the zip")

        val chosen = confEntries.minByOrNull { it.first }!!
        val note = if (confEntries.size > 1) {
            "Zip had ${confEntries.size} .conf files; imported \"${chosen.first}\" " +
                "(Umbra keeps a single profile) — the others were skipped."
        } else {
            null
        }
        return Result.Success(chosen.second.toString(Charsets.UTF_8), note)
    }
}
