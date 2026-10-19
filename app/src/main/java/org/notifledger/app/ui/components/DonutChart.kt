package org.notifledger.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.notifledger.app.R
import java.math.BigDecimal
import java.util.Locale

data class DonutSlice(
    val label: String,
    val value: BigDecimal,
    val amountText: String,
)

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerValue: String,
    centerLabel: String,
    currency: String,
    modifier: Modifier = Modifier,
) {
    if (slices.isEmpty()) return

    val merged = slices.size > 6
    val prepared = if (merged) {
        val otherTotal = slices.drop(6).sumOf { it.value }
        slices.take(6) + DonutSlice(
            stringResource(R.string.stats_other),
            otherTotal,
            "${otherTotal.toPlainString()} $currency",
        )
    } else {
        slices
    }
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
        Color(0xFFA5D6A7),
        Color(0xFFFFB74D),
    )
    val otherColor = MaterialTheme.colorScheme.outlineVariant
    val colors = prepared.indices.map { if (merged && it == 6) otherColor else palette[it] }
    val total = prepared.sumOf { it.value }

    Column(modifier = modifier) {
        DonutDial(
            slices = prepared,
            colors = colors,
            total = total,
            centerValue = centerValue,
            centerLabel = centerLabel,
        )

        Spacer(Modifier.height(12.dp))

        prepared.forEachIndexed { index, slice ->
            LegendRow(slice, colors[index], formatPercent(slice.value, total))
        }
    }
}

@Composable
private fun DonutDial(
    slices: List<DonutSlice>,
    colors: List<Color>,
    total: BigDecimal,
    centerValue: String,
    centerLabel: String,
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (total <= BigDecimal.ZERO) return@Canvas
            val strokeWidth = 28.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            var startAngle = -90f
            slices.forEachIndexed { index, slice ->
                val sweep = slice.value.toFloat() / total.toFloat() * 360f
                drawArc(
                    color = colors[index],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = centerValue, style = MaterialTheme.typography.titleLarge)
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendRow(slice: DonutSlice, color: Color, percentText: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            text = slice.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = slice.amountText, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Text(
            text = percentText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatPercent(value: BigDecimal, total: BigDecimal): String {
    val percent = if (total > BigDecimal.ZERO) value.toFloat() / total.toFloat() * 100f else 0f
    return String.format(Locale.getDefault(), "%.1f%%", percent)
}
