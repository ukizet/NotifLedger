package org.notifledger.app.ui

import org.notifledger.app.filters.FilterMatcher
import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.model.SortOrder
import org.notifledger.app.model.TransactionFilter

object EntryList {

    fun visible(
        entries: List<JournalEntry>,
        filters: List<TransactionFilter>,
        sort: SortOrder,
        limit: Int,
    ): List<JournalEntry> {
        val accounts = FilterMatcher.activeAccounts(filters)
        val filtered = entries.filter { FilterMatcher.matches(it, accounts) }
        val sorted = when (sort) {
            SortOrder.NewestFirst -> filtered.sortedByDescending { it.date }
            SortOrder.OldestFirst -> filtered.sortedBy { it.date }
            SortOrder.HighestAmount -> filtered.sortedByDescending { it.postings.firstOrNull()?.amount?.toDoubleOrNull() ?: 0.0 }
            SortOrder.LowestAmount -> filtered.sortedBy { it.postings.firstOrNull()?.amount?.toDoubleOrNull() ?: 0.0 }
        }
        return sorted.take(limit.coerceAtLeast(1))
    }
}
