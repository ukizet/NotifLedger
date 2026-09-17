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
import org.notifledger.app.NotifLedgerApp
import org.notifledger.app.filters.FilterIO
import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.journal.JournalWriter
import org.notifledger.app.log.AppLogger
import org.notifledger.app.log.LogEntry
import org.notifledger.app.model.CategorizationRule
import org.notifledger.app.model.SortOrder
import org.notifledger.app.model.Transaction
import org.notifledger.app.model.TransactionFilter
import org.notifledger.app.parser.ParserEngine
import org.notifledger.app.parser.RuleIO
import org.notifledger.app.settings.SettingsManager

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val settings = SettingsManager(application)

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

    private val _filters = MutableStateFlow<List<TransactionFilter>>(emptyList())
    val filters: StateFlow<List<TransactionFilter>> = _filters.asStateFlow()

    private val _filterLimit = MutableStateFlow(5)
    val filterLimit: StateFlow<Int> = _filterLimit.asStateFlow()

    val hasEntries: StateFlow<Boolean> = _allEntries.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Filtered + sorted + limited entries ready for the UI. */
    val entries: StateFlow<List<JournalEntry>> = combine(
        _allEntries,
        _sortOrder,
        _pageLimit,
        _filters,
    ) { all, sort, limit, filters ->
        EntryList.visible(all, filters, sort, limit)
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

    val lastListenerConnectedAt = settings.lastListenerConnectedAt.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null
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
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _filters.value = FilterIO.loadFilters(getFiltersDir())
            }
            AppLogger.info("Filters", "Loaded ${_filters.value.size} filters")
        }
        viewModelScope.launch {
            _filterLimit.value = settings.filterRowLimit.first()
            settings.filterRowLimit.collect { limit ->
                _filterLimit.value = limit
                val current = _filters.value
                if (current.size > limit) persistFilters(current.take(limit))
            }
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
            val app = getApplication<NotifLedgerApp>()
            app.journalWriteMutex.withLock {
                val path = settings.journalPath.first()
                if (path.isBlank()) return@withLock
                val existing = readJournalContent(path) ?: return@withLock
                val result = JournalWriter.appendToContent(existing, tx)
                emitSaveResult(writeJournalContent(path, result.content))
                _allEntries.value = result.entries
            }
        }
    }

    fun editTransaction(offset: Int, tx: Transaction) {
        viewModelScope.launch {
            val app = getApplication<NotifLedgerApp>()
            app.journalWriteMutex.withLock {
                val path = settings.journalPath.first()
                if (path.isBlank()) return@withLock
                val content = readJournalContent(path) ?: return@withLock
                val result = JournalWriter.replaceInContent(content, offset, tx)
                emitSaveResult(writeJournalContent(path, result.content))
                _allEntries.value = result.entries
            }
        }
    }

    fun deleteTransaction(offset: Int) {
        viewModelScope.launch {
            val app = getApplication<NotifLedgerApp>()
            app.journalWriteMutex.withLock {
                val path = settings.journalPath.first()
                if (path.isBlank()) return@withLock
                val content = readJournalContent(path) ?: return@withLock
                val result = JournalWriter.deleteFromContent(content, offset)
                if (writeJournalContent(path, result.content)) emitMessage("Deleted") else emitMessage("Delete failed — see Logs")
                _allEntries.value = result.entries
            }
        }
    }

    private fun emitSaveResult(wrote: Boolean) {
        emitMessage(if (wrote) "Saved" else "Save failed — see Logs")
    }

    fun reloadEntries() {
        viewModelScope.launch {
            val app = getApplication<NotifLedgerApp>()
            app.journalWriteMutex.withLock {
                val path = settings.journalPath.first()
                if (path.isNotBlank()) {
                    val content = readJournalContent(path)
                    if (content != null) {
                        _allEntries.value = JournalWriter.readAll(content)
                    }
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

        val app = getApplication<NotifLedgerApp>()
        return app.journalWriteMutex.withLock {
            val path = settings.journalPath.first()
            if (path.isBlank()) {
                AppLogger.warn("Simulate", "No journal file set")
                emitMessage("No journal file set.")
                return@withLock "No journal file set."
            }
            val existing = readJournalContent(path) ?: return@withLock "Read failed — see Logs"
            val writeResult = JournalWriter.appendToContent(existing, result)
            if (!writeJournalContent(path, writeResult.content)) {
                emitMessage("Save failed — see Logs")
                return@withLock "Save failed — see Logs"
            }
            _allEntries.value = writeResult.entries
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

    private fun getFiltersDir(): java.io.File {
        val dir = java.io.File(getApplication<Application>().filesDir, "filters")
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

    private val filterSaveMutex = Mutex()

    @Volatile
    private var filterSaveRevision = 0

    private fun persistFilters(filters: List<TransactionFilter>) {
        _filters.value = filters
        val revision = ++filterSaveRevision
        viewModelScope.launch(Dispatchers.IO) {
            filterSaveMutex.withLock {
                if (revision == filterSaveRevision) FilterIO.saveFilters(getFiltersDir(), filters)
            }
        }
    }

    fun getCachedCategorizationRules(): List<CategorizationRule> = _categorizationRules.value

    fun setNotificationSources(sources: List<String>) {
        viewModelScope.launch { settings.setNotificationSources(sources) }
    }

    fun addFilter(account: String) {
        val trimmed = account.trim()
        if (trimmed.isBlank()) return
        val current = _filters.value
        if (current.size >= _filterLimit.value) return
        if (current.any { it.account == trimmed }) return
        persistFilters(current + TransactionFilter(label = trimmed, account = trimmed))
    }

    fun renameFilter(index: Int, label: String) {
        val trimmed = label.trim()
        if (trimmed.isBlank()) return
        val current = _filters.value
        if (index !in current.indices) return
        persistFilters(current.mapIndexed { i, filter ->
            if (i == index) filter.copy(label = trimmed) else filter
        })
    }

    fun changeFilterCategory(index: Int, account: String) {
        val trimmed = account.trim()
        if (trimmed.isBlank()) return
        val current = _filters.value
        if (index !in current.indices) return
        val usedElsewhere = current.indices.any { i -> i != index && current[i].account == trimmed }
        if (usedElsewhere) return
        persistFilters(current.mapIndexed { i, filter ->
            if (i == index) filter.copy(account = trimmed) else filter
        })
    }

    fun deleteFilter(index: Int) {
        val current = _filters.value
        if (index !in current.indices) return
        persistFilters(current.filterIndexed { i, _ -> i != index })
    }

    fun toggleFilterActive(index: Int) {
        val current = _filters.value
        if (index !in current.indices) return
        persistFilters(current.mapIndexed { i, filter ->
            if (i == index) filter.copy(isActive = !filter.isActive) else filter
        })
    }

    fun setFilterLimit(limit: Int) {
        viewModelScope.launch { settings.setFilterRowLimit(limit) }
    }
}
