package org.notifledger.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import org.notifledger.app.R
import org.notifledger.app.model.FilterMode
import org.notifledger.app.model.TransactionFilter
import org.notifledger.app.ui.FilterAddGreen

@Composable
fun FilterRow(
    filters: List<TransactionFilter>,
    maxFilters: Int,
    accountSuggestions: List<String>,
    onAdd: (String) -> Unit,
    onRename: (Int, String) -> Unit,
    onChangeCategory: (Int, String) -> Unit,
    onDelete: (Int) -> Unit,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var renameIndex by remember { mutableStateOf(-1) }
    var changeIndex by remember { mutableStateOf(-1) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (filters.size < maxFilters) {
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                colors = if (filters.isEmpty()) {
                    ButtonDefaults.buttonColors(
                        containerColor = FilterAddGreen,
                        contentColor = Color.White,
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            ) {
                Icon(
                    Lucide.Plus,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.filter_add),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        filters.forEachIndexed { index, filter ->
            FilterChipItem(
                label = filter.label,
                mode = filter.mode,
                onToggle = { onToggle(index) },
                onRename = { renameIndex = index },
                onChangeCategory = { changeIndex = index },
                onDelete = { onDelete(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showAddDialog) {
        AccountFilterDialog(
            title = stringResource(R.string.filter_add_title),
            confirmLabel = stringResource(R.string.filter_add),
            initialQuery = "",
            accountSuggestions = accountSuggestions,
            usedAccounts = filters.map { it.account },
            onConfirm = {
                onAdd(it)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    if (renameIndex in filters.indices) {
        RenameFilterDialog(
            initialLabel = filters[renameIndex].label,
            onConfirm = {
                onRename(renameIndex, it)
                renameIndex = -1
            },
            onDismiss = { renameIndex = -1 },
        )
    }

    if (changeIndex in filters.indices) {
        AccountFilterDialog(
            title = stringResource(R.string.filter_change_title),
            confirmLabel = stringResource(R.string.save),
            initialQuery = filters[changeIndex].account,
            accountSuggestions = accountSuggestions,
            usedAccounts = filters.filterIndexed { i, _ -> i != changeIndex }.map { it.account },
            onConfirm = {
                onChangeCategory(changeIndex, it)
                changeIndex = -1
            },
            onDismiss = { changeIndex = -1 },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterChipItem(
    label: String,
    mode: FilterMode,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onChangeCategory: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val containerColor = when (mode) {
        FilterMode.INCLUDE -> FilterAddGreen
        FilterMode.EXCLUDE -> MaterialTheme.colorScheme.errorContainer
        FilterMode.OFF -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (mode) {
        FilterMode.INCLUDE -> Color.White
        FilterMode.EXCLUDE -> MaterialTheme.colorScheme.onErrorContainer
        FilterMode.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val leadingIcon = when (mode) {
        FilterMode.INCLUDE -> Lucide.Check
        FilterMode.EXCLUDE -> Lucide.Minus
        FilterMode.OFF -> null
    }
    val stateLabel = when (mode) {
        FilterMode.INCLUDE -> stringResource(R.string.filter_state_include)
        FilterMode.EXCLUDE -> stringResource(R.string.filter_state_exclude)
        FilterMode.OFF -> stringResource(R.string.filter_state_off)
    }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = containerColor,
            contentColor = contentColor,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .semantics { stateDescription = stateLabel }
                .combinedClickable(
                    onClick = onToggle,
                    onLongClick = { menuExpanded = true },
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.filter_menu_rename)) },
                onClick = {
                    menuExpanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.filter_menu_change)) },
                onClick = {
                    menuExpanded = false
                    onChangeCategory()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.filter_menu_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun AccountFilterDialog(
    title: String,
    confirmLabel: String,
    initialQuery: String,
    accountSuggestions: List<String>,
    usedAccounts: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(initialQuery) }
    val trimmedQuery = query.trim()
    val matchedAccount = accountSuggestions.firstOrNull { it == trimmedQuery }
    val isDuplicate = matchedAccount != null && matchedAccount in usedAccounts
    val canConfirm = matchedAccount != null && !isDuplicate
    val supportingText = when {
        trimmedQuery.isEmpty() -> null
        matchedAccount == null -> stringResource(R.string.filter_account_invalid)
        isDuplicate -> stringResource(R.string.filter_account_duplicate)
        else -> null
    }
    val suggestions = accountSuggestions
        .filter { it.contains(trimmedQuery, ignoreCase = true) && it !in usedAccounts }
        .take(6)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.filter_account_label)) },
                    singleLine = true,
                    supportingText = if (supportingText != null) {
                        { Text(supportingText) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (suggestions.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        suggestions.forEach { suggestion ->
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { query = suggestion }
                                    .padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { matchedAccount?.let(onConfirm) },
                enabled = canConfirm,
            ) {
                Text(confirmLabel)
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
private fun RenameFilterDialog(
    initialLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(initialLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_rename_title)) },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.filter_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(label.trim()) },
                enabled = label.isNotBlank(),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
