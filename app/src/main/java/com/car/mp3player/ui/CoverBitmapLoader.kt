package com.car.mp3player.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.car.mp3player.util.AlbumColorExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LoadedCover(val bitmap: Bitmap, val backgroundColor: Int)

object CoverBitmapLoader {
    // BitmapFactory decoding is not cancellable. Serialize decodes so rapidly
    // skipping tracks cannot allocate several full cover buffers concurrently.
    private val decodeMutex = Mutex()

    suspend fun load(path: String, targetEdgePx: Int): LoadedCover? {
        var decoded: LoadedCover? = null
        try {
            return withContext(Dispatchers.IO) {
                decodeMutex.withLock {
                    ensureActive()
                    decoded = decode(path, targetEdgePx.coerceIn(128, 1024))
                    ensureActive()
                    decoded
                }
            }
        } catch (cancelled: CancellationException) {
            // This bitmap has not been delivered to an ImageView yet.
            decoded?.bitmap?.recycle()
            throw cancelled
        }
    }

    private fun decode(path: String, targetEdgePx: Int): LoadedCover? {
        var bitmap: Bitmap? = null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateCoverSampleSize(bounds.outWidth, bounds.outHeight, targetEdgePx)
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(path, options) ?: return null
            bitmap = decoded
            LoadedCover(decoded, AlbumColorExtractor.backgroundColor(decoded))
        }.getOrElse {
            bitmap?.recycle()
            Log.w("CoverBitmapLoader", "Unable to decode album cover", it)
            null
        }
    }
}

internal fun coverTargetEdge(requestedEdgePx: Int, lowRamDevice: Boolean): Int =
    requestedEdgePx.coerceIn(128, if (lowRamDevice) 512 else 1024)

/** Power-of-two sampling works consistently with legacy Android image decoders. */
internal fun calculateCoverSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    require(maxEdge >= 2)
    if (width <= 0 || height <= 0) return 1
    val longest = maxOf(width, height).toLong()
    var sample = 1
    while ((longest + sample - 1) / sample > maxEdge) sample *= 2
    return sample
}
