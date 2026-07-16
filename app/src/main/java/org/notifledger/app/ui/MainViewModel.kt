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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.journal.JournalWriter
import org.notifledger.app.model.CategorizationRule
import org.notifledger.app.model.SortOrder
import org.notifledger.app.model.Transaction
import org.notifledger.app.parser.ParserEngine
import org.notifledger.app.parser.RuleIO
import org.notifledger.app.settings.SettingsManager

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val settings = SettingsManager(application)

    private val _allEntries = MutableStateFlow<List<JournalEntry>>(emptyList())

    val existingPayees: StateFlow<List<String>> = _allEntries.map { entries ->
        entries.map { it.payee }.filter { it.isNotBlank() }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val existingAccounts: StateFlow<List<String>> = _allEntries.map { entries ->
        entries.flatMap { it.postings.map { p -> p.account } }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sortOrder = MutableStateFlow("newest_first")
    val sortOrder: StateFlow<String> = _sortOrder.asStateFlow()

    private val _pageLimit = MutableStateFlow(20)
    val pageLimit: StateFlow<Int> = _pageLimit.asStateFlow()

    /** Sorted + limited entries ready for the UI. */
    val entries: StateFlow<List<JournalEntry>> = combine(
        _allEntries,
        _sortOrder,
        _pageLimit,
    ) { all, sort, limit ->
        val sorted = when (SortOrder.fromValue(sort)) {
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

    init {
        viewModelScope.launch {
            _sortOrder.value = settings.sortOrder.first()
            settings.sortOrder.collect { _sortOrder.value = it }
        }
        viewModelScope.launch {
            _pageLimit.value = settings.pageLimit.first()
            settings.pageLimit.collect { _pageLimit.value = it }
        }
        viewModelScope.launch {
            settings.journalPath.collect { path ->
                if (path.isNotBlank()) {
                    val content = readJournalContent(path)
                    _allEntries.value = JournalWriter.readAll(content)
                } else {
                    _allEntries.value = emptyList()
                }
            }
        }
        viewModelScope.launch {
            val rulesDir = getRulesDir()
            _categorizationRules.value = RuleIO.loadCategorizationRules(rulesDir)
        }
    }

    private suspend fun readJournalContent(uriString: String): String = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val context = getApplication<Application>()
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
        } catch (_: Exception) { "" }
    }

    private suspend fun writeJournalContent(uriString: String, content: String) = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val context = getApplication<Application>()
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                it.write(content)
            }
        } catch (_: Exception) { }
    }

    fun setSortOrder(order: String) {
        viewModelScope.launch { settings.setSortOrder(order) }
    }

    fun setPageLimit(limit: Int) {
        viewModelScope.launch { settings.setPageLimit(limit) }
    }

    fun addTransaction(tx: Transaction) {
        viewModelScope.launch {
            val path = settings.journalPath.first()
            if (path.isBlank()) return@launch
            val existing = readJournalContent(path)
            val updated = JournalWriter.appendToContent(existing, tx)
            writeJournalContent(path, updated)
            _allEntries.value = JournalWriter.readAll(updated)
        }
    }

    fun editTransaction(offset: Int, tx: Transaction) {
        viewModelScope.launch {
            val path = settings.journalPath.first()
            if (path.isBlank()) return@launch
            val content = readJournalContent(path)
            val updated = JournalWriter.replaceInContent(content, offset, tx)
            writeJournalContent(path, updated)
            _allEntries.value = JournalWriter.readAll(updated)
        }
    }

    fun deleteTransaction(offset: Int) {
        viewModelScope.launch {
            val path = settings.journalPath.first()
            if (path.isBlank()) return@launch
            val content = readJournalContent(path)
            val lines = content.lines().toMutableList()
            val entryLineCount = JournalWriter.countEntryLines(lines, offset)
            repeat(entryLineCount) { if (offset < lines.size) lines.removeAt(offset) }
            val updated = lines.joinToString("\n")
            writeJournalContent(path, updated)
            _allEntries.value = JournalWriter.readAll(updated)
        }
    }

    fun reloadEntries() {
        viewModelScope.launch {
            val path = settings.journalPath.first()
            if (path.isNotBlank()) {
                val content = readJournalContent(path)
                _allEntries.value = JournalWriter.readAll(content)
            }
        }
    }

    fun simulateNotification(title: String, text: String): String {
        val defaultAccount = runBlocking { settings.defaultAccount.first() }

        val result = ParserEngine.parse(title, text, "expenses:unknown", defaultAccount)
        if (result == null) {
            return "No number found in notification text."
        }

        viewModelScope.launch {
            val path = settings.journalPath.first()
            if (path.isNotBlank()) {
                val existing = readJournalContent(path)
                val updated = JournalWriter.appendToContent(existing, result)
                writeJournalContent(path, updated)
            }
        }

        return "Written: ${result.date} ${result.payee} — ${result.postings.firstOrNull()?.let { "${it.amount} ${it.currency}" } ?: ""}"
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
