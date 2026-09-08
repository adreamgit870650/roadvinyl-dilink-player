package com.car.mp3player.model

data class PlaylistTextSizes(
    val primarySp: Float,
    val secondarySp: Float,
    val badgeSp: Float
)

object PlaylistTextScale {
    const val MIN = 12f
    const val MAX = 24f
    const val DEFAULT = 18f

    fun clamp(value: Float): Float = value.coerceIn(MIN, MAX)

    fun sizes(value: Float): PlaylistTextSizes {
        val primary = clamp(value)
        return PlaylistTextSizes(
            primarySp = primary,
            secondarySp = (primary - 4f).coerceAtLeast(10f),
            badgeSp = (primary - 5f).coerceAtLeast(10f)
        )
    }
}
