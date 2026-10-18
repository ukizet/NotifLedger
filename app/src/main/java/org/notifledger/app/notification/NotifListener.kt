package org.notifledger.app.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.notifledger.app.NotifLedgerApp
import org.notifledger.app.log.AppLogger

/**
 * Listens for notifications from user-selected banking/payment apps.
 *
 * Uses title as payee, finds the first number as amount, then writes to journal.
 */
class NotifListener : NotificationListenerService() {

    private val channel = Channel<StatusBarNotification>(Channel.UNLIMITED)
    private lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        // The single consumer both keeps the main looper free and serializes
        // dedup reads and persists.
        scope.launch {
            for (sbn in channel) {
                try {
                    handleNotification(sbn)
                } catch (e: Exception) {
                    // One malformed notification must not kill the only consumer — a dead
                    // consumer means silent capture loss and unbounded queue growth.
                    AppLogger.error("Notif", "Handling failed for ${sbn.packageName}: ${e.message}")
                }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (channel.trySend(sbn).isFailure) {
            AppLogger.error("Notif", "Listener channel closed — dropped ${sbn.packageName}")
        }
    }

    private suspend fun handleNotification(sbn: StatusBarNotification) {
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

        // The single-consumer worker serializes this read and the persist below,
        // so they cannot interleave with each other.
        val updatedRaw = NotificationDedup.add(
            app.getProcessedNotificationIds(),
            NotificationDedup.notificationId(sbn.postTime, sbn.key),
        ) ?: return

        val extras = sbn.notification.extras
        val title = extras.getString(EXTRA_TITLE) ?: return
        val text = extras.getString(EXTRA_TEXT) ?: ""

        val defaultAccount = app.getDefaultAccount()
        val defaultCurrency = app.getDefaultCurrency()
        val rules = app.getCategorizationRules()

        val tx = NotificationHandler.processNotification(
            title = title,
            text = text,
            defaultAccount = defaultAccount,
            currency = defaultCurrency,
            rules = rules,
        ) ?: return

        // Title (payee) and amount are retained in the journal; the raw body text is not,
        // per design §4.1, so we never log the body.
        AppLogger.info("Notif", "Notification from ${sbn.packageName}")

        if (!app.journalRepository.appendRaw(journalUri.toString(), tx)) {
            AppLogger.error("Notif", "Failed to write to journal")
            return
        }
        Log.d(TAG, "Transaction written to journal")
        AppLogger.info("Notif", "Transaction written")

        try {
            app.settings.setProcessedNotificationIds(updatedRaw)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist dedup state", e)
            AppLogger.error("Notif", "Failed to persist dedup state: ${e.message}")
        }
    }

    override fun onListenerConnected() {
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
        active.sortedBy { it.postTime }.forEach { sbn ->
            if (channel.trySend(sbn).isFailure) {
                AppLogger.error("Notif", "Listener channel closed — dropped ${sbn.packageName}")
            }
        }
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "Notification listener disconnected")
        AppLogger.warn("Notif", "Notification listener disconnected")
        NotificationHelper.hideListeningNotification(this)
    }

    override fun onDestroy() {
        scope.cancel()
        channel.close()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NotifLedger"
        private const val EXTRA_TITLE = "android.title"
        private const val EXTRA_TEXT = "android.text"
    }
}
