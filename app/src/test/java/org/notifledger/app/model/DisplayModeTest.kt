package org.notifledger.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayModeTest {

    @Test
    fun `stored values round-trip`() {
        DisplayMode.entries.forEach { mode ->
            assertEquals(mode, DisplayMode.fromValue(DisplayMode.toValue(mode)))
        }
    }

    @Test
    fun `table is stored as table`() {
        assertEquals("table", DisplayMode.toValue(DisplayMode.Table))
    }

    @Test
    fun `unknown stored value falls back to the journal`() {
        assertEquals(DisplayMode.Journal, DisplayMode.fromValue(""))
        assertEquals(DisplayMode.Journal, DisplayMode.fromValue("cards"))
    }
}
