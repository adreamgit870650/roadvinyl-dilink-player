package com.car.mp3player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.car.mp3player.data.PlaybackBootstrap
import com.car.mp3player.data.PlaylistCache
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.model.LibraryKind
import com.car.mp3player.playback.PlaybackStateHolder
import com.car.mp3player.ui.StartupSoundPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BootResumeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val settings by lazy { SettingsRepository(this) }
    private var resumeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (resumeJob?.isActive == true) return START_NOT_STICKY
        resumeJob = scope.launch {
            delay(BOOT_WAIT_MS)
            val resumed = attemptResume(playGreeting = true)
            if (!resumed && !PlaybackStateHolder.isPlaying) {
                delay(RETRY_WAIT_MS)
                if (!PlaybackStateHolder.isPlaying) {
                    attemptResume(playGreeting = !StartupSoundPlayer.hasPlayedThisSession())
                }
            }

            if (settings.bootOpenApp) {
                launchMainActivity()
            }

            if (settings.bootReturnHome && settings.bootAutoStart) {
                delay(1200)
                returnToHome()
            }

            ServiceCompat.stopForeground(this@BootResumeService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private suspend fun attemptResume(playGreeting: Boolean): Boolean {
        if (!settings.autoResumePlayback) return false
        if (PlaybackStateHolder.isPlaying) return true

        val library = settings.lastActiveLibrary
        val songs = withContext(Dispatchers.IO) {
            when (library) {
                // Boot must never scan storage. It only restores the last list that
                // the user explicitly scanned and cached in the app.
                LibraryKind.MUSIC -> PlaybackBootstrap.loadCachedMusic(this@BootResumeService)
                else -> PlaylistCache.loadQueue(this@BootResumeService, library)
            }
        }
        if (songs.isEmpty()) return false

        PlaybackStateHolder.setActiveLibrary(library)
        PlaybackStateHolder.setPlaylist(songs, library = library)
        if (playGreeting && settings.startupSoundEnabled && !StartupSoundPlayer.hasPlayedThisSession()) {
            StartupSoundPlayer.playBeforeBootPlayback(this@BootResumeService, settings)
        }
        return PlaybackBootstrap.resumeIfNeeded(this@BootResumeService, songs, settings, library)
    }

    private fun launchMainActivity() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(launchIntent)
    }

    private fun returnToHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(homeIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_boot),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.boot_starting))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "boot_resume"
        private const val NOTIFICATION_ID = 1002
        private const val BOOT_WAIT_MS = 5000L
        private const val RETRY_WAIT_MS = 10_000L
    }
}
