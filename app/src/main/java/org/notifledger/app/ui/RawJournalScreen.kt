package org.notifledger.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.notifledger.app.R
import org.notifledger.app.log.AppLogger
import org.notifledger.app.ui.components.NotifLedgerTopAppBar

private sealed interface RawLoadState {
    data object Loading : RawLoadState
    data object Empty : RawLoadState
    data object Error : RawLoadState
    data class Loaded(val content: String) : RawLoadState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawJournalScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val journalPath by viewModel.journalPath.collectAsState()
    var state by remember { mutableStateOf<RawLoadState>(RawLoadState.Loading) }
    val context = LocalContext.current

    LaunchedEffect(journalPath) {
        state = if (journalPath.isBlank()) RawLoadState.Empty else loadRawJournal(journalPath, context)
    }

    Scaffold(
        topBar = {
            NotifLedgerTopAppBar(
                title = stringResource(R.string.raw_journal),
                onBack = onBack,
            )
        },
    ) { padding ->
        when (val s = state) {
            RawLoadState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            RawLoadState.Empty -> Text(
                text = stringResource(R.string.no_journal_or_empty),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            )

            RawLoadState.Error -> Text(
                text = "Couldn't open journal — permission may have been revoked. Re-select the file in Settings.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            )

            is RawLoadState.Loaded -> Text(
                text = s.content,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

private suspend fun loadRawJournal(
    journalPath: String,
    context: android.content.Context,
): RawLoadState = withContext(Dispatchers.IO) {
    try {
        val uri = Uri.parse(journalPath)
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
        if (text.isBlank()) RawLoadState.Empty else RawLoadState.Loaded(text)
    } catch (e: Exception) {
        AppLogger.error("Journal", "Raw read failed: ${e.message}")
        RawLoadState.Error
    }
}
