package com.car.mp3player.data

import java.io.InputStream

/** Reads embedded USLT/ULT and SYLT/SLT lyric frames from ID3v2 MP3 tags. */
object Id3EmbeddedLyricsParser {
    private const val HEADER_SIZE = 10
    private const val MAX_TAG_SIZE = 16 * 1024 * 1024
    private val lrcTimestamp = Regex("\\[\\d{1,3}:\\d{2}[.:]\\d{2,3}]")

    fun readLyrics(input: InputStream): String? {
        val header = ByteArray(HEADER_SIZE)
        if (!input.readFully(header)) return null
        if (header[0] != 'I'.code.toByte() ||
            header[1] != 'D'.code.toByte() ||
            header[2] != '3'.code.toByte()
        ) {
            return null
        }

        val version = header[3].toInt() and 0xff
        if (version !in 2..4) return null
        val flags = header[5].toInt() and 0xff
        if (version == 2 && flags and 0x40 != 0) return null // Compressed ID3v2.2 tag.

        val tagSize = synchsafeInt(header, 6) ?: return null
        if (tagSize <= 0 || tagSize > MAX_TAG_SIZE) return null
        val body = ByteArray(tagSize)
        if (!input.readFully(body)) return null

        var offset = extendedHeaderEnd(body, version, flags) ?: return null
        val unsynchronisedLyrics = mutableListOf<String>()
        val synchronisedLyrics = mutableListOf<String>()

        while (offset < body.size) {
            val frameHeaderSize = if (version == 2) 6 else 10
            if (offset + frameHeaderSize > body.size) break

            val idLength = if (version == 2) 3 else 4
            val idBytes = body.copyOfRange(offset, offset + idLength)
            if (idBytes.all { it == 0.toByte() }) break
            val frameId = idBytes.toString(Charsets.ISO_8859_1)
            if (!frameId.all { it in 'A'..'Z' || it in '0'..'9' }) break

            val frameSize = when (version) {
                2 -> unsignedInt24(body, offset + 3)
                3 -> unsignedInt32(body, offset + 4)
                else -> synchsafeInt(body, offset + 4)
            } ?: break
            val payloadStart = offset + frameHeaderSize
            val payloadEndLong = payloadStart.toLong() + frameSize.toLong()
            if (frameSize <= 0 || payloadEndLong > body.size) break
            val payloadEnd = payloadEndLong.toInt()

            val frameFlags = if (version == 2) 0 else unsignedInt16(body, offset + 8)
            normalizeFramePayload(
                raw = body.copyOfRange(payloadStart, payloadEnd),
                version = version,
                tagUnsynchronised = flags and 0x80 != 0,
                frameFlags = frameFlags
            )?.let { payload ->
                when (frameId) {
                    "USLT", "ULT" -> decodeUnsynchronisedLyrics(payload)?.let(unsynchronisedLyrics::add)
                    "SYLT", "SLT" -> decodeSynchronisedLyrics(payload)?.let(synchronisedLyrics::add)
                    else -> Unit
                }
            }
            offset = payloadEnd
        }

        return unsynchronisedLyrics.firstOrNull { lrcTimestamp.containsMatchIn(it) }
            ?: synchronisedLyrics.firstOrNull()
            ?: unsynchronisedLyrics.firstOrNull()
    }

    private fun normalizeFramePayload(
        raw: ByteArray,
        version: Int,
        tagUnsynchronised: Boolean,
        frameFlags: Int
    ): ByteArray? {
        var payload = raw
        when (version) {
            3 -> {
                val compressed = frameFlags and 0x0080 != 0
                val encrypted = frameFlags and 0x0040 != 0
                if (compressed || encrypted) return null
                if (frameFlags and 0x0020 != 0) {
                    if (payload.isEmpty()) return null
                    payload = payload.copyOfRange(1, payload.size)
                }
            }
            4 -> {
                val compressed = frameFlags and 0x0008 != 0
                val encrypted = frameFlags and 0x0004 != 0
                if (compressed || encrypted) return null
                if (frameFlags and 0x0040 != 0) {
                    if (payload.isEmpty()) return null
                    payload = payload.copyOfRange(1, payload.size)
                }
                if (frameFlags and 0x0001 != 0) {
                    if (payload.size < 4) return null
                    payload = payload.copyOfRange(4, payload.size)
                }
            }
        }
        return if (tagUnsynchronised || version == 4 && frameFlags and 0x0002 != 0) {
            removeUnsynchronisation(payload)
        } else {
            payload
        }
    }

    private fun decodeUnsynchronisedLyrics(payload: ByteArray): String? {
        if (payload.size < 5) return null
        val encoding = payload[0].toInt() and 0xff
        val lyricsStart = afterTerminatedString(payload, 4, encoding) ?: return null
        return decodeText(payload, lyricsStart, payload.size, encoding)?.cleanLyrics()
    }

    private fun decodeSynchronisedLyrics(payload: ByteArray): String? {
        if (payload.size < 11) return null
        val encoding = payload[0].toInt() and 0xff
        val timestampFormat = payload[4].toInt() and 0xff
        if (timestampFormat != 2) return null // MPEG-frame timestamps cannot be converted without audio details.
        var cursor = afterTerminatedString(payload, 6, encoding) ?: return null
        val lines = mutableListOf<Pair<Long, String>>()

        while (cursor < payload.size) {
            val textEnd = terminatorIndex(payload, cursor, encoding) ?: break
            val afterText = textEnd + terminatorWidth(encoding)
            if (afterText + 4 > payload.size) break
            val text = decodeText(payload, cursor, textEnd, encoding)?.cleanLyrics().orEmpty()
            val timeMs = unsignedInt32(payload, afterText)?.toLong() ?: break
            if (text.isNotEmpty()) lines += timeMs to text
            cursor = afterText + 4
        }
        if (lines.isEmpty()) return null
        return lines.sortedBy { it.first }.joinToString("\n") { (timeMs, text) ->
            "${formatLrcTime(timeMs)}$text"
        }
    }

    private fun extendedHeaderEnd(body: ByteArray, version: Int, flags: Int): Int? {
        if (flags and 0x40 == 0 || version == 2) return 0
        if (body.size < 4) return null
        return when (version) {
            3 -> {
                val size = unsignedInt32(body, 0) ?: return null
                (4L + size).takeIf { it <= body.size }?.toInt()
            }
            4 -> {
                val size = synchsafeInt(body, 0) ?: return null
                size.takeIf { it >= 4 && it <= body.size }
            }
            else -> 0
        }
    }

    private fun afterTerminatedString(bytes: ByteArray, start: Int, encoding: Int): Int? {
        val end = terminatorIndex(bytes, start, encoding) ?: return null
        return end + terminatorWidth(encoding)
    }

    private fun terminatorIndex(bytes: ByteArray, start: Int, encoding: Int): Int? {
        if (encoding !in 0..3 || start !in 0..bytes.size) return null
        val width = terminatorWidth(encoding)
        var index = start
        while (index + width <= bytes.size) {
            if (bytes[index] == 0.toByte() && (width == 1 || bytes[index + 1] == 0.toByte())) {
                return index
            }
            index += width
        }
        return null
    }

    private fun terminatorWidth(encoding: Int): Int = if (encoding == 1 || encoding == 2) 2 else 1

    private fun decodeText(bytes: ByteArray, start: Int, end: Int, encoding: Int): String? {
        if (start < 0 || end < start || end > bytes.size) return null
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> return null
        }
        return runCatching { String(bytes, start, end - start, charset) }.getOrNull()
    }

    private fun String.cleanLyrics(): String? =
        replace("\u0000", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
            .takeIf { it.isNotEmpty() }

    private fun formatLrcTime(timeMs: Long): String {
        val minutes = timeMs / 60_000
        val seconds = timeMs / 1_000 % 60
        val millis = timeMs % 1_000
        return "[${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}.${millis.toString().padStart(3, '0')}]"
    }

    private fun removeUnsynchronisation(bytes: ByteArray): ByteArray {
        val result = ByteArray(bytes.size)
        var read = 0
        var written = 0
        while (read < bytes.size) {
            val value = bytes[read]
            result[written++] = value
            if (value == 0xff.toByte() && read + 1 < bytes.size && bytes[read + 1] == 0.toByte()) {
                read++
            }
            read++
        }
        return result.copyOf(written)
    }

    private fun synchsafeInt(bytes: ByteArray, offset: Int): Int? {
        if (offset < 0 || offset + 4 > bytes.size) return null
        val a = bytes[offset].toInt() and 0xff
        val b = bytes[offset + 1].toInt() and 0xff
        val c = bytes[offset + 2].toInt() and 0xff
        val d = bytes[offset + 3].toInt() and 0xff
        if ((a or b or c or d) and 0x80 != 0) return null
        return (a shl 21) or (b shl 14) or (c shl 7) or d
    }

    private fun unsignedInt16(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xff) shl 8) or
            (bytes[offset + 1].toInt() and 0xff)
    }

    private fun unsignedInt24(bytes: ByteArray, offset: Int): Int? {
        if (offset < 0 || offset + 3 > bytes.size) return null
        return ((bytes[offset].toInt() and 0xff) shl 16) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            (bytes[offset + 2].toInt() and 0xff)
    }

    private fun unsignedInt32(bytes: ByteArray, offset: Int): Int? {
        if (offset < 0 || offset + 4 > bytes.size) return null
        val value = ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)
        return value.takeIf { it <= Int.MAX_VALUE }?.toInt()
    }

    private fun InputStream.readFully(target: ByteArray): Boolean {
        var offset = 0
        while (offset < target.size) {
            val count = read(target, offset, target.size - offset)
            if (count < 0) return false
            if (count == 0) continue
            offset += count
        }
        return true
    }
}
