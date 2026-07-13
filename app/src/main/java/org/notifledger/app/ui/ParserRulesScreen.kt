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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.unit.dp
import org.notifledger.app.model.AmountExtractor
import org.notifledger.app.model.ParserRule
import org.notifledger.app.model.PayeeExtractor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParserRulesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val rules by viewModel.parserRules.collectAsState()
    var showAddForm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parser rules") },
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
            Text(
                text = "Tell the app how to read each bank's notifications.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Just paste what the notification says, pick where the amount and store name are.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            if (!showAddForm) {
                OutlinedButton(onClick = { showAddForm = true }) {
                    Text("Add rule for a bank")
                }
                Spacer(Modifier.height(12.dp))
            }

            if (showAddForm) {
                ParserRuleForm(
                    onSave = { rule ->
                        viewModel.saveParserRule(rule)
                        showAddForm = false
                    },
                    onCancel = { showAddForm = false },
                )
                Spacer(Modifier.height(12.dp))
            }

            if (rules.isEmpty() && !showAddForm) {
                Text(
                    "No rules yet. Add one above.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn {
                itemsIndexed(rules) { _, rule ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(rule.appName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Look for: \"${rule.containsText}\"",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "Amount: ${AmountExtractor.toRuleString(rule.amountExtractor)}${if (rule.amountPrefix.isNotBlank()) " after \"${rule.amountPrefix}\"" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "Payee: ${PayeeExtractor.toRuleString(rule.payeeExtractor)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                rule.currency,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParserRuleForm(
    onSave: (ParserRule) -> Unit,
    onCancel: () -> Unit,
) {
    var appName by remember { mutableStateOf("") }
    var containsText by remember { mutableStateOf("") }
    var amountExtractor by remember { mutableStateOf(AmountExtractor.FirstNumber) }
    var payeeExtractor by remember { mutableStateOf(PayeeExtractor.BeforeAmount) }
    var amountPrefix by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("NOK") }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = appName,
            onValueChange = { appName = it },
            label = { Text("Bank or app name") },
            placeholder = { Text("e.g. DNB, Vipps, Klarna") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = containsText,
            onValueChange = { containsText = it },
            label = { Text("What does the notification say?") },
            placeholder = { Text("e.g. Betalt, Purchase, Kjøpt") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        Text("Where is the amount?", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))

        var amountExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = amountExpanded,
            onExpandedChange = { amountExpanded = !amountExpanded },
        ) {
            OutlinedTextField(
                value = AmountExtractor.toRuleString(amountExtractor).replace("_", " "),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(amountExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = amountExpanded,
                onDismissRequest = { amountExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("First number in the notification") },
                    onClick = { amountExtractor = AmountExtractor.FirstNumber; amountExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text("Last number in the notification") },
                    onClick = { amountExtractor = AmountExtractor.LastNumber; amountExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text("Number after a specific word") },
                    onClick = { amountExtractor = AmountExtractor.AfterText; amountExpanded = false },
                )
            }
        }

        if (amountExtractor == AmountExtractor.AfterText) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = amountPrefix,
                onValueChange = { amountPrefix = it },
                label = { Text("After which word?") },
                placeholder = { Text("e.g. kr, $, sek") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(8.dp))

        Text("Where is the store/payee name?", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))

        var payeeExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = payeeExpanded,
            onExpandedChange = { payeeExpanded = !payeeExpanded },
        ) {
            OutlinedTextField(
                value = when (payeeExtractor) {
                    PayeeExtractor.BeforeAmount -> "Text before the amount"
                    PayeeExtractor.WholeText -> "Entire notification text"
                    PayeeExtractor.AfterText -> "Text after a specific word"
                },
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(payeeExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = payeeExpanded,
                onDismissRequest = { payeeExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Text before the amount") },
                    onClick = { payeeExtractor = PayeeExtractor.BeforeAmount; payeeExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text("Entire notification text") },
                    onClick = { payeeExtractor = PayeeExtractor.WholeText; payeeExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text("Text after a specific word") },
                    onClick = { payeeExtractor = PayeeExtractor.AfterText; payeeExpanded = false },
                )
            }
        }

        if (payeeExtractor == PayeeExtractor.AfterText) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = amountPrefix,
                onValueChange = { amountPrefix = it },
                label = { Text("After which word?") },
                placeholder = { Text("e.g. at, hos, på") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it },
                label = { Text("Currency") },
                singleLine = true,
                modifier = Modifier.width(100.dp),
            )
            Spacer(Modifier.width(12.dp))
        }

        Spacer(Modifier.height(16.dp))

        Row {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    val rule = ParserRule(
                        appName = appName.trim(),
                        containsText = containsText.trim(),
                        amountExtractor = amountExtractor,
                        payeeExtractor = payeeExtractor,
                        amountPrefix = amountPrefix.trim(),
                        currency = currency.trim(),
                    )
                    onSave(rule)
                },
                enabled = appName.isNotBlank() && containsText.isNotBlank(),
            ) {
                Text("Save rule")
            }
        }
    }
}
