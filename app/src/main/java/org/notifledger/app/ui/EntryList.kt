package org.notifledger.app.ui

import org.notifledger.app.filters.FilterMatcher
import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.log.AppLogger
import org.notifledger.app.model.SortOrder
import org.notifledger.app.model.TransactionFilter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
        val sorted = EntrySorting.sorted(filtered, sort)
        val pageSize = limit.coerceAtLeast(1)
        val pageCount = maxOf(1, (sorted.size + pageSize - 1) / pageSize)
        val safePage = page.coerceIn(0, pageCount - 1)
        val slice = sorted.drop(safePage * pageSize).take(pageSize)
        return EntryPage(slice, safePage, pageCount)
    }
}

fun toggleSelection(selectedOffsets: Set<Int>, lineOffset: Int): Set<Int> =
    if (lineOffset in selectedOffsets) selectedOffsets - lineOffset else selectedOffsets + lineOffset

private val dateDisplay = DateTimeFormatter.ofPattern("MMM d")

fun formatDisplayDate(raw: String): String {
    if (raw.isBlank()) return raw
    return try {
        LocalDate.parse(raw).format(dateDisplay)
    } catch (e: Exception) {
        AppLogger.warn("EntryList", "Unparseable date \"$raw\": ${e.message}")
        raw
    }
}
