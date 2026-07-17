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
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.Lucide
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.notifledger.app.R
import org.notifledger.app.ui.components.EditableSettingRow
import org.notifledger.app.ui.components.NotifLedgerTopAppBar
import org.notifledger.app.ui.util.rememberListenerEnabled

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
    val isListening = rememberListenerEnabled(context)

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

    Scaffold(
        topBar = {
            NotifLedgerTopAppBar(
                title = stringResource(R.string.settings),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            ListenerStatusCard(isListening = isListening)

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.journal_file), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (journalPath.isNotBlank()) journalPath
                        else stringResource(R.string.not_set),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { pickFileLauncher.launch(arrayOf("*/*")) }) {
                        Text(stringResource(R.string.select_journal_file))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            EditableSettingRow(
                title = stringResource(R.string.default_account),
                value = defaultAccount,
                onSave = { scope.launch { viewModel.settings.setDefaultAccount(it) } },
            )

            Spacer(Modifier.height(12.dp))

            EditableSettingRow(
                title = stringResource(R.string.default_currency),
                value = defaultCurrency,
                onSave = { scope.launch { viewModel.settings.setDefaultCurrency(it) } },
            )
        }
    }
}

@Composable
private fun ListenerStatusCard(isListening: Boolean) {
    val context = LocalContext.current
    val containerColor = if (isListening)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.errorContainer
    val onContainerColor = if (isListening)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onErrorContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isListening) Lucide.Bell else Lucide.BellOff,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = onContainerColor,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isListening)
                        stringResource(R.string.listener_status_on)
                    else
                        stringResource(R.string.listener_status_off),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (isListening)
                        stringResource(R.string.listener_status_on_hint)
                    else
                        stringResource(R.string.listener_status_off_hint),
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
                    Text(stringResource(R.string.enable))
                }
            }
        }
    }
}
