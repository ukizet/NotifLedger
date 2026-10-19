package org.notifledger.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.notifledger.app.NotifLedgerApp
import org.notifledger.app.model.Transaction
import org.notifledger.app.settings.SettingsManager

/** State for the manual quick-add screen. */
class QuickAddViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<NotifLedgerApp>()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    fun consumeMessage() {
        _uiMessage.value = null
    }

    val defaultAccount = app.settings.defaultAccount.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsManager.DEFAULT_ACCOUNT
    )

    val defaultCurrency = app.settings.defaultCurrency.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsManager.DEFAULT_CURRENCY
    )

    val existingPayees: StateFlow<List<String>> = app.journalSession.entries.map { entries ->
        entries.payeeSuggestions()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val existingAccounts: StateFlow<List<String>> = app.journalSession.entries.map { entries ->
        entries.accountSuggestions()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addTransaction(tx: Transaction) {
        app.writeScope.launch {
            val path = app.settings.journalPath.first()
            if (path.isBlank()) return@launch
            val result = app.journalRepository.append(path, tx)
            if (result != null) {
                _uiMessage.value = "Saved"
                app.journalSession.apply(result.entries)
            } else {
                _uiMessage.value = "Save failed — see Logs"
            }
        }
    }
}
