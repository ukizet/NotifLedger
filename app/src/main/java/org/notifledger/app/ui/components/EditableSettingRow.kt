package org.notifledger.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.notifledger.app.R

/**
 * Repeated "Card with title + value, edit-toggle reveals an OutlinedTextField + Save"
 * row used by SettingsScreen. Three near-identical blocks justified the extraction.
 *
 * Container color is explicitly surfaceVariant so the card stays visible on the
 * AMOLED dark theme (where surface = #000000); see Theme.kt.
 */
@Composable
fun EditableSettingRow(
    title: String,
    value: String,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            if (editing) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        onSave(input.trim())
                        editing = false
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            } else {
                Text(value, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = {
                    input = value
                    editing = true
                }) {
                    Text(stringResource(R.string.change))
                }
            }
        }
    }
}
