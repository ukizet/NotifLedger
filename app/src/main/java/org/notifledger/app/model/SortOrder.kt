package org.notifledger.app.model

/** Which direction a sort order walks through its column. */
enum class SortDirection { Ascending, Descending }

/**
 * A column of the transaction table, which is the same thing as a sortable
 * field. Amounts read the first posting's amount, text columns compare
 * case-insensitively.
 */
enum class SortColumn(val isAmount: Boolean = false) {
    Date,
    Payee,
    Account,
    Amount(isAmount = true),
    Currency,
}

/**
 * A sort order for the main screen: one direction of one [SortColumn]. Which
 * orders a column has, and which of them is ascending-first, lives in
 * [EntrySorting] as compile-checked tables rather than as cross-references
 * between enum entries.
 */
enum class SortOrder(
    val column: SortColumn,
    val direction: SortDirection,
) {
    NewestFirst(SortColumn.Date, SortDirection.Descending),
    OldestFirst(SortColumn.Date, SortDirection.Ascending),
    HighestAmount(SortColumn.Amount, SortDirection.Descending),
    LowestAmount(SortColumn.Amount, SortDirection.Ascending),
    PayeeAscending(SortColumn.Payee, SortDirection.Ascending),
    PayeeDescending(SortColumn.Payee, SortDirection.Descending),
    AccountAscending(SortColumn.Account, SortDirection.Ascending),
    AccountDescending(SortColumn.Account, SortDirection.Descending),
    CurrencyAscending(SortColumn.Currency, SortDirection.Ascending),
    CurrencyDescending(SortColumn.Currency, SortDirection.Descending);

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
