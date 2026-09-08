package com.car.mp3player.model

object VinylScale {
    const val MIN = 0.60f
    const val MAX = 2.00f
    const val DEFAULT = 1.00f
    const val STEP = 0.05f

    fun clamp(value: Float): Float = value.coerceIn(MIN, MAX)
}
