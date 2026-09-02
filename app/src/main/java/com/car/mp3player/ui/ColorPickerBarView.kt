package com.car.mp3player.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

class ColorPickerBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.WHITE
    }
    private val outerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.argb(180, 0, 0, 0)
    }
    private val trackBounds = RectF()
    private var position = 0.5f
    private var selectedColor = Color.WHITE
    private var onColorSelected: ((Int) -> Unit)? = null

    fun setColor(color: Int) {
        selectedColor = color or 0xFF000000.toInt()
        position = nearestPosition(selectedColor)
        invalidate()
    }

    fun setOnColorSelectedListener(listener: (Int) -> Unit) {
        onColorSelected = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val thumbRadius = 11f * density
        val left = paddingLeft + thumbRadius
        val right = width - paddingRight - thumbRadius
        if (right <= left) return
        val centerY = height / 2f
        val trackHalfHeight = 7f * density
        trackBounds.set(left, centerY - trackHalfHeight, right, centerY + trackHalfHeight)
        trackPaint.shader = LinearGradient(
            left,
            centerY,
            right,
            centerY,
            PALETTE,
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(trackBounds, trackHalfHeight, trackHalfHeight, trackPaint)

        val thumbX = left + (right - left) * position
        thumbPaint.color = selectedColor
        thumbPaint.style = Paint.Style.FILL
        canvas.drawCircle(thumbX, centerY, thumbRadius, thumbPaint)
        canvas.drawCircle(thumbX, centerY, thumbRadius, outerStrokePaint)
        canvas.drawCircle(thumbX, centerY, thumbRadius - density, thumbStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateFromTouch(event.x)
                return true
            }
            MotionEvent.ACTION_UP -> {
                updateFromTouch(event.x)
                parent?.requestDisallowInterceptTouchEvent(false)
                onColorSelected?.invoke(selectedColor)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateFromTouch(x: Float) {
        if (trackBounds.width() <= 0f) return
        position = ((x - trackBounds.left) / trackBounds.width()).coerceIn(0f, 1f)
        selectedColor = colorAt(position)
        invalidate()
    }

    private fun nearestPosition(color: Int): Float {
        var bestPosition = 0f
        var bestDistance = Long.MAX_VALUE
        for (step in 0..POSITION_SEARCH_STEPS) {
            val candidatePosition = step.toFloat() / POSITION_SEARCH_STEPS
            val candidate = colorAt(candidatePosition)
            val red = Color.red(color) - Color.red(candidate)
            val green = Color.green(color) - Color.green(candidate)
            val blue = Color.blue(color) - Color.blue(candidate)
            val distance = (red * red + green * green + blue * blue).toLong()
            if (distance < bestDistance) {
                bestDistance = distance
                bestPosition = candidatePosition
            }
        }
        return bestPosition
    }

    private fun colorAt(value: Float): Int {
        val scaled = value.coerceIn(0f, 1f) * (PALETTE.size - 1)
        val startIndex = scaled.toInt().coerceAtMost(PALETTE.lastIndex)
        val endIndex = (startIndex + 1).coerceAtMost(PALETTE.lastIndex)
        val fraction = scaled - startIndex
        val start = PALETTE[startIndex]
        val end = PALETTE[endIndex]
        return Color.rgb(
            lerp(Color.red(start), Color.red(end), fraction),
            lerp(Color.green(start), Color.green(end), fraction),
            lerp(Color.blue(start), Color.blue(end), fraction)
        )
    }

    private fun lerp(start: Int, end: Int, fraction: Float): Int =
        (start + (end - start) * fraction).roundToInt().coerceIn(0, 255)

    companion object {
        private const val POSITION_SEARCH_STEPS = 720
        private val PALETTE = intArrayOf(
            Color.BLACK,
            Color.DKGRAY,
            Color.GRAY,
            Color.LTGRAY,
            Color.WHITE,
            Color.RED,
            Color.YELLOW,
            Color.GREEN,
            Color.CYAN,
            Color.BLUE,
            Color.MAGENTA,
            Color.BLACK
        )
    }
}
