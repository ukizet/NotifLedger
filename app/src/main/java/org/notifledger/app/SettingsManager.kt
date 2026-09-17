package org.notifledger.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * App settings: journal path, default payment account, notification source apps.
 *
 * Stored in Android's DataStore (key-value, coroutine-based).
 */
class SettingsManager(private val context: Context) {

    /** The user-selected journal file path. */
    val journalPath: Flow<String>
        get() = context.dataStore.data.map { prefs ->
            prefs[JOURNAL_PATH] ?: ""
        }

    /** The default payment account for the money-side leg of transactions. */
    val defaultAccount: Flow<String>
        get() = context.dataStore.data.map { prefs ->
            prefs[DEFAULT_ACCOUNT] ?: DEFAULT_ACCOUNT_VALUE
        }

    /** The default currency for new transactions. */
    val defaultCurrency: Flow<String>
        get() = context.dataStore.data.map { prefs ->
            prefs[DEFAULT_CURRENCY] ?: DEFAULT_CURRENCY_VALUE
        }

    /** How to sort transactions on the main screen. */
    val sortOrder: Flow<String>
        get() = context.dataStore.data.map { prefs ->
            prefs[SORT_ORDER] ?: "newest_first"
        }

    /** Max transactions to show on the main screen. */
    val pageLimit: Flow<Int>
        get() = context.dataStore.data.map { prefs ->
            prefs[PAGE_LIMIT] ?: 20
        }

    /** Max filter chips shown on the main screen. */
    val filterRowLimit: Flow<Int>
        get() = context.dataStore.data.map { prefs ->
            prefs[FILTER_ROW_LIMIT] ?: DEFAULT_FILTER_ROW_LIMIT
        }

    /** Comma-separated list of allowed notification source package names. */
    val notificationSources: Flow<List<String>>
        get() = context.dataStore.data.map { prefs ->
            SettingsCodecs.decodeSources(prefs[NOTIF_SOURCES] ?: "")
        }

    /**
     * Internal bookkeeping for notification dedup: the keys of recently
     * processed notifications, so the same event isn't journaled twice.
     */
    val processedNotificationIds: Flow<String>
        get() = context.dataStore.data.map { prefs ->
            prefs[PROCESSED_NOTIFICATION_IDS] ?: ""
        }

    /** Timestamp of the last successful [org.notifledger.app.notification.NotifListener.onListenerConnected] call, or null. */
    val lastListenerConnectedAt: Flow<Long?>
        get() = context.dataStore.data.map { prefs ->
            prefs[LAST_LISTENER_CONNECTED_AT]
        }

    suspend fun setJournalPath(path: String) {
        context.dataStore.edit { prefs ->
            prefs[JOURNAL_PATH] = path
        }
    }

    suspend fun setDefaultAccount(account: String) {
        context.dataStore.edit { prefs ->
            prefs[DEFAULT_ACCOUNT] = account
        }
    }

    suspend fun setDefaultCurrency(currency: String) {
        context.dataStore.edit { prefs ->
            prefs[DEFAULT_CURRENCY] = currency
        }
    }

    suspend fun setSortOrder(order: String) {
        context.dataStore.edit { prefs ->
            prefs[SORT_ORDER] = order
        }
    }

    suspend fun setPageLimit(limit: Int) {
        context.dataStore.edit { prefs ->
            prefs[PAGE_LIMIT] = limit
        }
    }

    suspend fun setFilterRowLimit(limit: Int) {
        context.dataStore.edit { prefs ->
            prefs[FILTER_ROW_LIMIT] = SettingsCodecs.normalizeFilterLimit(limit)
        }
    }

    suspend fun setNotificationSources(sources: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[NOTIF_SOURCES] = SettingsCodecs.encodeSources(sources)
        }
    }

    suspend fun setProcessedNotificationIds(raw: String) {
        context.dataStore.edit { prefs ->
            prefs[PROCESSED_NOTIFICATION_IDS] = raw
        }
    }

    suspend fun setLastListenerConnectedAt(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[LAST_LISTENER_CONNECTED_AT] = timestamp
        }
    }

    companion object {
        private val JOURNAL_PATH = stringPreferencesKey("journal_path")
        private val DEFAULT_ACCOUNT = stringPreferencesKey("default_account")
        private val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        private val NOTIF_SOURCES = stringPreferencesKey("notification_sources")
        private val SORT_ORDER = stringPreferencesKey("sort_order")
        private val PAGE_LIMIT = intPreferencesKey("page_limit")
        private val FILTER_ROW_LIMIT = intPreferencesKey("filter_row_limit")
        private val PROCESSED_NOTIFICATION_IDS = stringPreferencesKey("processed_notification_ids")
        private val LAST_LISTENER_CONNECTED_AT = longPreferencesKey("last_listener_connected_at")

        const val DEFAULT_FILTER_ROW_LIMIT = 5

        private const val DEFAULT_ACCOUNT_VALUE = "assets:bank:checking"
        private const val DEFAULT_CURRENCY_VALUE = "NOK"
    }
}
