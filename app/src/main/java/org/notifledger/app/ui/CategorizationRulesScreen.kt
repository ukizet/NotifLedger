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
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.notifledger.app.model.CategorizationRule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorizationRulesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val rules by viewModel.categorizationRules.collectAsState()
    var showAddForm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categorization rules") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Lucide.ArrowLeft, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            if (!showAddForm) {
                OutlinedButton(onClick = { showAddForm = true }) {
                    Text("Add rule")
                }
                Spacer(Modifier.height(12.dp))
            }

            if (showAddForm) {
                CategorizationRuleForm(
                    onSave = { rule ->
                        val updated = viewModel.getCachedCategorizationRules().toMutableList().apply {
                            add(rule)
                        }
                        viewModel.saveCategorizationRules(updated)
                        showAddForm = false
                    },
                    onCancel = { showAddForm = false },
                )
                Spacer(Modifier.height(12.dp))
            }

            if (rules.isEmpty() && !showAddForm) {
                Text("No categorization rules yet. Transactions default to expenses:unknown.")
            }

            LazyColumn {
                itemsIndexed(rules) { index, rule ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (rule.match.isBlank()) "(default)" else rule.match,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "→ ${rule.account}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            TextButton(onClick = {
                                val updated = viewModel.getCachedCategorizationRules().toMutableList()
                                if (index in updated.indices) {
                                    updated.removeAt(index)
                                    viewModel.saveCategorizationRules(updated)
                                }
                            }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
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
    onSave: (CategorizationRule) -> Unit,
    onCancel: () -> Unit,
) {
    var match by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = match,
            onValueChange = { match = it },
            label = { Text("Payee pattern (regex)") },
            placeholder = { Text("e.g. REMA|KIWI|COOP") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = account,
            onValueChange = { account = it },
            label = { Text("Account") },
            placeholder = { Text("e.g. expenses:groceries") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    onSave(CategorizationRule(match = match.trim(), account = account.trim()))
                },
                enabled = account.isNotBlank(),
            ) {
                Text("Save")
            }
        }
    }
}
