package org.notifledger.app.model

enum class FilterMode { INCLUDE, EXCLUDE, OFF }

/**
 * A transaction filter: [label] is what the chip shows; [account] is the exact
 * account name matched.
 *
 * INCLUDE keeps entries whose postings touch [account] (union across include
 * filters). EXCLUDE hides entries whose postings touch [account]. OFF is ignored.
 */
data class TransactionFilter(
    val label: String,
    val account: String,
    val mode: FilterMode = FilterMode.INCLUDE,
)
