package org.notifledger.app.model

/** Which direction a sort order walks through its column. */
enum class SortDirection { Ascending, Descending }

/**
 * A sort order for the main screen. Amount orders read the first posting's
 * amount (blank counts as zero); text orders are case-insensitive.
 */
enum class SortOrder(val direction: SortDirection) {
    NewestFirst(SortDirection.Descending),
    OldestFirst(SortDirection.Ascending),
    HighestAmount(SortDirection.Descending),
    LowestAmount(SortDirection.Ascending),
    PayeeAscending(SortDirection.Ascending),
    PayeeDescending(SortDirection.Descending),
    AccountAscending(SortDirection.Ascending),
    AccountDescending(SortDirection.Descending),
    CurrencyAscending(SortDirection.Ascending),
    CurrencyDescending(SortDirection.Descending);

    companion object {
        fun fromValue(s: String): SortOrder = when (s) {
            "newest_first" -> NewestFirst
            "oldest_first" -> OldestFirst
            "highest_amount" -> HighestAmount
            "lowest_amount" -> LowestAmount
            "payee_ascending" -> PayeeAscending
            "payee_descending" -> PayeeDescending
            "account_ascending" -> AccountAscending
            "account_descending" -> AccountDescending
            "currency_ascending" -> CurrencyAscending
            "currency_descending" -> CurrencyDescending
            else -> NewestFirst
        }

        fun toValue(order: SortOrder): String = when (order) {
            NewestFirst -> "newest_first"
            OldestFirst -> "oldest_first"
            HighestAmount -> "highest_amount"
            LowestAmount -> "lowest_amount"
            PayeeAscending -> "payee_ascending"
            PayeeDescending -> "payee_descending"
            AccountAscending -> "account_ascending"
            AccountDescending -> "account_descending"
            CurrencyAscending -> "currency_ascending"
            CurrencyDescending -> "currency_descending"
        }
    }
}
