package org.notifledger.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import org.notifledger.app.R
import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.ui.components.FilterRow
import org.notifledger.app.ui.components.TransactionRow

@Composable
internal fun JournalContent(
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
