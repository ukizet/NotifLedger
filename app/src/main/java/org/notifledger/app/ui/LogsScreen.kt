package org.notifledger.app.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleX
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.TriangleAlert
import org.notifledger.app.R
import org.notifledger.app.log.LogEntry
import org.notifledger.app.log.LogLevel
import org.notifledger.app.ui.components.NotifLedgerTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val entries by viewModel.logEntries.collectAsState()
    var filterLevel by remember { mutableStateOf<LogLevel?>(null) }
    val listState = rememberLazyListState()

    val filtered = remember(entries, filterLevel) {
        if (filterLevel != null) entries.filter { it.level == filterLevel } else entries
    }

    LaunchedEffect(entries.size, filterLevel) {
        if (filtered.isEmpty()) return@LaunchedEffect
        val nearBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let {
            it >= filtered.size - 3
        } ?: true
        if (nearBottom) listState.animateScrollToItem(filtered.size - 1)
    }

    Scaffold(
        topBar = {
            NotifLedgerTopAppBar(
                title = stringResource(R.string.logs),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { org.notifledger.app.log.AppLogger.clear() }) {
                        Icon(Lucide.Trash2, contentDescription = stringResource(R.string.clear_logs))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = filterLevel == null,
                    onClick = { filterLevel = null },
                    label = { Text(stringResource(R.string.filter_all)) },
                )
                FilterChip(
                    selected = filterLevel == LogLevel.INFO,
                    onClick = { filterLevel = LogLevel.INFO },
                    label = { Text(stringResource(R.string.filter_info)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
                FilterChip(
                    selected = filterLevel == LogLevel.WARN,
                    onClick = { filterLevel = LogLevel.WARN },
                    label = { Text(stringResource(R.string.filter_warnings)) },
                    colors = FilterChipDefaults.filterChipColors(
                        // Teal/secondary reads as "less severe than error", not as a third accent category.
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                )
                FilterChip(
                    selected = filterLevel == LogLevel.ERROR,
                    onClick = { filterLevel = LogLevel.ERROR },
                    label = { Text(stringResource(R.string.filter_errors)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))

            val infoCount = entries.count { it.level == LogLevel.INFO }
            val warnCount = entries.count { it.level == LogLevel.WARN }
            val errorCount = entries.count { it.level == LogLevel.ERROR }
            Text(
                text = stringResource(R.string.log_summary, entries.size, infoCount, warnCount, errorCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (entries.isEmpty())
                            stringResource(R.string.no_log_entries)
                        else
                            stringResource(R.string.no_log_match_filter),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
    }
}

@Composable
private fun LogEntryCard(entry: LogEntry) {
    val (bgColor, icon, iconTint) = when (entry.level) {
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
