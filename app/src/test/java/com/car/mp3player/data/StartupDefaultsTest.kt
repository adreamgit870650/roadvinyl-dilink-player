package com.car.mp3player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StartupDefaultsTest {
    @Test
    fun `first launch has no default scan directories`() {
        assertEquals("", SettingsRepository.DEFAULT_SCAN_PATHS)
    }

    @Test
    fun `startup greeting is disabled by default`() {
        assertFalse(SettingsRepository.DEFAULT_STARTUP_SOUND_ENABLED)
    }
}
