package com.car.mp3player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverDecodePolicyTest {
    @Test fun `small covers keep original resolution`() {
        assertEquals(1, calculateCoverSampleSize(300, 300, 512))
    }

    @Test fun `large cover decode stays within the requested edge`() {
        assertEquals(8, calculateCoverSampleSize(4000, 3000, 512))
        assertEquals(4, calculateCoverSampleSize(3000, 4000, 1024))
        assertEquals(2, calculateCoverSampleSize(1025, 500, 1024))
    }

    @Test fun `panoramic and portrait covers are bounded by the longer dimension`() {
        assertEquals(16, calculateCoverSampleSize(8000, 10, 512))
        assertEquals(16, calculateCoverSampleSize(10, 8000, 512))
    }

    @Test fun `untrusted huge dimensions cannot overflow the sample calculation`() {
        val sample = calculateCoverSampleSize(Int.MAX_VALUE, Int.MAX_VALUE, 512)
        assertTrue(sample > 0)
        assertTrue((Int.MAX_VALUE.toLong() + sample - 1) / sample <= 512)
    }

    @Test fun `low memory devices use a smaller bitmap budget`() {
        assertEquals(512, coverTargetEdge(2000, lowRamDevice = true))
        assertEquals(1024, coverTargetEdge(2000, lowRamDevice = false))
        assertEquals(128, coverTargetEdge(0, lowRamDevice = true))
    }

    @Test fun `invalid image bounds do not loop`() {
        assertEquals(1, calculateCoverSampleSize(-1, -1, 512))
    }
}
