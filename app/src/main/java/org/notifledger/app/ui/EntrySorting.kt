package org.notifledger.app.ui

import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.model.SortDirection
import org.notifledger.app.model.SortOrder

object EntrySorting {

    fun sorted(entries: List<JournalEntry>, order: SortOrder): List<JournalEntry> = when (order) {
        SortOrder.NewestFirst -> entries.sortedByDescending { it.date }
        SortOrder.OldestFirst -> entries.sortedBy { it.date }
        SortOrder.HighestAmount -> entries.sortedByDescending { postedAmount(it) }
        SortOrder.LowestAmount -> entries.sortedBy { postedAmount(it) }
        SortOrder.PayeeAscending -> byText(entries, order, JournalEntry::payee)
        SortOrder.PayeeDescending -> byText(entries, order, JournalEntry::payee)
        SortOrder.AccountAscending -> byText(entries, order, ::firstAccount)
        SortOrder.AccountDescending -> byText(entries, order, ::firstAccount)
        SortOrder.CurrencyAscending -> byText(entries, order, ::firstCurrency)
        SortOrder.CurrencyDescending -> byText(entries, order, ::firstCurrency)
    }

    /**
     * The ascending order the table header uses for a column, and whether that
     * column currently holds the sort.
     */
    fun indicator(ascending: SortOrder, current: SortOrder): SortDirection? =
        if (current == ascending || current == descending(ascending)) current.direction else null

    /** Tapping a column header sorts ascending, or flips to descending when it already sorts that column. */
    fun toggle(ascending: SortOrder, current: SortOrder): SortOrder =
        if (current == ascending) descending(ascending) else ascending

    private fun descending(ascending: SortOrder): SortOrder = when (ascending) {
        SortOrder.OldestFirst -> SortOrder.NewestFirst
        SortOrder.PayeeAscending -> SortOrder.PayeeDescending
        SortOrder.AccountAscending -> SortOrder.AccountDescending
        SortOrder.CurrencyAscending -> SortOrder.CurrencyDescending
        SortOrder.LowestAmount -> SortOrder.HighestAmount
        else -> ascending
    }

    /** The first posting's amount as a number; blank (auto-balanced) counts as zero. */
    fun postedAmount(entry: JournalEntry): Double =
        entry.postings.firstOrNull()?.amount?.toDoubleOrNull() ?: 0.0

    fun firstAmount(entry: JournalEntry): String = entry.postings.firstOrNull()?.amount ?: ""

    fun firstAccount(entry: JournalEntry): String = entry.postings.firstOrNull()?.account ?: ""

    fun firstCurrency(entry: JournalEntry): String = entry.postings.firstOrNull()?.currency ?: ""

    /** Text columns sort case-insensitively and break ties by newest date first. */
    private fun byText(
        entries: List<JournalEntry>,
        order: SortOrder,
        value: (JournalEntry) -> String,
    ): List<JournalEntry> {
        val byValue = compareBy<JournalEntry> { value(it).lowercase() }
        val comparator = if (order.direction == SortDirection.Ascending) {
            byValue.thenByDescending { it.date }
        } else {
            byValue.reversed().thenByDescending { it.date }
        }
        return entries.sortedWith(comparator)
    }
}
