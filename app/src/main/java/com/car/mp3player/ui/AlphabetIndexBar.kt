package com.car.mp3player.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.car.mp3player.R
import kotlin.math.min

class AlphabetIndexBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val letters = ('A'..'Z').toList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private var primaryColor = ContextCompat.getColor(context, R.color.netease_red)
    private var normalColor = ContextCompat.getColor(context, R.color.text_secondary)
    private var availableSections: Set<Char> = emptySet()
    private var selectedIndex = -1

    var onLetterSelected: ((Char) -> Unit)? = null
    var onSelectionFinished: (() -> Unit)? = null

    fun setColors(primary: Int, normal: Int) {
        primaryColor = primary
        normalColor = normal
        invalidate()
    }

    fun setAvailableSections(sections: Set<Char>) {
        availableSections = sections.map { it.uppercaseChar() }.filter { it in 'A'..'Z' }.toSet()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val contentHeight = height - paddingTop - paddingBottom
        if (contentHeight <= 0) return
        val cellHeight = contentHeight.toFloat() / letters.size
        val maxTextPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            12f,
            resources.displayMetrics
        )
        paint.textSize = min(maxTextPx, cellHeight * 0.72f)
        val metrics = paint.fontMetrics
        val textOffset = -(metrics.ascent + metrics.descent) / 2f
        val centerX = width / 2f

        letters.forEachIndexed { index, letter ->
            val centerY = paddingTop + cellHeight * (index + 0.5f)
            if (index == selectedIndex) {
                paint.color = withAlpha(primaryColor, 55)
                canvas.drawCircle(centerX, centerY, min(width * 0.42f, cellHeight * 0.48f), paint)
            }
            paint.color = when {
                index == selectedIndex -> primaryColor
                letter in availableSections -> normalColor
                else -> withAlpha(normalColor, 70)
            }
            canvas.drawText(letter.toString(), centerX, centerY + textOffset, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || visibility != VISIBLE || availableSections.isEmpty()) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateSelection(event.y)
                true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                finishSelection()
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                finishSelection()
                true
            }
            else -> true
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateSelection(y: Float) {
        val contentHeight = (height - paddingTop - paddingBottom).coerceAtLeast(1)
        val relativeY = (y - paddingTop).coerceIn(0f, contentHeight.toFloat() - 1f)
        val touchedIndex = (relativeY / contentHeight * letters.size).toInt().coerceIn(letters.indices)
        val resolvedIndex = resolveAvailableIndex(touchedIndex)
        if (resolvedIndex == selectedIndex) return
        selectedIndex = resolvedIndex
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        invalidate()
        onLetterSelected?.invoke(letters[resolvedIndex])
    }

    private fun resolveAvailableIndex(touchedIndex: Int): Int {
        if (letters[touchedIndex] in availableSections) return touchedIndex
        return letters.indices.firstOrNull { it >= touchedIndex && letters[it] in availableSections }
            ?: letters.indices.lastOrNull { letters[it] in availableSections }
            ?: touchedIndex
    }

    private fun finishSelection() {
        parent?.requestDisallowInterceptTouchEvent(false)
        selectedIndex = -1
        invalidate()
        onSelectionFinished?.invoke()
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
