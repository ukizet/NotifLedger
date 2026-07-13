package org.notifledger.app.model

/**
 * A categorization rule: a payee pattern maps to an expense account.
 *
 * Rules are checked in order; the first match wins.
 * A rule with no match pattern is the default/fallback.
 */
data class CategorizationRule(
    val match: String = "",     // regex matched against payee; empty = default/fallback
    val account: String,        // e.g. "expenses:groceries"
)
