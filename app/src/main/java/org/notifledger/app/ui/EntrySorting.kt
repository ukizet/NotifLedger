package org.notifledger.app.ui

import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.model.SortColumn
import org.notifledger.app.model.SortDirection
import org.notifledger.app.model.SortOrder

object EntrySorting {

    fun sorted(entries: List<JournalEntry>, order: SortOrder): List<JournalEntry> =
        entries.sortedWith(comparator(order))

    /**
     * The one ascending comparator per column: text is case-insensitive and
     * ties break by newest date first. Descending is that comparator reversed,
     * so both directions read the same tie rule.
     */
    private fun comparator(order: SortOrder): Comparator<JournalEntry> {
        val ascending = when (order) {
            SortOrder.NewestFirst, SortOrder.OldestFirst ->
                compareBy<JournalEntry> { it.date }

            SortOrder.HighestAmount, SortOrder.LowestAmount ->
                compareBy<JournalEntry> { postedAmount(it) }

            SortOrder.PayeeAscending, SortOrder.PayeeDescending ->
                textColumn(JournalEntry::payee)

            SortOrder.AccountAscending, SortOrder.AccountDescending ->
                textColumn(::firstAccount)

            SortOrder.CurrencyAscending, SortOrder.CurrencyDescending ->
                textColumn(::firstCurrency)
        }
        return if (order.direction == SortDirection.Ascending) ascending else ascending.reversed()
    }

    private fun textColumn(value: (JournalEntry) -> String): Comparator<JournalEntry> =
        compareBy<JournalEntry> { value(it).lowercase() }.thenByDescending { it.date }

    /** Tapping a column header sorts ascending, or flips direction when it already sorts that column. */
    fun toggle(column: SortColumn, current: SortOrder): SortOrder =
        if (current.column != column) ascending(column) else opposite(current)

    /** Which way a column header should point, or null when that column is not the sort. */
    fun indicator(column: SortColumn, current: SortOrder): SortDirection? =
        if (current.column == column) current.direction else null

    /** The direction A-Z / oldest-first / lowest-first, i.e. the header's first tap. */
    private fun ascending(column: SortColumn): SortOrder = when (column) {
        SortColumn.Date -> SortOrder.OldestFirst
        SortColumn.Payee -> SortOrder.PayeeAscending
        SortColumn.Account -> SortOrder.AccountAscending
        SortColumn.Amount -> SortOrder.LowestAmount
        SortColumn.Currency -> SortOrder.CurrencyAscending
    }

    private fun opposite(order: SortOrder): SortOrder = when (order) {
        SortOrder.NewestFirst -> SortOrder.OldestFirst
        SortOrder.OldestFirst -> SortOrder.NewestFirst
        SortOrder.HighestAmount -> SortOrder.LowestAmount
        SortOrder.LowestAmount -> SortOrder.HighestAmount
        SortOrder.PayeeAscending -> SortOrder.PayeeDescending
        SortOrder.PayeeDescending -> SortOrder.PayeeAscending
        SortOrder.AccountAscending -> SortOrder.AccountDescending
        SortOrder.AccountDescending -> SortOrder.AccountAscending
        SortOrder.CurrencyAscending -> SortOrder.CurrencyDescending
        SortOrder.CurrencyDescending -> SortOrder.CurrencyAscending
    }

    /** The first posting's amount as a number; blank (auto-balanced) counts as zero. */
    fun postedAmount(entry: JournalEntry): Double =
        entry.postings.firstOrNull()?.amount?.toDoubleOrNull() ?: 0.0

    fun firstAmount(entry: JournalEntry): String = entry.postings.firstOrNull()?.amount ?: ""

    fun firstAccount(entry: JournalEntry): String = entry.postings.firstOrNull()?.account ?: ""

    fun firstCurrency(entry: JournalEntry): String = entry.postings.firstOrNull()?.currency ?: ""
}
