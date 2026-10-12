package org.notifledger.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.notifledger.app.R
import org.notifledger.app.model.Posting
import org.notifledger.app.model.Transaction
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/**
 * Reusable form for entering/editing a transaction.
 * Used by: QuickAdd screen, Edit dialog from main list.
 *
 * Features a dynamic list of postings. Currency is shared across all postings.
 *
 * @param payeeSuggestions list of existing payee names for autocomplete dropdown.
 * @param accountSuggestions list of existing account names for autocomplete dropdown.
 * @param defaultCurrency default currency to use when no currency is set on the initial.
 * @param defaultPaymentAccount quick-add mode: the money-side leg is editable in Show
 *   details and prefilled from Settings. When null all postings are edited directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionForm(
    initial: Transaction,
    onSave: (Transaction) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    payeeSuggestions: List<String> = emptyList(),
    accountSuggestions: List<String> = emptyList(),
    defaultCurrency: String = "NOK",
    defaultPaymentAccount: String? = null,
) {
    var payee by remember { mutableStateOf(initial.payee) }
    var date by remember { mutableStateOf(initial.date) }
    var note by remember { mutableStateOf(initial.note) }
    var showDetails by remember { mutableStateOf(false) }
    var currency by remember(defaultCurrency) {
        mutableStateOf(
            initial.postings.firstOrNull()?.currency?.takeIf { it.isNotBlank() } ?: defaultCurrency,
        )
    }
    val postings = remember {
        mutableStateListOf<Posting>().apply {
            if (initial.postings.isNotEmpty()) {
                addAll(initial.postings)
            } else {
                add(Posting(account = "", amount = "", currency = currency))
            }
        }
    }
    var paymentAuto by remember { mutableStateOf(true) }
    var paymentManual by remember { mutableStateOf("") }
    var paymentAccountValue by remember(defaultPaymentAccount) {
        mutableStateOf(defaultPaymentAccount ?: "")
    }

    val amountTotal = postings
        .filter { it.amount.isNotBlank() }
        .sumOf { it.amount.replace(",", ".").toDoubleOrNull() ?: 0.0 }
    val formattedAuto = String.format(Locale.ROOT, "%.2f", -amountTotal)
    val paymentAmountText = if (paymentAuto) formattedAuto else paymentManual
    val paymentManualValue = paymentManual.replace(",", ".").toDoubleOrNull()
    val paymentUnbalanced = !paymentAuto &&
        (paymentManualValue == null || abs(amountTotal + paymentManualValue) > 1e-9)
    val dateValid = runCatching { LocalDate.parse(date) }.isSuccess
    val canSave = dateValid && (
        defaultPaymentAccount == null || (
            paymentAccountValue.isNotBlank() &&
                postings.any { it.amount.isNotBlank() } &&
                !paymentUnbalanced
            )
        )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        DateField(
            value = date,
            onValueChange = { date = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.form_postings_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(8.dp))

        postings.forEachIndexed { index, posting ->
            Column(modifier = Modifier.fillMaxWidth()) {
                AccountField(
                    value = posting.account,
                    onValueChange = { newAccount -> postings[index] = posting.copy(account = newAccount) },
                    suggestions = accountSuggestions,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = posting.amount,
                        onValueChange = { newAmount -> postings[index] = posting.copy(amount = newAmount) },
                        label = { Text(stringResource(R.string.amount)) },
                        placeholder = { Text(stringResource(R.string.form_amount_placeholder)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    if (postings.size > 2) {
                        TextButton(onClick = { postings.removeAt(index) }) {
                            Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                postings.add(Posting(account = "", amount = "", currency = currency))
            },
        ) {
            Text(stringResource(R.string.form_add_posting))
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(stringResource(R.string.form_note_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = { showDetails = !showDetails }) {
            Text(
                stringResource(
                    if (showDetails) R.string.form_hide_details else R.string.form_show_details,
                ),
            )
        }

        if (showDetails) {
            var payeeExpanded by remember { mutableStateOf(false) }
            val filteredPayees = remember(payee, payeeSuggestions) {
                payeeSuggestions.filter { it.contains(payee, ignoreCase = true) }
            }

            ExposedDropdownMenuBox(
                expanded = payeeExpanded && filteredPayees.isNotEmpty(),
                onExpandedChange = { payeeExpanded = it },
            ) {
                OutlinedTextField(
                    value = payee,
                    onValueChange = {
                        payee = it
                        payeeExpanded = true
                    },
                    label = { Text(stringResource(R.string.payee)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = payeeExpanded) },
                )
                ExposedDropdownMenu(
                    expanded = payeeExpanded && filteredPayees.isNotEmpty(),
                    onDismissRequest = { payeeExpanded = false },
                ) {
                    filteredPayees.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion) },
                            onClick = {
                                payee = suggestion
                                payeeExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it },
                label = { Text(stringResource(R.string.currency)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (defaultPaymentAccount != null) {
                Spacer(Modifier.height(8.dp))

                AccountField(
                    value = paymentAccountValue,
                    onValueChange = { paymentAccountValue = it },
                    suggestions = accountSuggestions,
                    label = stringResource(R.string.default_account),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = paymentAmountText,
                    onValueChange = { new ->
                        val trimmed = new.trim()
                        if (trimmed.isBlank() || trimmed == "-") {
                            paymentAuto = true
                        } else {
                            paymentAuto = false
                            paymentManual = new
                        }
                    },
                    label = { Text(stringResource(R.string.amount)) },
                    singleLine = true,
                    isError = paymentUnbalanced,
                    supportingText = if (paymentUnbalanced) {
                        { Text(stringResource(R.string.form_unbalanced)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    val resolvedPostings = postings.map { p ->
                        p.copy(currency = currency)
                    }.toMutableList()
                    if (defaultPaymentAccount != null) {
                        val resolvedPaymentAmount =
                            if (paymentAuto) formattedAuto else paymentManual.replace(",", ".")
                        resolvedPostings.add(
                            Posting(
                                account = paymentAccountValue,
                                amount = resolvedPaymentAmount,
                                currency = currency,
                            ),
                        )
                    }
                    val tx = Transaction(
                        date = date,
                        payee = payee,
                        postings = resolvedPostings,
                        note = note,
                        source = initial.source,
                    )
                    onSave(tx)
                },
                enabled = canSave,
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}
