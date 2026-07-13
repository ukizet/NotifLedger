package org.notifledger.app.parser

import org.notifledger.app.model.AmountExtractor
import org.notifledger.app.model.ParserRule
import org.notifledger.app.model.PayeeExtractor
import org.notifledger.app.model.Posting
import org.notifledger.app.model.Transaction
import java.time.LocalDate

/**
 * Extracts transaction data from notification text using simple, obvious rules.
 *
 * This does not use regex. You just tell it what to look for:
 * - What text identifies the notification as a transaction ("Betalt", "kjøpt", etc.)
 * - Where is the amount? (first number, last number, after "kr")
 * - Where is the payee? (text before the amount, or the whole thing)
 */
object ParserEngine {

    /**
     * Try to parse a notification. Returns a Transaction if the rule matches, null otherwise.
     */
    fun parse(
        title: String,
        text: String,
        rule: ParserRule,
        expenseAccount: String,
        moneyAccount: String,
    ): Transaction? {
        val combined = "$title $text".trim()

        // Rule must contain the identifying text
        if (!combined.contains(rule.containsText, ignoreCase = true)) return null

        // Find the amount
        val amount = extractAmount(combined, rule) ?: return null
        // Parse it to make sure it's valid
        val cleanAmount = amount.replace(",", ".").replace(" ", "")
        if (cleanAmount.toDoubleOrNull() == null) return null

        // Find the payee
        val payee = extractPayee(combined, amount, rule) ?: "Unknown"
        val cleanedPayee = payee.trim().replace(Regex("\\s+"), " ")

        return Transaction(
            date = LocalDate.now().toString(),
            payee = cleanedPayee,
            postings = listOf(
                Posting(account = expenseAccount, amount = cleanAmount, currency = rule.currency),
                Posting(account = moneyAccount, amount = "", currency = rule.currency),
            ),
            source = org.notifledger.app.model.Source.Notification,
        )
    }

    private fun extractAmount(text: String, rule: ParserRule): String? {
        return when (rule.amountExtractor) {
            AmountExtractor.FirstNumber -> findNumber(text, fromEnd = false)
            AmountExtractor.LastNumber -> findNumber(text, fromEnd = true)
            AmountExtractor.AfterText -> {
                val idx = text.indexOf(rule.amountPrefix, ignoreCase = true)
                if (idx == -1) return null
                findNumber(text.substring(idx + rule.amountPrefix.length), fromEnd = false)
            }
        }
    }

    private fun extractPayee(text: String, amount: String, rule: ParserRule): String? {
        return when (rule.payeeExtractor) {
            PayeeExtractor.BeforeAmount -> {
                val idx = text.indexOf(amount)
                if (idx == -1) null else text.substring(0, idx).trim().ifBlank { null }
            }
            PayeeExtractor.WholeText -> {
                text.replace(amount, "").trim().ifBlank { null }
            }
            PayeeExtractor.AfterText -> {
                val idx = text.indexOf(rule.amountPrefix, ignoreCase = true)
                if (idx == -1) return null
                text.substring(idx + rule.amountPrefix.length).trim().ifBlank { null }
            }
        }
    }

    /**
     * Find the first or last number in a string.
     * Handles: "1234", "1 234", "1234,56", "1234.56"
     */
    private fun findNumber(text: String, fromEnd: Boolean): String? {
        val numberPattern = Regex("""\d[\d\s.,]*""")
        val matches = numberPattern.findAll(text).toList()
        if (matches.isEmpty()) return null
        val match = if (fromEnd) matches.last() else matches.first()
        return match.value
    }
}
