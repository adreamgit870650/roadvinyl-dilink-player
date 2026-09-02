package com.car.mp3player.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.car.mp3player.model.Song
import com.car.mp3player.util.LibraryTitleComparator
import java.io.File

class MusicScanner(
    private val context: Context,
    private val customPaths: List<String> = emptyList(),
    private val treeUris: List<String> = emptyList()
) {
    private val audioExtensions = setOf("mp3", "flac", "m4a", "wav", "ogg", "aac")
    private val titleComparator = LibraryTitleComparator()
    private val songTitleComparator = Comparator<Song> { left, right ->
        titleComparator.compare(left.title, right.title)
    }

    fun scan(): List<Song> {
        val merged = linkedMapOf<String, Song>()
        scanDirectories(resolvePaths()).forEach { song ->
            merged[AudioFileIdentity.key(song.path)] = song
        }
        scanDocumentTrees().forEach { song ->
            merged[AudioFileIdentity.key(song.path)] = song
        }
        return merged.values.sortedWith(songTitleComparator)
    }

    private fun resolvePaths(): List<File> {
        return customPaths.map(::File).distinctBy { it.absolutePath }
    }

    private fun scanDirectories(paths: List<File>): List<Song> {
        val songs = mutableListOf<Song>()
        var id = 1L
        SelectedDirectoryAudioFiles.collect(paths, audioExtensions).forEach { file ->
            val metadata = readMetadata(
                file.absolutePath,
                fallbackTitle = file.nameWithoutExtension,
                fallbackArtist = file.parentFile?.name ?: "本地音乐"
            )
            songs.add(
                Song(
                    id = id++,
                    title = metadata.title,
                    artist = metadata.artist,
                    path = file.absolutePath,
                    lrcPath = null,
                    durationMs = metadata.durationMs
                )
            )
        }
        return songs
    }

    private fun scanDocumentTrees(): List<Song> {
        val songs = mutableListOf<Song>()
        var id = 10_000_000L
        for (uriStr in treeUris) {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(uriStr)) ?: continue
            walkDocumentFile(root, songs) { id++ }
        }
        return songs
    }

    private fun walkDocumentFile(
        file: DocumentFile,
        out: MutableList<Song>,
        nextId: () -> Long
    ) {
        if (file.isDirectory) {
            file.listFiles().forEach { walkDocumentFile(it, out, nextId) }
            return
        }
        val name = file.name ?: return
        if (!isAudioFile(name)) return
        val uri = file.uri.toString()
        val id = nextId()
        val metadata = readMetadata(
            uri,
            fallbackTitle = name.substringBeforeLast('.'),
            fallbackArtist = "本地音乐"
        )
        out.add(
            Song(
                id = id,
                title = metadata.title,
                artist = metadata.artist,
                path = uri,
                lrcPath = null,
                durationMs = metadata.durationMs
            )
        )
    }

    private fun readMetadata(
        pathOrUri: String,
        fallbackTitle: String,
        fallbackArtist: String
    ): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        val metadata = runCatching {
            if (pathOrUri.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(pathOrUri))
            } else {
                retriever.setDataSource(pathOrUri)
            }
            AudioMetadata(
                title = fallbackTitle,
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" } ?: fallbackArtist,
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            )
        }.getOrDefault(AudioMetadata(fallbackTitle, fallbackArtist, 0L))
        runCatching { retriever.release() }
        return metadata
    }

    private fun isAudioFile(path: String): Boolean {
        return path.substringAfterLast('.', "").lowercase() in audioExtensions
    }

    private data class AudioMetadata(
        val title: String,
        val artist: String,
        val durationMs: Long
    )
}
