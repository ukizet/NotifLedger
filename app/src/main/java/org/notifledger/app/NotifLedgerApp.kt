package org.notifledger.app

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.notifledger.app.settings.SettingsManager
import java.io.File

class NotifLedgerApp : Application() {

    lateinit var settings: SettingsManager
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsManager(this)
    }

    /** Used by NotifListener to get the current journal path as a Uri. */
    fun getJournalUri(): Uri? {
        val path = runBlocking {
            try { settings.journalPath.first().takeIf { it.isNotBlank() } } catch (_: Exception) { null }
        } ?: return null
        return try { Uri.parse(path) } catch (_: Exception) { null }
    }

    /** Used by NotifListener to get the rules directory. */
    fun getRulesDir(): File? {
        val dir = File(filesDir, "rules")
        dir.mkdirs()
        return dir
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
