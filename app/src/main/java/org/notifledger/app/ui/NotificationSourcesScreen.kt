package org.notifledger.app.ui

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.notifledger.app.R
import org.notifledger.app.log.AppLogger
import org.notifledger.app.ui.components.NotifLedgerTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSourcesScreen(
    viewModel: NotificationSourcesViewModel,
    onBack: () -> Unit,
) {
    val sources by viewModel.notificationSources.collectAsState()
    val pm = LocalContext.current.packageManager
    var appLabels by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var showSearchDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sources) {
        appLabels = resolveAppLabels(pm, sources)
    }

    Scaffold(
        topBar = {
            NotifLedgerTopAppBar(
                title = stringResource(R.string.notification_sources),
                onBack = onBack,
            )
        },
    ) { padding ->
        NotificationSourcesContent(
            appLabels = appLabels,
            onAddClick = { showSearchDialog = true },
            onRemove = { pkg -> viewModel.setNotificationSources(sources - pkg) },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }

    if (showSearchDialog) {
        SearchAppsDialog(
            selectedPackages = sources.toSet(),
            onSelect = { pkg ->
                if (pkg !in sources) {
                    viewModel.setNotificationSources(sources + pkg)
                }
                showSearchDialog = false
            },
            onDismiss = { showSearchDialog = false },
        )
    }
}

private suspend fun resolveAppLabels(
    pm: PackageManager,
    packages: List<String>,
): List<Pair<String, String>> = withContext(Dispatchers.IO) {
    packages.mapNotNull { pkg ->
        try {
            val info = pm.getApplicationInfo(pkg, 0)
            pkg to info.loadLabel(pm).toString()
        } catch (e: Exception) {
            AppLogger.warn("Sources", "Label lookup failed for $pkg: ${e.message}")
            pkg to pkg
        }
    }
}

@Composable
private fun NotificationSourcesContent(
    appLabels: List<Pair<String, String>>,
    onAddClick: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.notif_sources_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        OutlinedButton(onClick = onAddClick) {
            Icon(Lucide.Plus, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.add_app))
        }

        Spacer(Modifier.height(12.dp))

        if (appLabels.isEmpty()) {
            Text(
                stringResource(R.string.no_sources_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn {
            items(appLabels, key = { it.first }) { (pkg, label) ->
                SourceRow(pkg = pkg, label = label, onRemove = { onRemove(pkg) })
            }
        }
    }
}

@Composable
private fun SourceRow(
    pkg: String,
    label: String,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Lucide.Trash2,
                    contentDescription = stringResource(R.string.remove),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SearchAppsDialog(
    selectedPackages: Set<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val pm = LocalContext.current.packageManager
    var allApps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        allApps = queryInstalledApps(pm)
    }

    val loaded = allApps
    val filtered = remember(loaded, selectedPackages, query) {
        filterApps(loaded, selectedPackages, query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_app)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    leadingIcon = { Icon(Lucide.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                SearchResultsList(
                    loading = loaded == null,
                    apps = filtered,
                    onSelect = onSelect,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private suspend fun queryInstalledApps(pm: PackageManager): List<Pair<String, String>> =
    withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .sortedBy { (_, label) -> label }
    }

private fun filterApps(
    loaded: List<Pair<String, String>>?,
    selectedPackages: Set<String>,
    query: String,
): List<Pair<String, String>> {
    if (loaded == null) return emptyList()
    val unselected = loaded.filter { (pkg, _) -> pkg !in selectedPackages }
    if (query.isBlank()) return unselected
    return unselected.filter { (pkg, label) ->
        label.contains(query, ignoreCase = true) ||
            pkg.contains(query, ignoreCase = true)
    }
}

@Composable
private fun SearchResultsList(
    loading: Boolean,
    apps: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    when {
        loading -> Box(
            modifier = Modifier.fillMaxWidth().height(400.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        apps.isEmpty() -> Text(
            stringResource(R.string.no_apps_match),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    LazyColumn(modifier = Modifier.height(400.dp)) {
        items(apps, key = { it.first }) { (pkg, label) ->
            AppResultRow(pkg = pkg, label = label, onSelect = { onSelect(pkg) })
        }
    }
}

@Composable
private fun AppResultRow(
    pkg: String,
    label: String,
    onSelect: () -> Unit,
) {
    TextButton(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
