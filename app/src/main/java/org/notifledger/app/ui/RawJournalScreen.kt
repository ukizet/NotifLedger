package org.notifledger.app.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.notifledger.app.R
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
    viewModel: RawJournalViewModel,
    onBack: () -> Unit,
) {
    val journalPath by viewModel.journalPath.collectAsState()
    var state by remember { mutableStateOf<RawLoadState>(RawLoadState.Loading) }

    LaunchedEffect(journalPath) {
        if (journalPath.isBlank()) {
            state = RawLoadState.Empty
        } else {
            val content = viewModel.readRawJournal(journalPath)
            state = when {
                content == null -> RawLoadState.Error
                content.isBlank() -> RawLoadState.Empty
                else -> RawLoadState.Loaded(content)
            }
        }
    }

    Scaffold(
        topBar = {
            NotifLedgerTopAppBar(
                title = stringResource(R.string.raw_journal),
                onBack = onBack,
            )
        },
    ) { padding ->
        RawJournalContent(
            state = state,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun RawJournalContent(state: RawLoadState, modifier: Modifier = Modifier) {
    when (state) {
        RawLoadState.Loading -> RawLoadingBox(modifier)

        RawLoadState.Empty -> RawJournalMessage(
            text = stringResource(R.string.no_journal_or_empty),
            modifier = modifier,
        )

        RawLoadState.Error -> RawJournalMessage(
            text = stringResource(R.string.raw_journal_open_error),
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )

        is RawLoadState.Loaded -> RawJournalText(state.content, modifier)
    }
}

@Composable
private fun RawLoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}

@Composable
private fun RawJournalMessage(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier.fillMaxSize().padding(16.dp),
    )
}

@Composable
private fun RawJournalText(content: String, modifier: Modifier = Modifier) {
    Text(
        text = content,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        softWrap = false,
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .horizontalScroll(rememberScrollState())
            .verticalScroll(rememberScrollState()),
    )
}
