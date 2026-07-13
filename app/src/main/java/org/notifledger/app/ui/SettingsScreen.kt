package org.notifledger.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaultAccount by viewModel.defaultAccount.collectAsState()
    val journalPath by viewModel.journalPath.collectAsState()
    val currentSortOrder by viewModel.sortOrder.collectAsState()
    val currentPageLimit by viewModel.pageLimit.collectAsState()

    var editingAccount by remember { mutableStateOf(false) }
    var accountInput by remember { mutableStateOf("") }

    val pickDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            // Journal path
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Journal file", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (journalPath.isNotBlank()) journalPath else "Not set",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { pickDirLauncher.launch(null) }) {
                        Text("Select directory")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Default payment account
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Default payment account", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    if (editingAccount) {
                        OutlinedTextField(
                            value = accountInput,
                            onValueChange = { accountInput = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    viewModel.settings.setDefaultAccount(accountInput.trim())
                                }
                                editingAccount = false
                            },
                        ) {
                            Text("Save")
                        }
                    } else {
                        Text(
                            text = defaultAccount,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = {
                            accountInput = defaultAccount
                            editingAccount = true
                        }) {
                            Text("Change")
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Sort order
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sort transactions", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    var sortExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = sortExpanded,
                        onExpandedChange = { sortExpanded = !sortExpanded },
                    ) {
                        OutlinedTextField(
                            value = sortLabel(currentSortOrder),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sortExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = sortExpanded,
                            onDismissRequest = { sortExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Newest first") },
                                onClick = { viewModel.setSortOrder("newest_first"); sortExpanded = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Oldest first") },
                                onClick = { viewModel.setSortOrder("oldest_first"); sortExpanded = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Highest amount first") },
                                onClick = { viewModel.setSortOrder("highest_amount"); sortExpanded = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Lowest amount first") },
                                onClick = { viewModel.setSortOrder("lowest_amount"); sortExpanded = false },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Page limit
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Transactions per page", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    var editingLimit by remember { mutableStateOf(false) }
                    var limitInput by remember { mutableStateOf(currentPageLimit.toString()) }
                    if (editingLimit) {
                        Row {
                            OutlinedTextField(
                                value = limitInput,
                                onValueChange = { limitInput = it },
                                singleLine = true,
                                modifier = Modifier.width(120.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    val n = limitInput.toIntOrNull()
                                    if (n != null && n > 0) {
                                        viewModel.setPageLimit(n)
                                    }
                                    editingLimit = false
                                },
                            ) {
                                Text("Set")
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$currentPageLimit entries",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(onClick = {
                                limitInput = currentPageLimit.toString()
                                editingLimit = true
                            }) {
                                Text("Change")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Notification listener status
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Notification capture", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    val isEnabled = remember {
                        val cn = context.packageName + "/.notification.NotifListener"
                        Settings.Secure.getString(
                            context.contentResolver,
                            "enabled_notification_listeners",
                        )?.contains(cn) == true
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isEnabled) "Listener is active"
                            else "Listener is disabled",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = {
                                if (!isEnabled) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun sortLabel(key: String): String = when (key) {
    "newest_first" -> "Newest first"
    "oldest_first" -> "Oldest first"
    "highest_amount" -> "Highest amount first"
    "lowest_amount" -> "Lowest amount first"
    else -> "Newest first"
}
