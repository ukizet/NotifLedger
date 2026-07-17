package org.notifledger.app.notification

import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.notifledger.app.NotifLedgerApp
import org.notifledger.app.journal.JournalWriter
import org.notifledger.app.log.AppLogger
import org.notifledger.app.model.Posting
import org.notifledger.app.model.Source
import org.notifledger.app.model.Transaction
import org.notifledger.app.parser.ParserEngine

/**
 * Listens for notifications from user-selected banking/payment apps.
 *
 * Uses title as payee, finds the first number as amount, then writes to journal.
 */
class NotifListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = application as? NotifLedgerApp ?: return

        val allowedPackages = app.getAllowedNotificationPackages()
        if (allowedPackages.isEmpty()) return
        if (sbn.packageName !in allowedPackages) return

        val extras = sbn.notification.extras
        val title = extras.getString(EXTRA_TITLE) ?: return
        val text = extras.getString(EXTRA_TEXT) ?: ""

        // Title (payee) and amount are retained in the journal; the raw body text is not,
        // per design §4.1, so we never log the body.
        AppLogger.info("Notif", "Notification from ${sbn.packageName}")

        val journalUri = app.getJournalUri()
        val defaultAccount = app.getDefaultAccount()
        if (journalUri == null) {
            AppLogger.warn("Notif", "No journal URI set — cannot write")
            return
        }

        val result = ParserEngine.parse(title, text, "expenses:unknown", defaultAccount)
        if (result != null) {
            writeToJournal(journalUri, result.copy(source = Source.Notification))
            Log.d(TAG, "Wrote transaction to journal: ${result.payee}")
            AppLogger.info("Notif", "Wrote transaction: ${result.payee} — ${result.postings.firstOrNull()?.let { "${it.amount} ${it.currency}" } ?: ""}")
        } else {
            AppLogger.warn("Notif", "No number found in notification from ${sbn.packageName}")
            postBestEffort(journalUri, title, defaultAccount)
        }
    }

    private fun postBestEffort(journalUri: Uri, title: String, defaultAccount: String) {
        AppLogger.info("Notif", "Posting best-effort entry for «$title»")
        val tx = Transaction(
            date = java.time.LocalDate.now().toString(),
            payee = title,
            postings = listOf(
                Posting(account = "expenses:unknown", amount = "", currency = ""),
                Posting(account = defaultAccount, amount = "", currency = ""),
            ),
            source = Source.Notification,
        )
        writeToJournal(journalUri, tx)
        Log.d(TAG, "Posted best-effort entry for: $title")
        AppLogger.info("Notif", "Best-effort entry written")
    }

    // Serialized so concurrent notifications can't interleave read-modify-write and lose entries.
    @Synchronized
    private fun writeToJournal(uri: Uri, tx: Transaction) {
        try {
            val existing = contentResolver.openInputStream(uri)?.use {
                it.bufferedReader().use { r -> r.readText() }
            } ?: ""
            val updated = JournalWriter.appendToContent(existing, tx)
            contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                it.write(updated)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to journal", e)
            AppLogger.error("Notif", "Failed to write to journal: ${e.message}")
        }
    }

    override fun onListenerConnected() {
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
