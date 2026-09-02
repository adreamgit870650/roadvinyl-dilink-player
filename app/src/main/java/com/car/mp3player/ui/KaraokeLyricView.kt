package com.car.mp3player.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.model.LrcLine
import com.car.mp3player.model.LyricState
import kotlin.math.abs

class KaraokeLyricView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val settings = SettingsRepository(context)
    private val sungPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val nextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val pendingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val dragHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var lyricState: LyricState? = null
    private var displayPositionMs = 0f
    private var targetPositionMs = 0L
    private var animating = false
    private var dragHandleVisible = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!animating) return
            val lerp = if (settings.smoothLyrics) 0.22f else 1f
            displayPositionMs += (targetPositionMs - displayPositionMs) * lerp
            if (!settings.smoothLyrics) {
                displayPositionMs = targetPositionMs.toFloat()
            }
            invalidate()
            if (needsContinuousFrames()) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                displayPositionMs = targetPositionMs.toFloat()
                animating = false
            }
        }
    }

    init {
        setWillNotDraw(false)
    }

    fun update(state: LyricState?) {
        val firstState = lyricState == null
        lyricState = state
        targetPositionMs = state?.positionMs ?: 0L
        val largeSeek = abs(targetPositionMs - displayPositionMs) > 2_000f
        if (!settings.smoothLyrics || firstState || largeSeek) {
            displayPositionMs = targetPositionMs.toFloat()
            invalidate()
            if (settings.smoothLyrics) startAnimatingIfNeeded()
            return
        }
        startAnimatingIfNeeded()
    }

    fun applySettings() {
        invalidate()
    }

    fun setDragHandleVisible(visible: Boolean) {
        if (dragHandleVisible == visible) return
        dragHandleVisible = visible
        invalidate()
    }

    private fun startAnimatingIfNeeded() {
        if (animating) return
        animating = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun needsContinuousFrames(): Boolean {
        if (abs(targetPositionMs - displayPositionMs) > 2f) return true
        val line = lyricState?.currentLine ?: return false
        return line.chars.size > 1 && displayPositionMs in line.startTimeMs.toFloat()..line.endTimeMs.toFloat()
    }

    override fun onDetachedFromWindow() {
        animating = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val state = lyricState ?: return
        val density = resources.displayMetrics.scaledDensity
        val style = LyricRenderer.styleFrom(context, settings, density, forPlayer = false)
        LyricRenderer.drawOverlayBlock(
            canvas,
            state.currentLine,
            state.nextLine,
            displayPositionMs,
            width,
            style,
            sungPaint,
            nextPaint,
            pendingPaint
        )
        if (dragHandleVisible) drawDragHandle(canvas)
    }

    private fun drawDragHandle(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val handleWidth = 36f * density
        val handleHeight = 4f * density
        val centerX = width / 2f
        val bottom = height - 4f * density
        val color = settings.overlayHighlightLyricColor
        dragHandlePaint.color = Color.argb(
            210,
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
        canvas.drawRoundRect(
            centerX - handleWidth / 2f,
            bottom - handleHeight,
            centerX + handleWidth / 2f,
            bottom,
            handleHeight / 2f,
            handleHeight / 2f,
            dragHandlePaint
        )
    }
}
