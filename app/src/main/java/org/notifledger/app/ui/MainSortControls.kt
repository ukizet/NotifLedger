package org.notifledger.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.notifledger.app.R
import org.notifledger.app.model.SortOrder

@Composable
internal fun SortControls(
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    pageLimit: Int,
    onPageLimitChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SortOrderDropdown(
            sortOrder = sortOrder,
            onSortOrderChange = onSortOrderChange,
            modifier = Modifier.weight(1f),
        )
        PageSizeEditor(pageLimit = pageLimit, onPageLimitChange = onPageLimitChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortOrderDropdown(
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = sortLabel(sortOrder),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            textStyle = MaterialTheme.typography.bodySmall,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(sortLabel(order)) },
                    onClick = {
                        onSortOrderChange(order)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PageSizeEditor(
    pageLimit: Int,
    onPageLimitChange: (Int) -> Unit,
) {
    var editingLimit by remember { mutableStateOf(false) }
    var limitInput by remember { mutableStateOf("") }

    if (editingLimit) {
        OutlinedTextField(
            value = limitInput,
            onValueChange = { limitInput = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.page_size)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(120.dp),
            textStyle = MaterialTheme.typography.bodySmall,
        )
        TextButton(
            onClick = {
                val parsed = limitInput.toIntOrNull()
                if (parsed != null && parsed > 0) {
                    onPageLimitChange(parsed)
                    editingLimit = false
                }
            },
        ) {
            Text(stringResource(R.string.done), style = MaterialTheme.typography.bodySmall)
        }
    } else {
        OutlinedButton(
            onClick = {
                limitInput = pageLimit.toString()
                editingLimit = true
            },
        ) {
            Text(
                stringResource(R.string.page_per_page, pageLimit),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun sortLabel(key: SortOrder): String = when (key) {
    SortOrder.NewestFirst -> stringResource(R.string.sort_newest_first)
    SortOrder.OldestFirst -> stringResource(R.string.sort_oldest_first)
    SortOrder.HighestAmount -> stringResource(R.string.sort_highest_amount)
    SortOrder.LowestAmount -> stringResource(R.string.sort_lowest_amount)
}
