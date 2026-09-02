package com.cosmicindustries.umbra.tunnel

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.InputStream
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
 *
 * Both the raw picked file and the zip's decompressed contents are size-capped (a real config is
 * a few KB) — a malicious/corrupt file otherwise reads unbounded data into memory, the classic
 * "zip bomb" resource-exhaustion risk of expanding an archive without limits.
 */
object WireGuardConfigImport {

    sealed interface Result {
        data class Success(val configText: String, val note: String? = null) : Result
        data class Error(val message: String) : Result
    }

    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04) // "PK\x03\x04"
    private const val MAX_INPUT_BYTES = 8 * 1024 * 1024 // 8 MiB: generous for any real wg-quick zip
    private const val MAX_ENTRY_BYTES = 1 * 1024 * 1024 // 1 MiB per decompressed .conf entry
    private const val MAX_ZIP_ENTRIES = 256

    fun read(resolver: ContentResolver, uri: Uri): Result {
        // Two distinct failure modes were previously collapsed into one null via
        // ?.use{} ?: — "couldn't open" and "too large" both flattened to null, so
        // the too-large branch below was unreachable dead code (Sonar caught
        // this as a "useless null check that always fails"). Kept separate here.
        val stream = try {
            resolver.openInputStream(uri) ?: return Result.Error("Could not open the selected file")
        } catch (e: Exception) {
            return Result.Error("Could not read the selected file: ${e.message}")
        }

        // NOSONAR: Sonar claims this elvis "always succeeds" (dead code), but that's
        // wrong — verified directly with a real kotlinc: the try-expression's type
        // stays ByteArray? (the catch branch's `return` is Nothing, which doesn't
        // widen the try block's own nullable type back to non-null), and a build
        // that actually returns null from readBounded() genuinely takes this branch.
        // Removing it would turn a real "file too large" case into an NPE instead.
        val bytes = try {
            stream.use { readBounded(it, MAX_INPUT_BYTES) }
        } catch (e: Exception) {
            return Result.Error("Could not read the selected file: ${e.message}")
        } ?: return Result.Error("Selected file is too large (over ${MAX_INPUT_BYTES / 1024 / 1024} MiB)") // NOSONAR

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
                var entryCount = 0
                while (entry != null) {
                    if (++entryCount > MAX_ZIP_ENTRIES) {
                        return Result.Error("Zip has too many entries (over $MAX_ZIP_ENTRIES)")
                    }
                    val name = entry.name
                    if (!entry.isDirectory && name.substringAfterLast('/').endsWith(".conf", ignoreCase = true)) {
                        val content = readBounded(zip, MAX_ENTRY_BYTES)
                            ?: return Result.Error("\"$name\" is too large (over ${MAX_ENTRY_BYTES / 1024} KiB decompressed)")
                        confEntries += name to content
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

    /** Reads [input] fully, or returns null (without silently truncating) if it exceeds [maxBytes]. */
    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray? {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            total += n
            if (total > maxBytes) return null
            buffer.write(chunk, 0, n)
        }
        return buffer.toByteArray()
    }
}
