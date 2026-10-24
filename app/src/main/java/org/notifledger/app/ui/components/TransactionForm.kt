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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.notifledger.app.R
import org.notifledger.app.journal.JournalWriter
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
@Composable
fun TransactionForm(
    initial: Transaction,
    onSave: (Transaction) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    payeeSuggestions: List<String> = emptyList(),
    accountSuggestions: List<String> = emptyList(),
    defaultCurrency: String,
    defaultPaymentAccount: String? = null,
) {
    val state = rememberTransactionFormState(initial, defaultCurrency, defaultPaymentAccount)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        DateField(
            value = state.date,
            onValueChange = { state.date = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        PostingsSection(
            postings = state.postings,
            accountSuggestions = accountSuggestions,
            onPostingChange = { index, posting -> state.postings[index] = posting },
            onRemovePosting = { state.postings.removeAt(it) },
            onAddPosting = {
                state.postings.add(Posting(account = "", amount = "", currency = state.currency))
            },
        )

        Spacer(Modifier.height(8.dp))

        NoteField(note = state.note, onNoteChange = { state.note = it })

        Spacer(Modifier.height(8.dp))

        DetailsSection(
            state = state,
            payeeSuggestions = payeeSuggestions,
            accountSuggestions = accountSuggestions,
            defaultPaymentAccount = defaultPaymentAccount,
        )

        Spacer(Modifier.height(16.dp))

        FormActions(
            canSave = state.canSave(defaultPaymentAccount),
            onCancel = onCancel,
            onSave = { onSave(state.toTransaction(initial, defaultPaymentAccount)) },
        )
    }
}

/**
 * All editable state of the form plus the derived values the UI and save path need.
 * Kept outside the composable so sections can share it without long parameter lists.
 */
private class TransactionFormState(
    payeeState: MutableState<String>,
    dateState: MutableState<String>,
    noteState: MutableState<String>,
    showDetailsState: MutableState<Boolean>,
    currencyState: MutableState<String>,
    val postings: SnapshotStateList<Posting>,
    paymentAutoState: MutableState<Boolean>,
    paymentManualState: MutableState<String>,
    paymentAccountState: MutableState<String>,
) {
    var payee by payeeState
    var date by dateState
    var note by noteState
    var showDetails by showDetailsState
    var currency by currencyState
    var paymentAuto by paymentAutoState
    var paymentManual by paymentManualState
    var paymentAccountValue by paymentAccountState

    val formattedAuto: String
        get() = String.format(Locale.ROOT, "%.2f", -amountTotal)

    val paymentAmountText: String
        get() = if (paymentAuto) formattedAuto else paymentManual

    val paymentUnbalanced: Boolean
        get() {
            val manualValue = JournalWriter.normalizeAmount(paymentManual)?.toDoubleOrNull()
            return !paymentAuto && (manualValue == null || abs(amountTotal + manualValue) > 1e-9)
        }

    val dateValid: Boolean
        get() = runCatching { LocalDate.parse(date) }.isSuccess

    fun canSave(defaultPaymentAccount: String?): Boolean =
        dateValid && amountsValid && (
            defaultPaymentAccount == null ||
                (paymentAccountValue.isNotBlank() && hasAnyAmount && !paymentUnbalanced)
            )

    fun updatePaymentAmount(newAmount: String) {
        val trimmed = newAmount.trim()
        if (trimmed.isBlank() || trimmed == "-") {
            paymentAuto = true
        } else {
            paymentAuto = false
            paymentManual = newAmount
        }
    }

    fun toTransaction(initial: Transaction, defaultPaymentAccount: String?): Transaction {
        val resolvedPostings = postings.map { it.copy(currency = currency) }.toMutableList()
        if (defaultPaymentAccount != null) {
            val resolvedPaymentAmount =
                if (paymentAuto) formattedAuto else (JournalWriter.normalizeAmount(paymentManual) ?: formattedAuto)
            resolvedPostings.add(
                Posting(
                    account = paymentAccountValue,
                    amount = resolvedPaymentAmount,
                    currency = currency,
                ),
            )
        }
        return Transaction(
            date = date,
            payee = payee,
            postings = resolvedPostings,
            note = note,
            source = initial.source,
        )
    }

    private val amountTotal: Double
        get() = postings
            .filter { it.amount.isNotBlank() }
            .sumOf { JournalWriter.normalizeAmount(it.amount)?.toDoubleOrNull() ?: 0.0 }

    private val amountsValid: Boolean
        get() = postings.all {
            it.amount.isBlank() || JournalWriter.normalizeAmount(it.amount) != null
        }

    private val hasAnyAmount: Boolean
        get() = postings.any { it.amount.isNotBlank() }
}

@Composable
private fun rememberTransactionFormState(
    initial: Transaction,
    defaultCurrency: String,
    defaultPaymentAccount: String?,
): TransactionFormState {
    val currencyState = remember(defaultCurrency) {
        mutableStateOf(
            initial.postings.firstOrNull()?.currency?.takeIf { it.isNotBlank() } ?: defaultCurrency,
        )
    }
    val postings = remember {
        mutableStateListOf<Posting>().apply {
            if (initial.postings.isEmpty()) {
                add(Posting(account = "", amount = "", currency = currencyState.value))
            } else {
                addAll(initial.postings)
            }
        }
    }

    return TransactionFormState(
        payeeState = remember { mutableStateOf(initial.payee) },
        dateState = remember { mutableStateOf(initial.date) },
        noteState = remember { mutableStateOf(initial.note) },
        showDetailsState = remember { mutableStateOf(false) },
        currencyState = currencyState,
        postings = postings,
        paymentAutoState = remember { mutableStateOf(true) },
        paymentManualState = remember { mutableStateOf("") },
        paymentAccountState = remember(defaultPaymentAccount) {
            mutableStateOf(defaultPaymentAccount ?: "")
        },
    )
}

@Composable
private fun PostingsSection(
    postings: List<Posting>,
    accountSuggestions: List<String>,
    onPostingChange: (Int, Posting) -> Unit,
    onRemovePosting: (Int) -> Unit,
    onAddPosting: () -> Unit,
) {
    Text(
        text = stringResource(R.string.form_postings_title),
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(Modifier.height(8.dp))

    postings.forEachIndexed { index, posting ->
        PostingRow(
            posting = posting,
            accountSuggestions = accountSuggestions,
            showRemove = postings.size > 2,
            onAccountChange = { onPostingChange(index, posting.copy(account = it)) },
            onAmountChange = { onPostingChange(index, posting.copy(amount = it)) },
            onRemove = { onRemovePosting(index) },
        )
        Spacer(Modifier.height(12.dp))
    }

    Spacer(Modifier.height(8.dp))

    OutlinedButton(onClick = onAddPosting) {
        Text(stringResource(R.string.form_add_posting))
    }
}

@Composable
private fun NoteField(note: String, onNoteChange: (String) -> Unit) {
    OutlinedTextField(
        value = note,
        onValueChange = onNoteChange,
        label = { Text(stringResource(R.string.form_note_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PostingRow(
    posting: Posting,
    accountSuggestions: List<String>,
    showRemove: Boolean,
    onAccountChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val amountInvalid =
        posting.amount.isNotBlank() && JournalWriter.normalizeAmount(posting.amount) == null
    Column(modifier = Modifier.fillMaxWidth()) {
        AccountField(
            value = posting.account,
            onValueChange = onAccountChange,
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
                onValueChange = onAmountChange,
                label = { Text(stringResource(R.string.amount)) },
                placeholder = { Text(stringResource(R.string.form_amount_placeholder)) },
                singleLine = true,
                isError = amountInvalid,
                supportingText = if (amountInvalid) {
                    { Text(stringResource(R.string.form_invalid_amount)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            if (showRemove) {
                TextButton(onClick = onRemove) {
                    Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun DetailsSection(
    state: TransactionFormState,
    payeeSuggestions: List<String>,
    accountSuggestions: List<String>,
    defaultPaymentAccount: String?,
) {
    TextButton(onClick = { state.showDetails = !state.showDetails }) {
        Text(
            stringResource(
                if (state.showDetails) R.string.form_hide_details else R.string.form_show_details,
            ),
        )
    }

    if (!state.showDetails) return

    PayeeField(
        payee = state.payee,
        onPayeeChange = { state.payee = it },
        suggestions = payeeSuggestions,
    )

    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        value = state.currency,
        onValueChange = { state.currency = it },
        label = { Text(stringResource(R.string.currency)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    if (defaultPaymentAccount == null) return

    Spacer(Modifier.height(8.dp))

    PaymentFields(
        accountValue = state.paymentAccountValue,
        onAccountChange = { state.paymentAccountValue = it },
        accountSuggestions = accountSuggestions,
        amountText = state.paymentAmountText,
        onAmountChange = state::updatePaymentAmount,
        isUnbalanced = state.paymentUnbalanced,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayeeField(
    payee: String,
    onPayeeChange: (String) -> Unit,
    suggestions: List<String>,
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(payee, suggestions) {
        suggestions.filter { it.contains(payee, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = payee,
            onValueChange = {
                onPayeeChange(it)
                expanded = true
            },
            label = { Text(stringResource(R.string.payee)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            filtered.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onPayeeChange(suggestion)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PaymentFields(
    accountValue: String,
    onAccountChange: (String) -> Unit,
    accountSuggestions: List<String>,
    amountText: String,
    onAmountChange: (String) -> Unit,
    isUnbalanced: Boolean,
) {
    AccountField(
        value = accountValue,
        onValueChange = onAccountChange,
        suggestions = accountSuggestions,
        label = stringResource(R.string.default_account),
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        value = amountText,
        onValueChange = onAmountChange,
        label = { Text(stringResource(R.string.amount)) },
        singleLine = true,
        isError = isUnbalanced,
        supportingText = if (isUnbalanced) {
            { Text(stringResource(R.string.form_unbalanced)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FormActions(
    canSave: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.cancel))
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = onSave,
            enabled = canSave,
        ) {
            Text(stringResource(R.string.save))
        }
    }
}
