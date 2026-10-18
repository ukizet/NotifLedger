package org.notifledger.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.notifledger.app.R
import org.notifledger.app.stats.Statistics
import org.notifledger.app.ui.components.DonutChart
import org.notifledger.app.ui.components.DonutSlice
import org.notifledger.app.ui.components.NotifLedgerTopAppBar
import org.notifledger.app.ui.components.SpendingBarChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val entries by viewModel.journalEntries.collectAsState()
    val accountOptions by viewModel.existingAccounts.collectAsState()
    var selectedAccounts by remember { mutableStateOf(emptySet<String>()) }
    var daysInput by remember { mutableStateOf("7") }
    var days by remember { mutableStateOf(7) }

    val accounts = selectedAccounts
    val currency = remember(entries, accounts, days) {
        Statistics.dominantCurrency(entries, accounts, days)
    }
    val categories = remember(entries, accounts, days, currency) {
        currency?.let { Statistics.spendingByCategory(entries, accounts, days, currency = it) }.orEmpty()
    }
    val periods = remember(entries, accounts, days, currency) {
        currency?.let { Statistics.spendingByPeriod(entries, accounts, days, currency = it) }.orEmpty()
    }
    val count = remember(entries, accounts, days) {
        Statistics.transactionCount(entries, accounts, days)
    }

    Scaffold(
        topBar = {
            NotifLedgerTopAppBar(
                title = stringResource(R.string.statistics),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            AccountDropdown(
                selected = selectedAccounts,
                options = accountOptions,
                onToggle = { account ->
                    selectedAccounts = if (account in selectedAccounts) {
                        selectedAccounts - account
                    } else {
                        selectedAccounts + account
                    }
                },
                onClear = { selectedAccounts = emptySet() },
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = daysInput,
                onValueChange = { text ->
                    val digits = text.filter(Char::isDigit).take(5)
                    daysInput = digits
                    digits.toIntOrNull()?.let { if (it >= 1) days = it }
                },
                label = { Text(stringResource(R.string.stats_days)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = pluralStringResource(
                    R.plurals.stats_transaction_count,
                    count,
                    count,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (count == 0) {
                Text(
                    text = stringResource(R.string.stats_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (currency == null || categories.isEmpty()) {
                Text(
                    text = stringResource(R.string.stats_no_expenses),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.stats_in_currency, currency),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.stats_spending_by_category),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        DonutChart(
                            slices = categories.map {
                                DonutSlice(it.label, it.total, "${it.total.toPlainString()} $currency")
                            },
                            centerValue = "${categories.sumOf { it.total }.toPlainString()} $currency",
                            centerLabel = "",
                            currency = currency,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.stats_spending_over_time),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        SpendingBarChart(periods = periods)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDropdown(
    selected: Set<String>,
    options: List<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val allLabel = stringResource(R.string.stats_all_accounts)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = if (selected.isEmpty()) allLabel else selected.joinToString(", "),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.account)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(allLabel) },
                leadingIcon = { Checkbox(checked = selected.isEmpty(), onCheckedChange = null) },
                onClick = { onClear() },
            )
            options.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account) },
                    leadingIcon = { Checkbox(checked = account in selected, onCheckedChange = null) },
                    onClick = { onToggle(account) },
                )
            }
        }
    }
}
