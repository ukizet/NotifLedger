package org.notifledger.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCodecsTest {

    @Test
    fun `decode blank returns empty list`() {
        assertEquals(emptyList<String>(), SettingsCodecs.decodeSources(""))
    }

    @Test
    fun `decode whitespace-only returns empty list`() {
        assertEquals(emptyList<String>(), SettingsCodecs.decodeSources("   "))
    }

    @Test
    fun `decode trims whitespace around sources`() {
        assertEquals(
            listOf("no.dnb.mobil", "com.vipps"),
            SettingsCodecs.decodeSources(" no.dnb.mobil , com.vipps "),
        )
    }

    @Test
    fun `decode drops empty entries between separators`() {
        assertEquals(listOf("a", "b"), SettingsCodecs.decodeSources("a,,b"))
        assertEquals(emptyList<String>(), SettingsCodecs.decodeSources(","))
    }

    @Test
    fun `encode empty list returns blank string`() {
        assertEquals("", SettingsCodecs.encodeSources(emptyList()))
    }

    @Test
    fun `encode and decode round-trip preserves sources in order`() {
        val sources = listOf("no.dnb.mobil", "com.vipps", "com.klarna")
        assertEquals(sources, SettingsCodecs.decodeSources(SettingsCodecs.encodeSources(sources)))
    }

    @Test
    fun `normalize filter limit zero becomes one`() {
        assertEquals(1, SettingsCodecs.normalizeFilterLimit(0))
    }

    @Test
    fun `normalize filter limit negative becomes one`() {
        assertEquals(1, SettingsCodecs.normalizeFilterLimit(-5))
    }

    @Test
    fun `normalize filter limit one stays one`() {
        assertEquals(1, SettingsCodecs.normalizeFilterLimit(1))
    }

    @Test
    fun `normalize filter limit above one is unchanged`() {
        assertEquals(5, SettingsCodecs.normalizeFilterLimit(5))
    }
}
