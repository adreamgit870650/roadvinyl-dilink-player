package com.car.mp3player.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/** Circular album cover that also works before outline clipping was added in API 21. */
class CircularCoverImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {
    private val clipBounds = RectF()
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val renderCanvas = Canvas()
    private var renderBitmap: Bitmap? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clipBounds.set(0f, 0f, w.toFloat(), h.toFloat())
        renderBitmap?.recycle()
        renderBitmap = if (w > 0 && h > 0) Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) else null
        renderCanvas.setBitmap(renderBitmap)
        circlePaint.shader = renderBitmap?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = renderBitmap
        if (bitmap == null) {
            super.onDraw(canvas)
            return
        }
        renderCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        super.onDraw(renderCanvas)
        canvas.drawOval(clipBounds, circlePaint)
    }
}
