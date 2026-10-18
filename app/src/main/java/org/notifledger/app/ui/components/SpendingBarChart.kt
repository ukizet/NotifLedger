package org.notifledger.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.notifledger.app.stats.PeriodTotal
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

@Composable
fun SpendingBarChart(periods: List<PeriodTotal>, modifier: Modifier = Modifier) {
    val max = periods.maxOfOrNull { it.total } ?: BigDecimal.ZERO
    val barColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            val baselineWidth = 1.dp.toPx()
            val baselineY = size.height - baselineWidth / 2f
            drawLine(
                color = baselineColor,
                start = Offset(0f, baselineY),
                end = Offset(size.width, baselineY),
                strokeWidth = baselineWidth,
            )
            if (max <= BigDecimal.ZERO) return@Canvas

            val slotWidth = size.width / periods.size
            val barWidth = slotWidth * 0.6f
            val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            periods.forEachIndexed { index, period ->
                val ratio = (period.total.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                val barHeight = baselineY * ratio
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset((index + 0.5f) * slotWidth - barWidth / 2f, baselineY - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = cornerRadius,
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            val step = ceil(periods.size / 6.0).toInt().coerceAtLeast(1)
            periods.forEachIndexed { index, period ->
                Text(
                    text = if (index % step == 0) formatLabel(period.label) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun formatLabel(label: String): String = when {
    label.length == 10 -> runCatching {
        val date = LocalDate.parse(label)
        "${date.dayOfMonth}.${date.monthValue}."
    }.getOrElse { label }
    label.length == 7 -> runCatching {
        YearMonth.parse(label).month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }.getOrElse { label }
    else -> label
}
