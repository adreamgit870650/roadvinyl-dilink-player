package com.car.mp3player.ui

object OverlayPositioning {
    data class PixelPosition(val x: Int, val y: Int)
    data class CenterFractions(val x: Float, val y: Float)

    fun clamp(
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        overlayWidth: Int,
        overlayHeight: Int
    ): PixelPosition {
        val maxX = (screenWidth - overlayWidth).coerceAtLeast(0)
        val maxY = (screenHeight - overlayHeight).coerceAtLeast(0)
        return PixelPosition(x.coerceIn(0, maxX), y.coerceIn(0, maxY))
    }

    fun fromCenterFractions(
        centerXFraction: Float,
        centerYFraction: Float,
        screenWidth: Int,
        screenHeight: Int,
        overlayWidth: Int,
        overlayHeight: Int
    ): PixelPosition = clamp(
        x = (centerXFraction.coerceIn(0f, 1f) * screenWidth - overlayWidth / 2f).toInt(),
        y = (centerYFraction.coerceIn(0f, 1f) * screenHeight - overlayHeight / 2f).toInt(),
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        overlayWidth = overlayWidth,
        overlayHeight = overlayHeight
    )

    fun toCenterFractions(
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        overlayWidth: Int,
        overlayHeight: Int
    ): CenterFractions {
        val safeWidth = screenWidth.coerceAtLeast(1)
        val safeHeight = screenHeight.coerceAtLeast(1)
        return CenterFractions(
            x = ((x + overlayWidth / 2f) / safeWidth).coerceIn(0f, 1f),
            y = ((y + overlayHeight / 2f) / safeHeight).coerceIn(0f, 1f)
        )
    }
}
