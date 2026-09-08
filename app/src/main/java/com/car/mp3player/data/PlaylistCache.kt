package com.car.mp3player.data

import android.content.Context
import android.util.AtomicFile
import com.car.mp3player.model.LibraryKind
import com.car.mp3player.model.Song
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object PlaylistCache {
    private const val CACHE_FILE = "playlist_cache.json"
    private const val QUEUE_FILE = "play_queue.json"
    private const val MUSIC_QUEUE_FILE = "play_queue_music.json"
    private const val ONLINE_QUEUE_FILE = "play_queue_online.json"
    private const val RADIO_QUEUE_FILE = "play_queue_radio.json"
    private const val PODCAST_QUEUE_FILE = "play_queue_podcast.json"

    fun save(context: Context, songs: List<Song>) {
        if (songs.isEmpty()) {
            runCatching { AtomicFile(File(context.filesDir, CACHE_FILE)).delete() }
        } else {
            writeSongs(context, CACHE_FILE, songs)
        }
    }

    fun load(context: Context): List<Song> = readSongs(context, CACHE_FILE)

    fun clearMusicLibrary(context: Context) {
        listOf(CACHE_FILE, MUSIC_QUEUE_FILE, QUEUE_FILE).forEach { fileName ->
            runCatching { AtomicFile(File(context.filesDir, fileName)).delete() }
        }
        MusicIndexStore(context).let { store ->
            try {
                store.clear()
            } finally {
                store.close()
            }
        }
    }

    fun saveQueue(context: Context, songs: List<Song>, library: LibraryKind = LibraryKind.MUSIC) {
        writeSongs(context, queueFile(library), songs)
        writeSongs(context, QUEUE_FILE, songs)
    }

    fun loadQueue(context: Context, library: LibraryKind = LibraryKind.MUSIC): List<Song> {
        val typed = readSongs(context, queueFile(library))
        if (typed.isNotEmpty()) return typed
        return readSongs(context, QUEUE_FILE)
    }

    private fun queueFile(library: LibraryKind): String = when (library) {
        LibraryKind.MUSIC -> MUSIC_QUEUE_FILE
        LibraryKind.ONLINE -> ONLINE_QUEUE_FILE
        LibraryKind.RADIO -> RADIO_QUEUE_FILE
        LibraryKind.PODCAST -> PODCAST_QUEUE_FILE
    }

    private fun writeSongs(context: Context, fileName: String, songs: List<Song>) {
        if (songs.isEmpty()) return
        val array = JSONArray()
        songs.forEach { song ->
            array.put(
                JSONObject().apply {
                    put("id", song.id)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("path", song.path)
                    put("lrcPath", song.lrcPath)
                    put("durationMs", song.durationMs)
                }
            )
        }
        val target = AtomicFile(File(context.filesDir, fileName))
        var output: FileOutputStream? = null
        try {
            output = target.startWrite()
            output.write(array.toString().toByteArray(Charsets.UTF_8))
            target.finishWrite(output)
            output = null
        } catch (_: Exception) {
            output?.let(target::failWrite)
        }
    }

    private fun readSongs(context: Context, fileName: String): List<Song> {
        val file = File(context.filesDir, fileName)
        if (!file.exists() && !File(file.absolutePath + ".bak").exists()) return emptyList()
        return runCatching {
            val text = AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            val array = JSONArray(text)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        Song(
                            id = item.optLong("id"),
                            title = item.optString("title"),
                            artist = item.optString("artist"),
                            path = item.optString("path"),
                            lrcPath = item.optString("lrcPath").takeIf { it.isNotBlank() },
                            durationMs = item.optLong("durationMs")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
