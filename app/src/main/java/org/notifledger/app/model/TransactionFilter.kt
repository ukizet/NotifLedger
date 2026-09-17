package org.notifledger.app.model

/**
 * A transaction filter: shows entries whose postings touch [account].
 *
 * [label] is what the chip shows; [account] is the exact account name matched.
 */
data class TransactionFilter(
    val label: String,
    val account: String,
    val isActive: Boolean = true,
)
