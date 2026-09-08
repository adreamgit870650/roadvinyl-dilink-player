package com.car.mp3player.compat

import android.media.MediaCodecList
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import java.util.Locale

/**
 * Some KitKat head-unit/emulator images advertise codecs whose shared library
 * is missing. Media3's default all-or-nothing query then rejects every decoder.
 * Probe entries independently so one broken codec does not hide valid codecs.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object LegacyMediaCodecSelector : MediaCodecSelector {
    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ): List<MediaCodecInfo> {
        if (requiresSecureDecoder || requiresTunnelingDecoder) return emptyList()
        val result = mutableListOf<MediaCodecInfo>()
        val count = runCatching { MediaCodecList.getCodecCount() }.getOrDefault(0)
        for (index in 0 until count) {
            val platformInfo = runCatching { MediaCodecList.getCodecInfoAt(index) }.getOrNull() ?: continue
            if (platformInfo.isEncoder) continue
            val codecMimeType = runCatching {
                platformInfo.supportedTypes.firstOrNull { it.equals(mimeType, ignoreCase = true) }
            }.getOrNull() ?: continue
            val capabilities = runCatching {
                platformInfo.getCapabilitiesForType(codecMimeType)
            }.getOrNull() ?: continue
            val lowerName = platformInfo.name.lowercase(Locale.US)
            val softwareOnly = lowerName.startsWith("omx.google.") ||
                lowerName.startsWith("omx.ffmpeg.") ||
                lowerName.startsWith("c2.android.")
            result += MediaCodecInfo.newInstance(
                platformInfo.name,
                mimeType,
                codecMimeType,
                capabilities,
                !softwareOnly,
                softwareOnly,
                !softwareOnly,
                false,
                false
            )
        }
        return result
    }
}
