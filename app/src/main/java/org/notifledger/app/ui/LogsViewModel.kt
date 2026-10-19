package org.notifledger.app.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.notifledger.app.log.AppLogger
import org.notifledger.app.log.LogEntry

/** In-app log feed for the logs screen. */
class LogsViewModel : ViewModel() {

    /** Captured so we can unsubscribe in [onCleared] (AppLogger outlives the ViewModel). */
    private var logSubscription: ((List<LogEntry>) -> Unit)? = null

    val logEntries: StateFlow<List<LogEntry>>

    init {
        val logState = MutableStateFlow(AppLogger.entries())
        logEntries = logState.asStateFlow()
        val subscription: (List<LogEntry>) -> Unit = { entries -> logState.value = entries }
        logSubscription = subscription
        AppLogger.subscribe(subscription)
    }

    override fun onCleared() {
        super.onCleared()
        logSubscription?.let { AppLogger.unsubscribe(it) }
        logSubscription = null
    }
}
