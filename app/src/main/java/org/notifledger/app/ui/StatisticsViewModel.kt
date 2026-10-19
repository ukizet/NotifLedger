package org.notifledger.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.notifledger.app.NotifLedgerApp
import org.notifledger.app.journal.JournalEntry

/** State for the statistics screen. */
class StatisticsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<NotifLedgerApp>()

    val entries: StateFlow<List<JournalEntry>> = app.journalSession.entries

    val accountOptions: StateFlow<List<String>> = app.journalSession.entries.map { entries ->
        entries.accountSuggestions()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
