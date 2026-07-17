package org.notifledger.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.Lucide
import org.notifledger.app.R

data class OnboardingItem(val label: String, val done: Boolean, val onClick: () -> Unit)

/**
 * First-run checklist card. Renders only while at least one item is incomplete —
 * once everything is done the card disappears so it never becomes noise.
 */
@Composable
fun OnboardingChecklist(items: List<OnboardingItem>, modifier: Modifier = Modifier) {
    if (items.all { it.done }) return
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            items.forEach { item -> OnboardingRow(item) }
        }
    }
}

@Composable
private fun OnboardingRow(item: OnboardingItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { item.onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (item.done) Lucide.Check else Lucide.Circle,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (item.done)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(item.label, style = MaterialTheme.typography.bodyMedium)
    }
}
