package org.notifledger.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import org.notifledger.app.R
import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.model.Transaction
import org.notifledger.app.ui.components.BulkChangeDialog
import org.notifledger.app.ui.components.SimulateNotificationDialog
import org.notifledger.app.ui.components.TransactionForm

@Composable
private fun EditTransactionDialog(
    entry: JournalEntry,
    payeeSuggestions: List<String>,
    accountSuggestions: List<String>,
    defaultCurrency: String,
    onSave: (Transaction) -> Unit,
    onDismiss: () -> Unit,
) {
    val tx = Transaction(
        date = entry.date,
        payee = entry.payee,
        postings = entry.postings,
        note = entry.note,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_transaction)) },
        text = {
            TransactionForm(
                initial = tx,
                payeeSuggestions = payeeSuggestions,
                accountSuggestions = accountSuggestions,
                defaultCurrency = defaultCurrency,
                onSave = onSave,
                onCancel = onDismiss,
            )
        },
        confirmButton = {},
    )
}

@Composable
internal fun MainDialogs(
    viewModel: MainViewModel,
    entries: List<JournalEntry>,
    editingIndex: Int,
    showBulkChange: Boolean,
    showSimulateDialog: Boolean,
    selectedOffsets: Set<Int>,
    onClearSelection: () -> Unit,
    onDismissBulkChange: () -> Unit,
    onDismissSimulate: () -> Unit,
    onDismissEdit: () -> Unit,
) {
    val payeeSuggestions by viewModel.existingPayees.collectAsState()
    val accountSuggestions by viewModel.existingAccounts.collectAsState()
    val defaultCurrency by viewModel.defaultCurrency.collectAsState()

    if (showBulkChange) {
        BulkChangeDialog(
            accountSuggestions = accountSuggestions,
            onConfirm = { date, payee, currency, account ->
                viewModel.changeTransactions(selectedOffsets, date, payee, currency, account)
                onClearSelection()
                onDismissBulkChange()
            },
            onDismiss = onDismissBulkChange,
        )
    }

    if (showSimulateDialog) {
        SimulateNotificationDialog(
            viewModel = viewModel,
            onDismiss = {
                onDismissSimulate()
                viewModel.reloadEntries()
            },
        )
    }

    if (editingIndex >= 0 && editingIndex < entries.size) {
        val entry = entries[editingIndex]
        EditTransactionDialog(
            entry = entry,
            payeeSuggestions = payeeSuggestions,
            accountSuggestions = accountSuggestions,
            defaultCurrency = defaultCurrency,
            onSave = { updated ->
                viewModel.editTransaction(entry.lineOffset, updated)
                onDismissEdit()
            },
            onDismiss = onDismissEdit,
        )
    }
}
