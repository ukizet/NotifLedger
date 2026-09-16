package org.notifledger.app.notification

import org.notifledger.app.model.CategorizationRule
import org.notifledger.app.model.Posting
import org.notifledger.app.model.Source
import org.notifledger.app.model.Transaction
import org.notifledger.app.parser.ParserEngine
import java.time.LocalDate

object NotificationHandler {

    private const val EXPENSE_ACCOUNT = "expenses:unknown"

    /** How old a connected timestamp can be before we flag the listener as needing attention. */
    private const val LISTENER_HEARTBEAT_THRESHOLD_MS = 24L * 60 * 60 * 1000 // 24 hours

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
        rules: List<CategorizationRule> = emptyList(),
    ): Transaction? {
        val expenseAccount = matchCategory(title, rules)
        return ParserEngine.parse(title, text, expenseAccount, defaultAccount)
    }

    fun createBestEffortTransaction(
        title: String,
        defaultAccount: String,
        rules: List<CategorizationRule> = emptyList(),
        date: String = LocalDate.now().toString(),
    ): Transaction {
        val expenseAccount = matchCategory(title, rules)
        return Transaction(
            date = date,
            payee = title,
            postings = listOf(
                Posting(account = expenseAccount, amount = "", currency = ""),
                Posting(account = defaultAccount, amount = "", currency = ""),
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
        rules: List<CategorizationRule> = emptyList(),
    ): Transaction? {
        if (!shouldProcess(packageName, allowedPackages)) return null
        return parseTransaction(title, text, defaultAccount, rules)
            ?: createBestEffortTransaction(title, defaultAccount, rules)
    }

    /**
     * Returns true when the permission is granted and a previously recorded heartbeat is
     * older than the threshold. A listener that has never connected is not flagged. This
     * catches the case where the OS has silently unbound the service while leaving the
     * permission flag on.
     */
    fun needsListenerAttention(
        isPermissionGranted: Boolean,
        lastConnectedAt: Long?,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!isPermissionGranted) return false
        val connectedAt = lastConnectedAt ?: return false
        return (now - connectedAt) > LISTENER_HEARTBEAT_THRESHOLD_MS
    }
}
