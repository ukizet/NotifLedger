package org.notifledger.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

    /** Comma-separated list of allowed notification source package names. */
    val notificationSources: Flow<List<String>>
        get() = context.dataStore.data.map { prefs ->
            val raw = prefs[NOTIF_SOURCES] ?: ""
            if (raw.isBlank()) emptyList() else raw.split(",").map { it.trim() }
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

    suspend fun setNotificationSources(sources: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[NOTIF_SOURCES] = sources.joinToString(",")
        }
    }

    companion object {
        private val JOURNAL_PATH = stringPreferencesKey("journal_path")
        private val DEFAULT_ACCOUNT = stringPreferencesKey("default_account")
        private val NOTIF_SOURCES = stringPreferencesKey("notification_sources")
        private val SORT_ORDER = stringPreferencesKey("sort_order")
        private val PAGE_LIMIT = intPreferencesKey("page_limit")

        private const val DEFAULT_ACCOUNT_VALUE = "assets:bank:checking"
    }
}
