package org.notifledger.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.notifledger.app.R

@Composable
fun BulkChangeDialog(
    accountSuggestions: List<String>,
    onConfirm: (date: String?, payee: String?, currency: String?, account: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var date by remember { mutableStateOf("") }
    var payee by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }

    val hasChange = date.isNotBlank() || payee.isNotBlank() || currency.isNotBlank() || account.isNotBlank()
    val dateValid = date.isBlank() || runCatching { java.time.LocalDate.parse(date.trim()) }.isSuccess

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bulk_change_title)) },
        text = {
            BulkChangeFields(
                date = date,
                payee = payee,
                currency = currency,
                account = account,
                accountSuggestions = accountSuggestions,
                onDateChange = { date = it },
                onPayeeChange = { payee = it },
                onCurrencyChange = { currency = it },
                onAccountChange = { account = it },
            )
        },
        confirmButton = {
            TextButton(
                enabled = hasChange && dateValid,
                onClick = {
                    onConfirm(
                        date.trim().ifBlank { null },
                        payee.trim().ifBlank { null },
                        currency.trim().ifBlank { null },
                        account.trim().ifBlank { null },
                    )
                },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun BulkChangeFields(
    date: String,
    payee: String,
    currency: String,
    account: String,
    accountSuggestions: List<String>,
    onDateChange: (String) -> Unit,
    onPayeeChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onAccountChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        DateField(
            value = date,
            onValueChange = onDateChange,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.date),
        )
        OutlinedTextField(
            value = payee,
            onValueChange = onPayeeChange,
            label = { Text(stringResource(R.string.payee)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = currency,
            onValueChange = onCurrencyChange,
            label = { Text(stringResource(R.string.currency)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        AccountField(
            value = account,
            onValueChange = onAccountChange,
            suggestions = accountSuggestions,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.account),
        )
        Text(
            text = stringResource(R.string.bulk_change_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
