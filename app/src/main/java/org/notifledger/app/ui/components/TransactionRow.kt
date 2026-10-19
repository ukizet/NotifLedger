package org.notifledger.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.notifledger.app.R
import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.log.AppLogger
import org.notifledger.app.model.Posting
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateDisplay = DateTimeFormatter.ofPattern("MMM d")

@Composable
fun TransactionRow(
    entry: JournalEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onLongClick: () -> Unit = {},
) {
    val displayDate = remember(entry.date) { formatDisplayDate(entry.date) }
    val deleteLabel = stringResource(R.string.delete)
    SwipeToDelete(
        onDelete = onDelete,
        modifier = modifier.fillMaxWidth(),
        enabled = !selectionMode,
    ) {
        TransactionCard(
            entry = entry,
            displayDate = displayDate,
            deleteLabel = deleteLabel,
            selected = selected,
            onClick = onClick,
            onLongClick = onLongClick,
            onDelete = onDelete,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionCard(
    entry: JournalEntry,
    displayDate: String,
    deleteLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
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
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            TransactionHeader(displayDate = displayDate, payee = entry.payee)
            entry.postings.forEach { posting ->
                PostingLine(posting = posting)
            }
        }
    }
}

@Composable
private fun TransactionHeader(displayDate: String, payee: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = displayDate,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = payee,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(3f),
        )
    }
}

@Composable
private fun PostingLine(posting: Posting) {
    val displayAmount = if (posting.amount.isNotBlank()) {
        "${posting.amount} ${posting.currency}"
    } else {
        ""
    }
    Text(
        text = "${posting.account}: $displayAmount",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp),
    )
}

private fun formatDisplayDate(raw: String): String {
    if (raw.isBlank()) return raw
    return try {
        LocalDate.parse(raw).format(dateDisplay)
    } catch (e: Exception) {
        AppLogger.warn("TransactionRow", "Unparseable date \"$raw\": ${e.message}")
        raw
    }
}
