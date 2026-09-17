package org.notifledger.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeToDeleteTest {

    private val width = 1000f

    @Test
    fun `start at zero is inside the swipe start zone`() {
        assertTrue(isInSwipeStartZone(0f, width))
    }

    @Test
    fun `start exactly at the zone boundary is inside the swipe start zone`() {
        assertTrue(isInSwipeStartZone(width * 0.33f, width))
    }

    @Test
    fun `start just past the zone boundary is outside the swipe start zone`() {
        assertFalse(isInSwipeStartZone(width * 0.33f + 1f, width))
    }

    @Test
    fun `zero width is never inside the swipe start zone`() {
        assertFalse(isInSwipeStartZone(0f, 0f))
    }

    @Test
    fun `drag past half width crosses the delete threshold`() {
        assertTrue(crossesDeleteThreshold(startX = width * 0.1f, draggedBy = width * 0.4f, width = width))
    }

    @Test
    fun `drag staying under half width does not cross the delete threshold`() {
        assertFalse(crossesDeleteThreshold(startX = width * 0.3f, draggedBy = width * 0.19f, width = width))
    }

    @Test
    fun `start inside the zone plus drag can cross the delete threshold`() {
        assertTrue(crossesDeleteThreshold(startX = width * 0.33f, draggedBy = width * 0.17f, width = width))
    }

    @Test
    fun `negative drag does not cross the delete threshold`() {
        assertFalse(crossesDeleteThreshold(startX = width * 0.1f, draggedBy = -width * 0.2f, width = width))
    }

    @Test
    fun `zero width never crosses the delete threshold`() {
        assertFalse(crossesDeleteThreshold(startX = 0f, draggedBy = width * 0.6f, width = 0f))
    }

    @Test
    fun `updated drag moves back toward the start on a negative delta`() {
        assertEquals(350f, updatedDrag(current = 600f, dragAmount = -250f), 0.0f)
    }

    @Test
    fun `updated drag never goes below zero`() {
        assertEquals(0f, updatedDrag(current = 100f, dragAmount = -250f), 0.0f)
    }

    @Test
    fun `drag past the threshold then back below it does not cross`() {
        val startX = width * 0.3f
        val past = updatedDrag(current = 0f, dragAmount = width * 0.25f)
        val back = updatedDrag(current = past, dragAmount = -width * 0.1f)

        assertFalse(crossesDeleteThreshold(startX, back, width))
    }

    @Test
    fun `swipe constants match the designed fractions`() {
        assertEquals(0.33f, SWIPE_START_ZONE_FRACTION, 0.0f)
        assertEquals(0.5f, SWIPE_DELETE_FRACTION, 0.0f)
    }
}
