package com.car.mp3player.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackResumeRequestGateTest {
    @Test
    fun `same resume request is suppressed while service is starting`() {
        val gate = PlaybackResumeRequestGate(debounceMs = 10_000L)

        assertTrue(gate.tryAcquire("MUSIC|song.mp3", 1_000L))
        assertFalse(gate.tryAcquire("MUSIC|song.mp3", 5_000L))
        assertTrue(gate.tryAcquire("MUSIC|song.mp3", 11_000L))
    }

    @Test
    fun `a different song can start immediately`() {
        val gate = PlaybackResumeRequestGate(debounceMs = 10_000L)

        assertTrue(gate.tryAcquire("MUSIC|first.mp3", 1_000L))
        assertTrue(gate.tryAcquire("MUSIC|second.mp3", 1_001L))
    }

    @Test
    fun `failed service start releases request`() {
        val gate = PlaybackResumeRequestGate(debounceMs = 10_000L)

        assertTrue(gate.tryAcquire("MUSIC|song.mp3", 1_000L))
        gate.release("MUSIC|song.mp3")
        assertTrue(gate.tryAcquire("MUSIC|song.mp3", 1_001L))
    }
}
