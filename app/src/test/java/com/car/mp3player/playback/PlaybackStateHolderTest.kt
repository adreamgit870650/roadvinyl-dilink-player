package com.car.mp3player.playback

import com.car.mp3player.model.LibraryKind
import com.car.mp3player.model.PlaybackMode
import com.car.mp3player.model.LrcChar
import com.car.mp3player.model.LrcLine
import com.car.mp3player.model.Song
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateHolderTest {
    @After
    fun resetState() {
        PlaybackStateHolder.clearPlaylist()
        PlaybackStateHolder.setPlayMode(PlaybackMode.SHUFFLE)
    }

    @Test
    fun `clearPlaylist resets queue and current playback state`() {
        val song = Song(1L, "Test", "Artist", "/music/test.mp3", null, 12_000L)
        val line = LrcLine(listOf(LrcChar("词", 1_000L)), 1_000L, 2_000L)
        PlaybackStateHolder.setPlaylist(listOf(song), library = LibraryKind.MUSIC)
        PlaybackStateHolder.setCoverArt("/cache/cover.jpg")
        PlaybackStateHolder.update(song, true, 1_500L, listOf(line), 12_000L)

        PlaybackStateHolder.clearPlaylist()

        assertTrue(PlaybackStateHolder.songs.isEmpty())
        assertEquals(-1, PlaybackStateHolder.currentIndex)
        assertNull(PlaybackStateHolder.currentSong)
        assertFalse(PlaybackStateHolder.isPlaying)
        assertEquals(0L, PlaybackStateHolder.positionMs)
        assertEquals(0L, PlaybackStateHolder.durationMs)
        assertTrue(PlaybackStateHolder.lrcLines.isEmpty())
        assertNull(PlaybackStateHolder.coverArtPath)
    }

    @Test
    fun `play mode can change safely with an empty playlist`() {
        PlaybackStateHolder.clearPlaylist()

        PlaybackStateHolder.setPlayMode(PlaybackMode.ORDER)

        assertTrue(PlaybackStateHolder.songs.isEmpty())
        assertEquals(PlaybackMode.ORDER, PlaybackStateHolder.playMode)
    }
}
