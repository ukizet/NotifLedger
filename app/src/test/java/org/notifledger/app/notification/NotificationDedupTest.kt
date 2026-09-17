package org.notifledger.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDedupTest {

    @Test
    fun `notification id combines post time and key`() {
        assertEquals(
            "1700000000000|com.bank.KEY",
            NotificationDedup.notificationId(1700000000000L, "com.bank.KEY"),
        )
    }

    @Test
    fun `add to empty raw returns raw containing the id`() {
        val updated = NotificationDedup.add("", "100|a")
        assertEquals("100|a", updated)
    }

    @Test
    fun `adding the same id again returns null`() {
        val raw = NotificationDedup.add("", "100|a")
        assertNull(NotificationDedup.add(raw!!, "100|a"))
    }

    @Test
    fun `cap keeps exactly the newest entries and drops the oldest`() {
        var raw = ""
        val ids = listOf(
            NotificationDedup.notificationId(100L, "a"),
            NotificationDedup.notificationId(200L, "b"),
            NotificationDedup.notificationId(300L, "c"),
            NotificationDedup.notificationId(400L, "d"),
            NotificationDedup.notificationId(500L, "e"),
        )

        for (id in ids) {
            val updated = NotificationDedup.add(raw, id, maxEntries = 3)
            assertNotNull(updated)
            raw = updated!!
        }

        val lines = raw.lines()
        assertEquals(3, lines.size)
        assertTrue(lines.contains("500|e"))
        assertTrue(lines.contains("400|d"))
        assertTrue(lines.contains("300|c"))
        assertFalse(lines.contains("200|b"))
        assertFalse(lines.contains("100|a"))
    }

    @Test
    fun `under the cap insertion order is preserved`() {
        var raw = ""
        raw = NotificationDedup.add(raw, "100|a")!!
        raw = NotificationDedup.add(raw, "200|b")!!
        raw = NotificationDedup.add(raw, "300|c")!!

        assertEquals("100|a\n200|b\n300|c", raw)
    }

    @Test
    fun `adding an id older than the whole window still records it`() {
        val raw = NotificationDedup.add("", "200|b", maxEntries = 1)!!

        val updated = NotificationDedup.add(raw, "100|a", maxEntries = 1)

        assertNotNull(updated)
        assertTrue(updated!!.lines().contains("100|a"))
    }

    @Test
    fun `blank lines are ignored and surrounding whitespace is trimmed`() {
        val raw = "  100|a  \n\n   \n200|b"

        val updated = NotificationDedup.add(raw, "300|c")

        assertEquals("100|a\n200|b\n300|c", updated)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-positive cap is rejected`() {
        NotificationDedup.add("", "100|a", maxEntries = 0)
    }
}
