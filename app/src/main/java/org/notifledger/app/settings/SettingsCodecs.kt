package org.notifledger.app.settings

object SettingsCodecs {

    fun decodeSources(raw: String): List<String> =
        if (raw.isBlank()) emptyList() else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun encodeSources(sources: List<String>): String = sources.joinToString(",")

    fun normalizeFilterLimit(limit: Int): Int = limit.coerceAtLeast(1)
}
