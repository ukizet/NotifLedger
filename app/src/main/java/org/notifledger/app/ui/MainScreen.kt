package org.notifledger.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
    onNavigateToNotificationSources: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val entryPage by viewModel.entryPage.collectAsState()
    val entries = entryPage.entries
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val pickFileLauncher = rememberJournalPicker(viewModel)

    MainSnackbarEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)
    LaunchedEffect(entryPage.page) { listState.scrollToItem(0) }
    ReloadEntriesOnResume(viewModel)

    MainScaffold(
        viewModel = viewModel,
        navController = navController,
        snackbarHostState = snackbarHostState,
        onNavigateToQuickAdd = onNavigateToQuickAdd,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToNotificationSources = onNavigateToNotificationSources,
        entries = entries,
        entryPage = entryPage,
        listState = listState,
        onPickFile = { pickFileLauncher.launch(arrayOf("*/*")) },
    )
}

@Composable
private fun MainScaffold(
    viewModel: MainViewModel,
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    onNavigateToQuickAdd: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToNotificationSources: () -> Unit,
    entries: List<JournalEntry>,
    entryPage: EntryPage,
    listState: LazyListState,
    onPickFile: () -> Unit,
) {
    var editingIndex by remember { mutableStateOf(-1) }
    var showSimulateDialog by remember { mutableStateOf(false) }
    var selectedOffsets by remember { mutableStateOf(setOf<Int>()) }
    var showBulkChange by remember { mutableStateOf(false) }

    LaunchedEffect(entries) {
        selectedOffsets = emptySet()
        if (editingIndex >= entries.size) editingIndex = -1
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { MainBottomBar(navController = navController, onSimulate = { showSimulateDialog = true }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToQuickAdd) {
                Icon(Lucide.Plus, contentDescription = stringResource(R.string.quick_add))
            }
        },
    ) { padding ->
        MainColumn(
            padding = padding,
            viewModel = viewModel,
            entries = entries,
            entryPage = entryPage,
            listState = listState,
            selectedOffsets = selectedOffsets,
            onSelectedOffsetsChange = { selectedOffsets = it },
            onEdit = { editingIndex = it },
            onShowBulkChange = { showBulkChange = true },
            onPickFile = onPickFile,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToNotificationSources = onNavigateToNotificationSources,
        )
    }
    MainDialogs(
        viewModel = viewModel,
        entries = entries,
        editingIndex = editingIndex,
        showBulkChange = showBulkChange,
        showSimulateDialog = showSimulateDialog,
        selectedOffsets = selectedOffsets,
        onClearSelection = { selectedOffsets = emptySet() },
        onDismissBulkChange = { showBulkChange = false },
        onDismissSimulate = { showSimulateDialog = false },
        onDismissEdit = { editingIndex = -1 },
    )
}

@Composable
private fun MainColumn(
    padding: PaddingValues,
    viewModel: MainViewModel,
    entries: List<JournalEntry>,
    entryPage: EntryPage,
    listState: LazyListState,
    selectedOffsets: Set<Int>,
    onSelectedOffsetsChange: (Set<Int>) -> Unit,
    onEdit: (Int) -> Unit,
    onShowBulkChange: () -> Unit,
    onPickFile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToNotificationSources: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        MainOnboarding(
            viewModel = viewModel,
            onPickFile = onPickFile,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToNotificationSources = onNavigateToNotificationSources,
        )
        MainContent(
            viewModel = viewModel,
            entries = entries,
            entryPage = entryPage,
            listState = listState,
            selectedOffsets = selectedOffsets,
            onSelectedOffsetsChange = onSelectedOffsetsChange,
            onEdit = onEdit,
            onShowBulkChange = onShowBulkChange,
            onPickFile = onPickFile,
        )
    }
}

@Composable
private fun MainOnboarding(
    viewModel: MainViewModel,
    onPickFile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToNotificationSources: () -> Unit,
) {
    val journalPath by viewModel.journalPath.collectAsState()
    val notificationSources by viewModel.notificationSources.collectAsState()
    val defaultAccount by viewModel.defaultAccount.collectAsState()
    val isListening = rememberListenerEnabled(LocalContext.current)

    OnboardingChecklist(
        items = listOf(
            OnboardingItem(
                label = stringResource(R.string.onboarding_pick_journal),
                done = journalPath.isNotBlank(),
                onClick = onPickFile,
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
        ),
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun MainContent(
    viewModel: MainViewModel,
    entries: List<JournalEntry>,
    entryPage: EntryPage,
    listState: LazyListState,
    selectedOffsets: Set<Int>,
    onSelectedOffsetsChange: (Set<Int>) -> Unit,
    onEdit: (Int) -> Unit,
    onShowBulkChange: () -> Unit,
    onPickFile: () -> Unit,
) {
    val journalPath by viewModel.journalPath.collectAsState()
    val hasEntries by viewModel.hasEntries.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

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
            SelectionBar(
                selectedCount = selectedOffsets.size,
                onShowBulkChange = onShowBulkChange,
                onClearSelection = { onSelectedOffsetsChange(emptySet()) },
            )
        }

        TransactionPane(
            viewModel = viewModel,
            entries = entries,
            entryPage = entryPage,
            listState = listState,
            selectedOffsets = selectedOffsets,
            onSelectedOffsetsChange = onSelectedOffsetsChange,
            onEdit = onEdit,
        )
    }
}

@Composable
private fun SelectionBar(
    selectedCount: Int,
    onShowBulkChange: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.bulk_selected_count, selectedCount),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onShowBulkChange) {
            Text(stringResource(R.string.bulk_change_title))
        }
        IconButton(onClick = onClearSelection) {
            Icon(Lucide.X, contentDescription = stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun ColumnScope.TransactionPane(
    viewModel: MainViewModel,
    entries: List<JournalEntry>,
    entryPage: EntryPage,
    listState: LazyListState,
    selectedOffsets: Set<Int>,
    onSelectedOffsetsChange: (Set<Int>) -> Unit,
    onEdit: (Int) -> Unit,
) {
    if (entries.isEmpty()) {
        NoMatchingContent()
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

@Composable
private fun NoMatchingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.no_matching_transactions))
    }
}

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
                        onSelectedOffsetsChange(toggleSelection(selectedOffsets, entry.lineOffset))
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

private fun toggleSelection(selectedOffsets: Set<Int>, lineOffset: Int): Set<Int> =
    if (lineOffset in selectedOffsets) selectedOffsets - lineOffset else selectedOffsets + lineOffset

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
private fun MainDialogs(
    viewModel: MainViewModel,
    entries: List<JournalEntry>,
    editingIndex: Int,
    showBulkChange: Boolean,
    showSimulateDialog: Boolean,
    selectedOffsets: Set<Int>,
    onClearSelection: () -> Unit,
    onDismissBulkChange: () -> Unit,
    onDismissSimulate: () -> Unit,
    onDismissEdit: () -> Unit,
) {
    val payeeSuggestions by viewModel.existingPayees.collectAsState()
    val accountSuggestions by viewModel.existingAccounts.collectAsState()
    val defaultCurrency by viewModel.defaultCurrency.collectAsState()

    if (showBulkChange) {
        BulkChangeDialog(
            accountSuggestions = accountSuggestions,
            onConfirm = { date, payee, currency, account ->
                viewModel.changeTransactions(selectedOffsets, date, payee, currency, account)
                onClearSelection()
                onDismissBulkChange()
            },
            onDismiss = onDismissBulkChange,
        )
    }

    if (showSimulateDialog) {
        SimulateNotificationDialog(
            viewModel = viewModel,
            onDismiss = {
                onDismissSimulate()
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
                onDismissEdit()
            },
            onDismiss = onDismissEdit,
        )
    }
}

@Composable
private fun MainSnackbarEffect(
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val uiMessage by viewModel.uiMessage.collectAsState()
    LaunchedEffect(uiMessage) {
        uiMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
}

@Composable
private fun ReloadEntriesOnResume(viewModel: MainViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.reloadEntries()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun rememberJournalPicker(viewModel: MainViewModel): ManagedActivityResultLauncher<Array<String>, Uri?> {
    val context = LocalContext.current
    return rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)
            viewModel.setJournalPath(it.toString())
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
