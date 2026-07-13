package org.notifledger.app.model

/**
 * A single parsed transaction, ready to be written as an hledger journal entry.
 *
 * Both notification-triggered and manual-entry paths produce this same type.
 * The journal writer doesn't care which path produced it.
 *
 * Postings with an empty amount string are auto-balanced by the journal writer.
 */
data class Transaction(
    val date: String,               // YYYY-MM-DD
    val payee: String,
    val postings: List<Posting>,    // at least 2 postings; empty amount = auto-balance
    val note: String = "",          // optional comment appended to the entry
    val source: Source = Source.Notification,
)

/**
 * A single posting line in a journal transaction.
 *
 * @param account  The account name, e.g. "expenses:groceries"
 * @param amount   The amount string, e.g. "184.50" or "" for auto-balance
 * @param currency The currency code, e.g. "NOK"
 */
data class Posting(
    val account: String,
    val amount: String,    // empty string means auto-balance
    val currency: String,
)

enum class Source { Notification, Manual }
