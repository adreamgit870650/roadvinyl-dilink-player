package com.car.mp3player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollLyricViewportTest {

    @Test
    fun `blocks touching header or progress safety areas are omitted`() {
        assertFalse(lyricBlockFitsViewport(centerY = 15f, blockHeight = 40f, viewportTop = 10f, viewportBottom = 190f))
        assertFalse(lyricBlockFitsViewport(centerY = 185f, blockHeight = 40f, viewportTop = 10f, viewportBottom = 190f))
    }

    @Test
    fun `blocks fully inside adaptive viewport are rendered`() {
        assertTrue(lyricBlockFitsViewport(centerY = 30f, blockHeight = 40f, viewportTop = 10f, viewportBottom = 190f))
        assertTrue(lyricBlockFitsViewport(centerY = 170f, blockHeight = 40f, viewportTop = 10f, viewportBottom = 190f))
    }

    @Test
    fun `oversized active block can still be clipped around its center`() {
        assertTrue(lyricBlockFitsViewport(centerY = 100f, blockHeight = 240f, viewportTop = 10f, viewportBottom = 190f))
        assertFalse(lyricBlockFitsViewport(centerY = 210f, blockHeight = 240f, viewportTop = 10f, viewportBottom = 190f))
    }

    @Test
    fun `player font does not consume the already measured safe stage`() {
        assertEquals(
            LyricViewportBounds(topPx = 16f, bottomPx = 584f),
            resolveLyricViewportBounds(
                heightPx = 600f,
                paddingTopPx = 16f,
                paddingBottomPx = 16f,
                safeTopPx = 0f,
                safeBottomPx = Float.POSITIVE_INFINITY
            )
        )
    }

    @Test
    fun `measured header and progress bounds override generic guards`() {
        assertEquals(
            LyricViewportBounds(topPx = 120f, bottomPx = 470f),
            resolveLyricViewportBounds(
                heightPx = 600f,
                paddingTopPx = 16f,
                paddingBottomPx = 16f,
                safeTopPx = 120f,
                safeBottomPx = 470f
            )
        )
    }

    @Test
    fun `bilingual current and adjacent blocks fit in mate landscape stage`() {
        val currentSize = 58f
        val adjacentSize = 42f
        val step = lyricBlockStep(currentSize, adjacentSize, rowCount = 2)
        val viewportTop = 0f
        val viewportBottom = 382f
        val center = 191f
        val adjacentHeight = adjacentSize * 1.3f * 2f

        assertTrue(
            lyricBlockFitsViewport(
                centerY = center - step,
                blockHeight = adjacentHeight,
                viewportTop = viewportTop,
                viewportBottom = viewportBottom
            )
        )
        assertTrue(
            lyricBlockFitsViewport(
                centerY = center + step,
                blockHeight = adjacentHeight,
                viewportTop = viewportTop,
                viewportBottom = viewportBottom
            )
        )
    }
}
