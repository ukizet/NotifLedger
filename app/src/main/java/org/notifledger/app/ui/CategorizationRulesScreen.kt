package org.notifledger.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.notifledger.app.R
import org.notifledger.app.model.CategorizationRule
import org.notifledger.app.ui.components.AccountField
import org.notifledger.app.ui.components.NotifLedgerTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorizationRulesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val rules by viewModel.categorizationRules.collectAsState()
    val accountSuggestions by viewModel.existingAccounts.collectAsState()
    var showAddForm by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf(-1) }

    Scaffold(
        topBar = {
            NotifLedgerTopAppBar(
                title = stringResource(R.string.categorization_rules),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            if (!showAddForm) {
                OutlinedButton(onClick = { showAddForm = true }) {
                    Text(stringResource(R.string.add_rule))
                }
                Spacer(Modifier.height(12.dp))
            }

            if (showAddForm || editingIndex >= 0) {
                val editingRule = if (editingIndex >= 0 && editingIndex < rules.size) rules[editingIndex] else null
                CategorizationRuleForm(
                    initial = editingRule,
                    accountSuggestions = accountSuggestions,
                    onSave = { rule ->
                        val updated = viewModel.getCachedCategorizationRules().toMutableList()
                        if (editingRule != null && editingIndex in updated.indices) {
                            updated[editingIndex] = rule
                        } else {
                            updated.add(rule)
                        }
                        viewModel.saveCategorizationRules(updated)
                        showAddForm = false
                        editingIndex = -1
                    },
                    onCancel = {
                        showAddForm = false
                        editingIndex = -1
                    },
                )
                Spacer(Modifier.height(12.dp))
            }

            if (rules.isEmpty() && !showAddForm) {
                Text(stringResource(R.string.no_rules_hint))
            }

            LazyColumn {
                itemsIndexed(rules) { index, rule ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (rule.match.isBlank())
                                        stringResource(R.string.rule_default_label)
                                    else rule.match,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = stringResource(R.string.rule_account_arrow, rule.account),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            TextButton(onClick = {
                                editingIndex = index
                                showAddForm = false
                            }) {
                                Text(stringResource(R.string.edit))
                            }
                            TextButton(onClick = {
                                val updated = viewModel.getCachedCategorizationRules().toMutableList()
                                if (index in updated.indices) {
                                    updated.removeAt(index)
                                    viewModel.saveCategorizationRules(updated)
                                }
                            }) {
                                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorizationRuleForm(
    initial: CategorizationRule? = null,
    accountSuggestions: List<String> = emptyList(),
    onSave: (CategorizationRule) -> Unit,
    onCancel: () -> Unit,
) {
    var match by remember { mutableStateOf(initial?.match ?: "") }
    var account by remember { mutableStateOf(initial?.account ?: "") }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = match,
            onValueChange = { match = it },
            label = { Text(stringResource(R.string.payee_pattern_label)) },
            placeholder = { Text(stringResource(R.string.payee_pattern_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        AccountField(
            value = account,
            onValueChange = { account = it },
            suggestions = accountSuggestions,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    onSave(CategorizationRule(match = match.trim(), account = account.trim()))
                },
                enabled = account.isNotBlank(),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}
