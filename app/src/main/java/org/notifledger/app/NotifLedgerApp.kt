package org.notifledger.app

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.notifledger.app.log.AppLogger
import org.notifledger.app.model.CategorizationRule
import org.notifledger.app.notification.NotificationHelper
import org.notifledger.app.parser.RuleIO
import org.notifledger.app.settings.SettingsManager
import java.io.File

class NotifLedgerApp : Application() {

    /** Shared mutex for journal read-modify-write — used by both NotifListener and MainViewModel. */
    val journalWriteMutex = Mutex()

    lateinit var settings: SettingsManager
        private set

    private var cachedRules: List<CategorizationRule> = emptyList()

    override fun onCreate() {
        super.onCreate()
        AppLogger.info("App", "Application starting")
        settings = SettingsManager(this)
        cachedRules = loadRules()
        NotificationHelper.showListeningNotification(this)
        AppLogger.info("App", "Application started")
    }

    private fun loadRules(): List<CategorizationRule> {
        val dir = File(filesDir, "rules")
        dir.mkdirs()
        return RuleIO.loadCategorizationRules(dir)
    }

    /** Used by NotifListener to get categorization rules (cache, reloaded on process start). */
    fun getCategorizationRules(): List<CategorizationRule> = cachedRules

    /** Used by NotifListener to get the current journal path as a Uri. */
    fun getJournalUri(): Uri? {
        val path = runBlocking {
            try { settings.journalPath.first().takeIf { it.isNotBlank() } } catch (_: Exception) { null }
        } ?: return null
        return try { Uri.parse(path) } catch (_: Exception) { null }
    }

    /** Used by NotifListener to get the default payment account. */
    fun getDefaultAccount(): String = runBlocking {
        try {
            settings.defaultAccount.first()
        } catch (_: Exception) { "assets:bank:checking" }
    }

    /** Used by NotifListener to get allowed notification packages. */
    fun getAllowedNotificationPackages(): Set<String> = runBlocking {
        try {
            settings.notificationSources.first().toSet()
        } catch (_: Exception) { emptySet() }
    }
}
