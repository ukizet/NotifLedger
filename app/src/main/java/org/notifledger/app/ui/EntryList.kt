package org.notifledger.app.ui

import org.notifledger.app.filters.FilterMatcher
import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.model.SortOrder
import org.notifledger.app.model.TransactionFilter

data class EntryPage(
    val entries: List<JournalEntry>,
    val page: Int,
    val pageCount: Int,
)

object EntryList {

    fun visible(
        entries: List<JournalEntry>,
        filters: List<TransactionFilter>,
        sort: SortOrder,
        limit: Int,
        page: Int = 0,
    ): EntryPage {
        val includes = FilterMatcher.includeAccounts(filters)
        val excludes = FilterMatcher.excludeAccounts(filters)
        val filtered = entries.filter { FilterMatcher.matches(it, includes, excludes) }
        val sorted = when (sort) {
            SortOrder.NewestFirst -> filtered.sortedByDescending { it.date }
            SortOrder.OldestFirst -> filtered.sortedBy { it.date }
            SortOrder.HighestAmount -> filtered.sortedByDescending { it.postings.firstOrNull()?.amount?.toDoubleOrNull() ?: 0.0 }
            SortOrder.LowestAmount -> filtered.sortedBy { it.postings.firstOrNull()?.amount?.toDoubleOrNull() ?: 0.0 }
        }
        val pageSize = limit.coerceAtLeast(1)
        val pageCount = maxOf(1, (sorted.size + pageSize - 1) / pageSize)
        val safePage = page.coerceIn(0, pageCount - 1)
        val slice = sorted.drop(safePage * pageSize).take(pageSize)
        return EntryPage(slice, safePage, pageCount)
    }
}
