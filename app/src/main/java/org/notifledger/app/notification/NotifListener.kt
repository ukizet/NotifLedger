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
        handleNotification(sbn)
    }

    private fun handleNotification(sbn: StatusBarNotification) {
        val app = application as? NotifLedgerApp ?: return

        val allowedPackages = app.getAllowedNotificationPackages()
        // Disallowed notifications are not recorded, so allowlisting an app later
        // can still capture what is currently in the shade.
        if (!NotificationHandler.shouldProcess(sbn.packageName, allowedPackages)) return

        val journalUri = app.getJournalUri()
        if (journalUri == null) {
            AppLogger.warn("Notif", "No journal URI set — cannot write")
            return
        }

        // Listener callbacks are serialized on the main looper, so this read and
        // the persist below cannot interleave with each other.
        val updatedRaw = NotificationDedup.add(
            app.getProcessedNotificationIds(),
            NotificationDedup.notificationId(sbn.postTime, sbn.key),
        ) ?: return

        val extras = sbn.notification.extras
        val title = extras.getString(EXTRA_TITLE) ?: return
        val text = extras.getString(EXTRA_TEXT) ?: ""

        val defaultAccount = app.getDefaultAccount()
        val rules = app.getCategorizationRules()

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

        if (!writeToJournal(journalUri, tx.copy(source = Source.Notification))) return
        Log.d(TAG, "Wrote transaction to journal: ${tx.payee}")
        AppLogger.info("Notif", "Wrote transaction: ${tx.payee} — ${tx.postings.firstOrNull()?.let { "${it.amount} ${it.currency}" } ?: ""}")

        try {
            runBlocking { app.settings.setProcessedNotificationIds(updatedRaw) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist dedup state", e)
            AppLogger.error("Notif", "Failed to persist dedup state: ${e.message}")
        }
    }

    // Uses the shared app-level mutex so UI writes and notification writes don't interleave.
    private fun writeToJournal(uri: Uri, tx: Transaction): Boolean {
        val app = application as? NotifLedgerApp ?: return false
        return runBlocking(Dispatchers.IO) {
            app.journalWriteMutex.withLock {
                try {
                    val existing = contentResolver.openInputStream(uri)?.use {
                        it.bufferedReader().use { r -> r.readText() }
                    } ?: ""
                    val updated = JournalWriter.appendToContent(existing, tx)
                    contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                        it.write(updated.content)
                    } != null
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to write to journal", e)
                    AppLogger.error("Notif", "Failed to write to journal: ${e.message}")
                    false
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
        scanActiveNotifications()
    }

    // Catches up on notifications posted while the app wasn't running; only
    // notifications still in the shade are available.
    private fun scanActiveNotifications() {
        val active = try {
            activeNotifications
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read active notifications", e)
            AppLogger.error("Notif", "Failed to read active notifications: ${e.message}")
            return
        }
        if (active == null || active.isEmpty()) return
        AppLogger.info("Notif", "Scanning ${active.size} active notifications")
        active.sortedBy { it.postTime }.forEach { handleNotification(it) }
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
