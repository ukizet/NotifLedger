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
        val candidates = numberRun.findAll(text).flatMap { candidates(text, it) }.toList()
        return candidates.firstOrNull { it.isNextToCurrency }?.amount ?: candidates.firstOrNull()?.amount
    }

    private data class Candidate(val amount: String, val isNextToCurrency: Boolean)

    /** One number inside a regex match, with the offsets it occupies in the text. */
    private data class NumberGroup(val text: String, val start: Int, val end: Int)

    private fun candidates(text: String, match: MatchResult): List<Candidate> {
        if (isCardMask(text, match)) return maskedCandidates(text, match)
        val numbers = numberGroups(match).filter { stripLeadingDate(it.text) != null }
        if (numbers.isEmpty()) return emptyList()
        if (isThousandsGrouping(numbers.map { it.text })) return listOfNotNull(joinedCandidate(text, numbers))
        return numbers.mapNotNull { group -> candidate(text, group.text, group.start, group.end) }
    }

    private fun numberGroups(match: MatchResult): List<NumberGroup> =
        groupPattern.findAll(match.value).map { group ->
            NumberGroup(
                text = group.value,
                start = match.range.first + group.range.first,
                end = match.range.first + group.range.last,
            )
        }.toList()

    /** The amount the given groups spell out together (`1 234,56`). */
    private fun joinedCandidate(text: String, numbers: List<NumberGroup>): Candidate? =
        candidate(
            text,
            numbers.joinToString(" ") { it.text },
            numbers.first().start,
            numbers.last().end,
        )

    /**
     * `1 234 567,89` is one number: every group but the last is a 1-3 digit
     * thousands block. `1234 184,50` is not — that is a mask or reference
     * number followed by the amount, so its groups stay separate numbers.
     */
    private fun isThousandsGrouping(groups: List<String>): Boolean {
        val blocks = groups.dropLast(1)
        if (blocks.isEmpty()) return false
        return blocks.withIndex().all { (index, block) ->
            block.all { it.isDigit() } &&
                (if (index == 0) block.length <= 3 else block.length == 3)
        }
    }

    /**
     * `*1234 184,50` is one number run, so the mask and the amount must be
     * split. A leftover plain number (`*1234 5678`) is more likely mask groups
     * than an amount: it counts only as a single currency-adjacent group.
     */
    private fun maskedCandidates(text: String, match: MatchResult): List<Candidate> {
        val remainder = match.value.trim()
            .replaceFirst(maskPrefixPattern, "")
            .trimStart(' ', ',', '.')
        val leftover = groupPattern.findAll(remainder).map { it.value }.toList()
        if (leftover.isEmpty()) return emptyList()
        // `*1234 5678 9012 kr` is mask groups; `*1234 12 500,00 kr` is an amount.
        if (leftover.size > 1 && !isThousandsGrouping(leftover)) return emptyList()
        val amount = leftover.joinToString(" ")
        val hasDecimalMark = amount.contains(',') || amount.contains('.')
        if (!hasDecimalMark && !isNextToCurrency(text, match.range.first, match.range.last)) return emptyList()
        return listOfNotNull(candidate(text, amount, match.range.first, match.range.last))
    }

    private fun candidate(text: String, raw: String, start: Int, end: Int): Candidate? {
        val run = stripLeadingDate(raw) ?: return null
        val amount = parseableAmount(run) ?: return null
        return Candidate(amount, isNextToCurrency(text, start, end))
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
     * Normalize a matched number and return it only when it is a single
     * readable amount. The last '.' or ',' is the decimal mark, so both
     * `1,234.56` and Norwegian `1.234,56` read as 1234.56 while junk like
     * `12.3.4` or `12.3,4` stays unparseable.
     */
    private fun parseableAmount(matched: String): String? {
        val cleaned = matched.filterNot { it.isWhitespace() }.trimEnd('.', ',')
        if (cleaned.isEmpty()) return null
        val decimalMark = cleaned.indexOfLast { it == '.' || it == ',' }
        if (decimalMark < 0) return cleaned.takeIf { it.all { char -> char.isDigit() } }
        if (cleaned.count { it == cleaned[decimalMark] } > 1) return null
        val integerPart = cleaned.substring(0, decimalMark)
        if (!isReadableIntegerPart(integerPart)) return null
        val digits = cleaned.filter { it.isDigit() }
        val fractionLength = cleaned.length - decimalMark - 1
        if (fractionLength == 0) return digits
        return (digits.dropLast(fractionLength) + "." + digits.takeLast(fractionLength))
            .takeIf { it.toDoubleOrNull() != null }
    }

    /**
     * Digits, or marks separating 3-digit thousands blocks (`1.234.567`).
     * `12.3` is neither, so the mark in front of it is not a decimal mark.
     */
    private fun isReadableIntegerPart(integerPart: String): Boolean {
        val blocks = integerPart.split('.', ',')
        if (blocks.size == 1) return blocks[0].isNotEmpty() && blocks[0].all { it.isDigit() }
        return blocks.withIndex().all { (index, block) ->
            block.all { it.isDigit() } &&
                (if (index == 0) block.length in 1..3 else block.length == 3)
        }
    }

    private fun isNextToCurrency(text: String, start: Int, end: Int): Boolean {
        val after = text.substring(end + 1).trimStart()
        val before = text.substring(0, start).trimEnd()
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
    private val groupPattern = Regex("""\S+""")
    private val maskPrefixPattern = Regex("""^\d{4,}""")
    private val datePrefixPattern = Regex("""^\d{1,2}[./-]\d{1,2}[./-]\d{2,4}\s*""")
    private val currencyCodes = setOf("NOK", "USD", "EUR", "GBP", "SEK", "DKK")
    private val currencySymbols = listOf("€", "$", "£")
}
