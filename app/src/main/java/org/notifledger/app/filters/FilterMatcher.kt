package org.notifledger.app.filters

import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.model.TransactionFilter

object FilterMatcher {

    fun activeAccounts(filters: List<TransactionFilter>): Set<String> =
        filters.filter { it.isActive }.map { it.account }.toSet()

    fun matches(entry: JournalEntry, activeAccounts: Set<String>): Boolean =
        activeAccounts.isEmpty() || entry.postings.any { it.account in activeAccounts }
}
