package com.car.mp3player.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SelectedDirectoryAudioFilesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `collects only audio under selected roots including deeply nested folders`() {
        val selectedRoot = temporaryFolder.newFolder("selected")
        var nested = selectedRoot
        repeat(12) { depth -> nested = File(nested, "level-$depth").apply { mkdir() } }
        val selectedSong = File(nested, "inside.MP3").apply { writeBytes(byteArrayOf(1)) }
        File(selectedRoot, "ignore.txt").writeText("not audio")
        val outsideSong = File(temporaryFolder.newFolder("outside"), "outside.mp3")
            .apply { writeBytes(byteArrayOf(2)) }

        val result = SelectedDirectoryAudioFiles.collect(listOf(selectedRoot), setOf("mp3"))

        assertEquals(listOf(selectedSong.canonicalPath), result.map { it.canonicalPath })
        assertFalse(result.any { it.canonicalPath == outsideSong.canonicalPath })
    }

    @Test
    fun `does not duplicate a file when selected roots overlap`() {
        val root = temporaryFolder.newFolder("music")
        val child = File(root, "album").apply { mkdir() }
        val song = File(child, "song.mp3").apply { writeBytes(byteArrayOf(1)) }

        val result = SelectedDirectoryAudioFiles.collect(listOf(root, child), setOf("mp3"))

        assertEquals(listOf(song.canonicalPath), result.map { it.canonicalPath })
    }

    @Test
    fun `marks scan incomplete when a selected root is unavailable`() {
        val available = temporaryFolder.newFolder("available")
        val song = File(available, "song.mp3").apply { writeBytes(byteArrayOf(1)) }
        val missing = File(temporaryFolder.root, "missing-usb")

        val result = SelectedDirectoryAudioFiles.collectWithStatus(
            listOf(available, missing),
            setOf("mp3")
        )

        assertEquals(listOf(song.canonicalPath), result.files.map { it.canonicalPath })
        assertFalse(result.complete)
        assertTrue(result.files.isNotEmpty())
    }

    @Test
    fun `raw path and external storage document uri have the same identity`() {
        val rawPath = "/storage/emulated/0/Music/Album/A+B song.mp3"
        val documentUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic/" +
            "document/primary%3AMusic%2FAlbum%2FA%2BB%20song.mp3"

        assertEquals(AudioFileIdentity.key(rawPath), AudioFileIdentity.key(documentUri))
    }
}
