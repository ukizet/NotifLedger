package org.notifledger.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import org.notifledger.app.R
import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.log.AppLogger
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateDisplay = DateTimeFormatter.ofPattern("MMM d")

@Composable
fun TransactionRow(
    entry: JournalEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayDate = remember(entry.date) { formatDisplayDate(entry.date) }
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayDate,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = entry.payee,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(3f),
                )
                IconButton(onClick = onEdit) {
                    Icon(Lucide.Pencil, contentDescription = stringResource(R.string.edit), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Lucide.Trash2, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                }
            }

            entry.postings.forEach { posting ->
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
        }
    }
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
