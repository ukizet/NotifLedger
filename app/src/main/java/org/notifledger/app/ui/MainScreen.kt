package org.notifledger.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.List
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Tags
import kotlinx.coroutines.launch
import org.notifledger.app.model.Transaction
import org.notifledger.app.ui.components.TransactionForm
import org.notifledger.app.ui.components.TransactionRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToQuickAdd: () -> Unit,
    onNavigateToRawJournal: () -> Unit,
    onNavigateToParserRules: () -> Unit,
    onNavigateToCategorizationRules: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val entries by viewModel.entries.collectAsState()
    val journalPath by viewModel.journalPath.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)
            scope.launch {
                viewModel.settings.setJournalPath(it.toString())
            }
        }
    }

    var editingIndex by remember { mutableStateOf(-1) }
    var deletingIndex by remember { mutableStateOf(-1) }
    var showSimulateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("NotifLedger") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IconButton(onClick = onNavigateToParserRules) {
                        Icon(Lucide.FileText, contentDescription = "Parser rules")
                    }
                    IconButton(onClick = onNavigateToCategorizationRules) {
                        Icon(Lucide.Tags, contentDescription = "Categorization rules")
                    }
                    IconButton(onClick = onNavigateToRawJournal) {
                        Icon(Lucide.List, contentDescription = "Raw journal")
                    }
                    IconButton(onClick = { showSimulateDialog = true }) {
                        Icon(Lucide.Bot, contentDescription = "Test notification")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Lucide.Settings, contentDescription = "Settings")
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToQuickAdd) {
                Icon(Lucide.Plus, contentDescription = "Quick add")
            }
        },
    ) { padding ->
        if (journalPath.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(
                    onClick = { pickFileLauncher.launch(arrayOf("*/*")) },
                ) {
                    Text("Select a journal file to begin")
                }
            }
        } else if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No transactions yet. Add one with the + button.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(entries) { index, entry ->
                    TransactionRow(
                        entry = entry,
                        onEdit = { editingIndex = index },
                        onDelete = { deletingIndex = index },
                    )
                }
            }
        }
    }

    if (showSimulateDialog) {
        SimulateNotificationDialog(
            viewModel = viewModel,
            onDismiss = {
                showSimulateDialog = false
                viewModel.reloadEntries()
            },
        )
    }

    if (editingIndex >= 0 && editingIndex < entries.size) {
        val entry = entries[editingIndex]
        val tx = Transaction(
            date = entry.date,
            payee = entry.payee,
            postings = entry.postings,
        )
        AlertDialog(
            onDismissRequest = { editingIndex = -1 },
            title = { Text("Edit transaction") },
            text = {
                TransactionForm(
                    initial = tx,
                    onSave = { updated ->
                        viewModel.editTransaction(entry.lineOffset, updated)
                        editingIndex = -1
                    },
                    onCancel = { editingIndex = -1 },
                )
            },
            confirmButton = {},
        )
    }

    if (deletingIndex >= 0 && deletingIndex < entries.size) {
        val entry = entries[deletingIndex]
        AlertDialog(
            onDismissRequest = { deletingIndex = -1 },
            title = { Text("Delete transaction") },
            text = { Text("Delete ${entry.date} ${entry.payee}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTransaction(entry.lineOffset)
                        deletingIndex = -1
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingIndex = -1 }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SimulateNotificationDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Simulate notification") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Paste what a bank notification looks like, and the app will parse it using your rules.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Source app (package)") },
                    placeholder = { Text("e.g. no.dnb.mobil") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Notification title") },
                    placeholder = { Text("e.g. Betalt") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Notification text") },
                    placeholder = { Text("e.g. Betalt 184.50 kr hos Rema 1000") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                )
                if (resultMessage.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(resultMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    resultMessage = viewModel.simulateNotification(
                        packageName = packageName.trim(),
                        title = title.trim(),
                        text = text.trim(),
                    )
                },
            ) {
                Text("Test parse & write")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}
