package com.car.mp3player.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicIndexRecordTest {
    private val record = MusicIndexRecord(
        cacheKey = "file:/music/song.mp3",
        songPath = "/music/song.mp3",
        title = "song",
        fingerprint = AudioFingerprint(fileSize = 1234L, modifiedAt = 5678L),
        artist = "Artist",
        durationMs = 9000L
    )

    @Test
    fun `cache matches only identical file fingerprint`() {
        assertTrue(record.matches(AudioFingerprint(1234L, 5678L)))
        assertFalse(record.matches(AudioFingerprint(1235L, 5678L)))
        assertFalse(record.matches(AudioFingerprint(1234L, 5679L)))
    }

    @Test
    fun `unknown fingerprint is always refreshed`() {
        val unknown = MusicIndexRecord(
            "uri:test", "content://test", "test", AudioFingerprint(0L, 0L), "Artist", 1L
        )
        assertFalse(unknown.matches(AudioFingerprint(0L, 0L)))
    }
}
