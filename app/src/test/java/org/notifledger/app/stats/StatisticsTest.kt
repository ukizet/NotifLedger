package org.notifledger.app.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.model.Posting
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

class StatisticsTest {

    private val today = LocalDate.of(2026, 10, 18)

    private fun entry(date: String, vararg postings: Posting) =
        JournalEntry(date = date, payee = "Test", postings = postings.toList(), lineOffset = 0)

    private fun posting(account: String, amount: String, currency: String = "NOK") =
        Posting(account = account, amount = amount, currency = currency)

    @Test
    fun `transactionCount includes the cutoff day and excludes earlier or unparseable dates`() {
        val entries = listOf(
            entry("2026-10-12", posting("expenses:groceries", "10.00")),
            entry("2026-10-11", posting("expenses:groceries", "99.00")),
            entry("not-a-date", posting("expenses:groceries", "5.00")),
        )

        val count = Statistics.transactionCount(entries, emptySet(), days = 7, today = today)

        assertEquals(1, count)
    }

    @Test
    fun `future-dated entries are excluded from the range`() {
        val entries = listOf(entry("2026-10-19", posting("expenses:groceries", "10.00")))

        val count = Statistics.transactionCount(entries, emptySet(), days = 7, today = today)
        val result = Statistics.spendingByPeriod(entries, emptySet(), days = 7, today = today, currency = "NOK")

        assertEquals(0, count)
        assertEquals(7, result.size)
    }

    @Test
    fun `spendingByCategory groups by top-level account and sorts descending`() {
        val entries = listOf(
            entry(
                "2026-10-01",
                posting("expenses:groceries", "100.00"),
                posting("assets:bank:checking", "-100.00"),
            ),
            entry(
                "2026-10-02",
                posting("expenses:groceries:snacks", "20.00"),
                posting("assets:bank:checking", "-20.00"),
            ),
            entry(
                "2026-10-03",
                posting("expenses:transport", "30.00"),
                posting("assets:bank:checking", "-30.00"),
            ),
        )

        val result = Statistics.spendingByCategory(entries, emptySet(), days = 30, today = today, currency = "NOK")

        assertEquals(
            listOf(
                CategoryTotal("groceries", BigDecimal("120.00")),
                CategoryTotal("transport", BigDecimal("30.00")),
            ),
            result,
        )
    }

    @Test
    fun `spendingByCategory nets refunds and drops categories at or below zero`() {
        val entries = listOf(
            entry("2026-10-01", posting("expenses:groceries", "100.00")),
            entry("2026-10-02", posting("expenses:groceries", "-40.00")),
            entry("2026-10-03", posting("expenses:transport", "50.00")),
            entry("2026-10-04", posting("expenses:transport", "-50.00")),
        )

        val result = Statistics.spendingByCategory(entries, emptySet(), days = 30, today = today, currency = "NOK")

        assertEquals(listOf(CategoryTotal("groceries", BigDecimal("60.00"))), result)
    }

    @Test
    fun `spendingByCategory keeps expense legs only from account-matching entries`() {
        val entries = listOf(
            entry(
                "2026-10-01",
                posting("expenses:groceries", "50.00"),
                posting("assets:cash", "-50.00"),
            ),
            entry(
                "2026-10-02",
                posting("expenses:transport", "20.00"),
                posting("assets:bank:checking", "-20.00"),
            ),
        )

        val result = Statistics.spendingByCategory(entries, setOf("assets:cash"), days = 30, today = today, currency = "NOK")

        assertEquals(listOf(CategoryTotal("groceries", BigDecimal("50.00"))), result)
    }

    @Test
    fun `multiple accounts match entries touching any of them`() {
        val entries = listOf(
            entry(
                "2026-10-01",
                posting("expenses:groceries", "50.00"),
                posting("assets:cash", "-50.00"),
            ),
            entry(
                "2026-10-02",
                posting("expenses:transport", "20.00"),
                posting("assets:bank:checking", "-20.00"),
            ),
            entry(
                "2026-10-03",
                posting("expenses:kitchen", "5.00"),
                posting("assets:savings", "-5.00"),
            ),
        )

        val result = Statistics.spendingByCategory(
            entries,
            setOf("assets:cash", "assets:bank:checking"),
            days = 30,
            today = today,
            currency = "NOK",
        )

        assertEquals(
            listOf(
                CategoryTotal("groceries", BigDecimal("50.00")),
                CategoryTotal("transport", BigDecimal("20.00")),
            ),
            result,
        )
    }

    @Test
    fun `spendingByCategory counts only the chosen currency`() {
        val entries = listOf(
            entry("2026-10-01", posting("expenses:groceries", "10.00", currency = "NOK")),
            entry("2026-10-02", posting("expenses:transport", "5.00", currency = "EUR")),
            entry("2026-10-03", posting("expenses:food", "7.00", currency = "")),
        )

        val result = Statistics.spendingByCategory(entries, emptySet(), days = 30, today = today, currency = "NOK")

        assertEquals(listOf(CategoryTotal("groceries", BigDecimal("10.00"))), result)
    }

    @Test
    fun `spendingByCategory labels the bare expenses account as expenses`() {
        val entries = listOf(entry("2026-10-01", posting("expenses", "25.00")))

        val result = Statistics.spendingByCategory(entries, emptySet(), days = 30, today = today, currency = "NOK")

        assertEquals(listOf(CategoryTotal("expenses", BigDecimal("25.00"))), result)
    }

    @Test
    fun `dominantCurrency picks the currency with the most expense postings`() {
        val entries = listOf(
            entry(
                "2026-10-01",
                posting("expenses:groceries", "10.00", currency = "NOK"),
                posting("expenses:food", "7.00", currency = "NOK"),
                posting("assets:bank:checking", "-10.00", currency = "EUR"),
                posting("assets:savings", "-7.00", currency = "EUR"),
            ),
        )

        val currency = Statistics.dominantCurrency(entries, emptySet(), days = 30, today = today)

        assertEquals("NOK", currency)
    }

    @Test
    fun `dominantCurrency breaks ties lexicographically`() {
        val entries = listOf(
            entry(
                "2026-10-01",
                posting("expenses:groceries", "10.00", currency = "USD"),
                posting("expenses:transport", "5.00", currency = "EUR"),
            ),
        )

        val currency = Statistics.dominantCurrency(entries, emptySet(), days = 30, today = today)

        assertEquals("EUR", currency)
    }

    @Test
    fun `dominantCurrency is null when no expense posting has a currency`() {
        val entries = listOf(entry("2026-10-01", posting("expenses:groceries", "10.00", currency = "")))

        assertNull(Statistics.dominantCurrency(entries, emptySet(), days = 30, today = today))
    }

    @Test
    fun `spendingByPeriod builds one bucket per day`() {
        val entries = listOf(
            entry("2026-10-12", posting("expenses:groceries", "10.00")),
            entry("2026-10-15", posting("expenses:transport", "5.00")),
        )

        val result = Statistics.spendingByPeriod(entries, emptySet(), days = 7, today = today, currency = "NOK")

        assertEquals(
            listOf("2026-10-12", "2026-10-13", "2026-10-14", "2026-10-15", "2026-10-16", "2026-10-17", "2026-10-18"),
            result.map { it.label },
        )
        assertEquals(
            listOf(
                BigDecimal("10.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal("5.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
            ),
            result.map { it.total },
        )
    }

    @Test
    fun `spendingByPeriod builds ISO week buckets with Monday labels`() {
        val entries = listOf(entry("2026-10-13", posting("expenses:groceries", "10.00")))

        val result = Statistics.spendingByPeriod(entries, emptySet(), days = 60, today = today, currency = "NOK")

        assertEquals(9, result.size)
        assertTrue(result.all { LocalDate.parse(it.label).dayOfWeek == DayOfWeek.MONDAY })
        assertEquals("2026-08-17", result.first().label)
        assertEquals("2026-10-12", result.last().label)
        assertEquals(BigDecimal("10.00"), result.last().total)
    }

    @Test
    fun `spendingByPeriod builds monthly buckets`() {
        val entries = listOf(entry("2026-10-05", posting("expenses:groceries", "10.00")))

        val result = Statistics.spendingByPeriod(entries, emptySet(), days = 400, today = today, currency = "NOK")

        assertEquals(14, result.size)
        assertEquals("2025-09", result.first().label)
        assertEquals("2026-10", result.last().label)
        assertEquals(BigDecimal.ZERO, result.first().total)
        assertEquals(BigDecimal("10.00"), result.last().total)
    }
}
