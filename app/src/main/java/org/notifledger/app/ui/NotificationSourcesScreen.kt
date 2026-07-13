package org.notifledger.app.ui

import android.content.pm.PackageManager
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSourcesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val sources by viewModel.notificationSources.collectAsState()
    val context = LocalContext.current
    val pm = context.packageManager

    // Resolve package names to labels for display
    val appLabels = remember(sources) {
        sources.mapNotNull { pkg ->
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                pkg to info.loadLabel(pm).toString()
            } catch (_: Exception) {
                pkg to pkg
            }
        }
    }

    var showSearchDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification sources") },
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
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                text = "Apps that NotifLedger listens to for notifications.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            OutlinedButton(onClick = { showSearchDialog = true }) {
                Icon(Lucide.Plus, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add app")
            }

            Spacer(Modifier.height(12.dp))

            if (appLabels.isEmpty()) {
                Text(
                    "No apps selected. Tap \"Add app\" to choose which apps to listen to.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn {
                items(appLabels, key = { it.first }) { (pkg, label) ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                            IconButton(onClick = {
                                viewModel.setNotificationSources(sources - pkg)
                            }) {
                                Icon(
                                    Lucide.Minus,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
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

@Composable
private fun SearchAppsDialog(
    selectedPackages: Set<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager

    // All installed apps with launcher icons, sorted by label
    val allApps = remember {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .sortedBy { (_, label) -> label }
            .filter { (pkg, _) -> pkg !in selectedPackages } // Only show unselected ones
    }

    var query by remember { mutableStateOf("") }

    val filtered = remember(allApps, query) {
        if (query.isBlank()) allApps
        else allApps.filter { (pkg, label) ->
            label.contains(query, ignoreCase = true) ||
            pkg.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add app") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps...") },
                    leadingIcon = { Icon(Lucide.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                if (filtered.isEmpty()) {
                    Text(
                        "No apps match your search.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                LazyColumn(modifier = Modifier.height(400.dp)) {
                    items(filtered, key = { it.first }) { (pkg, label) ->
                        TextButton(
                            onClick = { onSelect(pkg) },
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
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
