package org.notifledger.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Exercises the real DataStore-backed [SettingsManager] on the JVM.
 *
 * A wrong preference key, a wrong default, or two settings sharing one key
 * fails here. The comma-separated source encoding itself is covered by
 * SettingsCodecsTest.
 */
class SettingsManagerTest {

    private val dir = Files.createTempDirectory("notifledger-settings-test").toFile()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: SettingsManager

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(dir, "settings.preferences_pb")
        }
        settings = SettingsManager(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun `unset settings fall back to their defaults`() = runBlocking {
        assertEquals("", settings.journalPath.first())
        assertEquals("assets:bank:checking", settings.defaultAccount.first())
        assertEquals("NOK", settings.defaultCurrency.first())
        assertEquals("newest_first", settings.sortOrder.first())
        assertEquals("journal", settings.displayMode.first())
        assertEquals(20, settings.pageLimit.first())
        assertEquals(5, settings.filterRowLimit.first())
        assertEquals(emptyList<String>(), settings.notificationSources.first())
        assertEquals("", settings.processedNotificationIds.first())
    }

    @Test
    fun `every setting round-trips through its own key`() = runBlocking {
        settings.setJournalPath("content://com.android.providers.downloads.documents/document/42")
        settings.setDefaultAccount("assets:bank:savings")
        settings.setDefaultCurrency("EUR")
        settings.setSortOrder("payee_ascending")
        settings.setPageLimit(50)
        settings.setFilterRowLimit(7)
        settings.setDisplayMode("table")
        settings.setNotificationSources(listOf("no.dnb.mobil", "com.vipps"))
        settings.setProcessedNotificationIds("1|a\n2|b")

        assertEquals(
            "content://com.android.providers.downloads.documents/document/42",
            settings.journalPath.first(),
        )
        assertEquals("assets:bank:savings", settings.defaultAccount.first())
        assertEquals("EUR", settings.defaultCurrency.first())
        assertEquals("payee_ascending", settings.sortOrder.first())
        assertEquals(50, settings.pageLimit.first())
        assertEquals(7, settings.filterRowLimit.first())
        assertEquals("table", settings.displayMode.first())
        assertEquals(listOf("no.dnb.mobil", "com.vipps"), settings.notificationSources.first())
        assertEquals("1|a\n2|b", settings.processedNotificationIds.first())
    }

    @Test
    fun `filter row limit is normalized before it is stored`() = runBlocking {
        settings.setFilterRowLimit(0)
        assertEquals(1, settings.filterRowLimit.first())

        settings.setFilterRowLimit(-3)
        assertEquals(1, settings.filterRowLimit.first())
    }

    @Test
    fun `notification sources survive an encode and decode round-trip`() = runBlocking {
        settings.setNotificationSources(listOf("no.dnb.mobil"))
        assertEquals(listOf("no.dnb.mobil"), settings.notificationSources.first())

        settings.setNotificationSources(emptyList())
        assertEquals(emptyList<String>(), settings.notificationSources.first())
    }

    /**
     * The stored key names are a persistence contract: renaming one silently
     * orphans every existing user's stored setting, and the round-trip test
     * above cannot see that because it reads and writes through the same
     * constants. These literals must not change.
     */
    @Test
    fun `stored key names are unchanged`() = runBlocking {
        settings.setJournalPath("content://journal")
        settings.setDefaultAccount("assets:bank:savings")
        settings.setDefaultCurrency("EUR")
        settings.setSortOrder("highest_amount")
        settings.setPageLimit(50)
        settings.setFilterRowLimit(7)
        settings.setDisplayMode("table")
        settings.setNotificationSources(listOf("no.dnb.mobil"))
        settings.setProcessedNotificationIds("1|a")

        val prefs = dataStore.data.first()
        assertEquals("content://journal", prefs[stringPreferencesKey("journal_path")])
        assertEquals("assets:bank:savings", prefs[stringPreferencesKey("default_account")])
        assertEquals("EUR", prefs[stringPreferencesKey("default_currency")])
        assertEquals("highest_amount", prefs[stringPreferencesKey("sort_order")])
        assertEquals(50, prefs[intPreferencesKey("page_limit")])
        assertEquals(7, prefs[intPreferencesKey("filter_row_limit")])
        assertEquals("table", prefs[stringPreferencesKey("display_mode")])
        assertEquals("no.dnb.mobil", prefs[stringPreferencesKey("notification_sources")])
        assertEquals("1|a", prefs[stringPreferencesKey("processed_notification_ids")])
    }
}
