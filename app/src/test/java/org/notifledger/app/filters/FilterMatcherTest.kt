package org.notifledger.app.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.model.Posting
import org.notifledger.app.model.TransactionFilter

class FilterMatcherTest {

    @Test
    fun `active accounts ignores inactive filters`() {
        val filters = listOf(
            TransactionFilter(label = "Groceries", account = "expenses:groceries"),
            TransactionFilter(label = "Travel", account = "expenses:travel", isActive = false),
        )
        assertEquals(setOf("expenses:groceries"), FilterMatcher.activeAccounts(filters))
    }

    @Test
    fun `active accounts collects every active account`() {
        val filters = listOf(
            TransactionFilter(label = "Groceries", account = "expenses:groceries"),
            TransactionFilter(label = "Travel", account = "expenses:travel"),
            TransactionFilter(label = "Off", account = "expenses:off", isActive = false),
        )
        assertEquals(setOf("expenses:groceries", "expenses:travel"), FilterMatcher.activeAccounts(filters))
    }

    @Test
    fun `matches with empty active accounts returns true`() {
        val entry = entryWithAccounts("expenses:anything")
        assertTrue(FilterMatcher.matches(entry, emptySet()))
    }

    @Test
    fun `matches account exactly and case-sensitively`() {
        val entry = entryWithAccounts("expenses:groceries")
        assertTrue(FilterMatcher.matches(entry, setOf("expenses:groceries")))
        assertFalse(FilterMatcher.matches(entry, setOf("Expenses:groceries")))
        assertFalse(FilterMatcher.matches(entry, setOf("expenses:grocery")))
    }

    @Test
    fun `matches account on any posting`() {
        val entry = entryWithAccounts("assets:bank:checking", "expenses:groceries")
        assertTrue(FilterMatcher.matches(entry, setOf("expenses:groceries")))
    }

    @Test
    fun `matches returns false when no posting account is active`() {
        val entry = entryWithAccounts("assets:bank:checking", "expenses:other")
        assertFalse(FilterMatcher.matches(entry, setOf("expenses:groceries")))
    }

    private fun entryWithAccounts(vararg accounts: String): JournalEntry = JournalEntry(
        date = "2026-01-01",
        payee = "Payee",
        postings = accounts.map { Posting(account = it, amount = "10.00", currency = "NOK") },
        lineOffset = 0,
    )
}
