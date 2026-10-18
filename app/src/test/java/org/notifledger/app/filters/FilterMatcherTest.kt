package org.notifledger.app.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.model.FilterMode
import org.notifledger.app.model.Posting
import org.notifledger.app.model.TransactionFilter

class FilterMatcherTest {

    @Test
    fun `include accounts ignores exclude and off filters`() {
        val filters = listOf(
            TransactionFilter(label = "Groceries", account = "expenses:groceries"),
            TransactionFilter(label = "Travel", account = "expenses:travel", mode = FilterMode.EXCLUDE),
            TransactionFilter(label = "Off", account = "expenses:off", mode = FilterMode.OFF),
        )
        assertEquals(setOf("expenses:groceries"), FilterMatcher.includeAccounts(filters))
    }

    @Test
    fun `exclude accounts collects only exclude filters`() {
        val filters = listOf(
            TransactionFilter(label = "Groceries", account = "expenses:groceries"),
            TransactionFilter(label = "Travel", account = "expenses:travel", mode = FilterMode.EXCLUDE),
            TransactionFilter(label = "Off", account = "expenses:off", mode = FilterMode.OFF),
        )
        assertEquals(setOf("expenses:travel"), FilterMatcher.excludeAccounts(filters))
    }

    @Test
    fun `matches with both sets empty returns true`() {
        val entry = entryWithAccounts("expenses:anything")
        assertTrue(FilterMatcher.matches(entry, emptySet(), emptySet()))
    }

    @Test
    fun `matches account exactly and case-sensitively`() {
        val entry = entryWithAccounts("expenses:groceries")
        assertTrue(FilterMatcher.matches(entry, setOf("expenses:groceries"), emptySet()))
        assertFalse(FilterMatcher.matches(entry, setOf("Expenses:groceries"), emptySet()))
        assertFalse(FilterMatcher.matches(entry, setOf("expenses:grocery"), emptySet()))
    }

    @Test
    fun `matches account on any posting`() {
        val entry = entryWithAccounts("assets:bank:checking", "expenses:groceries")
        assertTrue(FilterMatcher.matches(entry, setOf("expenses:groceries"), emptySet()))
    }

    @Test
    fun `matches returns false when no posting account is included`() {
        val entry = entryWithAccounts("assets:bank:checking", "expenses:other")
        assertFalse(FilterMatcher.matches(entry, setOf("expenses:groceries"), emptySet()))
    }

    @Test
    fun `exclude subtracts even when an include would match`() {
        val entry = entryWithAccounts("expenses:groceries")
        assertFalse(FilterMatcher.matches(entry, setOf("expenses:groceries"), setOf("expenses:groceries")))
    }

    @Test
    fun `include union applies and exclude removes`() {
        val groceries = entryWithAccounts("expenses:groceries")
        val travel = entryWithAccounts("expenses:travel")
        val other = entryWithAccounts("expenses:other")
        val includes = setOf("expenses:groceries", "expenses:travel")
        val excludes = setOf("expenses:travel")

        assertTrue(FilterMatcher.matches(groceries, includes, excludes))
        assertFalse(FilterMatcher.matches(travel, includes, excludes))
        assertFalse(FilterMatcher.matches(other, includes, excludes))
    }

    @Test
    fun `off filter has no effect`() {
        val filters = listOf(
            TransactionFilter(label = "Off", account = "expenses:off", mode = FilterMode.OFF),
        )
        val entry = entryWithAccounts("expenses:anything")

        assertTrue(
            FilterMatcher.matches(
                entry,
                FilterMatcher.includeAccounts(filters),
                FilterMatcher.excludeAccounts(filters),
            )
        )
    }

    private fun entryWithAccounts(vararg accounts: String): JournalEntry = JournalEntry(
        date = "2026-01-01",
        payee = "Payee",
        postings = accounts.map { Posting(account = it, amount = "10.00", currency = "NOK") },
        lineOffset = 0,
    )
}
