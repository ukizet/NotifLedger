package org.notifledger.app.notification

import org.notifledger.app.model.CategorizationRule
import org.notifledger.app.model.Posting
import org.notifledger.app.model.Source
import org.notifledger.app.model.Transaction
import org.notifledger.app.parser.ParserEngine
import java.time.LocalDate

object NotificationHandler {

    private const val DEFAULT_EXPENSE_ACCOUNT = "expenses:unknown"

    fun shouldProcess(packageName: String, allowedPackages: Set<String>): Boolean {
        if (allowedPackages.isEmpty()) return false
        return packageName in allowedPackages
    }

    /**
     * Match [title] against [rules] in order. Returns the first matching rule's account,
     * or [DEFAULT_EXPENSE_ACCOUNT] if no rule matches.
     */
    fun matchCategory(title: String, rules: List<CategorizationRule>): String {
        for (rule in rules) {
            if (rule.match.isNotBlank()) {
                try {
                    if (Regex(rule.match, RegexOption.IGNORE_CASE).containsMatchIn(title)) {
                        return rule.account
                    }
                } catch (_: Exception) {
                    // malformed regex — skip
                }
            }
        }
        return DEFAULT_EXPENSE_ACCOUNT
    }

    fun parseTransaction(
        title: String,
        text: String,
        defaultAccount: String,
        currency: String = "",
        rules: List<CategorizationRule> = emptyList(),
    ): Transaction? {
        val expenseAccount = matchCategory(title, rules)
        return ParserEngine.parse(title, text, expenseAccount, defaultAccount, currency)
    }

    fun createBestEffortTransaction(
        title: String,
        defaultAccount: String,
        currency: String = "",
        rules: List<CategorizationRule> = emptyList(),
        date: String = LocalDate.now().toString(),
    ): Transaction {
        val expenseAccount = matchCategory(title, rules)
        return Transaction(
            date = date,
            payee = title,
            postings = listOf(
                Posting(account = expenseAccount, amount = "", currency = currency),
                Posting(account = defaultAccount, amount = "", currency = currency),
            ),
            source = Source.Notification,
        )
    }

    fun processNotification(
        packageName: String,
        title: String,
        text: String,
        allowedPackages: Set<String>,
        defaultAccount: String,
        currency: String = "",
        rules: List<CategorizationRule> = emptyList(),
    ): Transaction? {
        if (!shouldProcess(packageName, allowedPackages)) return null
        return parseTransaction(title, text, defaultAccount, currency, rules)
            ?: createBestEffortTransaction(title, defaultAccount, currency, rules)
    }
}
