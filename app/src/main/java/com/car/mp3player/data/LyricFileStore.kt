package com.car.mp3player.data

import android.content.Context
import com.car.mp3player.lrc.LrcParser
import com.car.mp3player.model.LrcLine
import com.car.mp3player.model.Song
import java.io.File
import java.security.MessageDigest

/** Stores lyrics downloaded from online providers without touching the song directory. */
object LyricFileStore {
    fun readOnlineCache(context: Context, song: Song): List<LrcLine>? {
        val file = onlineCacheFile(context, song).takeIf { it.exists() && it.length() > 0L } ?: return null
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return null
        return LrcParser.parseContent(text)
            .ifEmpty { LrcParser.fromPlainText(text) }
            .takeIf { it.isNotEmpty() }
    }

    fun onlineCachePath(context: Context, song: Song): String? =
        onlineCacheFile(context, song).takeIf { it.exists() && it.length() > 0L }?.absolutePath

    fun saveOnlineCache(context: Context, song: Song, lrcText: String): String? {
        val file = onlineCacheFile(context, song)
        return runCatching {
            file.parentFile?.mkdirs()
            file.writeText(lrcText, Charsets.UTF_8)
            file.absolutePath
        }.getOrNull()
    }

    fun deleteOnlineCache(context: Context, song: Song) {
        onlineCacheFile(context, song).delete()
    }

    private fun onlineCacheFile(context: Context, song: Song): File =
        File(File(context.cacheDir, "lyrics"), "${songKey(song)}.lrc")

    private fun songKey(song: Song): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest("${song.path}|${song.title}|${song.artist}".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
