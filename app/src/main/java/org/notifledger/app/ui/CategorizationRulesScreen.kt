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
import androidx.compose.material3.AlertDialog
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
    viewModel: CategorizationRulesViewModel,
    onBack: () -> Unit,
) {
    val rules by viewModel.categorizationRules.collectAsState()
    val accountSuggestions by viewModel.accountSuggestions.collectAsState()
    var showAddForm by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf(-1) }
    var deletingIndex by remember { mutableStateOf(-1) }

    Scaffold(
        topBar = {
            NotifLedgerTopAppBar(
                title = stringResource(R.string.categorization_rules),
                onBack = onBack,
            )
        },
    ) { padding ->
        RulesContent(
            rules = rules,
            accountSuggestions = accountSuggestions,
            showAddForm = showAddForm,
            editingIndex = editingIndex,
            onAddClick = { showAddForm = true },
            onSave = { rule ->
                viewModel.saveCategorizationRules(
                    upsertRule(viewModel.getCachedCategorizationRules(), rules, rule, editingIndex),
                )
                showAddForm = false
                editingIndex = -1
            },
            onCancel = {
                showAddForm = false
                editingIndex = -1
            },
            onEdit = { index ->
                editingIndex = index
                showAddForm = false
            },
            onDelete = { index -> deletingIndex = index },
            modifier = Modifier.padding(padding),
        )
    }

    if (deletingIndex in rules.indices) {
        DeleteRuleDialog(
            onConfirm = {
                removeRuleAt(viewModel.getCachedCategorizationRules(), deletingIndex)?.let {
                    viewModel.saveCategorizationRules(it)
                }
                deletingIndex = -1
            },
            onDismiss = { deletingIndex = -1 },
        )
    }
}

private fun upsertRule(
    cached: List<CategorizationRule>,
    displayed: List<CategorizationRule>,
    rule: CategorizationRule,
    editingIndex: Int,
): List<CategorizationRule> {
    val updated = cached.toMutableList()
    val editingExisting = editingIndex in displayed.indices && editingIndex in updated.indices
    if (editingExisting) updated[editingIndex] = rule else updated.add(rule)
    return updated
}

private fun removeRuleAt(
    rules: List<CategorizationRule>,
    index: Int,
): List<CategorizationRule>? {
    if (index !in rules.indices) return null
    return rules.toMutableList().apply { removeAt(index) }
}

@Composable
private fun RulesContent(
    rules: List<CategorizationRule>,
    accountSuggestions: List<String>,
    showAddForm: Boolean,
    editingIndex: Int,
    onAddClick: () -> Unit,
    onSave: (CategorizationRule) -> Unit,
    onCancel: () -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        if (!showAddForm) {
            OutlinedButton(onClick = onAddClick) {
                Text(stringResource(R.string.add_rule))
            }
            Spacer(Modifier.height(12.dp))
        }

        RuleFormSection(
            rules = rules,
            accountSuggestions = accountSuggestions,
            showAddForm = showAddForm,
            editingIndex = editingIndex,
            onSave = onSave,
            onCancel = onCancel,
        )

        if (rules.isEmpty() && !showAddForm) {
            Text(stringResource(R.string.no_rules_hint))
        }

        LazyColumn {
            itemsIndexed(rules) { index, rule ->
                RuleCard(
                    rule = rule,
                    onEdit = { onEdit(index) },
                    onDelete = { onDelete(index) },
                )
            }
        }
    }
}

@Composable
private fun RuleFormSection(
    rules: List<CategorizationRule>,
    accountSuggestions: List<String>,
    showAddForm: Boolean,
    editingIndex: Int,
    onSave: (CategorizationRule) -> Unit,
    onCancel: () -> Unit,
) {
    if (!showAddForm && editingIndex < 0) return
    val editingRule = if (editingIndex in rules.indices) rules[editingIndex] else null
    CategorizationRuleForm(
        initial = editingRule,
        accountSuggestions = accountSuggestions,
        onSave = onSave,
        onCancel = onCancel,
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun RuleCard(
    rule: CategorizationRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
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
            TextButton(onClick = onEdit) {
                Text(stringResource(R.string.edit))
            }
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DeleteRuleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete)) },
        text = { Text(stringResource(R.string.rules_delete_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
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
    val matchValid = isValidRegex(match)

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = match,
            onValueChange = { match = it },
            label = { Text(stringResource(R.string.payee_pattern_label)) },
            placeholder = { Text(stringResource(R.string.payee_pattern_placeholder)) },
            singleLine = true,
            isError = !matchValid,
            supportingText = if (!matchValid) {
                { Text(stringResource(R.string.invalid_regex_pattern)) }
            } else {
                null
            },
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
                enabled = account.isNotBlank() && matchValid,
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

private fun isValidRegex(pattern: String): Boolean =
    runCatching { Regex(pattern) }.isSuccess
