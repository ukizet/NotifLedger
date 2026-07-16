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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.BellOff
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.notifledger.app.notification.NotificationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaultAccount by viewModel.defaultAccount.collectAsState()
    val defaultCurrency by viewModel.defaultCurrency.collectAsState()
    val journalPath by viewModel.journalPath.collectAsState()

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
            // Notification listener status
            val isListening = remember { NotificationHelper.isListenerEnabled(context) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isListening)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isListening) Lucide.Bell else Lucide.BellOff,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (isListening)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isListening) "Listening for notifications" else "Not listening",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = if (isListening)
                                "NotifLedger can capture notifications from allowed sources."
                            else
                                "Notification listener access is disabled. Tap to enable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!isListening) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                )
                            },
                        ) {
                            Text("Enable")
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

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

            // Default currency
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Default currency", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    var editingCurrency by remember { mutableStateOf(false) }
                    var currencyInput by remember { mutableStateOf("") }
                    if (editingCurrency) {
                        OutlinedTextField(
                            value = currencyInput,
                            onValueChange = { currencyInput = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    viewModel.settings.setDefaultCurrency(currencyInput.trim())
                                }
                                editingCurrency = false
                            },
                        ) {
                            Text("Save")
                        }
                    } else {
                        Text(
                            text = defaultCurrency,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = {
                            currencyInput = defaultCurrency
                            editingCurrency = true
                        }) {
                            Text("Change")
                        }
                    }
                }
            }
        }
    }
}
