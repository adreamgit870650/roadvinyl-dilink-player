package com.car.mp3player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPositioningTest {

    @Test
    fun `drag position is kept inside the visible screen`() {
        assertEquals(
            OverlayPositioning.PixelPosition(700, 600),
            OverlayPositioning.clamp(900, 800, 1000, 800, 300, 200)
        )
        assertEquals(
            OverlayPositioning.PixelPosition(0, 0),
            OverlayPositioning.clamp(-50, -20, 1000, 800, 300, 200)
        )
    }

    @Test
    fun `saved center fractions restore the same position`() {
        val fractions = OverlayPositioning.toCenterFractions(
            x = 240,
            y = 180,
            screenWidth = 1000,
            screenHeight = 800,
            overlayWidth = 300,
            overlayHeight = 200
        )
        val restored = OverlayPositioning.fromCenterFractions(
            fractions.x,
            fractions.y,
            screenWidth = 1000,
            screenHeight = 800,
            overlayWidth = 300,
            overlayHeight = 200
        )

        assertTrue(kotlin.math.abs(restored.x - 240) <= 1)
        assertTrue(kotlin.math.abs(restored.y - 180) <= 1)
    }
}
