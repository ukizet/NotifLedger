package org.notifledger.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.model.Posting
import org.notifledger.app.model.SortDirection
import org.notifledger.app.model.SortOrder

class EntrySortingTest {

    @Test
    fun `payee sorts alphabetically ignoring case`() {
        val entries = listOf(
            entry("2026-01-01", payee = "rema 1000"),
            entry("2026-01-02", payee = "Coop"),
            entry("2026-01-03", payee = "kiwi"),
        )
        val result = EntrySorting.sorted(entries, SortOrder.PayeeAscending)
        assertEquals(listOf("Coop", "kiwi", "rema 1000"), result.map { it.payee })
    }

    @Test
    fun `payee descending reverses the alphabetical order`() {
        val entries = listOf(
            entry("2026-01-01", payee = "Coop"),
            entry("2026-01-02", payee = "kiwi"),
            entry("2026-01-03", payee = "rema 1000"),
        )
        val result = EntrySorting.sorted(entries, SortOrder.PayeeDescending)
        assertEquals(listOf("rema 1000", "kiwi", "Coop"), result.map { it.payee })
    }

    @Test
    fun `equal payees break the tie by newest date`() {
        val entries = listOf(
            entry("2026-01-01", payee = "Coop"),
            entry("2026-03-01", payee = "Coop"),
            entry("2026-02-01", payee = "Coop"),
        )
        val result = EntrySorting.sorted(entries, SortOrder.PayeeAscending)
        assertEquals(listOf("2026-03-01", "2026-02-01", "2026-01-01"), result.map { it.date })
    }

    @Test
    fun `account sorts by the first posting`() {
        val entries = listOf(
            entryWithAccounts("2026-01-01", "expenses:travel", "assets:bank:checking"),
            entryWithAccounts("2026-01-02", "expenses:groceries", "assets:bank:checking"),
        )
        val result = EntrySorting.sorted(entries, SortOrder.AccountAscending)
        assertEquals(listOf("expenses:groceries", "expenses:travel"), result.map { it.postings.first().account })

        val reversed = EntrySorting.sorted(entries, SortOrder.AccountDescending)
        assertEquals(listOf("expenses:travel", "expenses:groceries"), reversed.map { it.postings.first().account })
    }

    @Test
    fun `currency sorts by the first posting`() {
        val entries = listOf(
            entry("2026-01-01", currency = "NOK"),
            entry("2026-01-02", currency = "EUR"),
            entry("2026-01-03", currency = "USD"),
        )
        val result = EntrySorting.sorted(entries, SortOrder.CurrencyAscending)
        assertEquals(listOf("EUR", "NOK", "USD"), result.map { it.postings.first().currency })

        val reversed = EntrySorting.sorted(entries, SortOrder.CurrencyDescending)
        assertEquals(listOf("USD", "NOK", "EUR"), reversed.map { it.postings.first().currency })
    }

    @Test
    fun `entries without postings carry blank text into the sort`() {
        val entries = listOf(entry("2026-01-01", payee = "Coop"), entryWithoutPostings("2026-01-02"))
        val result = EntrySorting.sorted(entries, SortOrder.PayeeAscending)
        assertEquals(listOf("", "Coop"), result.map { it.payee })
    }

    @Test
    fun `negative amounts sort below positive ones`() {
        val entries = listOf(
            entry("2026-01-01", amount = "100.00"),
            entry("2026-01-02", amount = "-250.50"),
            entry("2026-01-03", amount = "12.00"),
        )
        val result = EntrySorting.sorted(entries, SortOrder.LowestAmount)
        assertEquals(listOf("-250.50", "12.00", "100.00"), result.map { it.postings.first().amount })
    }

    @Test
    fun `amount ties keep the input order`() {
        val first = entry("2026-01-01", amount = "50.00", payee = "First")
        val second = entry("2026-01-02", amount = "50.00", payee = "Second")
        val result = EntrySorting.sorted(listOf(first, second), SortOrder.LowestAmount)
        assertEquals(listOf(first, second), result)
    }

    @Test
    fun `posted amount treats blank as zero`() {
        assertEquals(0.0, EntrySorting.postedAmount(entry("2026-01-01", amount = "")), 0.0)
        assertEquals(184.5, EntrySorting.postedAmount(entry("2026-01-01", amount = "184.50")), 0.0)
    }

    @Test
    fun `indicator marks the column that currently sorts`() {
        assertEquals(
            SortDirection.Ascending,
            EntrySorting.indicator(SortOrder.PayeeAscending, SortOrder.PayeeAscending),
        )
        assertEquals(
            SortDirection.Descending,
            EntrySorting.indicator(SortOrder.PayeeAscending, SortOrder.PayeeDescending),
        )
        assertNull(EntrySorting.indicator(SortOrder.PayeeAscending, SortOrder.AccountAscending))
    }

    @Test
    fun `indicator covers every sortable column`() {
        val columns = listOf(
            SortOrder.OldestFirst,
            SortOrder.PayeeAscending,
            SortOrder.AccountAscending,
            SortOrder.LowestAmount,
            SortOrder.CurrencyAscending,
        )
        columns.forEach { column ->
            assertEquals(SortDirection.Ascending, EntrySorting.indicator(column, column))
            val flipped = EntrySorting.toggle(column, column)
            assertEquals(SortDirection.Descending, EntrySorting.indicator(column, flipped))
            assertEquals(column, EntrySorting.toggle(column, flipped))
        }
    }

    @Test
    fun `tapping another column starts ascending`() {
        assertEquals(
            SortOrder.AccountAscending,
            EntrySorting.toggle(SortOrder.AccountAscending, SortOrder.NewestFirst),
        )
    }

    @Test
    fun `date column toggles between newest and oldest`() {
        assertEquals(SortOrder.NewestFirst, EntrySorting.toggle(SortOrder.OldestFirst, SortOrder.OldestFirst))
        assertEquals(SortOrder.OldestFirst, EntrySorting.toggle(SortOrder.OldestFirst, SortOrder.NewestFirst))
    }

    private fun entry(
        date: String,
        amount: String = "0.00",
        account: String = "expenses:unknown",
        payee: String = date,
        currency: String = "NOK",
    ): JournalEntry = JournalEntry(
        date = date,
        payee = payee,
        postings = listOf(Posting(account = account, amount = amount, currency = currency)),
        lineOffset = 0,
    )

    private fun entryWithAccounts(date: String, vararg accounts: String): JournalEntry = JournalEntry(
        date = date,
        payee = date,
        postings = accounts.map { Posting(account = it, amount = "1.00", currency = "NOK") },
        lineOffset = 0,
    )

    private fun entryWithoutPostings(date: String): JournalEntry = JournalEntry(
        date = date,
        payee = "",
        postings = emptyList(),
        lineOffset = 0,
    )
}
