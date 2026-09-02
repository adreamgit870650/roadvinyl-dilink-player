package com.car.mp3player.data

import android.content.Context
import android.net.Uri
import com.car.mp3player.lrc.LrcParser
import com.car.mp3player.model.LrcLine
import com.car.mp3player.model.Song
import com.car.mp3player.util.MediaPath
import java.io.File
import java.io.InputStream

class EmbeddedLyricReader(private val context: Context) {
    fun read(song: Song): List<LrcLine>? {
        if (MediaPath.isStream(song.path)) return null
        val lyricText = runCatching {
            open(song.path)?.use(Id3EmbeddedLyricsParser::readLyrics)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null

        return LrcParser.parseContent(lyricText)
            .ifEmpty { LrcParser.fromPlainText(lyricText) }
            .takeIf { it.isNotEmpty() }
    }

    private fun open(path: String): InputStream? = if (path.startsWith("content://")) {
        context.contentResolver.openInputStream(Uri.parse(path))
    } else {
        File(path).inputStream()
    }
}
