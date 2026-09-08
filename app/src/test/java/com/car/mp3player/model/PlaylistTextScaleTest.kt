package com.car.mp3player.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistTextScaleTest {
    @Test
    fun `playlist text sizes clamp and keep secondary text smaller`() {
        assertEquals(PlaylistTextSizes(12f, 10f, 10f), PlaylistTextScale.sizes(1f))
        assertEquals(PlaylistTextSizes(18f, 14f, 13f), PlaylistTextScale.sizes(18f))
        assertEquals(PlaylistTextSizes(24f, 20f, 19f), PlaylistTextScale.sizes(100f))
    }
}
