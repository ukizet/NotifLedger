package org.notifledger.app.model

/** How the main screen shows transactions: the journal list or the sortable table. */
enum class DisplayMode {
    Journal,
    Table;

    companion object {
        fun fromValue(s: String): DisplayMode = when (s) {
            "table" -> Table
            else -> Journal
        }

        fun toValue(mode: DisplayMode): String = when (mode) {
            Journal -> "journal"
            Table -> "table"
        }
    }
}
