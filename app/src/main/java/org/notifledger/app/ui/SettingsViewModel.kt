package org.notifledger.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.notifledger.app.NotifLedgerApp
import org.notifledger.app.settings.SettingsManager

/** State for the settings screen. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<NotifLedgerApp>()

    val defaultAccount = app.settings.defaultAccount.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsManager.DEFAULT_ACCOUNT
    )

    val defaultCurrency = app.settings.defaultCurrency.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsManager.DEFAULT_CURRENCY
    )

    val journalPath = app.settings.journalPath.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), ""
    )

    val filterLimit = app.settings.filterRowLimit.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsManager.DEFAULT_FILTER_ROW_LIMIT
    )

    fun setJournalPath(path: String) {
        app.writeScope.launch { app.settings.setJournalPath(path) }
    }

    fun setDefaultAccount(account: String) {
        app.writeScope.launch { app.settings.setDefaultAccount(account) }
    }

    fun setDefaultCurrency(currency: String) {
        app.writeScope.launch { app.settings.setDefaultCurrency(currency) }
    }

    fun setFilterLimit(limit: Int) {
        app.writeScope.launch { app.settings.setFilterRowLimit(limit) }
    }
}
