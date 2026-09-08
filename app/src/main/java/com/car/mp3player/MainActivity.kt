package com.car.mp3player

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.car.mp3player.data.PlaybackBootstrap
import com.car.mp3player.data.PlaylistCache
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.databinding.ActivityMainBinding
import com.car.mp3player.model.LibraryKind
import com.car.mp3player.model.LrcLine
import com.car.mp3player.model.PlaybackMode
import com.car.mp3player.model.Song
import com.car.mp3player.playback.PlaybackStateHolder
import com.car.mp3player.ui.AppThemeManager
import com.car.mp3player.ui.ImmersiveHelper
import com.car.mp3player.ui.MainHost
import com.car.mp3player.ui.MainPagerAdapter
import com.car.mp3player.ui.PlayerFragment
import com.car.mp3player.ui.PlaylistFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), MainHost, PlaybackStateHolder.Listener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsRepository
    private var musicSongs: List<Song> = emptyList()
    private var pendingScanCallback: ((Int) -> Unit)? = null
    private var musicScanGeneration = 0
    private var musicScanJob: Job? = null
    private var dockPlayingState: Boolean? = null

    private val storagePermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val callback = pendingScanCallback
        pendingScanCallback = null
        if (hasScanPermission()) {
            performMusicScan(callback)
        } else {
            callback?.invoke(0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = SettingsRepository(this)
        settings.applyTheme()
        setTheme(settings.appTheme().styleRes)
        super.onCreate(savedInstanceState)
        ImmersiveHelper.apply(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewPager.adapter = MainPagerAdapter(this)
        binding.viewPager.isUserInputEnabled = false
        binding.viewPager.offscreenPageLimit = 3
        setupPlayerDockControls()
        updatePlayerDockVisibility()
        applyAppTheme()

        binding.bottomNav.selectedItemId = R.id.nav_player
        binding.viewPager.setCurrentItem(1, false)

        binding.bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.nav_exit) {
                exitApplication()
                return@setOnItemSelectedListener false
            }
            val index = when (item.itemId) {
                R.id.nav_playlist -> 0
                R.id.nav_player -> 1
                R.id.nav_settings -> 2
                else -> return@setOnItemSelectedListener false
            }
            binding.viewPager.setCurrentItem(index, false)
            if (item.itemId == R.id.nav_player) {
                binding.viewPager.post {
                    (supportFragmentManager.findFragmentByTag("f1") as? PlayerFragment)
                        ?.syncBottomNavTheme()
                }
            } else {
                applyAppTheme()
            }
            true
        }

        restoreCachedLibraries()
    }

    private fun setupPlayerDockControls() {
        binding.dockBtnPlayPause.setOnClickListener {
            sendDockPlaybackAction(MusicPlaybackService.ACTION_TOGGLE)
        }
        binding.dockBtnNext.setOnClickListener {
            sendDockPlaybackAction(MusicPlaybackService.ACTION_NEXT)
        }
        binding.dockBtnPrev.setOnClickListener {
            sendDockPlaybackAction(MusicPlaybackService.ACTION_PREV)
        }
        binding.dockBtnMode.setOnClickListener { toggleDockPlaybackMode() }
        binding.dockBtnLyrics.setOnClickListener {
            switchToTab(1)
            binding.viewPager.post {
                (supportFragmentManager.findFragmentByTag("f1") as? PlayerFragment)
                    ?.toggleLyricsFromBottomDock()
            }
        }
        renderDockPlaybackState(PlaybackStateHolder.isPlaying)
        renderDockPlaybackMode(PlaybackStateHolder.playMode)
    }

    private fun updatePlayerDockVisibility() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        binding.playerDockControls.isVisible = landscape
        (binding.bottomNav.layoutParams as ConstraintLayout.LayoutParams).apply {
            width = if (landscape) {
                (resources.displayMetrics.widthPixels * LANDSCAPE_NAV_WIDTH_RATIO).toInt()
            } else {
                ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            }
            horizontalBias = if (landscape) 0f else 0.5f
            binding.bottomNav.layoutParams = this
        }
    }

    private fun sendDockPlaybackAction(action: String) {
        if (PlaybackStateHolder.songs.isEmpty()) {
            Toast.makeText(this, R.string.no_songs, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, MusicPlaybackService::class.java).apply { this.action = action }
            )
        }.onFailure {
            Toast.makeText(this, R.string.playback_start_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleDockPlaybackMode() {
        val next = if (PlaybackStateHolder.playMode == PlaybackMode.ORDER) {
            PlaybackMode.SHUFFLE
        } else {
            PlaybackMode.ORDER
        }
        if (PlaybackStateHolder.songs.isEmpty()) {
            settings.playMode = next
            PlaybackStateHolder.setPlayMode(next)
            return
        }
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, MusicPlaybackService::class.java).apply {
                    action = MusicPlaybackService.ACTION_SET_MODE
                    putExtra(MusicPlaybackService.EXTRA_MODE, next.ordinal)
                }
            )
        }
    }

    private fun renderDockPlaybackState(playing: Boolean) {
        if (dockPlayingState == playing) return
        dockPlayingState = playing
        binding.dockBtnPlayPause.setImageResource(
            if (playing) R.drawable.ic_pause else R.drawable.ic_play
        )
        binding.dockBtnPlayPause.contentDescription =
            getString(if (playing) R.string.pause else R.string.play)
    }

    private fun renderDockPlaybackMode(mode: PlaybackMode) {
        val shuffle = mode == PlaybackMode.SHUFFLE
        binding.dockBtnMode.setImageResource(
            if (shuffle) R.drawable.ic_mode_shuffle else R.drawable.ic_mode_order
        )
        binding.dockBtnMode.contentDescription =
            getString(if (shuffle) R.string.mode_shuffle else R.string.mode_order)
    }

    private fun exitApplication() {
        persistCurrentPlaybackProgress()
        LyricsOverlayService.stop(this)
        ClusterLyricService.stop(this)
        stopService(Intent(this, BootResumeService::class.java))
        stopService(Intent(this, MusicPlaybackService::class.java))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else {
            finish()
        }
        Handler(Looper.getMainLooper()).postDelayed(
            { android.os.Process.killProcess(android.os.Process.myPid()) },
            EXIT_PROCESS_DELAY_MS
        )
    }

    private fun persistCurrentPlaybackProgress() {
        val song = PlaybackStateHolder.currentSong ?: return
        settings.setLastSongImmediately(
            PlaybackStateHolder.activeLibrary,
            song.path,
            PlaybackStateHolder.positionMs
        )
    }

    override fun onResume() {
        super.onResume()
        ImmersiveHelper.apply(this)
        applyAppTheme()
        if (binding.viewPager.currentItem == 1) {
            (supportFragmentManager.findFragmentByTag("f1") as? PlayerFragment)?.syncBottomNavTheme()
        }
    }

    override fun onStart() {
        super.onStart()
        PlaybackStateHolder.addListener(this)
        renderDockPlaybackState(PlaybackStateHolder.isPlaying)
    }

    override fun onStop() {
        PlaybackStateHolder.removeListener(this)
        super.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updatePlayerDockVisibility()
        (supportFragmentManager.findFragmentByTag("f1") as? PlayerFragment)
            ?.refreshResponsiveLayout()
    }

    override fun onPlaybackChanged(
        song: Song?,
        playing: Boolean,
        positionMs: Long,
        lines: List<LrcLine>,
    ) {
        renderDockPlaybackState(playing)
    }

    override fun onPlayModeChanged(mode: PlaybackMode) {
        renderDockPlaybackMode(mode)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) ImmersiveHelper.apply(this)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (handleSteeringWheelKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun handleSteeringWheelKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (PlaybackStateHolder.songs.isEmpty()) return false
        val action = when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> MusicPlaybackService.ACTION_NEXT
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> MusicPlaybackService.ACTION_PREV
            KeyEvent.KEYCODE_MEDIA_PLAY -> MusicPlaybackService.ACTION_PLAY
            KeyEvent.KEYCODE_MEDIA_PAUSE -> MusicPlaybackService.ACTION_PAUSE
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> MusicPlaybackService.ACTION_TOGGLE
            KeyEvent.KEYCODE_MEDIA_STOP -> MusicPlaybackService.ACTION_STOP
            else -> return false
        }
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, MusicPlaybackService::class.java).apply { this.action = action }
            )
        }
        return true
    }

    private fun restoreCachedLibraries(resumePlayback: Boolean = true) {
        musicSongs = PlaybackBootstrap.loadCachedMusic(this)
        val library = settings.lastActiveLibrary
        val resumeList = resolveResumeList(library)
        if (PlaybackStateHolder.songs.isEmpty() && resumeList.isNotEmpty()) {
            val savedPath = settings.lastSongPath(library)
            val savedIndex = savedPath
                ?.let { path -> resumeList.indexOfFirst { it.path == path } }
                ?.takeIf { it >= 0 }
                ?: 0
            PlaybackStateHolder.setPlaylist(resumeList, savedIndex, library)
        }
        binding.root.post {
            refreshPlaylistFragment()
            if (!resumePlayback || PlaybackStateHolder.isPlaying) return@post
            if (resumeList.isNotEmpty()) {
                PlaybackBootstrap.resumeIfNeeded(this, resumeList, settings, library)
            }
        }
    }

    private fun resolveResumeList(library: LibraryKind): List<Song> = when (library) {
        LibraryKind.MUSIC -> musicSongs
        else -> PlaylistCache.loadQueue(this, library).ifEmpty { musicSongs }
    }

    override fun playSongAt(index: Int) {
        playSongSubset(musicSongs, index, LibraryKind.MUSIC)
    }

    override fun playSongSubset(subset: List<Song>, index: Int, library: LibraryKind) {
        if (subset.isEmpty() || index !in subset.indices) return
        if (library == LibraryKind.MUSIC) prioritizeSongMetadata(subset[index])
        settings.lastActiveLibrary = library
        PlaybackStateHolder.setActiveLibrary(library)
        if (PlaybackStateHolder.songs === subset) {
            PlaybackStateHolder.setCurrentIndex(index)
        } else {
            PlaybackStateHolder.setPlaylist(subset, index, library)
        }
        lifecycleScope.launch(Dispatchers.IO) {
            PlaylistCache.saveQueue(this@MainActivity, subset, library)
        }
        val intent = Intent(this, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_PLAY_INDEX
            putExtra(MusicPlaybackService.EXTRA_INDEX, index)
            putExtra(MusicPlaybackService.EXTRA_LIBRARY, library.name)
        }
        runCatching {
            ContextCompat.startForegroundService(this, intent)
        }.onFailure {
            Toast.makeText(this, R.string.playback_start_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun notifyLyricStyleChanged() {
        (supportFragmentManager.findFragmentByTag("f1") as? PlayerFragment)?.refreshLyricStyle()
    }

    override fun notifyVinylScaleChanged() {
        (supportFragmentManager.findFragmentByTag("f1") as? PlayerFragment)?.refreshVinylScale()
    }

    override fun notifyPlaylistTextSizeChanged() {
        (supportFragmentManager.findFragmentByTag("f0") as? PlaylistFragment)
            ?.refreshPlaylistTextSize()
    }

    override fun switchToTab(index: Int) {
        binding.viewPager.setCurrentItem(index, false)
        binding.bottomNav.menu.getItem(index).isChecked = true
    }

    override fun scanMusic(onDone: ((Int) -> Unit)?) {
        val needed = requiredScanPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            pendingScanCallback = onDone
            storagePermission.launch(needed.toTypedArray())
            return
        }
        performMusicScan(onDone)
    }

    companion object {
        private const val EXIT_PROCESS_DELAY_MS = 200L
        private const val LANDSCAPE_NAV_WIDTH_RATIO = 0.60f
    }

    override fun clearMusicList() {
        musicScanGeneration++
        val scanToCancel = musicScanJob
        val wasPlayingMusic = PlaybackStateHolder.activeLibrary == LibraryKind.MUSIC
        musicSongs = emptyList()
        if (wasPlayingMusic) {
            stopService(Intent(this, MusicPlaybackService::class.java))
            PlaybackStateHolder.clearPlaylist()
        }
        musicScanJob = lifecycleScope.launch {
            scanToCancel?.cancelAndJoin()
            withContext(Dispatchers.IO) { PlaylistCache.clearMusicLibrary(this@MainActivity) }
        }
        settings.setLastSong(LibraryKind.MUSIC, null, 0L)
        refreshPlaylistFragment()
        Toast.makeText(this, R.string.clear_playlist_done, Toast.LENGTH_SHORT).show()
    }

    private fun performMusicScan(onDone: ((Int) -> Unit)?) {
        if (musicScanJob?.isActive == true) {
            onDone?.invoke(musicSongs.size)
            return
        }
        val generation = ++musicScanGeneration
        val startedAt = SystemClock.elapsedRealtime()
        musicScanJob = lifecycleScope.launch {
            val quickScan = withContext(Dispatchers.IO) {
                PlaybackBootstrap.scanMusicLibraryQuick(this@MainActivity, settings)
            }
            val quickSongs = quickScan.songs
            if (generation != musicScanGeneration) return@launch
            publishMusicSongs(quickSongs)
            android.util.Log.i(
                "MusicScan",
                "quick playlist ready: count=${quickSongs.size}, pending=${quickScan.pendingMetadataCount}, " +
                    "complete=${quickScan.scanComplete}, elapsedMs=${SystemClock.elapsedRealtime() - startedAt}"
            )
            if (!PlaybackStateHolder.isPlaying) {
                val library = settings.lastActiveLibrary
                val resumeList = resolveResumeList(library)
                if (resumeList.isNotEmpty()) {
                    PlaybackBootstrap.resumeIfNeeded(this@MainActivity, resumeList, settings, library)
                }
            }
            onDone?.invoke(quickSongs.size)
            val enrichedSongs = withContext(Dispatchers.IO) {
                PlaybackBootstrap.enrichMusicLibrary(this@MainActivity, quickScan)
            }
            if (generation != musicScanGeneration) return@launch
            publishMusicSongs(enrichedSongs)
            android.util.Log.i(
                "MusicScan",
                "metadata ready: count=${enrichedSongs.size}, elapsedMs=${SystemClock.elapsedRealtime() - startedAt}"
            )
        }
    }

    private fun prioritizeSongMetadata(song: Song) {
        if (song.durationMs > 0L) return
        val generation = musicScanGeneration
        lifecycleScope.launch {
            val enriched = withContext(Dispatchers.IO) {
                PlaybackBootstrap.enrichSingleMusic(this@MainActivity, song)
            }
            if (generation != musicScanGeneration || enriched == song) return@launch
            val index = musicSongs.indexOfFirst { it.path == enriched.path }
            if (index < 0) return@launch
            musicSongs = musicSongs.toMutableList().also { it[index] = enriched }
            PlaybackStateHolder.updateSongMetadata(enriched)
            refreshPlaylistFragment()
        }
    }

    private fun publishMusicSongs(songs: List<Song>) {
        val currentPath = PlaybackStateHolder.currentSong?.path
        musicSongs = songs
        if (PlaybackStateHolder.activeLibrary == LibraryKind.MUSIC && songs.isNotEmpty()) {
            val currentIndex = currentPath?.let { path -> songs.indexOfFirst { it.path == path } }
                ?.takeIf { it >= 0 }
                ?: 0
            PlaybackStateHolder.setPlaylist(songs, currentIndex, LibraryKind.MUSIC)
        }
        refreshPlaylistFragment()
    }

    override fun allSongs(): List<Song> = musicSongs

    override fun refreshAppTheme() {
        applyAppTheme()
    }

    override fun syncPlayerBottomNav(backgroundColor: Int) {
        if (binding.viewPager.currentItem != 1) return
        val palette = AppThemeManager.palette(this, settings)
        AppThemeManager.applyPlayerBottomNav(binding.bottomNav, palette, backgroundColor)
        val dockColor = AppThemeManager.playerBottomNavColors(palette, backgroundColor)
        binding.bottomDock.setBackgroundColor(dockColor)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.navigationBarColor = dockColor
        }
    }

    private fun refreshPlaylistFragment() {
        (supportFragmentManager.findFragmentByTag("f0") as? PlaylistFragment)?.refreshFromHost()
    }

    private fun applyAppTheme() {
        val palette = AppThemeManager.palette(this, settings)
        binding.root.setBackgroundColor(palette.background)
        binding.bottomDock.setBackgroundColor(palette.bottomNavBg)
        AppThemeManager.applyBottomNav(binding.bottomNav, palette)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.navigationBarColor = palette.bottomNavBg
            window.statusBarColor = android.graphics.Color.TRANSPARENT
        }
    }

    private fun hasScanPermission(): Boolean = requiredScanPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requiredScanPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}
