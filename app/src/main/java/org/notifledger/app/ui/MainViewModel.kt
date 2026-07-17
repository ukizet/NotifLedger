package org.notifledger.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.journal.JournalWriter
import org.notifledger.app.log.AppLogger
import org.notifledger.app.log.LogEntry
import org.notifledger.app.model.CategorizationRule
import org.notifledger.app.model.SortOrder
import org.notifledger.app.model.Transaction
import org.notifledger.app.parser.ParserEngine
import org.notifledger.app.parser.RuleIO
import org.notifledger.app.settings.SettingsManager

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val settings = SettingsManager(application)

    /** Serializes journal read-modify-write so concurrent edits can't lose entries. */
    private val journalMutex = Mutex()

    /** Captured so we can unsubscribe in [onCleared] (AppLogger outlives the ViewModel). */
    private var logSubscription: ((List<LogEntry>) -> Unit)? = null

    private val _allEntries = MutableStateFlow<List<JournalEntry>>(emptyList())

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun consumeMessage() {
        _uiMessage.value = null
    }

    private fun emitMessage(msg: String) {
        _uiMessage.value = msg
    }

    val existingPayees: StateFlow<List<String>> = _allEntries.map { entries ->
        entries.map { it.payee }.filter { it.isNotBlank() }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val existingAccounts: StateFlow<List<String>> = _allEntries.map { entries ->
        entries.flatMap { it.postings.map { p -> p.account } }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sortOrder = MutableStateFlow(SortOrder.NewestFirst)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _pageLimit = MutableStateFlow(20)
    val pageLimit: StateFlow<Int> = _pageLimit.asStateFlow()

    /** Sorted + limited entries ready for the UI. */
    val entries: StateFlow<List<JournalEntry>> = combine(
        _allEntries,
        _sortOrder,
        _pageLimit,
    ) { all, sort, limit ->
        val sorted = when (sort) {
            SortOrder.NewestFirst -> all.sortedByDescending { it.date }
            SortOrder.OldestFirst -> all.sortedBy { it.date }
            SortOrder.HighestAmount -> all.sortedByDescending { it.postings.firstOrNull()?.amount?.toDoubleOrNull() ?: 0.0 }
            SortOrder.LowestAmount -> all.sortedBy { it.postings.firstOrNull()?.amount?.toDoubleOrNull() ?: 0.0 }
        }
        sorted.take(limit.coerceAtLeast(1))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _categorizationRules = MutableStateFlow<List<CategorizationRule>>(emptyList())
    val categorizationRules: StateFlow<List<CategorizationRule>> = _categorizationRules.asStateFlow()

    val defaultAccount = settings.defaultAccount.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), "assets:bank:checking"
    )

    val journalPath = settings.journalPath.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), ""
    )

    val defaultCurrency = settings.defaultCurrency.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), "NOK"
    )

    val notificationSources = settings.notificationSources.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val logEntries: StateFlow<List<LogEntry>>

    init {
        val logState = MutableStateFlow(AppLogger.entries())
        logEntries = logState.asStateFlow()
        val subscription: (List<LogEntry>) -> Unit = { entries -> logState.value = entries }
        logSubscription = subscription
        AppLogger.subscribe(subscription)

        viewModelScope.launch {
            _sortOrder.value = SortOrder.fromValue(settings.sortOrder.first())
            settings.sortOrder.collect { _sortOrder.value = SortOrder.fromValue(it) }
        }
        viewModelScope.launch {
            _pageLimit.value = settings.pageLimit.first()
            settings.pageLimit.collect { _pageLimit.value = it }
        }
        viewModelScope.launch {
            settings.journalPath.collect { path ->
                if (path.isNotBlank()) {
                    val content = readJournalContent(path)
                    if (content != null) {
                        _allEntries.value = JournalWriter.readAll(content)
                    }
                } else {
                    _allEntries.value = emptyList()
                }
                _isLoading.value = false
            }
        }
        viewModelScope.launch {
            val rulesDir = getRulesDir()
            _categorizationRules.value = RuleIO.loadCategorizationRules(rulesDir)
            AppLogger.info("Rules", "Loaded ${_categorizationRules.value.size} categorization rules")
        }

        AppLogger.info("App", "MainViewModel initialized")
    }

    override fun onCleared() {
        super.onCleared()
        logSubscription?.let { AppLogger.unsubscribe(it) }
        logSubscription = null
    }

    private suspend fun readJournalContent(uriString: String): String? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val context = getApplication<Application>()
            context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader().use { r -> r.readText() }
            } ?: ""
        } catch (e: Exception) {
            AppLogger.error("Journal", "Read failed: ${e.message}")
            emitMessage("Couldn't read journal — see Logs")
            null
        }
    }

    private suspend fun writeJournalContent(uriString: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val context = getApplication<Application>()
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                it.write(content)
            } != null
        } catch (e: Exception) {
            AppLogger.error("Journal", "Write failed: ${e.message}")
            false
        }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { settings.setSortOrder(SortOrder.toValue(order)) }
    }

    fun setPageLimit(limit: Int) {
        viewModelScope.launch { settings.setPageLimit(limit) }
    }

    fun addTransaction(tx: Transaction) {
        viewModelScope.launch {
            journalMutex.withLock {
                val path = settings.journalPath.first()
                if (path.isBlank()) return@withLock
                val existing = readJournalContent(path) ?: return@withLock
                val updated = JournalWriter.appendToContent(existing, tx)
                emitSaveResult(writeJournalContent(path, updated))
                _allEntries.value = JournalWriter.readAll(updated)
            }
        }
    }

    fun editTransaction(offset: Int, tx: Transaction) {
        viewModelScope.launch {
            journalMutex.withLock {
                val path = settings.journalPath.first()
                if (path.isBlank()) return@withLock
                val content = readJournalContent(path) ?: return@withLock
                val updated = JournalWriter.replaceInContent(content, offset, tx)
                emitSaveResult(writeJournalContent(path, updated))
                _allEntries.value = JournalWriter.readAll(updated)
            }
        }
    }

    fun deleteTransaction(offset: Int) {
        viewModelScope.launch {
            journalMutex.withLock {
                val path = settings.journalPath.first()
                if (path.isBlank()) return@withLock
                val content = readJournalContent(path) ?: return@withLock
                val lines = content.lines().toMutableList()
                val entryLineCount = JournalWriter.countEntryLines(lines, offset)
                repeat(entryLineCount) { if (offset < lines.size) lines.removeAt(offset) }
                // Remove the blank line separator that follows
                if (offset < lines.size && lines[offset].isBlank()) {
                    lines.removeAt(offset)
                }
                val updated = lines.joinToString("\n")
                if (writeJournalContent(path, updated)) emitMessage("Deleted") else emitMessage("Delete failed — see Logs")
                _allEntries.value = JournalWriter.readAll(updated)
            }
        }
    }

    private fun emitSaveResult(wrote: Boolean) {
        emitMessage(if (wrote) "Saved" else "Save failed — see Logs")
    }

    fun reloadEntries() {
        viewModelScope.launch {
            val path = settings.journalPath.first()
            if (path.isNotBlank()) {
                val content = readJournalContent(path)
                if (content != null) {
                    _allEntries.value = JournalWriter.readAll(content)
                }
            }
        }
    }

    suspend fun simulateNotification(title: String, text: String): String {
        val defaultAccount = settings.defaultAccount.first()

        AppLogger.info("Simulate", "Simulating notification: «$title» — «$text»")

        val result = ParserEngine.parse(title, text, "expenses:unknown", defaultAccount)
        if (result == null) {
            AppLogger.warn("Simulate", "No number found")
            emitMessage("No number found in notification text.")
            return "No number found in notification text."
        }

        return journalMutex.withLock {
            val path = settings.journalPath.first()
            if (path.isBlank()) {
                AppLogger.warn("Simulate", "No journal file set")
                emitMessage("No journal file set.")
                return@withLock "No journal file set."
            }
            val existing = readJournalContent(path) ?: return@withLock "Read failed — see Logs"
            val updated = JournalWriter.appendToContent(existing, result)
            if (!writeJournalContent(path, updated)) {
                emitMessage("Save failed — see Logs")
                return@withLock "Save failed — see Logs"
            }
            _allEntries.value = JournalWriter.readAll(updated)
            val msg = "Written: ${result.date} ${result.payee} — ${result.postings.firstOrNull()?.let { "${it.amount} ${it.currency}" } ?: ""}"
            AppLogger.info("Simulate", msg)
            emitMessage("Simulated: $msg")
            msg
        }
    }

    private fun getRulesDir(): java.io.File {
        val dir = java.io.File(getApplication<Application>().filesDir, "rules")
        dir.mkdirs()
        return dir
    }

    fun saveCategorizationRules(rules: List<CategorizationRule>) {
        viewModelScope.launch {
            val rulesDir = getRulesDir()
            RuleIO.saveCategorizationRules(rulesDir, rules)
            _categorizationRules.value = RuleIO.loadCategorizationRules(rulesDir)
        }
    }

    fun getCachedCategorizationRules(): List<CategorizationRule> = _categorizationRules.value

    fun setNotificationSources(sources: List<String>) {
        viewModelScope.launch { settings.setNotificationSources(sources) }
    }
}
