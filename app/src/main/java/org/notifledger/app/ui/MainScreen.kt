package org.notifledger.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import org.notifledger.app.R
import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.ui.components.MainBottomBar
import org.notifledger.app.ui.components.OnboardingChecklist
import org.notifledger.app.ui.components.OnboardingItem
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
