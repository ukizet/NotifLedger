package org.notifledger.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.notifledger.app.R
import org.notifledger.app.ui.MainViewModel

@Composable
fun SimulateNotificationDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.simulate_notification)) },
        text = {
            SimulateNotificationFields(
                title = title,
                text = text,
                resultMessage = resultMessage,
                onTitleChange = { title = it },
                onTextChange = { text = it },
            )
        },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        resultMessage = viewModel.simulateNotification(title.trim(), text.trim())
                    }
                },
            ) {
                Text(stringResource(R.string.test_parse_write))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
    )
}

@Composable
private fun SimulateNotificationFields(
    title: String,
    text: String,
    resultMessage: String,
    onTitleChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.simulate_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.notif_title_label)) },
            placeholder = { Text(stringResource(R.string.notif_title_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text(stringResource(R.string.notif_text_label)) },
            placeholder = { Text(stringResource(R.string.notif_text_placeholder)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().height(100.dp),
        )
        if (resultMessage.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(resultMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
