package com.car.mp3player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VinylGeometryTest {

    @Test
    fun `large car display is not restricted by legacy 520dp cap`() {
        val geometry = calculateVinylGeometry(
            widthPx = 1080,
            heightPx = 900,
            horizontalPaddingPx = 28,
            verticalPaddingPx = 28,
            minimumDiscPx = 160
        )

        assertTrue(geometry.discPx > 520)
        assertTrue(geometry.discPx <= 872)
    }

    @Test
    fun `split layout scales record to its current short side`() {
        val geometry = calculateVinylGeometry(
            widthPx = 600,
            heightPx = 700,
            horizontalPaddingPx = 28,
            verticalPaddingPx = 28,
            minimumDiscPx = 160
        )

        assertEquals(564, geometry.discPx)
        assertEquals(384, geometry.coverPx)
    }

    @Test
    fun `small viewport never forces record beyond available area`() {
        val geometry = calculateVinylGeometry(
            widthPx = 120,
            heightPx = 100,
            horizontalPaddingPx = 28,
            verticalPaddingPx = 28,
            minimumDiscPx = 160
        )

        assertEquals(96, geometry.discPx)
        assertTrue(geometry.coverPx < geometry.discPx)
    }

    @Test
    fun `high density dp padding cannot consume car display stage`() {
        val geometry = calculateVinylGeometry(
            widthPx = 1140,
            heightPx = 470,
            horizontalPaddingPx = 100,
            verticalPaddingPx = 100,
            minimumDiscPx = 360
        )

        assertEquals(442, geometry.discPx)
        assertTrue(geometry.discPx > 400)
    }

    @Test
    fun `landscape record stays inside stage to protect artist text`() {
        val geometry = calculateVinylGeometry(
            widthPx = 1088,
            heightPx = 382,
            horizontalPaddingPx = 92,
            verticalPaddingPx = 92,
            minimumDiscPx = 520
        )

        assertEquals(367, geometry.discPx)
        assertEquals(250, geometry.coverPx)
    }

    @Test
    fun `empty viewport produces no geometry`() {
        assertEquals(
            VinylGeometry(0, 0),
            calculateVinylGeometry(0, 20, 28, 28, 160)
        )
    }

    @Test
    fun `user scale changes disc and cover together within limits`() {
        val base = VinylGeometry(discPx = 400, coverPx = 272)

        assertEquals(VinylGeometry(240, 163), scaleVinylGeometry(base, 0.1f))
        assertEquals(VinylGeometry(800, 544), scaleVinylGeometry(base, 2f))
    }
}
