package org.notifledger.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.notifledger.app.NotifLedgerApp
import org.notifledger.app.filters.FilterIO
import org.notifledger.app.log.AppLogger
import org.notifledger.app.model.FilterMode
import org.notifledger.app.model.SortOrder
import org.notifledger.app.model.Transaction
import org.notifledger.app.model.TransactionFilter
import org.notifledger.app.parser.ParserEngine
import org.notifledger.app.settings.SettingsManager
import java.io.File

/** State for the main transaction list: filters, sorting, paging, edits, simulate. */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<NotifLedgerApp>()

    private val settings = app.settings

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    fun consumeMessage() {
        _uiMessage.value = null
    }

    private fun emitMessage(msg: String) {
        _uiMessage.value = msg
    }

    val existingPayees: StateFlow<List<String>> = app.journalSession.entries.map { entries ->
        entries.payeeSuggestions()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val existingAccounts: StateFlow<List<String>> = app.journalSession.entries.map { entries ->
        entries.accountSuggestions()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sortOrder = MutableStateFlow(SortOrder.NewestFirst)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _pageLimit = MutableStateFlow(20)
    val pageLimit: StateFlow<Int> = _pageLimit.asStateFlow()

    private val _filters = MutableStateFlow<List<TransactionFilter>>(emptyList())
    val filters: StateFlow<List<TransactionFilter>> = _filters.asStateFlow()

    private val _filterLimit = MutableStateFlow(5)
    val filterLimit: StateFlow<Int> = _filterLimit.asStateFlow()

    private val _page = MutableStateFlow(0)

    val hasEntries: StateFlow<Boolean> = app.journalSession.entries.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isLoading: StateFlow<Boolean> = app.journalSession.isLoading

    /** Filtered + sorted + paginated entries ready for the UI. */
    val entryPage: StateFlow<EntryPage> = combine(
        app.journalSession.entries,
        _sortOrder,
        _pageLimit,
        _filters,
        _page,
    ) { all, sort, limit, filters, page ->
        EntryList.visible(all, filters, sort, limit, page)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryPage(emptyList(), 0, 1))

    val defaultAccount = app.settings.defaultAccount.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsManager.DEFAULT_ACCOUNT
    )

    val journalPath = app.settings.journalPath.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), ""
    )

    val defaultCurrency = app.settings.defaultCurrency.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsManager.DEFAULT_CURRENCY
    )

    val notificationSources = app.settings.notificationSources.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    init {
        viewModelScope.launch {
            _sortOrder.value = SortOrder.fromValue(settings.sortOrder.first())
            settings.sortOrder.collect { _sortOrder.value = SortOrder.fromValue(it) }
        }
        viewModelScope.launch {
            _pageLimit.value = settings.pageLimit.first()
            settings.pageLimit.collect { _pageLimit.value = it }
        }
        viewModelScope.launch {
            settings.journalPath.distinctUntilChanged().collect { reload() }
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

    private suspend fun reload() {
        if (!app.journalSession.refresh()) emitMessage("Couldn't read journal — see Logs")
    }

    fun reloadEntries() {
        viewModelScope.launch { reload() }
    }

    fun setJournalPath(path: String) {
        viewModelScope.launch { settings.setJournalPath(path) }
    }

    fun setSortOrder(order: SortOrder) {
        _page.value = 0
        viewModelScope.launch { settings.setSortOrder(SortOrder.toValue(order)) }
    }

    fun setPageLimit(limit: Int) {
        _page.value = 0
        viewModelScope.launch { settings.setPageLimit(limit) }
    }

    fun nextPage() {
        val page = entryPage.value
        if (page.page < page.pageCount - 1) _page.value = page.page + 1
    }

    fun previousPage() {
        val page = entryPage.value
        if (page.page > 0) _page.value = page.page - 1
    }

    fun editTransaction(offset: Int, tx: Transaction) {
        viewModelScope.launch {
            val path = settings.journalPath.first()
            if (path.isBlank()) return@launch
            val result = app.journalRepository.replace(path, offset, tx)
            if (result != null) {
                emitMessage("Saved")
                app.journalSession.apply(result.entries)
            } else {
                emitMessage("Save failed — see Logs")
            }
        }
    }

    fun deleteTransaction(offset: Int) {
        viewModelScope.launch {
            val path = settings.journalPath.first()
            if (path.isBlank()) return@launch
            val result = app.journalRepository.delete(path, offset)
            if (result != null) {
                emitMessage("Deleted")
                app.journalSession.apply(result.entries)
            } else {
                emitMessage("Delete failed — see Logs")
            }
        }
    }

    fun changeTransactions(offsets: Set<Int>, date: String?, payee: String?, currency: String?, account: String?) {
        viewModelScope.launch {
            val path = settings.journalPath.first()
            if (path.isBlank()) return@launch
            val byOffset = app.journalSession.entries.value.associateBy { it.lineOffset }
            val updates = offsets.mapNotNull { offset ->
                val entry = byOffset[offset] ?: return@mapNotNull null
                val postings = entry.postings.mapIndexed { index, posting ->
                    val withCurrency = if (currency != null) posting.copy(currency = currency) else posting
                    if (account != null && index == 0) withCurrency.copy(account = account) else withCurrency
                }
                offset to Transaction(
                    date = date ?: entry.date,
                    payee = payee ?: entry.payee,
                    postings = postings,
                    note = entry.note,
                )
            }.toMap()
            if (updates.isEmpty()) return@launch
            val result = app.journalRepository.replaceMany(path, updates)
            if (result != null) {
                emitMessage("Updated ${updates.size}")
                app.journalSession.apply(result.entries)
            } else {
                emitMessage("Update failed — see Logs")
            }
        }
    }

    suspend fun simulateNotification(title: String, text: String): String {
        val defaultAccount = settings.defaultAccount.first()
        val defaultCurrency = settings.defaultCurrency.first()

        AppLogger.info("Simulate", "Simulating notification")

        val result = ParserEngine.parse(title, text, "expenses:unknown", defaultAccount, defaultCurrency)
        if (result == null) {
            AppLogger.warn("Simulate", "No number found")
            emitMessage("No number found in notification text.")
            return "No number found in notification text."
        }

        val path = settings.journalPath.first()
        if (path.isBlank()) {
            AppLogger.warn("Simulate", "No journal file set")
            emitMessage("No journal file set.")
            return "No journal file set."
        }
        val writeResult = app.journalRepository.append(path, result)
        if (writeResult == null) {
            emitMessage("Save failed — see Logs")
            return "Save failed — see Logs"
        }
        app.journalSession.apply(writeResult.entries)
        val msg = "Written: ${result.date} ${result.payee} — ${result.postings.firstOrNull()?.let { "${it.amount} ${it.currency}" } ?: ""}"
        AppLogger.info("Simulate", "Notification written ${result.date}")
        emitMessage("Simulated: $msg")
        return msg
    }

    private fun getFiltersDir(): File {
        val dir = File(getApplication<Application>().filesDir, "filters")
        dir.mkdirs()
        return dir
    }

    private val filterSaveMutex = Mutex()

    @Volatile
    private var filterSaveRevision = 0

    private fun persistFilters(filters: List<TransactionFilter>) {
        _filters.value = filters
        _page.value = 0
        val revision = ++filterSaveRevision
        viewModelScope.launch(Dispatchers.IO) {
            filterSaveMutex.withLock {
                if (revision == filterSaveRevision) FilterIO.saveFilters(getFiltersDir(), filters)
            }
        }
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

    fun cycleFilterMode(index: Int) {
        val current = _filters.value
        if (index !in current.indices) return
        persistFilters(current.mapIndexed { i, filter ->
            if (i == index) filter.copy(mode = nextMode(filter.mode)) else filter
        })
    }

    private fun nextMode(mode: FilterMode): FilterMode = when (mode) {
        FilterMode.INCLUDE -> FilterMode.EXCLUDE
        FilterMode.EXCLUDE -> FilterMode.OFF
        FilterMode.OFF -> FilterMode.INCLUDE
    }
}
