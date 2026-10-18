package org.notifledger.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.X
import kotlinx.coroutines.launch
import org.notifledger.app.R
import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.model.SortOrder
import org.notifledger.app.model.Transaction
import org.notifledger.app.ui.components.BulkChangeDialog
import org.notifledger.app.ui.components.FilterRow
import org.notifledger.app.ui.components.MainBottomBar
import org.notifledger.app.ui.components.OnboardingChecklist
import org.notifledger.app.ui.components.OnboardingItem
import org.notifledger.app.ui.components.SimulateNotificationDialog
import org.notifledger.app.ui.components.TransactionForm
import org.notifledger.app.ui.components.TransactionRow
import org.notifledger.app.ui.util.rememberListenerEnabled

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
    val entryPage by viewModel.entryPage.collectAsState()
    val entries = entryPage.entries
    val hasEntries by viewModel.hasEntries.collectAsState()
    val journalPath by viewModel.journalPath.collectAsState()
    val notificationSources by viewModel.notificationSources.collectAsState()
    val payeeSuggestions by viewModel.existingPayees.collectAsState()
    val accountSuggestions by viewModel.existingAccounts.collectAsState()
    val defaultCurrency by viewModel.defaultCurrency.collectAsState()
    val defaultAccount by viewModel.defaultAccount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    val context = LocalContext.current
    val isListening = rememberListenerEnabled(context)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(uiMessage) {
        uiMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(entryPage.page) {
        listState.scrollToItem(0)
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
    var selectedOffsets by remember { mutableStateOf(setOf<Int>()) }
    var showBulkChange by remember { mutableStateOf(false) }

    LaunchedEffect(entries) {
        selectedOffsets = emptySet()
        if (editingIndex >= entries.size) editingIndex = -1
    }

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

    Scaffold(
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OnboardingChecklist(
                items = onboardingItems,
                modifier = Modifier.padding(top = 12.dp),
            )
            MainContent(
                journalPath = journalPath,
                hasEntries = hasEntries,
                isLoading = isLoading,
                onPickFile = { pickFileLauncher.launch(arrayOf("*/*")) },
                viewModel = viewModel,
                entries = entries,
                entryPage = entryPage,
                listState = listState,
                selectedOffsets = selectedOffsets,
                onSelectedOffsetsChange = { selectedOffsets = it },
                onEdit = { editingIndex = it },
                onShowBulkChange = { showBulkChange = true },
            )
        }
    }

    if (showBulkChange) {
        BulkChangeDialog(
            accountSuggestions = accountSuggestions,
            onConfirm = { date, payee, currency, account ->
                viewModel.changeTransactions(selectedOffsets, date, payee, currency, account)
                selectedOffsets = emptySet()
                showBulkChange = false
            },
            onDismiss = { showBulkChange = false },
        )
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
        EditTransactionDialog(
            entry = entry,
            payeeSuggestions = payeeSuggestions,
            accountSuggestions = accountSuggestions,
            defaultCurrency = defaultCurrency,
            onSave = { updated ->
                viewModel.editTransaction(entry.lineOffset, updated)
                editingIndex = -1
            },
            onDismiss = { editingIndex = -1 },
        )
    }
}

@Composable
private fun MainContent(
    journalPath: String,
    hasEntries: Boolean,
    isLoading: Boolean,
    onPickFile: () -> Unit,
    viewModel: MainViewModel,
    entries: List<JournalEntry>,
    entryPage: EntryPage,
    listState: LazyListState,
    selectedOffsets: Set<Int>,
    onSelectedOffsetsChange: (Set<Int>) -> Unit,
    onEdit: (Int) -> Unit,
    onShowBulkChange: () -> Unit,
) {
    when {
        journalPath.isBlank() -> NoJournalContent(onPickFile)
        !hasEntries -> EmptyJournalContent(isLoading)
        else -> JournalContent(
            viewModel = viewModel,
            entries = entries,
            entryPage = entryPage,
            listState = listState,
            selectedOffsets = selectedOffsets,
            onSelectedOffsetsChange = onSelectedOffsetsChange,
            onEdit = onEdit,
            onShowBulkChange = onShowBulkChange,
        )
    }
}

@Composable
private fun NoJournalContent(onPickFile: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onPickFile) {
            Text(stringResource(R.string.no_journal_selected))
        }
    }
}

@Composable
private fun EmptyJournalContent(isLoading: Boolean) {
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

@Composable
private fun JournalContent(
    viewModel: MainViewModel,
    entries: List<JournalEntry>,
    entryPage: EntryPage,
    listState: LazyListState,
    selectedOffsets: Set<Int>,
    onSelectedOffsetsChange: (Set<Int>) -> Unit,
    onEdit: (Int) -> Unit,
    onShowBulkChange: () -> Unit,
) {
    val filters by viewModel.filters.collectAsState()
    val filterLimit by viewModel.filterLimit.collectAsState()
    val accountSuggestions by viewModel.existingAccounts.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val pageLimit by viewModel.pageLimit.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        SortControls(
            sortOrder = sortOrder,
            onSortOrderChange = viewModel::setSortOrder,
            pageLimit = pageLimit,
            onPageLimitChange = viewModel::setPageLimit,
        )

        FilterRow(
            filters = filters,
            maxFilters = filterLimit,
            accountSuggestions = accountSuggestions,
            onAdd = viewModel::addFilter,
            onRename = viewModel::renameFilter,
            onChangeCategory = viewModel::changeFilterCategory,
            onDelete = viewModel::deleteFilter,
            onToggle = viewModel::cycleFilterMode,
        )

        if (selectedOffsets.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.bulk_selected_count, selectedOffsets.size),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onShowBulkChange) {
                    Text(stringResource(R.string.bulk_change_title))
                }
                IconButton(onClick = { onSelectedOffsetsChange(emptySet()) }) {
                    Icon(Lucide.X, contentDescription = stringResource(R.string.cancel))
                }
            }
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.no_matching_transactions))
            }
        } else {
            TransactionList(
                entries = entries,
                listState = listState,
                selectedOffsets = selectedOffsets,
                onSelectedOffsetsChange = onSelectedOffsetsChange,
                onEdit = onEdit,
                onDelete = viewModel::deleteTransaction,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            TransactionPager(
                page = entryPage.page,
                pageCount = entryPage.pageCount,
                onPrevious = viewModel::previousPage,
                onNext = viewModel::nextPage,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortControls(
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
        var sortExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = sortExpanded,
            onExpandedChange = { sortExpanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = sortLabel(sortOrder),
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
                SortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text(sortLabel(order)) },
                        onClick = {
                            onSortOrderChange(order)
                            sortExpanded = false
                        },
                    )
                }
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
}

@Composable
private fun TransactionList(
    entries: List<JournalEntry>,
    listState: LazyListState,
    selectedOffsets: Set<Int>,
    onSelectedOffsetsChange: (Set<Int>) -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.lineOffset }) { index, entry ->
            TransactionRow(
                entry = entry,
                onClick = {
                    if (selectedOffsets.isEmpty()) {
                        onEdit(index)
                    } else {
                        onSelectedOffsetsChange(
                            if (entry.lineOffset in selectedOffsets) {
                                selectedOffsets - entry.lineOffset
                            } else {
                                selectedOffsets + entry.lineOffset
                            },
                        )
                    }
                },
                onLongClick = { onSelectedOffsetsChange(selectedOffsets + entry.lineOffset) },
                selected = entry.lineOffset in selectedOffsets,
                selectionMode = selectedOffsets.isNotEmpty(),
                onDelete = { onDelete(entry.lineOffset) },
            )
        }
    }
}

@Composable
private fun TransactionPager(
    page: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    if (pageCount <= 1) return

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = page > 0,
        ) {
            Icon(Lucide.ChevronLeft, contentDescription = stringResource(R.string.page_previous))
        }
        Text(stringResource(R.string.page_of, page + 1, pageCount))
        IconButton(
            onClick = onNext,
            enabled = page < pageCount - 1,
        ) {
            Icon(Lucide.ChevronRight, contentDescription = stringResource(R.string.page_next))
        }
    }
}

@Composable
private fun EditTransactionDialog(
    entry: JournalEntry,
    payeeSuggestions: List<String>,
    accountSuggestions: List<String>,
    defaultCurrency: String,
    onSave: (Transaction) -> Unit,
    onDismiss: () -> Unit,
) {
    val tx = Transaction(
        date = entry.date,
        payee = entry.payee,
        postings = entry.postings,
        note = entry.note,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_transaction)) },
        text = {
            TransactionForm(
                initial = tx,
                payeeSuggestions = payeeSuggestions,
                accountSuggestions = accountSuggestions,
                defaultCurrency = defaultCurrency,
                onSave = onSave,
                onCancel = onDismiss,
            )
        },
        confirmButton = {},
    )
}

@Composable
private fun sortLabel(key: SortOrder): String = when (key) {
    SortOrder.NewestFirst -> stringResource(R.string.sort_newest_first)
    SortOrder.OldestFirst -> stringResource(R.string.sort_oldest_first)
    SortOrder.HighestAmount -> stringResource(R.string.sort_highest_amount)
    SortOrder.LowestAmount -> stringResource(R.string.sort_lowest_amount)
}
