package com.car.mp3player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayStrokeWidthTest {
    @Test
    fun `all ten stroke levels increase at small-screen density`() {
        val widths = (1..10).map { LyricRenderer.overlayStrokeWidthPx(it, density = 1.5f) }

        assertTrue(widths.zipWithNext().all { (left, right) -> right > left })
    }

    @Test
    fun `default stroke level is one density-independent pixel`() {
        assertEquals(1.5f, LyricRenderer.overlayStrokeWidthPx(level = 3, density = 1.5f), 0.001f)
        assertEquals(3f, LyricRenderer.overlayStrokeWidthPx(level = 3, density = 3f), 0.001f)
    }

    @Test
    fun `stroke levels are clamped to settings range`() {
        assertEquals(
            LyricRenderer.overlayStrokeWidthPx(1, 2f),
            LyricRenderer.overlayStrokeWidthPx(-10, 2f),
            0.001f,
        )
        assertEquals(
            LyricRenderer.overlayStrokeWidthPx(10, 2f),
            LyricRenderer.overlayStrokeWidthPx(99, 2f),
            0.001f,
        )
    }
}
