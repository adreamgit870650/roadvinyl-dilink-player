package com.car.mp3player.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.view.GestureDetectorCompat
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.model.LrcLine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ScrollLyricView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val settings = SettingsRepository(context)
    private val sungPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pendingPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var lines: List<LrcLine> = emptyList()
    private var targetPositionMs = 0L
    private var displayPositionMs = 0f
    private var smoothScrollY = 0f
    private var targetScrollY = 0f
    private var userScrollOffset = 0f
    private var isUserScrolling = false
    private var animating = false
    private var safeViewportTopPx = 0f
    private var safeViewportBottomPx = Float.POSITIVE_INFINITY
    private var blockMetricsDirty = true
    private var cachedBlockHeight = 0f
    private var cachedMetricsWidth = -1f
    private var cachedMetricsTextSize = -1f
    private var cachedMetricsNextTextSize = -1f
    private var cachedMetricsMaxLines = -1

    private val gestureDetector = GestureDetectorCompat(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                isUserScrolling = true
                userScrollOffset -= distanceY
                clampUserScrollOffset()
                invalidate()
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                performClick()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                performLongClick()
            }
        }
    )

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!animating) return
            val smooth = settings.smoothLyrics
            val lerp = if (smooth) 0.18f else 1f
            displayPositionMs += (targetPositionMs - displayPositionMs) * lerp
            if (!isUserScrolling) {
                userScrollOffset *= 0.9f
                if (abs(userScrollOffset) < 1.5f) userScrollOffset = 0f
            }
            computeTargetScroll()
            smoothScrollY += (targetScrollY - smoothScrollY) * lerp
            invalidate()
            if (smooth && (abs(targetPositionMs - displayPositionMs) > 8f ||
                    abs(targetScrollY - smoothScrollY) > 0.5f ||
                    (!isUserScrolling && abs(userScrollOffset) > 1.5f))
            ) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                displayPositionMs = targetPositionMs.toFloat()
                smoothScrollY = targetScrollY
                if (!isUserScrolling) userScrollOffset = 0f
                animating = false
            }
        }
    }

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
    }

    fun update(lines: List<LrcLine>, positionMs: Long) {
        if (this.lines !== lines) blockMetricsDirty = true
        this.lines = lines
        targetPositionMs = positionMs
        if (!settings.smoothLyrics) {
            displayPositionMs = positionMs.toFloat()
            computeTargetScroll()
            smoothScrollY = targetScrollY
            if (!isUserScrolling) userScrollOffset = 0f
            invalidate()
            return
        }
        computeTargetScroll()
        if (!animating) {
            animating = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    fun refreshStyle() {
        blockMetricsDirty = true
        computeTargetScroll()
        invalidate()
    }

    fun setSafeVerticalViewport(topPx: Float, bottomPx: Float) {
        val safeTop = topPx.coerceAtLeast(0f)
        val safeBottom = bottomPx.coerceAtLeast(safeTop)
        if (safeTop == safeViewportTopPx && safeBottom == safeViewportBottomPx) return
        safeViewportTopPx = safeTop
        safeViewportBottomPx = safeBottom
        computeTargetScroll()
        if (!isUserScrolling) smoothScrollY = targetScrollY
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == oldw && h == oldh) return
        blockMetricsDirty = true
        computeTargetScroll()
        if (!isUserScrolling) smoothScrollY = targetScrollY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isUserScrolling = false
        }
        return true
    }

    override fun onDetachedFromWindow() {
        animating = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    private fun blockHeight(style: LyricRenderer.Style, contentWidth: Float = lyricContentWidth()): Float {
        val width = contentWidth.coerceAtLeast(1f)
        if (!blockMetricsDirty &&
            cachedMetricsWidth == width &&
            cachedMetricsTextSize == style.currentSizePx &&
            cachedMetricsNextTextSize == style.nextSizePx &&
            cachedMetricsMaxLines == style.maxVisualLines
        ) {
            return cachedBlockHeight
        }

        pendingPaint.typeface = style.typeface
        pendingPaint.isFakeBoldText = style.bold
        pendingPaint.textSize = style.currentSizePx
        val textWidth = (width - 24f).coerceAtLeast(1f)
        val rowCount = lines.maxOfOrNull { line ->
            if (line.chars.isNotEmpty()) {
                LyricRenderer.layoutKaraokeRows(
                    line,
                    pendingPaint,
                    textWidth,
                    style.maxVisualLines
                ).size
            } else {
                LyricRenderer.wrapText(
                    line.text,
                    pendingPaint,
                    textWidth,
                    max(style.maxVisualLines, LyricRenderer.minimumLyricRows(line.text))
                ).size
            }
        }?.coerceAtLeast(1) ?: 1
        cachedBlockHeight = lyricBlockStep(
            currentSizePx = style.currentSizePx,
            adjacentSizePx = style.nextSizePx,
            rowCount = rowCount
        )
        cachedMetricsWidth = width
        cachedMetricsTextSize = style.currentSizePx
        cachedMetricsNextTextSize = style.nextSizePx
        cachedMetricsMaxLines = style.maxVisualLines
        blockMetricsDirty = false
        return cachedBlockHeight
    }

    private fun computeTargetScroll() {
        val pos = if (settings.smoothLyrics) targetPositionMs else displayPositionMs.toLong()
        val idx = findIndex(pos)
        val density = spScale()
        val style = LyricRenderer.styleFrom(context, settings, density, forPlayer = true)
        targetScrollY = idx * blockHeight(style)
    }

    private fun effectiveScrollY(): Float = smoothScrollY + userScrollOffset

    private fun clampUserScrollOffset() {
        if (lines.isEmpty()) {
            userScrollOffset = 0f
            return
        }
        val density = spScale()
        val style = LyricRenderer.styleFrom(context, settings, density, forPlayer = true)
        val blockH = blockHeight(style)
        val contentH = lines.size * blockH
        val viewportHeight = (height - paddingTop - paddingBottom).coerceAtLeast(0)
        val maxOffset = (contentH / 2f + viewportHeight / 2f).coerceAtLeast(blockH)
        userScrollOffset = userScrollOffset.coerceIn(-maxOffset, maxOffset)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = spScale()
        val style = LyricRenderer.styleFrom(context, settings, density, forPlayer = true)
        val verticalViewport = resolveLyricViewportBounds(
            heightPx = height.toFloat(),
            paddingTopPx = paddingTop.toFloat(),
            paddingBottomPx = paddingBottom.toFloat(),
            safeTopPx = safeViewportTopPx,
            safeBottomPx = safeViewportBottomPx
        )
        val contentLeft = paddingLeft.toFloat()
        val contentTop = verticalViewport.topPx
        val contentRight = (width - paddingRight).toFloat()
        val contentBottom = verticalViewport.bottomPx
        val contentWidth = (contentRight - contentLeft).coerceAtLeast(0f)
        val contentHeight = (contentBottom - contentTop).coerceAtLeast(0f)
        if (contentWidth <= 0f || contentHeight <= 0f) return

        val checkpoint = canvas.save()
        canvas.clipRect(contentLeft, contentTop, contentRight, contentBottom)
        canvas.translate(contentLeft, 0f)
        val centerY = contentTop + contentHeight / 2f

        if (lines.isEmpty()) {
            LyricRenderer.drawWrappedStaticLine(
                canvas, LyricRenderer.PLACEHOLDER_LINE, centerY, pendingPaint, style, contentWidth,
                style.pendingColor, style.otherSizePx, 2
            )
            canvas.restoreToCount(checkpoint)
            return
        }

        val idx = findIndex(displayPositionMs.toLong())
        val blockH = blockHeight(style, contentWidth)
        val scrollY = effectiveScrollY()

        for (i in lines.indices) {
            val lineCenterY = centerY - scrollY + i * blockH
            val isCurrent = i == idx
            val size = when {
                isCurrent -> style.currentSizePx
                abs(i - idx) == 1 -> style.nextSizePx
                else -> style.otherSizePx
            }
            val visualHeight = lyricVisualHeight(
                line = lines[i],
                style = style,
                sizePx = size,
                contentWidth = contentWidth,
                current = isCurrent
            )
            if (!lyricBlockFitsViewport(lineCenterY, visualHeight, contentTop, contentBottom)) continue

            if (isCurrent) {
                LyricRenderer.drawKaraokeLine(
                    canvas, lines[i], displayPositionMs, lineCenterY,
                    style, contentWidth, sungPaint, pendingPaint
                )
            } else {
                val color = if (i < idx) style.nextLineColor else style.pendingColor
                LyricRenderer.drawWrappedStaticLine(
                    canvas, lines[i].text, lineCenterY, nextPaint, style,
                    contentWidth, color, size, style.maxVisualLines
                )
            }
        }
        canvas.restoreToCount(checkpoint)
    }

    private fun lyricVisualHeight(
        line: LrcLine,
        style: LyricRenderer.Style,
        sizePx: Float,
        contentWidth: Float,
        current: Boolean
    ): Float {
        val paint = if (current) pendingPaint else nextPaint
        paint.typeface = style.typeface
        paint.isFakeBoldText = style.bold
        paint.textSize = sizePx
        val availableWidth = (contentWidth - 24f).coerceAtLeast(1f)
        val rowCount = if (current && line.chars.isNotEmpty()) {
            LyricRenderer.layoutKaraokeRows(
                line,
                paint,
                availableWidth,
                style.maxVisualLines
            ).size
        } else {
            LyricRenderer.wrapText(
                line.text,
                paint,
                availableWidth,
                max(style.maxVisualLines, LyricRenderer.minimumLyricRows(line.text))
            ).size
        }.coerceAtLeast(1)
        return rowCount * sizePx * if (current) 1.35f else 1.3f
    }

    private fun findIndex(positionMs: Long): Int {
        var index = 0
        for (i in lines.indices) {
            if (positionMs >= lines[i].startTimeMs) index = i else break
        }
        return index
    }

    private fun spScale(): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        1f,
        resources.displayMetrics
    )

    private fun lyricContentWidth(): Float =
        (width - paddingLeft - paddingRight).toFloat().coerceAtLeast(1f)
}

internal fun lyricBlockStep(
    currentSizePx: Float,
    adjacentSizePx: Float,
    rowCount: Int
): Float {
    val rows = rowCount.coerceAtLeast(1)
    val currentHeight = currentSizePx.coerceAtLeast(0f) * 1.35f * rows
    val adjacentHeight = adjacentSizePx.coerceAtLeast(0f) * 1.3f * rows
    val gap = currentSizePx.coerceAtLeast(0f) * 0.05f
    return currentHeight / 2f + adjacentHeight / 2f + gap
}

internal fun lyricBlockFitsViewport(
    centerY: Float,
    blockHeight: Float,
    viewportTop: Float,
    viewportBottom: Float
): Boolean {
    val safeHeight = (viewportBottom - viewportTop).coerceAtLeast(0f)
    if (safeHeight <= 0f || blockHeight <= 0f) return false
    if (blockHeight >= safeHeight) return centerY in viewportTop..viewportBottom
    val halfBlock = blockHeight / 2f
    return centerY - halfBlock >= viewportTop && centerY + halfBlock <= viewportBottom
}

internal data class LyricViewportBounds(
    val topPx: Float,
    val bottomPx: Float
)

internal fun resolveLyricViewportBounds(
    heightPx: Float,
    paddingTopPx: Float,
    paddingBottomPx: Float,
    safeTopPx: Float,
    safeBottomPx: Float
): LyricViewportBounds {
    val height = heightPx.coerceAtLeast(0f)
    val top = max(paddingTopPx.coerceAtLeast(0f), safeTopPx)
        .coerceIn(0f, height)
    val bottom = min(
        height - paddingBottomPx.coerceAtLeast(0f),
        safeBottomPx
    ).coerceIn(top, height)
    return LyricViewportBounds(top, bottom)
}
