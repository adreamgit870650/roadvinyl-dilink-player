package com.car.mp3player.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.car.mp3player.R
import com.car.mp3player.model.VinylScale
import kotlin.math.min
import kotlin.math.roundToInt

class VinylRecordView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val rotateGroup: FrameLayout
    private val discView: ImageView
    private val coverView: ImageView
    private var rotationAnimator: ObjectAnimator? = null
    private var discPx = 0
    private var baseGeometry = VinylGeometry(0, 0)
    var userScale: Float = VinylScale.DEFAULT
        private set

    init {
        clipChildren = false
        clipToPadding = false
        LayoutInflater.from(context).inflate(R.layout.view_vinyl_record, this, true)
        rotateGroup = findViewById(R.id.vinylRotateGroup)
        discView = findViewById(R.id.vinylDisc)
        coverView = findViewById(R.id.vinylCover)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val geometry = calculateVinylGeometry(
            widthPx = w,
            heightPx = h,
            horizontalPaddingPx = paddingLeft + paddingRight,
            verticalPaddingPx = paddingTop + paddingBottom,
            minimumDiscPx = dp(160)
        )
        baseGeometry = geometry
        applyUserScale()
    }

    fun setUserScale(scale: Float): Float {
        val normalized = VinylScale.clamp(scale)
        if (normalized == userScale) return normalized
        userScale = normalized
        applyUserScale()
        return normalized
    }

    private fun applyUserScale() {
        val geometry = scaleVinylGeometry(baseGeometry, userScale)
        if (geometry.discPx <= 0) return
        discPx = geometry.discPx
        layoutDiscAndCover(geometry.discPx, geometry.coverPx)
    }

    private fun layoutDiscAndCover(discPx: Int, coverPx: Int) {
        (rotateGroup.layoutParams as FrameLayout.LayoutParams).apply {
            width = discPx
            height = discPx
            gravity = Gravity.CENTER
            rotateGroup.layoutParams = this
        }
        (discView.layoutParams as FrameLayout.LayoutParams).apply {
            width = discPx
            height = discPx
            gravity = Gravity.CENTER
            discView.layoutParams = this
        }
        (coverView.layoutParams as FrameLayout.LayoutParams).apply {
            width = coverPx
            height = coverPx
            gravity = Gravity.CENTER
            coverView.layoutParams = this
        }
        rotateGroup.requestLayout()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    /** Whether a touch point (view-local coords) hits the visible disc circle. */
    fun isDiscTap(localX: Float, localY: Float): Boolean {
        if (discPx <= 0) return false
        val cx = width / 2f
        val cy = height / 2f
        val r = discPx / 2f
        val dx = localX - cx
        val dy = localY - cy
        return dx * dx + dy * dy <= r * r
    }

    fun setCoverBitmap(bitmap: Bitmap?) {
        if (bitmap == null) {
            coverView.setImageResource(R.drawable.bg_album_placeholder)
        } else {
            coverView.setImageBitmap(bitmap)
        }
    }

    fun setPlaying(playing: Boolean) {
        if (playing) {
            if (rotationAnimator?.isRunning == true) return
            rotationAnimator?.cancel()
            rotationAnimator = ObjectAnimator.ofFloat(
                rotateGroup,
                View.ROTATION,
                rotateGroup.rotation,
                rotateGroup.rotation + 360f
            ).apply {
                duration = 18_000L
                interpolator = LinearInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        } else {
            rotationAnimator?.cancel()
            rotationAnimator = null
        }
    }

    override fun onDetachedFromWindow() {
        rotationAnimator?.cancel()
        rotationAnimator = null
        super.onDetachedFromWindow()
    }
}

internal data class VinylGeometry(
    val discPx: Int,
    val coverPx: Int
)

internal fun calculateVinylGeometry(
    widthPx: Int,
    heightPx: Int,
    horizontalPaddingPx: Int,
    verticalPaddingPx: Int,
    minimumDiscPx: Int
): VinylGeometry {
    val rawShortSide = min(widthPx.coerceAtLeast(0), heightPx.coerceAtLeast(0))
    if (rawShortSide <= 0) return VinylGeometry(0, 0)

    // dp-based padding becomes disproportionately large on high-density phones
    // such as Mate 60 Pro. Keep the requested padding, but cap the combined
    // inset on each axis to 4% of the actual stage short side.
    val maximumAxisPadding = (rawShortSide * 0.04f).roundToInt()
    val effectiveHorizontalPadding = horizontalPaddingPx.coerceIn(0, maximumAxisPadding)
    val effectiveVerticalPadding = verticalPaddingPx.coerceIn(0, maximumAxisPadding)
    val availableWidth = (widthPx - effectiveHorizontalPadding).coerceAtLeast(0)
    val availableHeight = (heightPx - effectiveVerticalPadding).coerceAtLeast(0)
    // The player header ends exactly where this stage begins. Keeping the disc
    // within both axes guarantees that it cannot cover the song or artist text.
    val maximumDiscPx = min(availableWidth, availableHeight)
    if (maximumDiscPx <= 0) return VinylGeometry(0, 0)

    val preferredDiscPx = (maximumDiscPx * 0.98f).roundToInt()
    val effectiveMinimum = minimumDiscPx.coerceAtLeast(0).coerceAtMost(maximumDiscPx)
    val discPx = preferredDiscPx.coerceIn(effectiveMinimum, maximumDiscPx)
    return VinylGeometry(
        discPx = discPx,
        coverPx = (discPx * 0.68f).roundToInt()
    )
}

internal fun scaleVinylGeometry(base: VinylGeometry, scale: Float): VinylGeometry {
    if (base.discPx <= 0 || base.coverPx <= 0) return VinylGeometry(0, 0)
    val normalized = VinylScale.clamp(scale)
    return VinylGeometry(
        discPx = (base.discPx * normalized).roundToInt().coerceAtLeast(1),
        coverPx = (base.coverPx * normalized).roundToInt().coerceAtLeast(1)
    )
}
