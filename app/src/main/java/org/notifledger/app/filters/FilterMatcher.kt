package org.notifledger.app.filters

import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.model.FilterMode
import org.notifledger.app.model.TransactionFilter

object FilterMatcher {

    fun includeAccounts(filters: List<TransactionFilter>): Set<String> =
        filters.filter { it.mode == FilterMode.INCLUDE }.map { it.account }.toSet()

    fun excludeAccounts(filters: List<TransactionFilter>): Set<String> =
        filters.filter { it.mode == FilterMode.EXCLUDE }.map { it.account }.toSet()

    fun matches(entry: JournalEntry, includeAccounts: Set<String>, excludeAccounts: Set<String>): Boolean =
        (includeAccounts.isEmpty() || entry.postings.any { it.account in includeAccounts }) &&
            entry.postings.none { it.account in excludeAccounts }
}
