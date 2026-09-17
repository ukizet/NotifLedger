package org.notifledger.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the notification-source format used by SettingsManager.
 *
 * SettingsManager itself requires an Android Context, so the comma-separated
 * source encoding is verified through the pure SettingsCodecs helpers it
 * delegates to.
 */
class SettingsManagerTest {

    @Test
    fun `empty sources encode to blank string`() {
        assertEquals("", SettingsCodecs.encodeSources(emptyList()))
    }

    @Test
    fun `single source encodes to plain package name`() {
        assertEquals("no.dnb.mobil", SettingsCodecs.encodeSources(listOf("no.dnb.mobil")))
    }

    @Test
    fun `multiple sources encode comma-separated`() {
        assertEquals(
            "no.dnb.mobil,com.vipps,com.klarna",
            SettingsCodecs.encodeSources(listOf("no.dnb.mobil", "com.vipps", "com.klarna")),
        )
    }

    @Test
    fun `decode blank to empty list`() {
        assertEquals(emptyList<String>(), SettingsCodecs.decodeSources(""))
    }

    @Test
    fun `decode single source`() {
        assertEquals(listOf("no.dnb.mobil"), SettingsCodecs.decodeSources("no.dnb.mobil"))
    }

    @Test
    fun `decode multiple sources`() {
        assertEquals(
            listOf("no.dnb.mobil", "com.vipps", "com.klarna"),
            SettingsCodecs.decodeSources("no.dnb.mobil,com.vipps,com.klarna"),
        )
    }

    @Test
    fun `add source to existing list`() {
        val sources = SettingsCodecs.decodeSources(SettingsCodecs.encodeSources(listOf("no.dnb.mobil")))
        val updated = sources + "com.vipps"
        assertEquals(
            listOf("no.dnb.mobil", "com.vipps"),
            SettingsCodecs.decodeSources(SettingsCodecs.encodeSources(updated)),
        )
    }

    @Test
    fun `remove source from list`() {
        val sources = SettingsCodecs.decodeSources("no.dnb.mobil,com.vipps")
        val updated = sources - "no.dnb.mobil"
        assertEquals(
            listOf("com.vipps"),
            SettingsCodecs.decodeSources(SettingsCodecs.encodeSources(updated)),
        )
    }

    @Test
    fun `already selected source is not added again`() {
        val sources = SettingsCodecs.decodeSources("no.dnb.mobil")
        val selected = "no.dnb.mobil"
        val updated = if (selected !in sources) sources + selected else sources
        assertEquals("no.dnb.mobil", SettingsCodecs.encodeSources(updated))
    }
}
