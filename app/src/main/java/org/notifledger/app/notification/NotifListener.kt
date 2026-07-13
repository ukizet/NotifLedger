package org.notifledger.app.notification

import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.notifledger.app.NotifLedgerApp
import org.notifledger.app.journal.JournalWriter
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

        val journalUri = app.getJournalUri()
        val defaultAccount = app.getDefaultAccount()
        if (journalUri == null) return

        val result = ParserEngine.parse(title, text, "expenses:unknown", defaultAccount)
        if (result != null) {
            writeToJournal(journalUri, result.copy(source = Source.Notification))
            Log.d(TAG, "Wrote transaction to journal: ${result.payee}")
        } else {
            postBestEffort(journalUri, title, defaultAccount)
        }
    }

    private fun postBestEffort(journalUri: Uri, title: String, defaultAccount: String) {
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
    }

    private fun writeToJournal(uri: Uri, tx: Transaction) {
        try {
            val existing = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
            val updated = JournalWriter.appendToContent(existing, tx)
            contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                it.write(updated)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to journal", e)
        }
    }

    override fun onListenerConnected() {
        Log.d(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "Notification listener disconnected")
    }

    companion object {
        private const val TAG = "NotifListener"
        private const val EXTRA_TITLE = "android.title"
        private const val EXTRA_TEXT = "android.text"
    }
}
