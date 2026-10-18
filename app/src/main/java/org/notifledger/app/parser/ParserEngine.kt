package org.notifledger.app.parser

import org.notifledger.app.model.Posting
import org.notifledger.app.model.Transaction
import java.time.LocalDate

/**
 * Find the first number in notification text and return a Transaction.
 *
 * No rule configuration needed — just finds a number and uses the
 * notification title as the payee/description.
 */
object ParserEngine {

    /**
     * Parse notification text into a Transaction.
     * Returns null if no number can be found.
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
        val amount = parseableAmount(findNumber(text)) ?: parseableAmount(findNumber(title)) ?: return null

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
     * Normalize a matched number (comma decimals, spaces) and return it only
     * when it actually parses as a Double.
     */
    private fun parseableAmount(matched: String?): String? {
        val cleaned = matched?.replace(",", ".")?.replace(" ", "") ?: return null
        return if (cleaned.toDoubleOrNull() == null) null else cleaned
    }

    /**
     * Find the first number in a string.
     * Handles: "1234", "1 234", "1234,56", "1234.56"
     */
    private fun findNumber(text: String): String? {
        return numberPattern.find(text)?.value
    }

    private val numberPattern = Regex("""\d[\d\s.,]*""")
}
