package org.notifledger.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.model.Posting
import org.notifledger.app.model.SortOrder
import org.notifledger.app.model.TransactionFilter

class EntryListTest {

    @Test
    fun `newest first sorts by date descending`() {
        val entries = listOf(entry("2026-01-01"), entry("2026-03-01"), entry("2026-02-01"))
        val result = EntryList.visible(entries, emptyList(), SortOrder.NewestFirst, 20)
        assertEquals(listOf("2026-03-01", "2026-02-01", "2026-01-01"), result.map { it.date })
    }

    @Test
    fun `oldest first sorts by date ascending`() {
        val entries = listOf(entry("2026-01-01"), entry("2026-03-01"), entry("2026-02-01"))
        val result = EntryList.visible(entries, emptyList(), SortOrder.OldestFirst, 20)
        assertEquals(listOf("2026-01-01", "2026-02-01", "2026-03-01"), result.map { it.date })
    }

    @Test
    fun `highest amount sorts by first posting amount descending with blank as zero`() {
        val entries = listOf(
            entry("2026-01-01", amount = "12.50"),
            entry("2026-01-02", amount = "500.00"),
            entry("2026-01-03", amount = ""),
            entry("2026-01-04", amount = "99.99"),
        )
        val result = EntryList.visible(entries, emptyList(), SortOrder.HighestAmount, 20)
        assertEquals(listOf("500.00", "99.99", "12.50", ""), result.map { it.postings.first().amount })
    }

    @Test
    fun `lowest amount sorts by first posting amount ascending with blank as zero`() {
        val entries = listOf(
            entry("2026-01-01", amount = "12.50"),
            entry("2026-01-02", amount = "500.00"),
            entry("2026-01-03", amount = ""),
            entry("2026-01-04", amount = "99.99"),
        )
        val result = EntryList.visible(entries, emptyList(), SortOrder.LowestAmount, 20)
        assertEquals(listOf("", "12.50", "99.99", "500.00"), result.map { it.postings.first().amount })
    }

    @Test
    fun `limit zero still returns one entry`() {
        val entries = listOf(entry("2026-01-01"), entry("2026-02-01"), entry("2026-03-01"))
        val result = EntryList.visible(entries, emptyList(), SortOrder.NewestFirst, 0)
        assertEquals(1, result.size)
        assertEquals("2026-03-01", result[0].date)
    }

    @Test
    fun `negative limit still returns one entry`() {
        val entries = listOf(entry("2026-01-01"), entry("2026-02-01"), entry("2026-03-01"))
        val result = EntryList.visible(entries, emptyList(), SortOrder.NewestFirst, -5)
        assertEquals(1, result.size)
        assertEquals("2026-03-01", result[0].date)
    }

    @Test
    fun `limit larger than list returns all entries`() {
        val entries = listOf(entry("2026-01-01"), entry("2026-02-01"), entry("2026-03-01"))
        val result = EntryList.visible(entries, emptyList(), SortOrder.NewestFirst, 100)
        assertEquals(entries.reversed(), result)
    }

    @Test
    fun `no filters returns all entries`() {
        val entries = listOf(
            entryWithAccounts("2026-01-01", "expenses:groceries", "assets:bank:checking"),
            entryWithAccounts("2026-01-02", "expenses:travel", "assets:bank:checking"),
        )
        val result = EntryList.visible(entries, emptyList(), SortOrder.NewestFirst, 20)
        assertEquals(entries.reversed(), result)
    }

    @Test
    fun `one active filter keeps entries whose posting account matches exactly`() {
        val groceriesFirst = entryWithAccounts("2026-01-01", "expenses:groceries", "assets:bank:checking")
        val groceriesSecond = entryWithAccounts("2026-01-02", "assets:bank:checking", "expenses:groceries")
        val wrongCase = entryWithAccounts("2026-01-03", "Expenses:groceries", "assets:bank:checking")
        val other = entryWithAccounts("2026-01-04", "expenses:travel", "assets:bank:checking")
        val filter = TransactionFilter(label = "Groceries", account = "expenses:groceries")

        val result = EntryList.visible(
            listOf(groceriesFirst, groceriesSecond, wrongCase, other),
            listOf(filter),
            SortOrder.NewestFirst,
            20,
        )

        assertEquals(listOf(groceriesSecond, groceriesFirst), result)
    }

    @Test
    fun `inactive filter does not filter`() {
        val entries = listOf(
            entryWithAccounts("2026-01-01", "expenses:groceries", "assets:bank:checking"),
            entryWithAccounts("2026-01-02", "expenses:travel", "assets:bank:checking"),
        )
        val filter = TransactionFilter(label = "Groceries", account = "expenses:groceries", isActive = false)

        val result = EntryList.visible(entries, listOf(filter), SortOrder.NewestFirst, 20)

        assertEquals(entries.reversed(), result)
    }

    @Test
    fun `multiple active filters keep the union`() {
        val groceries = entryWithAccounts("2026-01-01", "expenses:groceries", "assets:bank:checking")
        val travel = entryWithAccounts("2026-01-02", "expenses:travel", "assets:bank:checking")
        val other = entryWithAccounts("2026-01-03", "expenses:other", "assets:bank:checking")
        val filters = listOf(
            TransactionFilter(label = "Groceries", account = "expenses:groceries"),
            TransactionFilter(label = "Travel", account = "expenses:travel"),
        )

        val result = EntryList.visible(listOf(groceries, travel, other), filters, SortOrder.NewestFirst, 20)

        assertEquals(listOf(travel, groceries), result)
    }

    @Test
    fun `filter sort and limit combine`() {
        val janGroceries = entry("2026-01-01", amount = "10.00", account = "expenses:groceries")
        val febTravel = entry("2026-02-01", amount = "20.00", account = "expenses:travel")
        val marGroceries = entry("2026-03-01", amount = "30.00", account = "expenses:groceries")
        val aprOther = entry("2026-04-01", amount = "40.00", account = "expenses:other")
        val filters = listOf(
            TransactionFilter(label = "Groceries", account = "expenses:groceries"),
            TransactionFilter(label = "Travel", account = "expenses:travel"),
        )

        val result = EntryList.visible(
            listOf(marGroceries, aprOther, janGroceries, febTravel),
            filters,
            SortOrder.OldestFirst,
            2,
        )

        assertEquals(listOf(janGroceries, febTravel), result)
    }

    @Test
    fun `equal dates preserve input order`() {
        val first = entry("2026-01-01", payee = "First")
        val second = entry("2026-01-01", payee = "Second")
        val third = entry("2026-01-01", payee = "Third")

        val result = EntryList.visible(listOf(first, second, third), emptyList(), SortOrder.NewestFirst, 20)

        assertEquals(listOf(first, second, third), result)
    }

    private fun entry(
        date: String,
        amount: String = "0.00",
        account: String = "expenses:unknown",
        payee: String = date,
    ): JournalEntry = JournalEntry(
        date = date,
        payee = payee,
        postings = listOf(Posting(account = account, amount = amount, currency = "NOK")),
        lineOffset = 0,
    )

    private fun entryWithAccounts(date: String, vararg accounts: String): JournalEntry = JournalEntry(
        date = date,
        payee = date,
        postings = accounts.map { Posting(account = it, amount = "1.00", currency = "NOK") },
        lineOffset = 0,
    )
}
