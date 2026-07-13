package org.notifledger.app.model

enum class SortOrder {
    NewestFirst,
    OldestFirst,
    HighestAmount,
    LowestAmount;

    companion object {
        fun fromValue(s: String): SortOrder = when (s) {
            "newest_first" -> NewestFirst
            "oldest_first" -> OldestFirst
            "highest_amount" -> HighestAmount
            "lowest_amount" -> LowestAmount
            else -> NewestFirst
        }

        fun toValue(order: SortOrder): String = when (order) {
            NewestFirst -> "newest_first"
            OldestFirst -> "oldest_first"
            HighestAmount -> "highest_amount"
            LowestAmount -> "lowest_amount"
        }
    }
}
