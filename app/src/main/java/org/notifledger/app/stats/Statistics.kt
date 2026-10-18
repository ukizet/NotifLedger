package org.notifledger.app.stats

import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.model.Posting
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class CategoryTotal(
    val label: String,
    val total: BigDecimal,
)

data class PeriodTotal(
    val label: String,
    val total: BigDecimal,
)

object Statistics {

    fun transactionCount(
        entries: List<JournalEntry>,
        accounts: Set<String>,
        days: Int,
        today: LocalDate = LocalDate.now(),
    ): Int = matchingEntries(entries, accounts, days, today).size

    fun dominantCurrency(
        entries: List<JournalEntry>,
        accounts: Set<String>,
        days: Int,
        today: LocalDate = LocalDate.now(),
    ): String? {
        val currencies = matchingEntries(entries, accounts, days, today)
            .flatMap { expenseLegs(it, accounts, currency = null) }
            .map { it.currency }
            .filter { it.isNotBlank() }
        return currencies.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.key
    }

    fun spendingByCategory(
        entries: List<JournalEntry>,
        accounts: Set<String>,
        days: Int,
        today: LocalDate = LocalDate.now(),
        currency: String,
    ): List<CategoryTotal> {
        val totals = mutableMapOf<String, BigDecimal>()
        for (entry in matchingEntries(entries, accounts, days, today)) {
            for (posting in expenseLegs(entry, accounts, currency)) {
                val category = category(posting.account)
                totals[category] = (totals[category] ?: BigDecimal.ZERO) + posting.amount.toBigDecimal()
            }
        }
        return totals
            .map { CategoryTotal(it.key, it.value) }
            .filter { it.total > BigDecimal.ZERO }
            .sortedByDescending { it.total }
    }

    fun spendingByPeriod(
        entries: List<JournalEntry>,
        accounts: Set<String>,
        days: Int,
        today: LocalDate = LocalDate.now(),
        currency: String,
    ): List<PeriodTotal> {
        val buckets = periodBuckets(days, today)
        for (entry in matchingEntries(entries, accounts, days, today)) {
            val date = parsedDate(entry.date) ?: continue
            val label = periodLabel(days, date)
            val total = expenseLegs(entry, accounts, currency).sumOf { it.amount.toBigDecimal() }
            buckets[label] = (buckets[label] ?: BigDecimal.ZERO) + total
        }
        return buckets.map { PeriodTotal(it.key, it.value) }
    }

    private fun matchingEntries(
        entries: List<JournalEntry>,
        accounts: Set<String>,
        days: Int,
        today: LocalDate,
    ): List<JournalEntry> {
        val cutoff = today.minusDays(days - 1L)
        return entries.filter { inRange(it.date, cutoff, today) && matchesAccounts(it, accounts) }
    }

    private fun inRange(date: String, cutoff: LocalDate, today: LocalDate): Boolean {
        val parsed = parsedDate(date) ?: return false
        return !parsed.isBefore(cutoff) && !parsed.isAfter(today)
    }

    private fun parsedDate(date: String): LocalDate? =
        runCatching { LocalDate.parse(date) }.getOrNull()

    private fun matchesAccounts(entry: JournalEntry, accounts: Set<String>): Boolean =
        accounts.isEmpty() || entry.postings.any { it.account in accounts }

    private fun expenseLegs(entry: JournalEntry, accounts: Set<String>, currency: String?): List<Posting> {
        val fundedBySelection = entry.postings.any { it.account in accounts && !isExpense(it.account) }
        return entry.postings.filter {
            isExpense(it.account) &&
                (accounts.isEmpty() || it.account in accounts || fundedBySelection) &&
                (currency == null || it.currency == currency) &&
                it.amount.toBigDecimalOrNull() != null
        }
    }

    private fun isExpense(account: String): Boolean =
        account == "expenses" || account.startsWith("expenses:")

    private fun category(account: String): String =
        account.removePrefix("expenses:").substringBefore(":")

    private fun periodBuckets(days: Int, today: LocalDate): MutableMap<String, BigDecimal> {
        val cutoff = today.minusDays(days - 1L)
        val labels = when {
            days <= 31 -> dailyLabels(cutoff, today)
            days <= 180 -> weeklyLabels(cutoff, today)
            else -> monthlyLabels(cutoff, today)
        }
        return labels.associateWithTo(linkedMapOf<String, BigDecimal>()) { BigDecimal.ZERO }
    }

    private fun dailyLabels(cutoff: LocalDate, today: LocalDate): List<String> =
        (0..ChronoUnit.DAYS.between(cutoff, today)).map { cutoff.plusDays(it).toString() }

    private fun weeklyLabels(cutoff: LocalDate, today: LocalDate): List<String> {
        val firstMonday = mondayOf(cutoff)
        val weekCount = ChronoUnit.WEEKS.between(firstMonday, mondayOf(today))
        return (0..weekCount).map { firstMonday.plusWeeks(it).toString() }
    }

    private fun monthlyLabels(cutoff: LocalDate, today: LocalDate): List<String> {
        val firstMonth = YearMonth.from(cutoff)
        val monthCount = ChronoUnit.MONTHS.between(firstMonth, YearMonth.from(today))
        return (0..monthCount).map { firstMonth.plusMonths(it).toString() }
    }

    private fun periodLabel(days: Int, date: LocalDate): String = when {
        days <= 31 -> date.toString()
        days <= 180 -> mondayOf(date).toString()
        else -> YearMonth.from(date).toString()
    }

    private fun mondayOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
