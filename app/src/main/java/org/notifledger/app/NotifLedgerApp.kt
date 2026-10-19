package org.notifledger.app

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import org.notifledger.app.journal.JournalRepository
import org.notifledger.app.journal.JournalSession
import org.notifledger.app.log.AppLogger
import org.notifledger.app.model.CategorizationRule
import org.notifledger.app.notification.NotificationHelper
import org.notifledger.app.parser.RuleIO
import org.notifledger.app.settings.SettingsManager
import java.io.File

class NotifLedgerApp : Application() {

    lateinit var settings: SettingsManager
        private set

    val journalRepository = JournalRepository(this)

    /** In-memory parsed-journal state shared by the per-screen ViewModels. */
    val journalSession = JournalSession(this)

    /**
     * Screen-scoped ViewModels die on back-stack pop, so their write
     * coroutines must run here or a fast back tap silently drops the write.
     */
    val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var cachedRules: List<CategorizationRule> = emptyList()

    override fun onCreate() {
        super.onCreate()
        val logDir = File(filesDir, "logs")
        logDir.mkdirs()
        AppLogger.initLogFile(File(logDir, "notifledger.log"))
        AppLogger.info("App", "Application starting")
        AppLogger.info("App", "File logging started")
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
    suspend fun getJournalUri(): Uri? {
        val path = try {
            settings.journalPath.first().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            AppLogger.warn("App", "Failed to read journal path: ${e.message}")
            null
        } ?: return null
        return try {
            Uri.parse(path)
        } catch (e: Exception) {
            AppLogger.warn("App", "Failed to parse journal path '$path': ${e.message}")
            null
        }
    }

    /** Used by NotifListener to get the default payment account. */
    suspend fun getDefaultAccount(): String = try {
        settings.defaultAccount.first()
    } catch (e: Exception) {
        AppLogger.warn("App", "Failed to read default account: ${e.message}")
        SettingsManager.DEFAULT_ACCOUNT
    }

    /** Used by NotifListener to get the default currency. */
    suspend fun getDefaultCurrency(): String = try {
        settings.defaultCurrency.first()
    } catch (e: Exception) {
        AppLogger.warn("App", "Failed to read default currency: ${e.message}")
        SettingsManager.DEFAULT_CURRENCY
    }

    /** Used by NotifListener to get allowed notification packages. */
    suspend fun getAllowedNotificationPackages(): Set<String> = try {
        settings.notificationSources.first().toSet()
    } catch (e: Exception) {
        AppLogger.warn("App", "Failed to read notification sources: ${e.message}")
        emptySet()
    }

    /** Used by NotifListener to get the raw processed-notification dedup state. */
    suspend fun getProcessedNotificationIds(): String = try {
        settings.processedNotificationIds.first()
    } catch (e: Exception) {
        AppLogger.warn("App", "Failed to read processed notification ids: ${e.message}")
        ""
    }
}
