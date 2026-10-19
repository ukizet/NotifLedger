package org.notifledger.app.ui

import org.notifledger.app.journal.JournalEntry

/** Distinct sorted payees from parsed journal entries (autocomplete). */
fun List<JournalEntry>.payeeSuggestions(): List<String> =
    map { it.payee }.filter { it.isNotBlank() }.distinct().sorted()

/** Distinct sorted accounts from parsed journal entries (autocomplete). */
fun List<JournalEntry>.accountSuggestions(): List<String> =
    flatMap { entry -> entry.postings.map { it.account } }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()
