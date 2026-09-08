package com.car.mp3player.data

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.car.mp3player.MusicPlaybackService
import com.car.mp3player.model.LibraryKind
import com.car.mp3player.model.Song
import com.car.mp3player.playback.PlaybackResumeRequestGate
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object PlaybackBootstrap {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val resumeRequestGate = PlaybackResumeRequestGate()

    fun scanMusicLibrary(context: Context, settings: SettingsRepository): List<Song> {
        val quickScan = scanMusicLibraryQuick(context, settings)
        return enrichMusicLibrary(context, quickScan)
    }

    fun scanMusicLibraryQuick(context: Context, settings: SettingsRepository): MusicScanSnapshot {
        if (settings.allScanEntries().isEmpty()) {
            return MusicScanSnapshot(emptyList(), emptyList(), emptySet(), scanComplete = false)
        }
        return MusicScanner(
            context,
            settings.scanPaths(),
            settings.scanTreeUris()
        ).scanQuickIndexed()
    }

    fun enrichMusicLibrary(context: Context, scan: MusicScanSnapshot): List<Song> {
        val enriched = MusicScanner(context).enrichMetadata(scan)
        PlaylistCache.save(context, enriched)
        return enriched
    }

    fun enrichSingleMusic(context: Context, song: Song): Song = MusicScanner(context).enrichSingle(song)

    fun loadCachedMusic(context: Context): List<Song> {
        MusicIndexStore(context).let { store ->
            try {
                store.recoverInterruptedScan()
            } finally {
                store.close()
            }
        }
        return PlaylistCache.load(context)
    }

    fun resumeIfNeeded(
        context: Context,
        songs: List<Song>,
        settings: SettingsRepository,
        library: LibraryKind = settings.lastActiveLibrary
    ): Boolean {
        if (!settings.autoResumePlayback || songs.isEmpty()) return false
        val path = settings.lastSongPath(library) ?: return false
        val index = songs.indexOfFirst { it.path == path }
        if (index < 0) {
            settings.setLastSong(library, null, 0L)
            return false
        }
        if (!songReadable(songs[index])) {
            settings.setLastSong(library, null, 0L)
            return false
        }
        val requestKey = "${library.name}|$path"
        // A duplicate means an identical resume command is already in flight, so
        // callers should treat it as handled rather than scheduling a retry.
        if (!resumeRequestGate.tryAcquire(requestKey, SystemClock.elapsedRealtime())) return true
        ioScope.launch { PlaylistCache.saveQueue(context, songs, library) }
        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_PLAY_INDEX
            putExtra(MusicPlaybackService.EXTRA_INDEX, index)
            putExtra(MusicPlaybackService.EXTRA_SEEK, settings.lastPositionMs(library))
            putExtra(MusicPlaybackService.EXTRA_LIBRARY, library.name)
        }
        return runCatching {
            ContextCompat.startForegroundService(context, intent)
            true
        }.getOrElse {
            resumeRequestGate.release(requestKey)
            false
        }
    }

    private fun songReadable(song: Song): Boolean {
        if (song.path.startsWith("content://")) return true
        if (song.path.startsWith("http") || song.path.startsWith("online://") ||
            song.path.startsWith("radio://") || song.path.startsWith("podcast://")
        ) return true
        return runCatching { File(song.path).isFile }.getOrDefault(false)
    }
}
