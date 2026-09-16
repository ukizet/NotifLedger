package org.notifledger.app.notification

import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.notifledger.app.NotifLedgerApp
import org.notifledger.app.journal.JournalWriter
import org.notifledger.app.log.AppLogger
import org.notifledger.app.model.Source
import org.notifledger.app.model.Transaction

/**
 * Listens for notifications from user-selected banking/payment apps.
 *
 * Uses title as payee, finds the first number as amount, then writes to journal.
 */
class NotifListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = application as? NotifLedgerApp ?: return

        val allowedPackages = app.getAllowedNotificationPackages()
        val journalUri = app.getJournalUri()
        if (journalUri == null) {
            AppLogger.warn("Notif", "No journal URI set — cannot write")
            return
        }
        val defaultAccount = app.getDefaultAccount()
        val rules = app.getCategorizationRules()

        val extras = sbn.notification.extras
        val title = extras.getString(EXTRA_TITLE) ?: return
        val text = extras.getString(EXTRA_TEXT) ?: ""

        val tx = NotificationHandler.processNotification(
            packageName = sbn.packageName,
            title = title,
            text = text,
            allowedPackages = allowedPackages,
            defaultAccount = defaultAccount,
            rules = rules,
        ) ?: return

        // Title (payee) and amount are retained in the journal; the raw body text is not,
        // per design §4.1, so we never log the body.
        AppLogger.info("Notif", "Notification from ${sbn.packageName}")

        writeToJournal(journalUri, tx.copy(source = Source.Notification))
        Log.d(TAG, "Wrote transaction to journal: ${tx.payee}")
        AppLogger.info("Notif", "Wrote transaction: ${tx.payee} — ${tx.postings.firstOrNull()?.let { "${it.amount} ${it.currency}" } ?: ""}")
    }

    // Uses the shared app-level mutex so UI writes and notification writes don't interleave.
    private fun writeToJournal(uri: Uri, tx: Transaction) {
        val app = application as? NotifLedgerApp ?: return
        runBlocking(Dispatchers.IO) {
            app.journalWriteMutex.withLock {
                try {
                    val existing = contentResolver.openInputStream(uri)?.use {
                        it.bufferedReader().use { r -> r.readText() }
                    } ?: ""
                    val updated = JournalWriter.appendToContent(existing, tx)
                    contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                        it.write(updated.content)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to write to journal", e)
                    AppLogger.error("Notif", "Failed to write to journal: ${e.message}")
                }
            }
        }
    }

    override fun onListenerConnected() {
        val app = application as? NotifLedgerApp
        if (app != null) {
            runBlocking { app.settings.setLastListenerConnectedAt(System.currentTimeMillis()) }
        }
        Log.d(TAG, "Notification listener connected")
        AppLogger.info("Notif", "Notification listener connected")
        NotificationHelper.showListeningNotification(this)
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "Notification listener disconnected")
        AppLogger.warn("Notif", "Notification listener disconnected")
        NotificationHelper.hideListeningNotification(this)
    }

    companion object {
        private const val TAG = "NotifLedger"
        private const val EXTRA_TITLE = "android.title"
        private const val EXTRA_TEXT = "android.text"
    }
}
