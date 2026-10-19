package org.notifledger.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.notifledger.app.NotifLedgerApp

/** State for the notification sources screen. */
class NotificationSourcesViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<NotifLedgerApp>()

    val notificationSources = app.settings.notificationSources.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    fun setNotificationSources(sources: List<String>) {
        app.writeScope.launch { app.settings.setNotificationSources(sources) }
    }
}
