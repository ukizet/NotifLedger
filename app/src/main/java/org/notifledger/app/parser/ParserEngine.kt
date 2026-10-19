package org.notifledger.app.parser

import org.notifledger.app.model.Posting
import org.notifledger.app.model.Transaction
import java.time.LocalDate

/**
 * Extract a money amount from notification text and return a Transaction.
 *
 * No per-bank configuration: prefers the number a currency marker sits next
 * to, skips card masks and dates, falls back to the first parseable number in
 * the body, then the title. The notification title is the payee/description.
 */
object ParserEngine {

    /**
     * Parse notification text into a Transaction.
     * Returns null if no parseable amount can be found.
     *
     * @param title  Notification title (used as payee, and as the amount fallback
     *               when the body has no parseable number)
     * @param text   Notification body text (searched for amount)
     * @param expenseAccount  Account for the expense side
     * @param moneyAccount    Account for the money side (auto-balanced)
     * @param currency        Currency code applied to both postings (blank when unset)
     */
    fun parse(
        title: String,
        text: String,
        expenseAccount: String,
        moneyAccount: String,
        currency: String = "",
    ): Transaction? {
        val amount = findAmount(text) ?: findAmount(title) ?: return null

        return Transaction(
            date = LocalDate.now().toString(),
            payee = title,
            postings = listOf(
                Posting(account = expenseAccount, amount = amount, currency = currency),
                Posting(account = moneyAccount, amount = "", currency = currency),
            ),
            source = org.notifledger.app.model.Source.Notification,
        )
    }

    /**
     * The first amount next to a currency marker, else the first parseable
     * number. Null when every candidate is a card mask, a date, or unparseable.
     */
    private fun findAmount(text: String): String? {
        val candidates = numberRun.findAll(text).mapNotNull { candidate(text, it) }.toList()
        return candidates.firstOrNull { it.isNextToCurrency }?.amount ?: candidates.firstOrNull()?.amount
    }

    private data class Candidate(val amount: String, val isNextToCurrency: Boolean)

    private fun candidate(text: String, match: MatchResult): Candidate? {
        val run = stripLeadingDate(runAfterMask(text, match) ?: return null) ?: return null
        val amount = parseableAmount(run) ?: return null
        return Candidate(amount, isNextToCurrency(text, match))
    }

    /**
     * `*1234 184,50 kr` is one number run, so the mask and the amount must be
     * split. A leftover plain number (`*1234 5678`) is more likely mask groups
     * than an amount: it counts only as a single currency-adjacent group.
     */
    private fun runAfterMask(text: String, match: MatchResult): String? {
        if (!isCardMask(text, match)) return match.value.trim()
        val remainder = match.value.trim()
            .replaceFirst(maskPrefixPattern, "")
            .trimStart(' ', ',', '.')
        if (remainder.isEmpty()) return null
        if (remainder.contains(',') || remainder.contains('.')) return remainder
        if (!isNextToCurrency(text, match) || remainder.any { it.isWhitespace() }) return null
        return remainder
    }

    /** `*1234` is a card mask, not an amount. */
    private fun isCardMask(text: String, match: MatchResult): Boolean =
        match.range.first > 0 && text[match.range.first - 1] == '*'

    /**
     * Drop a leading date: the number regex reads `15.10.2026 184,50` as one
     * run. Null when the run is only a date.
     */
    private fun stripLeadingDate(run: String): String? {
        val withoutDate = run.replaceFirst(datePrefixPattern, "")
        if (withoutDate == run) return run
        return withoutDate.trim().ifEmpty { null }
    }

    /**
     * Normalize a matched number (comma decimals, spaces) and return it only
     * when it actually parses as a Double.
     */
    private fun parseableAmount(matched: String): String? {
        val cleaned = matched.replace(",", ".").replace(" ", "")
        return if (cleaned.toDoubleOrNull() == null) null else cleaned
    }

    private fun isNextToCurrency(text: String, match: MatchResult): Boolean {
        val after = text.substring(match.range.last + 1).trimStart()
        val before = text.substring(0, match.range.first).trimEnd()
        return hasCurrencyAfter(after) || hasCurrencyBefore(before)
    }

    private fun hasCurrencyAfter(text: String): Boolean {
        if (text.startsWith("kr", ignoreCase = true)) return true
        if (currencySymbols.any { text.startsWith(it) }) return true
        val code = text.take(3).uppercase()
        // `USDC` must not read as `USD`.
        return code in currencyCodes && (text.length == 3 || !text[3].isLetter())
    }

    private fun hasCurrencyBefore(text: String): Boolean {
        if (text.endsWith("kr", ignoreCase = true)) return true
        if (currencySymbols.any { text.endsWith(it) }) return true
        val code = text.takeLast(3).uppercase()
        return code in currencyCodes &&
            (text.length == 3 || !text[text.length - 4].isLetter())
    }

    private val numberRun = Regex("""\d[\d\s.,]*""")
    private val maskPrefixPattern = Regex("""^\d{4,}""")
    private val datePrefixPattern = Regex("""^\d{1,2}[./-]\d{1,2}[./-]\d{2,4}\s*""")
    private val currencyCodes = setOf("NOK", "USD", "EUR", "GBP", "SEK", "DKK")
    private val currencySymbols = listOf("€", "$", "£")
}
