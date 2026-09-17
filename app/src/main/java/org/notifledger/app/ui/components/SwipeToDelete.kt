package org.notifledger.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

internal const val SWIPE_START_ZONE_FRACTION = 0.33f
internal const val SWIPE_DELETE_FRACTION = 0.5f

internal fun isInSwipeStartZone(startX: Float, width: Float): Boolean =
    width > 0f && startX <= width * SWIPE_START_ZONE_FRACTION

internal fun crossesDeleteThreshold(startX: Float, draggedBy: Float, width: Float): Boolean =
    width > 0f && startX + draggedBy >= width * SWIPE_DELETE_FRACTION

internal fun updatedDrag(current: Float, dragAmount: Float): Float =
    (current + dragAmount).coerceAtLeast(0f)

@Composable
fun SwipeToDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var dragX by remember { mutableFloatStateOf(0f) }
    var isPastThreshold by remember { mutableStateOf(false) }
    val currentOnDelete by rememberUpdatedState(onDelete)

    Box(
        modifier = modifier.pointerInput(Unit) {
            var startX = 0f
            var isArmed = false
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    startX = offset.x
                    isArmed = isInSwipeStartZone(offset.x, size.width.toFloat())
                    dragX = 0f
                    isPastThreshold = false
                },
                onHorizontalDrag = { change, dragAmount ->
                    if (isArmed) {
                        change.consume()
                        dragX = updatedDrag(dragX, dragAmount)
                        isPastThreshold = crossesDeleteThreshold(startX, dragX, size.width.toFloat())
                    }
                },
                onDragEnd = {
                    if (isPastThreshold) currentOnDelete()
                    dragX = 0f
                    isPastThreshold = false
                },
                onDragCancel = {
                    dragX = 0f
                    isPastThreshold = false
                },
            )
        },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(if (isPastThreshold) MaterialTheme.colorScheme.errorContainer else Color.Transparent),
        )
        Box(
            modifier = Modifier.offset { IntOffset(dragX.roundToInt(), 0) },
        ) {
            content()
        }
    }
}