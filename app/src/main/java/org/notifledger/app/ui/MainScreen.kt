package org.notifledger.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.List
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.ScrollText
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.X
import kotlinx.coroutines.launch
import org.notifledger.app.R
import org.notifledger.app.model.SortOrder
import org.notifledger.app.model.Transaction
import org.notifledger.app.notification.NotificationHandler
import org.notifledger.app.ui.components.FilterRow
import org.notifledger.app.ui.components.OnboardingChecklist
import org.notifledger.app.ui.components.OnboardingItem
import org.notifledger.app.ui.components.TransactionForm
import org.notifledger.app.ui.components.TransactionRow
import org.notifledger.app.ui.util.rememberListenerEnabled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    navController: NavController,
    onNavigateToQuickAdd: () -> Unit,
    onNavigateToRawJournal: () -> Unit,
    onNavigateToCategorizationRules: () -> Unit,
    onNavigateToNotificationSources: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val entries by viewModel.entries.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val filterLimit by viewModel.filterLimit.collectAsState()
    val hasEntries by viewModel.hasEntries.collectAsState()
    val journalPath by viewModel.journalPath.collectAsState()
    val notificationSources by viewModel.notificationSources.collectAsState()
    val payeeSuggestions by viewModel.existingPayees.collectAsState()
    val accountSuggestions by viewModel.existingAccounts.collectAsState()
    val defaultCurrency by viewModel.defaultCurrency.collectAsState()
    val currentSortOrder by viewModel.sortOrder.collectAsState()
    val currentPageLimit by viewModel.pageLimit.collectAsState()
    val defaultAccount by viewModel.defaultAccount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    val lastListenerConnectedAt by viewModel.lastListenerConnectedAt.collectAsState()
    val context = LocalContext.current
    val isListening = rememberListenerEnabled(context)
    val listenerNeedsAttention = remember(isListening, lastListenerConnectedAt) {
        NotificationHandler.needsListenerAttention(isListening, lastListenerConnectedAt)
    }
    var listenerBannerDismissed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiMessage) {
        uiMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.reloadEntries()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    var editingIndex by remember { mutableStateOf(-1) }
    var showSimulateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            val cdListenerOff = stringResource(R.string.cd_listener_off)
            val cdNoSources = stringResource(R.string.cd_no_sources)
            val cdListening = stringResource(R.string.cd_listening)
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        when {
                            !isListening -> Icon(
                                Lucide.BellOff,
                                contentDescription = cdListenerOff,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            notificationSources.isEmpty() -> Icon(
                                Lucide.Bell,
                                contentDescription = cdNoSources,
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                            else -> Icon(
                                Lucide.Bell,
                                contentDescription = cdListening,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            MainBottomBar(navController = navController, onSimulate = { showSimulateDialog = true })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToQuickAdd) {
                Icon(Lucide.Plus, contentDescription = stringResource(R.string.quick_add))
            }
        },
    ) { padding ->
        val onboardingItems = listOf(
            OnboardingItem(
                label = stringResource(R.string.onboarding_pick_journal),
                done = journalPath.isNotBlank(),
                onClick = { pickFileLauncher.launch(arrayOf("*/*")) },
            ),
            OnboardingItem(
                label = stringResource(R.string.onboarding_grant_access),
                done = isListening,
                onClick = onNavigateToSettings,
            ),
            OnboardingItem(
                label = stringResource(R.string.onboarding_add_source),
                done = notificationSources.isNotEmpty(),
                onClick = onNavigateToNotificationSources,
            ),
            OnboardingItem(
                label = stringResource(R.string.onboarding_set_account),
                done = defaultAccount.isNotBlank(),
                onClick = onNavigateToSettings,
            ),
        )

        if (journalPath.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(
                    onClick = { pickFileLauncher.launch(arrayOf("*/*")) },
                ) {
                    Text(stringResource(R.string.no_journal_selected))
                }
            }
        } else if (!hasEntries) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (listenerNeedsAttention && !listenerBannerDismissed) {
                    ListenerHeartbeatBanner(
                        onDismiss = { listenerBannerDismissed = true },
                        context = context,
                    )
                }
                OnboardingChecklist(
                    items = onboardingItems,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Text(stringResource(R.string.no_transactions))
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (listenerNeedsAttention && !listenerBannerDismissed) {
                    ListenerHeartbeatBanner(
                        onDismiss = { listenerBannerDismissed = true },
                        context = context,
                    )
                }
                OnboardingChecklist(
                    items = onboardingItems,
                    modifier = Modifier.padding(top = 12.dp),
                )
                // Sort + page limit controls
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var sortExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = sortExpanded,
                        onExpandedChange = { sortExpanded = it },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = sortLabel(currentSortOrder),
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sortExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            textStyle = MaterialTheme.typography.bodySmall,
                        )
                        ExposedDropdownMenu(
                            expanded = sortExpanded,
                            onDismissRequest = { sortExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_newest_first)) },
                                onClick = { viewModel.setSortOrder(SortOrder.NewestFirst); sortExpanded = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_oldest_first)) },
                                onClick = { viewModel.setSortOrder(SortOrder.OldestFirst); sortExpanded = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_highest_amount)) },
                                onClick = { viewModel.setSortOrder(SortOrder.HighestAmount); sortExpanded = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_lowest_amount)) },
                                onClick = { viewModel.setSortOrder(SortOrder.LowestAmount); sortExpanded = false },
                            )
                        }
                    }

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
                                    viewModel.setPageLimit(parsed)
                                    editingLimit = false
                                }
                            },
                        ) {
                            Text(stringResource(R.string.done), style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                limitInput = currentPageLimit.toString()
                                editingLimit = true
                            },
                        ) {
                            Text(
                                stringResource(R.string.page_per_page, currentPageLimit),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                FilterRow(
                    filters = filters,
                    maxFilters = filterLimit,
                    accountSuggestions = accountSuggestions,
                    onAdd = viewModel::addFilter,
                    onRename = viewModel::renameFilter,
                    onChangeCategory = viewModel::changeFilterCategory,
                    onDelete = viewModel::deleteFilter,
                    onToggle = viewModel::toggleFilterActive,
                )

                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.no_matching_transactions))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 88.dp),
                    ) {
                        itemsIndexed(entries, key = { _, entry -> entry.lineOffset }) { index, entry ->
                            TransactionRow(
                                entry = entry,
                                onEdit = { editingIndex = index },
                                onDelete = { viewModel.deleteTransaction(entry.lineOffset) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSimulateDialog) {
        SimulateNotificationDialog(
            viewModel = viewModel,
            onDismiss = {
                showSimulateDialog = false
                viewModel.reloadEntries()
            },
        )
    }

    if (editingIndex >= 0 && editingIndex < entries.size) {
        val entry = entries[editingIndex]
        val tx = Transaction(
            date = entry.date,
            payee = entry.payee,
            postings = entry.postings,
        )
        AlertDialog(
            onDismissRequest = { editingIndex = -1 },
            title = { Text(stringResource(R.string.edit_transaction)) },
            text = {
                TransactionForm(
                    initial = tx,
                    payeeSuggestions = payeeSuggestions,
                    accountSuggestions = accountSuggestions,
                    defaultCurrency = defaultCurrency,
                    onSave = { updated ->
                        viewModel.editTransaction(entry.lineOffset, updated)
                        editingIndex = -1
                    },
                    onCancel = { editingIndex = -1 },
                )
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun sortLabel(key: SortOrder): String = when (key) {
    SortOrder.NewestFirst -> stringResource(R.string.sort_newest_first)
    SortOrder.OldestFirst -> stringResource(R.string.sort_oldest_first)
    SortOrder.HighestAmount -> stringResource(R.string.sort_highest_amount)
    SortOrder.LowestAmount -> stringResource(R.string.sort_lowest_amount)
}

@Composable
private fun MainBottomBar(
    navController: NavController,
    onSimulate: () -> Unit,
) {
    val currentRoute by navController.currentBackStackEntryAsState()
    val route = currentRoute?.destination?.route

    NavigationBar {
        BottomNavItem(
            icon = Lucide.Tags,
            label = stringResource(R.string.nav_rules),
            selected = route == Screen.CategorizationRules.route,
            onClick = { navController.navigate(Screen.CategorizationRules.route) },
        )
        BottomNavItem(
            icon = Lucide.List,
            label = stringResource(R.string.nav_journal),
            selected = route == Screen.RawJournal.route,
            onClick = { navController.navigate(Screen.RawJournal.route) },
        )
        BottomNavItem(
            icon = Lucide.Bell,
            label = stringResource(R.string.nav_sources),
            selected = route == Screen.NotificationSources.route,
            onClick = { navController.navigate(Screen.NotificationSources.route) },
        )
        BottomNavItem(
            icon = Lucide.ScrollText,
            label = stringResource(R.string.logs),
            selected = route == Screen.Logs.route,
            onClick = { navController.navigate(Screen.Logs.route) },
        )
        BottomNavItem(
            icon = Lucide.Bot,
            label = stringResource(R.string.nav_test),
            selected = false,
            onClick = onSimulate,
        )
        BottomNavItem(
            icon = Lucide.Settings,
            label = stringResource(R.string.settings),
            selected = route == Screen.Settings.route,
            onClick = { navController.navigate(Screen.Settings.route) },
        )
    }
}

@Composable
private fun RowScope.BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
        selected = selected,
        onClick = onClick,
    )
}

@Composable
private fun SimulateNotificationDialog(
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
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.simulate_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.notif_title_label)) },
                    placeholder = { Text(stringResource(R.string.notif_title_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
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
        },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        resultMessage = viewModel.simulateNotification(
                            title = title.trim(),
                            text = text.trim(),
                        )
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
private fun ListenerHeartbeatBanner(onDismiss: () -> Unit, context: android.content.Context) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                )
                onDismiss()
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.listener_needs_attention),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            IconButton(
                onClick = onDismiss,
            ) {
                Icon(
                    imageVector = Lucide.X,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
