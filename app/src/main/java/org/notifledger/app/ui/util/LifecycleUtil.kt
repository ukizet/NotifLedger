package org.notifledger.app.ui.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import org.notifledger.app.notification.NotificationHelper

/**
 * Tracks whether the notification listener is enabled, re-checking on every ON_RESUME
 * so the UI refreshes after the user toggles the permission in system Settings and
 * navigates back. System settings don't emit a broadcast we can observe, so polling
 * on resume is the simplest correct option.
 */
@Composable
fun rememberListenerEnabled(context: Context): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(NotificationHelper.isListenerEnabled(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = NotificationHelper.isListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return enabled
}
