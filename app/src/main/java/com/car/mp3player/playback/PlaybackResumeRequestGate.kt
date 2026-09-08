package com.car.mp3player.playback

/**
 * Suppresses duplicate resume commands while the playback service is still starting.
 *
 * Starting a foreground service is asynchronous. During boot both the receiver and
 * the activity can therefore observe an old `isPlaying == false` value and enqueue
 * the same play/seek command more than once.
 */
class PlaybackResumeRequestGate(
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
) {
    private var lastKey: String? = null
    private var lastRequestAtMs: Long = Long.MIN_VALUE

    @Synchronized
    fun tryAcquire(key: String, nowMs: Long): Boolean {
        val elapsed = nowMs - lastRequestAtMs
        if (key == lastKey && elapsed >= 0L && elapsed < debounceMs) return false
        lastKey = key
        lastRequestAtMs = nowMs
        return true
    }

    @Synchronized
    fun release(key: String) {
        if (lastKey != key) return
        lastKey = null
        lastRequestAtMs = Long.MIN_VALUE
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 10_000L
    }
}
