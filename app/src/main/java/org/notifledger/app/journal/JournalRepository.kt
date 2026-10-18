package org.notifledger.app.journal

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.notifledger.app.log.AppLogger
import org.notifledger.app.model.Transaction
import java.io.File

/**
 * The single point of journal I/O.
 *
 * Serializes every read-modify-write cycle with a mutex and does the
 * ContentResolver work on Dispatchers.IO. [JournalWriter] stays pure.
 */
class JournalRepository(private val app: Application) {

    private val mutex = Mutex()

    /** Journal content, or null when the read failed. */
    suspend fun read(path: String): String? = mutex.withLock {
        withContext(Dispatchers.IO) { readContent("Read", path) }
    }

    /** Append a transaction; returns fresh parsed entries, or null on failure. */
    suspend fun append(path: String, tx: Transaction): JournalWriteResult? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val existing = readContent("Append", path) ?: return@withContext null
            writeResult("Append", path, existing, JournalWriter.appendToContent(existing, tx))
        }
    }

    /** Append without re-parsing the whole file. Returns true only on success. */
    suspend fun appendRaw(path: String, tx: Transaction): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val existing = readContent("Append", path) ?: return@withContext false
            writeContent("Append", path, existing, JournalWriter.appendToContentRaw(existing, tx))
        }
    }

    suspend fun replace(path: String, offset: Int, tx: Transaction): JournalWriteResult? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val existing = readContent("Replace", path) ?: return@withContext null
            writeResult("Replace", path, existing, JournalWriter.replaceInContent(existing, offset, tx))
        }
    }

    suspend fun replaceMany(path: String, updates: Map<Int, Transaction>): JournalWriteResult? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val existing = readContent("Replace", path) ?: return@withContext null
            writeResult("Replace", path, existing, JournalWriter.replaceManyInContent(existing, updates))
        }
    }

    suspend fun delete(path: String, offset: Int): JournalWriteResult? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val existing = readContent("Delete", path) ?: return@withContext null
            writeResult("Delete", path, existing, JournalWriter.deleteFromContent(existing, offset))
        }
    }

    private fun readContent(operation: String, path: String): String? = try {
        val uri = Uri.parse(path)
        val stream = app.contentResolver.openInputStream(uri)
        if (stream == null) {
            AppLogger.error(TAG, "$operation read failed for $path: no input stream")
            null
        } else {
            stream.bufferedReader().use { it.readText() }
        }
    } catch (e: Exception) {
        AppLogger.error(TAG, "$operation read failed for $path: ${e.message}")
        null
    }

    private fun writeResult(
        operation: String,
        path: String,
        existing: String,
        result: JournalWriteResult,
    ): JournalWriteResult? =
        if (writeContent(operation, path, existing, result.content)) result else null

    private fun writeContent(operation: String, path: String, existing: String, content: String): Boolean {
        val backupAttempted = existing.isNotBlank()
        val backupNote = if (backupAttempted) " (previous content saved to $BACKUP_FILE_NAME)" else ""
        if (backupAttempted) {
            try {
                // ponytail: content:// writes are not atomic (no rename), so a truncated or failed
                // write has no undo — this backup is the recovery point. Revisit if a transactional
                // store ever appears.
                File(app.filesDir, BACKUP_FILE_NAME).writeText(existing)
            } catch (e: Exception) {
                AppLogger.error(
                    TAG,
                    "$operation write aborted for $path: backup to $BACKUP_FILE_NAME failed: " +
                        "${e.message} — journal untouched",
                )
                return false
            }
        }
        return try {
            val uri = Uri.parse(path)
            val stream = app.contentResolver.openOutputStream(uri, "wt")
            if (stream == null) {
                AppLogger.error(TAG, "$operation write failed for $path: no output stream$backupNote")
                false
            } else {
                stream.bufferedWriter().use { it.write(content) }
                true
            }
        } catch (e: Exception) {
            AppLogger.error(TAG, "$operation write failed for $path: ${e.message}$backupNote")
            false
        }
    }

    companion object {
        private const val TAG = "Journal"
        private const val BACKUP_FILE_NAME = "journal_backup.txt"
    }
}
