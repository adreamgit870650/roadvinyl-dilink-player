package com.car.mp3player.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Id3EmbeddedLyricsParserTest {
    @Test
    fun `reads timestamped UTF-8 USLT from ID3v2_4`() {
        val lyrics = "[00:01.20]第一句\n[00:04.50]Second line"
        val payload = bytes(3, 'e'.code, 'n'.code, 'g'.code, 0) + lyrics.toByteArray(Charsets.UTF_8)
        val mp3 = id3Tag(4, frame(4, "USLT", payload))

        assertEquals(lyrics, Id3EmbeddedLyricsParser.readLyrics(ByteArrayInputStream(mp3)))
    }

    @Test
    fun `reads plain UTF-16 USLT from ID3v2_3`() {
        val lyrics = "第一句\n第二句"
        val payload = bytes(1, 'z'.code, 'h'.code, 'o'.code, 0, 0) +
            lyrics.toByteArray(Charsets.UTF_16)
        val mp3 = id3Tag(3, frame(3, "USLT", payload))

        assertEquals(lyrics, Id3EmbeddedLyricsParser.readLyrics(ByteArrayInputStream(mp3)))
    }

    @Test
    fun `converts millisecond SYLT entries to LRC`() {
        val payload = ByteArrayOutputStream().apply {
            write(bytes(3, 'e'.code, 'n'.code, 'g'.code, 2, 1, 0))
            write("First line".toByteArray(Charsets.UTF_8))
            write(0)
            write(bigEndianInt(1_250))
            write("Second line".toByteArray(Charsets.UTF_8))
            write(0)
            write(bigEndianInt(63_045))
        }.toByteArray()
        val mp3 = id3Tag(4, frame(4, "SYLT", payload))

        assertEquals(
            "[00:01.250]First line\n[01:03.045]Second line",
            Id3EmbeddedLyricsParser.readLyrics(ByteArrayInputStream(mp3))
        )
    }

    @Test
    fun `reads legacy ULT frame from ID3v2_2`() {
        val lyrics = "legacy lyrics"
        val payload = bytes(0, 'e'.code, 'n'.code, 'g'.code, 0) +
            lyrics.toByteArray(Charsets.ISO_8859_1)
        val mp3 = id3Tag(2, frame(2, "ULT", payload))

        assertEquals(lyrics, Id3EmbeddedLyricsParser.readLyrics(ByteArrayInputStream(mp3)))
    }

    @Test
    fun `rejects a file without an ID3v2 tag`() {
        val mp3Header = byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0x90.toByte(), 0x64)

        assertNull(Id3EmbeddedLyricsParser.readLyrics(ByteArrayInputStream(mp3Header)))
    }

    private fun id3Tag(version: Int, body: ByteArray): ByteArray =
        bytes('I'.code, 'D'.code, '3'.code, version, 0, 0) + synchsafe(body.size) + body

    private fun frame(version: Int, id: String, payload: ByteArray): ByteArray {
        val size = when (version) {
            2 -> byteArrayOf(
                (payload.size ushr 16).toByte(),
                (payload.size ushr 8).toByte(),
                payload.size.toByte()
            )
            3 -> bigEndianInt(payload.size)
            else -> synchsafe(payload.size)
        }
        val flags = if (version == 2) byteArrayOf() else byteArrayOf(0, 0)
        return id.toByteArray(Charsets.ISO_8859_1) + size + flags + payload
    }

    private fun synchsafe(value: Int): ByteArray = byteArrayOf(
        ((value ushr 21) and 0x7f).toByte(),
        ((value ushr 14) and 0x7f).toByte(),
        ((value ushr 7) and 0x7f).toByte(),
        (value and 0x7f).toByte()
    )

    private fun bigEndianInt(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte()
    )

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
