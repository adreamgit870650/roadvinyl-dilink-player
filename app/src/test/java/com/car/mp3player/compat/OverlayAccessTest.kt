package com.car.mp3player.compat

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayAccessTest {
    @Test fun `permission alone cannot override ignored or denied AppOps`() {
        for (mode in listOf(1, 2, 3, 4)) {
            assertEquals(OverlayAccess.DENIED, legacyOverlayAccess(true, mode))
        }
    }

    @Test fun `both permission and allowed AppOp are needed`() {
        assertEquals(OverlayAccess.ALLOWED, legacyOverlayAccess(true, 0))
        assertEquals(OverlayAccess.DENIED, legacyOverlayAccess(false, 0))
    }

    @Test fun `unavailable AppOps must not be reported as allowed`() {
        assertEquals(OverlayAccess.UNKNOWN, legacyOverlayAccess(true, null))
        assertEquals(OverlayAccess.DENIED, legacyOverlayAccess(false, null))
    }
}
