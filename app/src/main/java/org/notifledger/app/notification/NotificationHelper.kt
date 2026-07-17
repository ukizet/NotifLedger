package org.notifledger.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import org.notifledger.app.MainActivity
import org.notifledger.app.R
import org.notifledger.app.log.AppLogger

/**
 * Helper for showing/hiding a persistent notification that indicates the
 * notification listener service is active.
 */
object NotificationHelper {

    private const val CHANNEL_ID = "notifledger_listener"
    private const val LISTENER_NOTIFICATION_ID = 9001

    /** Full component name of our [NotifListener]. */
    private const val LISTENER_COMPONENT = ".notification.NotifListener"

    /** Whether [NotifListener] has been granted notification listener access.
     *
     * Reads the system setting and checks for our component.
     */
    fun isListenerEnabled(context: Context): Boolean {
        val raw = try {
            Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ) ?: ""
        } catch (_: Exception) {
            ""
        }
        return isComponentInListeners(raw, context.packageName, LISTENER_COMPONENT)
    }

    /**
     * Pure function: checks whether [component] (fully qualified or ".relative")
     * appears in the colon-separated [enabledListeners] string.
     *
     * Public for testing.
     */
    internal fun isComponentInListeners(
        enabledListeners: String,
        packageName: String,
        component: String,
    ): Boolean {
        if (enabledListeners.isBlank()) return false
        val fullName = if (component.startsWith(".")) "$packageName$component" else component
        val systemEntry = "$packageName/$fullName"
        return enabledListeners.split(":").any { it.trim() == systemEntry }
    }

    fun showListeningNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createChannel(context, nm)

        val isActive = isListenerEnabled(context)
        AppLogger.info("Notif", "Listener enabled: $isActive — showing status notification")

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(R.string.notif_listening_title))
            .setContentText(
                if (isActive) context.getString(R.string.notif_listening_text)
                else context.getString(R.string.notif_not_listening_text)
            )
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        nm.notify(LISTENER_NOTIFICATION_ID, notification)
    }

    fun hideListeningNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(LISTENER_NOTIFICATION_ID)
    }

    private fun createChannel(context: Context, nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_listener_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notif_listener_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }
}
