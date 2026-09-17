package org.notifledger.app.notification

/**
 * Tracks already-processed notification ids so catch-up scans and listener
 * reconnects don't journal the same notification twice.
 */
object NotificationDedup {

    const val MAX_ENTRIES = 200

    fun notificationId(postTime: Long, key: String): String = "$postTime|$key"

    /** Returns the new raw state, or null when [id] was already recorded. */
    fun add(raw: String, id: String, maxEntries: Int = MAX_ENTRIES): String? {
        require(maxEntries > 0) { "maxEntries must be positive" }
        val entries = decode(raw)
        if (id in entries) return null
        return encode(prune(entries + id, maxEntries))
    }

    private fun decode(raw: String): List<String> =
        raw.lines().map { it.trim() }.filter { it.isNotBlank() }

    private fun prune(entries: List<String>, maxEntries: Int): List<String> {
        if (entries.size <= maxEntries) return entries
        // Insertion order, so a just-added id is never pruned by its own add() call.
        return entries.takeLast(maxEntries)
    }

    private fun encode(entries: List<String>): String = entries.joinToString("\n")
}
