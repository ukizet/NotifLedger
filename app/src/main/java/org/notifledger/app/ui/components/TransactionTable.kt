package org.notifledger.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import org.notifledger.app.R
import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.model.SortColumn
import org.notifledger.app.model.SortDirection
import org.notifledger.app.model.SortOrder
import org.notifledger.app.ui.EntrySorting
import org.notifledger.app.ui.formatDisplayDate
import org.notifledger.app.ui.toggleSelection

private val dateWidth = 80.dp
private val payeeWidth = 140.dp
private val accountWidth = 170.dp
private val amountWidth = 100.dp
private val currencyWidth = 64.dp
private val deleteWidth = 48.dp
private val cellPadding = 8.dp

private fun columnWidth(column: SortColumn): Dp = when (column) {
    SortColumn.Date -> dateWidth
    SortColumn.Payee -> payeeWidth
    SortColumn.Account -> accountWidth
    SortColumn.Amount -> amountWidth
    SortColumn.Currency -> currencyWidth
}

/** The columns plus the delete gutter; header, dividers, and rows all use it. */
private val tableWidth: Dp = SortColumn.entries
    .map { columnWidth(it) }
    .fold(deleteWidth) { total, width -> total + width }

/**
 * Column view of the journal: one row per transaction, one sortable header per
 * column. A table wider than the screen scrolls sideways as a single unit, so
 * the header always stays lined up with its cells.
 */
@Composable
fun TransactionTable(
    entries: List<JournalEntry>,
    listState: LazyListState,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    selectedOffsets: Set<Int>,
    onSelectedOffsetsChange: (Set<Int>) -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val horizontalScroll = rememberScrollState()

    Column(modifier = modifier.horizontalScroll(horizontalScroll)) {
        TableHeader(sortOrder = sortOrder, onSortOrderChange = onSortOrderChange)
        Divider(modifier = Modifier.width(tableWidth))
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            itemsIndexed(entries, key = { _, entry -> entry.lineOffset }) { index, entry ->
                TransactionTableRow(
                    entry = entry,
                    selected = entry.lineOffset in selectedOffsets,
                    onClick = {
                        if (selectedOffsets.isEmpty()) {
                            onEdit(index)
                        } else {
                            onSelectedOffsetsChange(toggleSelection(selectedOffsets, entry.lineOffset))
                        }
                    },
                    onLongClick = { onSelectedOffsetsChange(selectedOffsets + entry.lineOffset) },
                    onDelete = { onDelete(entry.lineOffset) },
                )
                Divider(modifier = Modifier.width(tableWidth))
            }
        }
    }
}

@Composable
private fun TableHeader(
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
) {
    Row(
        modifier = Modifier.width(tableWidth).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SortColumn.entries.forEach { column ->
            HeaderCell(
                column = column,
                sortOrder = sortOrder,
                onSortOrderChange = onSortOrderChange,
            )
        }
        Spacer(modifier = Modifier.width(deleteWidth))
    }
}

private fun columnLabel(column: SortColumn): Int = when (column) {
    SortColumn.Date -> R.string.column_date
    SortColumn.Payee -> R.string.column_payee
    SortColumn.Account -> R.string.column_account
    SortColumn.Amount -> R.string.column_amount
    SortColumn.Currency -> R.string.column_currency
}

/** A tappable column heading; tapping sorts by that column, tapping again flips direction. */
@Composable
private fun HeaderCell(
    column: SortColumn,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
) {
    TextButton(
        onClick = { onSortOrderChange(EntrySorting.toggle(column, sortOrder)) },
        contentPadding = PaddingValues(horizontal = cellPadding),
        modifier = Modifier.width(columnWidth(column)),
    ) {
        LabelWithSortIndicator(
            label = stringResource(columnLabel(column)),
            direction = EntrySorting.indicator(column, sortOrder),
            alignEnd = column.isAmount,
        )
    }
}

@Composable
private fun LabelWithSortIndicator(label: String, direction: SortDirection?, alignEnd: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (direction != null) {
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = if (direction == SortDirection.Ascending) Lucide.ArrowUp else Lucide.ArrowDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionTableRow(
    entry: JournalEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val deleteLabel = stringResource(R.string.delete)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .width(tableWidth - deleteWidth)
                .combinedClickable(
                    onClickLabel = stringResource(R.string.edit_transaction),
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(deleteLabel) {
                            onDelete()
                            true
                        },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableCell(
                text = remember(entry.date) { formatDisplayDate(entry.date) },
                width = dateWidth,
                selected = selected,
            )
            TableCell(
                text = entry.payee,
                width = payeeWidth,
                selected = selected,
                weight = FontWeight.Medium,
            )
            TableCell(
                text = EntrySorting.firstAccount(entry),
                width = accountWidth,
                selected = selected,
            )
            TableCell(
                text = EntrySorting.firstAmount(entry),
                width = amountWidth,
                selected = selected,
                alignEnd = true,
            )
            TableCell(
                text = EntrySorting.firstCurrency(entry),
                width = currencyWidth,
                selected = selected,
            )
        }
        Box(modifier = Modifier.width(deleteWidth), contentAlignment = Alignment.Center) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Lucide.Trash2,
                    contentDescription = deleteLabel,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** One cell; the row's selection colour is painted here so it covers every column. */
@Composable
private fun TableCell(
    text: String,
    width: Dp,
    selected: Boolean,
    weight: FontWeight = FontWeight.Normal,
    alignEnd: Boolean = false,
) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Box(
        modifier = Modifier
            .width(width)
            .background(background)
            .padding(horizontal = cellPadding, vertical = 12.dp),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = weight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        )
    }
}
