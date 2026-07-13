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
     * @param title  Notification title (used as payee)
     * @param text   Notification body text (searched for amount)
     * @param expenseAccount  Account for the expense side
     * @param moneyAccount    Account for the money side (auto-balanced)
     */
    fun parse(
        title: String,
        text: String,
        expenseAccount: String,
        moneyAccount: String,
    ): Transaction? {
        val combined = "$title $text".trim()
        val amount = findNumber(combined) ?: return null
        val cleanAmount = amount.replace(",", ".").replace(" ", "")
        if (cleanAmount.toDoubleOrNull() == null) return null

        return Transaction(
            date = LocalDate.now().toString(),
            payee = title,
            postings = listOf(
                Posting(account = expenseAccount, amount = cleanAmount, currency = ""),
                Posting(account = moneyAccount, amount = "", currency = ""),
            ),
            source = org.notifledger.app.model.Source.Notification,
        )
    }

    /**
     * Find the first number in a string.
     * Handles: "1234", "1 234", "1234,56", "1234.56"
     */
    private fun findNumber(text: String): String? {
        val numberPattern = Regex("""\d[\d\s.,]*""")
        return numberPattern.find(text)?.value
    }
}
