package org.notifledger.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.notifledger.app.NotifLedgerApp

/** State for the read-only raw journal screen. */
class RawJournalViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<NotifLedgerApp>()

    val journalPath = app.settings.journalPath.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), ""
    )

    /** Raw text for the viewer; null when the read failed. */
    suspend fun readRawJournal(path: String): String? = app.journalRepository.read(path)
}
