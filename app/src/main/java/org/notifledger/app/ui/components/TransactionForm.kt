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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.notifledger.app.model.Posting
import org.notifledger.app.model.Transaction

/**
 * Reusable form for entering/editing a transaction.
 * Used by: QuickAdd screen, Edit dialog from main list.
 *
 * Features a dynamic list of postings. Currency is shared across all postings.
 *
 * @param payeeSuggestions list of existing payee names for autocomplete dropdown.
 * @param accountSuggestions list of existing account names for autocomplete dropdown.
 * @param defaultCurrency default currency to use when no currency is set on the initial.
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
) {
    var payee by remember { mutableStateOf(initial.payee) }
    var date by remember { mutableStateOf(initial.date) }
    var note by remember { mutableStateOf(initial.note) }
    var currency by remember {
        mutableStateOf(initial.postings.firstOrNull()?.currency ?: defaultCurrency)
    }
    val postings = remember {
        mutableStateListOf<Posting>().apply {
            if (initial.postings.isNotEmpty()) {
                // Strip negative amounts — auto-balance fills them on save
                addAll(initial.postings.map { p ->
                    if (p.amount.startsWith("-")) p.copy(amount = "") else p
                })
            } else {
                add(Posting(account = "", amount = "", currency = currency))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        OutlinedTextField(
            value = date,
            onValueChange = { date = it },
            label = { Text("Date") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

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
                label = { Text("Payee") },
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
            label = { Text("Currency") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Postings",
            style = MaterialTheme.typography.titleSmall,
        )

        Spacer(Modifier.height(4.dp))

        postings.forEachIndexed { index, posting ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                AccountField(
                    value = posting.account,
                    onValueChange = { newAccount ->
                        postings[index] = posting.copy(account = newAccount)
                    },
                    suggestions = accountSuggestions,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = posting.amount,
                    onValueChange = { newAmount ->
                        postings[index] = posting.copy(amount = newAmount)
                    },
                    label = { Text("Amount") },
                    singleLine = true,
                    modifier = Modifier.width(100.dp),
                )
                if (postings.size > 2) {
                    TextButton(
                        onClick = { postings.removeAt(index) },
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                postings.add(Posting(account = "", amount = "", currency = currency))
            },
        ) {
            Text("Add posting")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    val resolvedPostings = postings.map { p ->
                        p.copy(currency = currency)
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
            ) {
                Text("Save")
            }
        }
    }
}
