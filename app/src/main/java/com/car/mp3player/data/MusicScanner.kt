package com.car.mp3player.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.car.mp3player.model.Song
import com.car.mp3player.util.LibraryTitleComparator
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

data class MusicScanSnapshot internal constructor(
    val songs: List<Song>,
    internal val entries: List<IndexedMusicEntry>,
    internal val seenKeys: Set<String>,
    val scanComplete: Boolean
) {
    val pendingMetadataCount: Int get() = entries.count(IndexedMusicEntry::needsMetadata)
}

internal data class IndexedMusicEntry(
    val cacheKey: String,
    val fingerprint: AudioFingerprint,
    val song: Song,
    val needsMetadata: Boolean = true
)

private data class EnrichedMusicEntry(
    val entry: IndexedMusicEntry,
    val song: Song,
    val indexRecord: MusicIndexRecord?
)

private data class IndexedEntryCollection(
    val entries: List<IndexedMusicEntry>,
    val complete: Boolean
)

class MusicScanner(
    private val context: Context,
    private val customPaths: List<String> = emptyList(),
    private val treeUris: List<String> = emptyList()
) {
    private val audioExtensions = setOf("mp3", "flac", "m4a", "wav", "ogg", "aac")
    private val titleComparator = LibraryTitleComparator()
    private val entryTitleComparator = Comparator<IndexedMusicEntry> { left, right ->
        titleComparator.compare(left.song.title, right.song.title)
    }

    fun scan(): List<Song> = enrichMetadata(scanQuickIndexed())

    /** Builds a usable playlist without opening every audio file. */
    fun scanQuick(): List<Song> = scanQuickIndexed().songs

    /** Reuses indexed metadata when path, size and modification time still match. */
    fun scanQuickIndexed(): MusicScanSnapshot {
        val cached = MusicIndexStore(context).let { store ->
            try {
                store.beginScan()
                store.loadAll()
            } finally {
                store.close()
            }
        }
        val merged = linkedMapOf<String, IndexedMusicEntry>()
        val directories = scanDirectoriesQuick(resolvePaths())
        val documents = scanDocumentTreesQuick()
        directories.entries.forEach { merged[it.cacheKey] = applyCache(it, cached[it.cacheKey]) }
        documents.entries.forEach { merged[it.cacheKey] = applyCache(it, cached[it.cacheKey]) }
        val seenKeys = merged.keys.toSet()
        val hasConfiguredRoots = customPaths.isNotEmpty() || treeUris.isNotEmpty()
        val scanComplete = hasConfiguredRoots && directories.complete && documents.complete
        if (!scanComplete) {
            var retainedId = 20_000_000L
            cached.values.forEach { record ->
                if (record.cacheKey !in merged && record.songPath.isNotBlank()) {
                    merged[record.cacheKey] = IndexedMusicEntry(
                        cacheKey = record.cacheKey,
                        fingerprint = record.fingerprint,
                        song = Song(
                            id = retainedId++,
                            title = record.title,
                            artist = record.artist,
                            path = record.songPath,
                            lrcPath = null,
                            durationMs = record.durationMs
                        ),
                        needsMetadata = false
                    )
                }
            }
        }
        val entries = merged.values.sortedWith(entryTitleComparator)
        return MusicScanSnapshot(entries.map(IndexedMusicEntry::song), entries, seenKeys, scanComplete)
    }

    /** Reads only missing/changed metadata, then updates and prunes the SQLite index. */
    fun enrichMetadata(snapshot: MusicScanSnapshot): List<Song> {
        val pending = snapshot.entries.filter(IndexedMusicEntry::needsMetadata)
        val enriched = enrichEntries(pending)
        val replacements = enriched.associateBy({ it.entry.cacheKey }, EnrichedMusicEntry::song)
        val result = snapshot.entries.map { entry -> replacements[entry.cacheKey] ?: entry.song }
        val store = MusicIndexStore(context)
        try {
            store.saveScan(
                records = enriched.mapNotNull(EnrichedMusicEntry::indexRecord),
                seenKeys = snapshot.seenKeys,
                scanComplete = snapshot.scanComplete
            )
        } finally {
            store.close()
        }
        return result
    }

    /** Priority path used when a user selects a song before bulk enrichment reaches it. */
    fun enrichSingle(song: Song): Song {
        val metadata = readMetadata(song.path, song.title, song.artist) ?: return song
        return song.copy(artist = metadata.artist, durationMs = metadata.durationMs)
    }

    private fun enrichEntries(entries: List<IndexedMusicEntry>): List<EnrichedMusicEntry> {
        if (entries.isEmpty()) return emptyList()
        if (entries.size == 1) return listOf(enrichEntry(entries.first()))
        val executor = Executors.newFixedThreadPool(minOf(MAX_METADATA_WORKERS, entries.size))
        return try {
            val futures = executor.invokeAll(entries.map { entry -> Callable { enrichEntry(entry) } })
            entries.indices.map { index ->
                runCatching { futures[index].get() }.getOrElse {
                    EnrichedMusicEntry(entries[index], entries[index].song, null)
                }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun enrichEntry(entry: IndexedMusicEntry): EnrichedMusicEntry {
        val metadata = readMetadata(entry.song.path, entry.song.title, entry.song.artist)
            ?: return EnrichedMusicEntry(entry, entry.song, null)
        val song = entry.song.copy(artist = metadata.artist, durationMs = metadata.durationMs)
        return EnrichedMusicEntry(
            entry = entry,
            song = song,
            indexRecord = MusicIndexRecord(
                cacheKey = entry.cacheKey,
                songPath = song.path,
                title = song.title,
                fingerprint = entry.fingerprint,
                artist = song.artist,
                durationMs = song.durationMs
            )
        )
    }

    private fun applyCache(entry: IndexedMusicEntry, cached: MusicIndexRecord?): IndexedMusicEntry {
        if (cached?.matches(entry.fingerprint) != true) return entry
        return entry.copy(
            song = entry.song.copy(artist = cached.artist, durationMs = cached.durationMs),
            needsMetadata = false
        )
    }

    private fun resolvePaths(): List<File> = customPaths.map(::File).distinctBy { it.absolutePath }

    private fun scanDirectoriesQuick(paths: List<File>): IndexedEntryCollection {
        if (paths.isEmpty()) return IndexedEntryCollection(emptyList(), true)
        val collected = SelectedDirectoryAudioFiles.collectWithStatus(paths, audioExtensions)
        val entries = collected.files.mapIndexed { index, file ->
            indexedEntry(
                song = Song(
                    id = index + 1L,
                    title = file.nameWithoutExtension,
                    artist = file.parentFile?.name ?: "本地音乐",
                    path = file.absolutePath,
                    lrcPath = null,
                    durationMs = 0L
                ),
                fingerprint = AudioFingerprint(file.length(), file.lastModified())
            )
        }
        return IndexedEntryCollection(entries, collected.complete)
    }

    private fun scanDocumentTreesQuick(): IndexedEntryCollection {
        val entries = mutableListOf<IndexedMusicEntry>()
        var complete = true
        var id = 10_000_000L
        treeUris.forEach { uriText ->
            val root = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(uriText)) }.getOrNull()
            val rootReady = root != null && runCatching { root.isDirectory && root.canRead() }.getOrDefault(false)
            if (!rootReady) {
                complete = false
                return@forEach
            }
            if (!walkDocumentFileQuick(root!!, entries) { id++ }) complete = false
        }
        return IndexedEntryCollection(entries, complete)
    }

    private fun walkDocumentFileQuick(
        file: DocumentFile,
        out: MutableList<IndexedMusicEntry>,
        nextId: () -> Long
    ): Boolean {
        val isDirectory = runCatching { file.isDirectory }.getOrElse { return false }
        if (isDirectory) {
            val children = runCatching { file.listFiles() }.getOrElse { return false }
            var complete = true
            children.forEach { child ->
                if (!walkDocumentFileQuick(child, out, nextId)) complete = false
            }
            return complete
        }
        val name = runCatching { file.name }.getOrElse { return false } ?: return true
        if (!isAudioFile(name)) return true
        out += indexedEntry(
            song = Song(
                id = nextId(),
                title = name.substringBeforeLast('.'),
                artist = "本地音乐",
                path = file.uri.toString(),
                lrcPath = null,
                durationMs = 0L
            ),
            fingerprint = runCatching { AudioFingerprint(file.length(), file.lastModified()) }
                .getOrDefault(AudioFingerprint(0L, 0L))
        )
        return true
    }

    private fun indexedEntry(song: Song, fingerprint: AudioFingerprint): IndexedMusicEntry =
        IndexedMusicEntry(AudioFileIdentity.key(song.path), fingerprint, song)

    private fun readMetadata(
        pathOrUri: String,
        fallbackTitle: String,
        fallbackArtist: String
    ): AudioMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
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
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun isAudioFile(path: String): Boolean =
        path.substringAfterLast('.', "").lowercase() in audioExtensions

    private data class AudioMetadata(
        val title: String,
        val artist: String,
        val durationMs: Long
    )

    companion object {
        private const val MAX_METADATA_WORKERS = 2
    }
}
