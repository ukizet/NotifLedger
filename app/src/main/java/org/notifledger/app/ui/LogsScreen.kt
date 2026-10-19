package org.notifledger.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.composables.icons.lucide.CircleX
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.TriangleAlert
import org.notifledger.app.R
import org.notifledger.app.log.AppLogger
import org.notifledger.app.log.LogEntry
import org.notifledger.app.log.LogLevel
import org.notifledger.app.ui.components.NotifLedgerTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: LogsViewModel,
    onBack: () -> Unit,
) {
    val entries by viewModel.logEntries.collectAsState()
    var filterLevel by remember { mutableStateOf<LogLevel?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var rawMode by remember { mutableStateOf(false) }
    var rawText by remember { mutableStateOf<String?>(null) }
    var rawRefresh by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(rawMode, rawRefresh) {
        rawText = if (rawMode) {
            withContext(Dispatchers.IO) { AppLogger.logFile()?.readText() }
        } else {
            null
        }
    }

    Scaffold(
        topBar = {
            LogsTopBar(
                onBack = onBack,
                onClearClick = { showClearConfirm = true },
            )
        },
    ) { padding ->
        LogsBody(
            rawMode = rawMode,
            rawText = rawText,
            entries = entries,
            filterLevel = filterLevel,
            onRawModeChange = { rawMode = it },
            onFilterLevelChange = { filterLevel = it },
            listState = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        )
    }

    if (showClearConfirm) {
        ClearLogsDialog(
            onConfirm = {
                AppLogger.clear()
                rawRefresh++
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}

@Composable
private fun LogsTopBar(onBack: () -> Unit, onClearClick: () -> Unit) {
    NotifLedgerTopAppBar(
        title = stringResource(R.string.logs),
        onBack = onBack,
        actions = {
            IconButton(onClick = onClearClick) {
                Icon(Lucide.Trash2, contentDescription = stringResource(R.string.clear_logs))
            }
        },
    )
}

@Composable
private fun LogsBody(
    rawMode: Boolean,
    rawText: String?,
    entries: List<LogEntry>,
    filterLevel: LogLevel?,
    onRawModeChange: (Boolean) -> Unit,
    onFilterLevelChange: (LogLevel?) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val filtered = remember(entries, filterLevel) {
        if (filterLevel != null) entries.filter { it.level == filterLevel } else entries
    }
    LogAutoScroll(filtered = filtered, rawMode = rawMode, listState = listState)

    Column(modifier = modifier) {
        LogModeChips(rawMode = rawMode, onSelect = onRawModeChange)
        Spacer(Modifier.height(8.dp))
        if (rawMode) {
            RawLogView(rawText)
        } else {
            LogEntriesPanel(
                entries = entries,
                filtered = filtered,
                filterLevel = filterLevel,
                onFilterChange = onFilterLevelChange,
                listState = listState,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogModeChips(rawMode: Boolean, onSelect: (Boolean) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = !rawMode,
            onClick = { onSelect(false) },
            label = { Text(stringResource(R.string.logs_entries)) },
        )
        FilterChip(
            selected = rawMode,
            onClick = { onSelect(true) },
            label = { Text(stringResource(R.string.logs_raw)) },
        )
    }
}

@Composable
private fun LogEntriesPanel(
    entries: List<LogEntry>,
    filtered: List<LogEntry>,
    filterLevel: LogLevel?,
    onFilterChange: (LogLevel?) -> Unit,
    listState: LazyListState,
) {
    LevelFilterRow(filterLevel = filterLevel, onFilterChange = onFilterChange)
    Spacer(Modifier.height(8.dp))
    LogSummary(entries)
    Spacer(Modifier.height(8.dp))
    if (filtered.isEmpty()) {
        EmptyLogsBox(isLogEmpty = entries.isEmpty())
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(filtered, key = { it.sequence }) { entry ->
                LogEntryCard(entry)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LevelFilterRow(filterLevel: LogLevel?, onFilterChange: (LogLevel?) -> Unit) {
    val levelFilters = listOf(
        null to R.string.filter_all,
        LogLevel.INFO to R.string.filter_info,
        LogLevel.WARN to R.string.filter_warnings,
        LogLevel.ERROR to R.string.filter_errors,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        levelFilters.forEach { (level, labelRes) ->
            FilterChip(
                selected = filterLevel == level,
                onClick = { onFilterChange(level) },
                label = { Text(stringResource(labelRes)) },
                colors = levelFilterChipColors(level),
            )
        }
    }
}

@Composable
private fun LogSummary(entries: List<LogEntry>) {
    val counts = remember(entries) {
        val info = entries.count { it.level == LogLevel.INFO }
        val warn = entries.count { it.level == LogLevel.WARN }
        val error = entries.count { it.level == LogLevel.ERROR }
        Triple(info, warn, error)
    }
    Text(
        text = stringResource(R.string.log_summary, entries.size, counts.first, counts.second, counts.third),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EmptyLogsBox(isLogEmpty: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isLogEmpty)
                stringResource(R.string.no_log_entries)
            else
                stringResource(R.string.no_log_match_filter),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClearLogsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_logs)) },
        text = { Text(stringResource(R.string.logs_clear_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.clear_logs), color = MaterialTheme.colorScheme.error)
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
private fun LogAutoScroll(
    filtered: List<LogEntry>,
    rawMode: Boolean,
    listState: LazyListState,
) {
    val shouldAutoScroll = remember {
        derivedStateOf {
            if (filtered.isEmpty()) false
            else {
                val nearBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let {
                    it >= filtered.size - 3
                } ?: true
                nearBottom
            }
        }
    }
    LaunchedEffect(shouldAutoScroll.value, rawMode) {
        if (!rawMode && shouldAutoScroll.value) {
            listState.animateScrollToItem(filtered.size - 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun levelFilterChipColors(level: LogLevel?) = when (level) {
    LogLevel.INFO -> FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    )
    // Teal/secondary reads as "less severe than error", not as a third accent category.
    LogLevel.WARN -> FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    LogLevel.ERROR -> FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
    )
    else -> FilterChipDefaults.filterChipColors()
}

@Composable
private fun RawLogView(text: String?) {
    val fileName = AppLogger.logFile()?.name
    Column(modifier = Modifier.fillMaxSize()) {
        if (fileName != null) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (text.isNullOrBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.logs_raw_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun logCardVisuals(level: LogLevel): Triple<Color, ImageVector, Color> = when (level) {
    LogLevel.INFO -> Triple(
        MaterialTheme.colorScheme.surfaceVariant,
        Lucide.Info,
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LogLevel.WARN -> Triple(
        MaterialTheme.colorScheme.secondaryContainer,
        Lucide.TriangleAlert,
        MaterialTheme.colorScheme.onSecondaryContainer,
    )
    LogLevel.ERROR -> Triple(
        MaterialTheme.colorScheme.errorContainer,
        Lucide.CircleX,
        MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
private fun LogEntryCard(entry: LogEntry) {
    val (bgColor, icon, iconTint) = logCardVisuals(entry.level)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = iconTint,
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                )
                Spacer(Modifier.height(2.dp))
                Row {
                    Text(
                        text = entry.tag,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = entry.timestamp.substringAfterLast("T").take(8),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
