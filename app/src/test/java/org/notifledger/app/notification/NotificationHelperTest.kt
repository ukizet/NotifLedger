package org.notifledger.app.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHelperTest {

    @Test
    fun `empty listener string returns false`() {
        assertFalse(NotificationHelper.isComponentInListeners("", "org.notifledger.app", ".notification.NotifListener"))
    }

    @Test
    fun `blank listener string returns false`() {
        assertFalse(NotificationHelper.isComponentInListeners("   ", "org.notifledger.app", ".notification.NotifListener"))
    }

    @Test
    fun `exact match with relative component`() {
        val raw = "org.notifledger.app/org.notifledger.app.notification.NotifListener:com.other.app/com.other.app.Service"
        assertTrue(NotificationHelper.isComponentInListeners(raw, "org.notifledger.app", ".notification.NotifListener"))
    }

    @Test
    fun `exact match with fully qualified component`() {
        val raw = "org.notifledger.app/org.notifledger.app.notification.NotifListener"
        assertTrue(NotificationHelper.isComponentInListeners(raw, "org.notifledger.app", "org.notifledger.app.notification.NotifListener"))
    }

    @Test
    fun `different package returns false`() {
        val raw = "com.other.app/com.other.app.NotifListener"
        assertFalse(NotificationHelper.isComponentInListeners(raw, "org.notifledger.app", ".notification.NotifListener"))
    }

    @Test
    fun `trims whitespace around colons`() {
        val raw = "org.notifledger.app/org.notifledger.app.notification.NotifListener "
        assertTrue(NotificationHelper.isComponentInListeners(raw, "org.notifledger.app", ".notification.NotifListener"))
    }

    @Test
    fun `substring does not match`() {
        val raw = "org.notifledger.app/org.notifledger.app.notification.NotifListenerExtra"
        assertFalse(NotificationHelper.isComponentInListeners(raw, "org.notifledger.app", ".notification.NotifListener"))
    }

    @Test
    fun `multiple listeners`() {
        val raw = "com.a/com.a:org.notifledger.app/org.notifledger.app.notification.NotifListener:com.b/com.b"
        assertTrue(NotificationHelper.isComponentInListeners(raw, "org.notifledger.app", ".notification.NotifListener"))
    }

    @Test
    fun `only other listeners returns false`() {
        val raw = "com.a/com.a:com.b/com.b:com.c/com.c"
        assertFalse(NotificationHelper.isComponentInListeners(raw, "org.notifledger.app", ".notification.NotifListener"))
    }
}
