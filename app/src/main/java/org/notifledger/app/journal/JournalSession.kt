package org.notifledger.app.journal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.notifledger.app.NotifLedgerApp

/**
 * In-memory view of the parsed journal, shared by the per-screen ViewModels.
 *
 * Not a stale cache: [refresh] re-reads the journal file (the source of
 * truth), and [apply] only publishes entries a successful write already parsed.
 */
class JournalSession(private val app: NotifLedgerApp) {

    private val _entries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val entries: StateFlow<List<JournalEntry>> = _entries.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Re-read the journal from the configured path. False when the read failed. */
    suspend fun refresh(): Boolean {
        val path = app.settings.journalPath.first()
        if (path.isBlank()) {
            _entries.value = emptyList()
            _isLoading.value = false
            return true
        }
        val content = app.journalRepository.read(path)
        if (content == null) {
            _isLoading.value = false
            return false
        }
        _entries.value = withContext(Dispatchers.Default) { JournalWriter.readAll(content) }
        _isLoading.value = false
        return true
    }

    /** Publish entries a successful repository write already parsed. */
    fun apply(entries: List<JournalEntry>) {
        _entries.value = entries
    }
}
