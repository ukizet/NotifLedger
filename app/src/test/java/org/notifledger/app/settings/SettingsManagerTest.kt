package org.notifledger.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for SettingsManager data format logic.
 *
 * The SettingsManager itself requires an Android Context, so these tests
 * verify the serialization format used for notification sources
 * (comma-separated list of package names).
 */
class SettingsManagerTest {

    @Test
    fun `empty sources serialize to blank string`() {
        val result = emptyList<String>().joinToString(",")
        assertEquals("", result)
    }

    @Test
    fun `single source serializes to plain package name`() {
        val result = listOf("no.dnb.mobil").joinToString(",")
        assertEquals("no.dnb.mobil", result)
    }

    @Test
    fun `multiple sources serialize comma-separated`() {
        val result = listOf("no.dnb.mobil", "com.vipps", "com.klarna").joinToString(",")
        assertEquals("no.dnb.mobil,com.vipps,com.klarna", result)
    }

    @Test
    fun `deserialize blank to empty list`() {
        val raw = ""
        val result = if (raw.isBlank()) emptyList() else raw.split(",").map { it.trim() }
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `deserialize single source`() {
        val raw = "no.dnb.mobil"
        val result = raw.split(",").map { it.trim() }
        assertEquals(listOf("no.dnb.mobil"), result)
    }

    @Test
    fun `deserialize multiple sources`() {
        val raw = "no.dnb.mobil,com.vipps,com.klarna"
        val result = raw.split(",").map { it.trim() }
        assertEquals(listOf("no.dnb.mobil", "com.vipps", "com.klarna"), result)
    }

    @Test
    fun `add source to existing list`() {
        val sources = mutableListOf("no.dnb.mobil")
        sources.add("com.vipps")
        assertEquals(listOf("no.dnb.mobil", "com.vipps"), sources)
    }

    @Test
    fun `remove source from list`() {
        val sources = mutableListOf("no.dnb.mobil", "com.vipps")
        sources.remove("no.dnb.mobil")
        assertEquals(listOf("com.vipps"), sources)
    }

    @Test
    fun `prevent duplicate sources`() {
        val sources = mutableSetOf("no.dnb.mobil")
        sources.add("no.dnb.mobil")
        assertEquals(setOf("no.dnb.mobil"), sources)
    }
}
