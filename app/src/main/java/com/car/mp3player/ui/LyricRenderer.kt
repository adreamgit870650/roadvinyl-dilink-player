package com.car.mp3player.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.model.LrcChar
import com.car.mp3player.model.LrcLine
import com.car.mp3player.model.LyricFontFamily
import com.car.mp3player.model.LyricHighlightMode
import kotlin.math.max
import kotlin.math.min

object LyricRenderer {

    const val PLACEHOLDER_LINE = "-----------"

    data class Style(
        val highlightColor: Int,
        val pendingColor: Int,
        val nextLineColor: Int,
        val overlayPlayedTopColor: Int,
        val overlayPlayedBottomColor: Int,
        val overlayPlayedStrokeColor: Int,
        val overlayPendingTopColor: Int,
        val overlayPendingBottomColor: Int,
        val overlayPendingStrokeColor: Int,
        val overlayNextTopColor: Int,
        val overlayNextBottomColor: Int,
        val overlayNextStrokeColor: Int,
        val currentSizePx: Float,
        val nextSizePx: Float,
        val otherSizePx: Float,
        val typeface: Typeface?,
        val maxVisualLines: Int,
        val currentScale: Float,
        val nextScale: Float,
        val bold: Boolean,
        val outline: Boolean,
        val strokeWidthPx: Float,
        val highlightMode: LyricHighlightMode
    )

    fun styleFrom(
        context: Context,
        settings: SettingsRepository,
        density: Float,
        forPlayer: Boolean
    ): Style {
        val overlaySize = settings.fontSizeSp
        val baseCurrent = if (forPlayer) settings.playerFontSizeSp else overlaySize
        val baseNext = if (forPlayer) settings.playerNextFontSizeSp else overlaySize * 0.88f
        val baseOther = if (forPlayer) settings.playerFontSizeSp * 0.82f else overlaySize * 0.85f
        val family = settings.lyricFontFamily()
        val bold = !forPlayer && settings.overlayLyricBold
        val themeHighlight = settings.highlightColor
        val overlayBaseColor = settings.overlayLyricColor
        val overlayHighlight = settings.overlayHighlightLyricColor
        val highlight = if (forPlayer) themeHighlight else overlayHighlight
        val pending = if (forPlayer) settings.pendingColor else overlayBaseColor
        val next = if (forPlayer) settings.nextLineColor else overlayBaseColor
        val overlayPlayedTop = adjustForGradient(highlight, lift = 0.35f)
        val overlayPlayedBottom = adjustForGradient(highlight, lift = -0.05f)
        val overlayPlayedStroke = adjustForStroke(highlight, played = true)
        val overlayPendingTop = adjustForGradient(pending, lift = 0.28f)
        val overlayPendingBottom = adjustForGradient(pending, lift = -0.08f)
        val overlayPendingStroke = overlayNeutralStrokeColor(overlayBaseColor)
        val overlayNextTop = adjustForGradient(next, lift = 0.24f)
        val overlayNextBottom = adjustForGradient(next, lift = -0.06f)
        val overlayNextStroke = overlayNeutralStrokeColor(overlayBaseColor, lighter = true)
        val strokeWidthPx = if (forPlayer) 0f else overlayStrokeWidthPx(settings.overlayStrokeWidth)
        return Style(
            highlightColor = highlight,
            pendingColor = pending,
            nextLineColor = next,
            overlayPlayedTopColor = overlayPlayedTop,
            overlayPlayedBottomColor = overlayPlayedBottom,
            overlayPlayedStrokeColor = overlayPlayedStroke,
            overlayPendingTopColor = overlayPendingTop,
            overlayPendingBottomColor = overlayPendingBottom,
            overlayPendingStrokeColor = overlayPendingStroke,
            overlayNextTopColor = overlayNextTop,
            overlayNextBottomColor = overlayNextBottom,
            overlayNextStrokeColor = overlayNextStroke,
            currentSizePx = baseCurrent * density * if (forPlayer) settings.currentLineScale else 1f,
            nextSizePx = baseNext * density * if (forPlayer) settings.nextLineScale else 1f,
            otherSizePx = baseOther * density,
            typeface = LyricFontProvider.getTypeface(context, family, bold),
            maxVisualLines = settings.maxLyricVisualLines,
            currentScale = settings.currentLineScale,
            nextScale = settings.nextLineScale,
            bold = bold,
            outline = !forPlayer && settings.overlayStrokeEnabled,
            strokeWidthPx = strokeWidthPx,
            highlightMode = settings.lyricHighlightMode
        )
    }

    /** Explicit translation rows belong to the same timed lyric and must never be clipped apart. */
    fun minimumLyricRows(text: String): Int =
        text.split('\n').count { it.isNotEmpty() }.coerceAtLeast(1)

    fun wrapText(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        if (text.isEmpty() || maxWidth <= 0f || maxLines <= 0) return emptyList()
        val lines = mutableListOf<String>()
        val explicitLines = text.split('\n')

        fun hasFollowingText(lineIndex: Int): Boolean =
            explicitLines.drop(lineIndex + 1).any { it.isNotEmpty() }

        fun ellipsize(value: String): String {
            var result = value.trimEnd()
            while (result.isNotEmpty() && paint.measureText("$result…") > maxWidth) {
                result = result.dropLast(1).trimEnd()
            }
            return "$result…"
        }

        for ((lineIndex, explicitLine) in explicitLines.withIndex()) {
            if (explicitLine.isEmpty()) continue
            var start = 0
            while (start < explicitLine.length && lines.size < maxLines) {
                val count = paint.breakText(
                    explicitLine,
                    start,
                    explicitLine.length,
                    true,
                    maxWidth,
                    null
                )
                if (count <= 0) break
                val end = start + count
                val moreText = end < explicitLine.length || hasFollowingText(lineIndex)
                val slice = explicitLine.substring(start, end).trimEnd()
                if (lines.size == maxLines - 1 && moreText) {
                    lines.add(ellipsize(slice))
                    return lines
                }
                lines.add(slice)
                start = end
                while (start < explicitLine.length && explicitLine[start].isWhitespace()) start++
            }
            if (lines.size >= maxLines) break
        }
        return lines.ifEmpty { listOf(text.replace("\n", "")) }
    }

    data class CharRow(
        val chars: List<LrcChar>,
        val sourceIndices: List<Int>,
        val startX: Float,
        val baselineY: Float
    )

    fun layoutKaraokeRows(
        line: LrcLine,
        paint: Paint,
        maxWidth: Float,
        maxRows: Int
    ): List<CharRow> {
        if (line.chars.isEmpty()) return emptyList()
        val rowLimit = max(maxRows.coerceAtLeast(1), minimumLyricRows(line.text))
        val rows = mutableListOf<CharRow>()
        var rowChars = mutableListOf<LrcChar>()
        var rowSourceIndices = mutableListOf<Int>()
        var rowWidth = 0f
        var currentY = 0f
        val lineHeight = paint.textSize * 1.35f

        fun flushRow() {
            if (rowChars.isEmpty()) return
            val totalW = rowChars.sumOf { paint.measureText(it.char).toDouble() }.toFloat()
            val startX = max(0f, (maxWidth - totalW) / 2f)
            rows.add(CharRow(rowChars.toList(), rowSourceIndices.toList(), startX, currentY))
            rowChars = mutableListOf()
            rowSourceIndices = mutableListOf()
            rowWidth = 0f
            currentY += lineHeight
        }

        for ((sourceIndex, ch) in line.chars.withIndex()) {
            if (ch.char == "\n") {
                flushRow()
                if (rows.size >= rowLimit) break
                continue
            }
            val w = paint.measureText(ch.char)
            if (rowWidth + w > maxWidth && rowChars.isNotEmpty()) {
                flushRow()
                if (rows.size >= rowLimit) break
            }
            rowChars.add(ch)
            rowSourceIndices.add(sourceIndex)
            rowWidth += w
        }
        if (rows.size < rowLimit && rowChars.isNotEmpty()) flushRow()
        return rows
    }

    fun drawKaraokeLine(
        canvas: Canvas,
        line: LrcLine,
        positionMs: Float,
        centerY: Float,
        style: Style,
        maxWidth: Float,
        sungPaint: Paint,
        pendingPaint: Paint,
        maxRows: Int = style.maxVisualLines
    ): Float {
        sungPaint.typeface = style.typeface
        pendingPaint.typeface = style.typeface
        sungPaint.isFakeBoldText = style.bold
        pendingPaint.isFakeBoldText = style.bold
        sungPaint.textSize = style.currentSizePx
        pendingPaint.textSize = style.currentSizePx

        val rows = layoutKaraokeRows(
            line,
            pendingPaint,
            maxWidth - padding(style),
            maxRows.coerceAtLeast(1)
        )
        if (rows.isEmpty()) return 0f

        val totalHeight = rows.size * style.currentSizePx * 1.35f
        var topY = centerY - totalHeight / 2f + style.currentSizePx
        val padX = padding(style) / 2f
        for (row in rows) {
            drawKaraokeRow(
                canvas = canvas,
                line = line,
                row = row,
                baselineY = topY,
                positionMs = positionMs,
                style = style,
                padX = padX,
                sungPaint = sungPaint,
                pendingPaint = pendingPaint
            )
            topY += style.currentSizePx * 1.35f
        }
        return totalHeight
    }

    private fun drawKaraokeRow(
        canvas: Canvas,
        line: LrcLine,
        row: CharRow,
        baselineY: Float,
        positionMs: Float,
        style: Style,
        padX: Float,
        sungPaint: Paint,
        pendingPaint: Paint
    ) {
        if (row.chars.isEmpty()) return
        val text = row.chars.joinToString("") { it.char }
        val x = row.startX + padX
        val textSize = style.currentSizePx

        drawKaraokeRowLayer(canvas, text, x, baselineY, pendingPaint, style, played = false)

        if (style.highlightMode == LyricHighlightMode.LINE) {
            if (positionMs >= line.startTimeMs) {
                drawKaraokeRowLayer(canvas, text, x, baselineY, sungPaint, style, played = true)
            }
            return
        }

        val fillEndX = sungFillEndX(line, positionMs, pendingPaint, row, padX)

        if (fillEndX > x + 0.5f) {
            canvas.save()
            canvas.clipRect(
                x - 1f,
                baselineY - textSize * 1.15f,
                fillEndX,
                baselineY + textSize * 0.15f
            )
            drawKaraokeRowLayer(canvas, text, x, baselineY, sungPaint, style, played = true)
            canvas.restore()
        }
    }

    private fun drawKaraokeRowLayer(
        canvas: Canvas,
        text: String,
        x: Float,
        baselineY: Float,
        paint: Paint,
        style: Style,
        played: Boolean
    ) {
        if (style.outline) {
            if (played) {
                drawGradientText(
                    canvas, text, x, baselineY, paint, style,
                    style.overlayPlayedTopColor,
                    style.overlayPlayedBottomColor,
                    style.overlayPlayedStrokeColor
                )
            } else {
                drawGradientText(
                    canvas, text, x, baselineY, paint, style,
                    style.overlayPendingTopColor,
                    style.overlayPendingBottomColor,
                    style.overlayPendingStrokeColor
                )
            }
        } else {
            paint.color = if (played) style.highlightColor else style.pendingColor
            canvas.drawText(text, x, baselineY, paint)
        }
    }

    private fun sungFillEndX(
        line: LrcLine,
        positionMs: Float,
        paint: Paint,
        row: CharRow,
        padX: Float
    ): Float {
        var x = row.startX + padX
        if (line.chars.any { it.char == "\n" }) {
            // Rows produced by the exact bilingual separator " / " are translations of the
            // same timed lyric. Wipe every language row by the same geometric percentage so a
            // shorter translation cannot start later than the original language.
            val rowWidth = row.chars.sumOf { paint.measureText(it.char).toDouble() }.toFloat()
            return x + rowWidth * lineProgressFraction(line, positionMs)
        }
        for (i in row.chars.indices) {
            val globalIdx = row.sourceIndices[i]
            val charText = row.chars[i].char
            val w = paint.measureText(charText)
            val start = charStartMs(line, globalIdx).toFloat()
            val end = charEndMs(line, globalIdx).toFloat().coerceAtLeast(start + 1f)
            when {
                positionMs >= end -> x += w
                positionMs <= start -> return x
                else -> {
                    val frac = ((positionMs - start) / (end - start)).coerceIn(0f, 1f)
                    return x + w * frac
                }
            }
        }
        return x
    }

    fun lineProgressFraction(line: LrcLine, positionMs: Float): Float {
        val duration = (line.endTimeMs - line.startTimeMs).coerceAtLeast(1L).toFloat()
        return ((positionMs - line.startTimeMs) / duration).coerceIn(0f, 1f)
    }

    private fun charStartMs(line: LrcLine, index: Int): Long {
        val chars = line.chars
        if (index in chars.indices && (index == 0 || chars[index].startTimeMs > chars[index - 1].startTimeMs)) {
            return chars[index].startTimeMs
        }
        val count = chars.size.coerceAtLeast(1)
        val duration = (line.endTimeMs - line.startTimeMs).coerceAtLeast(count.toLong())
        val slot = duration / count
        return line.startTimeMs + slot * index
    }

    private fun charEndMs(line: LrcLine, index: Int): Long {
        val chars = line.chars
        if (index + 1 < chars.size && chars[index + 1].startTimeMs > chars[index].startTimeMs) {
            return chars[index + 1].startTimeMs
        }
        val count = chars.size.coerceAtLeast(1)
        val duration = (line.endTimeMs - line.startTimeMs).coerceAtLeast(count.toLong())
        val slot = duration / count
        return charStartMs(line, index) + slot
    }

    fun drawWrappedStaticLine(
        canvas: Canvas,
        text: String,
        centerY: Float,
        paint: Paint,
        style: Style,
        maxWidth: Float,
        color: Int,
        sizePx: Float,
        maxLines: Int
    ) {
        paint.typeface = style.typeface
        paint.color = color
        paint.textSize = sizePx
        val lines = wrapText(
            text,
            paint,
            maxWidth - padding(style),
            max(maxLines, minimumLyricRows(text))
        )
        val lineHeight = sizePx * 1.3f
        val totalH = lines.size * lineHeight
        var y = centerY - totalH / 2f + sizePx
        val pad = padding(style) / 2f
        for (line in lines) {
            val w = paint.measureText(line)
            val lineX = max(pad, (maxWidth - w) / 2f)
            if (style.outline) {
                val isPlayedLike = color == style.highlightColor
                val isNextLike = color == style.nextLineColor
                val top = when {
                    isPlayedLike -> style.overlayPlayedTopColor
                    isNextLike -> style.overlayNextTopColor
                    else -> style.overlayPendingTopColor
                }
                val bottom = when {
                    isPlayedLike -> style.overlayPlayedBottomColor
                    isNextLike -> style.overlayNextBottomColor
                    else -> style.overlayPendingBottomColor
                }
                val stroke = when {
                    isPlayedLike -> style.overlayPlayedStrokeColor
                    isNextLike -> style.overlayNextStrokeColor
                    else -> style.overlayPendingStrokeColor
                }
                drawGradientText(canvas, line, lineX, y, paint, style, top, bottom, stroke)
            } else {
                drawText(canvas, line, lineX, y, paint, style)
            }
            y += lineHeight
        }
    }

    fun drawOverlayBlock(
        canvas: Canvas,
        current: LrcLine?,
        next: LrcLine?,
        positionMs: Float,
        width: Int,
        style: Style,
        sungPaint: Paint,
        nextPaint: Paint,
        pendingPaint: Paint
    ) {
        sungPaint.typeface = style.typeface
        nextPaint.typeface = style.typeface
        pendingPaint.typeface = style.typeface
        sungPaint.isFakeBoldText = style.bold
        nextPaint.isFakeBoldText = style.bold
        pendingPaint.isFakeBoldText = style.bold
        val maxW = width.toFloat()
        val maxVisualLines = style.maxVisualLines.coerceIn(1, 4)

        pendingPaint.textSize = style.currentSizePx
        nextPaint.textSize = style.nextSizePx
        val requestedCurrentRows = current?.let { line ->
            layoutKaraokeRows(
                line,
                pendingPaint,
                maxW - padding(style),
                MAX_ROWS_PER_LYRIC_BLOCK
            ).size.coerceAtLeast(1)
        } ?: 1
        val requestedNextRows = next?.let { line ->
            wrapText(
                line.text,
                nextPaint,
                maxW - padding(style),
                MAX_ROWS_PER_LYRIC_BLOCK
            ).size
        } ?: 0
        val allocation = allocateOverlayRows(
            maxVisualLines,
            requestedCurrentRows,
            requestedNextRows
        )

        val currentHeight = allocation.currentRows * style.currentSizePx * 1.35f
        val nextHeight = allocation.nextRows * style.nextSizePx * 1.3f
        val gap = if (allocation.nextRows > 0) style.currentSizePx * 0.35f else 0f
        // Desktop lyrics follow LX Music's TOP-aligned TextView behavior. The overlay window can
        // then move to y=0 and the visible glyphs also reach the status-bar coordinate area.
        val contentTop = 0f
        val currentCenterY = contentTop + currentHeight / 2f

        current?.let { line ->
            drawKaraokeLine(
                canvas,
                line,
                positionMs,
                currentCenterY,
                style,
                maxW,
                sungPaint,
                pendingPaint,
                allocation.currentRows
            )
        } ?: drawWrappedStaticLine(
            canvas, PLACEHOLDER_LINE, currentCenterY, pendingPaint, style, maxW,
            style.pendingColor, style.currentSizePx, 1
        )

        if (allocation.nextRows > 0) next?.let { line ->
            val nextCenterY = contentTop + currentHeight + gap + nextHeight / 2f
            drawWrappedStaticLine(
                canvas, line.text, nextCenterY, nextPaint, style, maxW,
                style.nextLineColor, style.nextSizePx, allocation.nextRows
            )
        }
    }

    data class OverlayRowAllocation(val currentRows: Int, val nextRows: Int)

    fun allocateOverlayRows(
        maxVisualLines: Int,
        requestedCurrentRows: Int,
        requestedNextRows: Int
    ): OverlayRowAllocation {
        // The setting counts timed lyric blocks, not physical text rows. A bilingual block such
        // as Korean + Chinese remains intact in one-line mode; two-line mode may also show the
        // next intact bilingual block.
        val showNextBlock = maxVisualLines.coerceIn(1, 4) > 1
        val currentRows = requestedCurrentRows.coerceAtLeast(1).coerceAtMost(MAX_ROWS_PER_LYRIC_BLOCK)
        val nextRows = if (showNextBlock) {
            requestedNextRows.coerceAtLeast(0).coerceAtMost(MAX_ROWS_PER_LYRIC_BLOCK)
        } else 0
        return OverlayRowAllocation(currentRows, nextRows)
    }

    fun measureOverlayContentHeight(
        current: LrcLine?,
        next: LrcLine?,
        width: Int,
        style: Style,
        paint: Paint
    ): Float {
        paint.typeface = style.typeface
        paint.isFakeBoldText = style.bold
        paint.textSize = style.currentSizePx
        val currentRows = current?.let {
            layoutKaraokeRows(
                it,
                paint,
                width.toFloat() - padding(style),
                MAX_ROWS_PER_LYRIC_BLOCK
            ).size.coerceAtLeast(1)
        } ?: 1
        paint.textSize = style.nextSizePx
        val nextRows = next?.let {
            wrapText(
                it.text,
                paint,
                width.toFloat() - padding(style),
                MAX_ROWS_PER_LYRIC_BLOCK
            ).size
        } ?: 0
        val allocation = allocateOverlayRows(style.maxVisualLines, currentRows, nextRows)
        val currentHeight = allocation.currentRows * style.currentSizePx * 1.35f
        val nextHeight = allocation.nextRows * style.nextSizePx * 1.3f
        val gap = if (allocation.nextRows > 0) style.currentSizePx * 0.35f else 0f
        return currentHeight + nextHeight + gap
    }

    private fun padding(style: Style) = 24f

    private const val MAX_ROWS_PER_LYRIC_BLOCK = 4

    private fun drawText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint, style: Style) {
        if (!style.outline) {
            canvas.drawText(text, x, y, paint)
            return
        }
        val fillColor = paint.color
        val strokeWidth = strokeWidthFor(paint.textSize, style.strokeWidthPx)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.argb(220, 0, 0, 0)
        canvas.drawText(text, x, y, paint)
        paint.style = Paint.Style.FILL
        paint.color = fillColor
        canvas.drawText(text, x, y, paint)
    }

    private fun drawGradientText(
        canvas: Canvas,
        text: String,
        x: Float,
        baselineY: Float,
        paint: Paint,
        style: Style,
        topColor: Int,
        bottomColor: Int,
        strokeColor: Int
    ) {
        val fillColor = paint.color
        val prevShader = paint.shader
        val textTop = baselineY - paint.textSize
        val textBottom = baselineY + paint.textSize * 0.1f
        paint.shader = LinearGradient(
            x, textTop, x, textBottom,
            topColor, bottomColor, Shader.TileMode.CLAMP
        )
        if (style.outline) {
            val strokeWidth = strokeWidthFor(paint.textSize, style.strokeWidthPx)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = strokeColor
            canvas.drawText(text, x, baselineY, paint)
            paint.style = Paint.Style.FILL
        }
        canvas.drawText(text, x, baselineY, paint)
        paint.shader = prevShader
        paint.color = fillColor
    }

    private fun adjustForGradient(color: Int, lift: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] + lift).coerceIn(0.20f, 1f)
        hsv[1] = if (hsv[1] < 0.01f) 0f else (hsv[1] * 0.88f).coerceIn(0.08f, 1f)
        return Color.HSVToColor(230, hsv)
    }

    private fun adjustForStroke(color: Int, played: Boolean): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * if (played) 0.45f else 0.30f).coerceAtLeast(0.18f)
        hsv[1] = if (hsv[1] < 0.01f) 0f else (hsv[1] * 0.9f).coerceIn(0.1f, 1f)
        return Color.HSVToColor(245, hsv)
    }

    /** 待唱/下一行用中性深灰描边，避免粉色描边糊字 */
    private fun overlayNeutralStrokeColor(themeHighlight: Int, lighter: Boolean = false): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(themeHighlight, hsv)
        hsv[1] = (hsv[1] * 0.06f).coerceIn(0f, 0.10f)
        hsv[2] = if (lighter) 0.32f else 0.24f
        return Color.HSVToColor(245, hsv)
    }

    /** 1=最细 … 10=最粗，默认 3 约等于字号的 3% */
    private fun overlayStrokeWidthPx(level: Int): Float {
        val ratio = 0.012f + (level - 1) * 0.007f
        return ratio
    }

    private fun strokeWidthFor(textSizePx: Float, ratio: Float): Float {
        if (ratio <= 0f) return (textSizePx * 0.03f).coerceIn(1f, 4f)
        return (textSizePx * ratio).coerceIn(1f, 5f)
    }
}
