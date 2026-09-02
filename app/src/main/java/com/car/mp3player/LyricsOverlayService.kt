package com.car.mp3player

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.model.LyricState
import com.car.mp3player.ui.KaraokeLyricView
import com.car.mp3player.ui.LyricRenderer
import com.car.mp3player.ui.OverlayPositioning
import kotlin.math.hypot
import kotlin.math.ceil

class LyricsOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: KaraokeLyricView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private lateinit var settings: SettingsRepository
    private var lastLyricState: LyricState? = null
    private var lastLayoutTextKey = ""
    private var dragDownRawX = 0f
    private var dragDownRawY = 0f
    private var dragStartX = 0
    private var dragStartY = 0
    private var dragMoved = false
    private var dragTouchActive = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!settings.overlayEnabled) {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }
        showOverlayIfNeeded()
        lastLyricState?.let { overlayView?.update(it) }
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlayIfNeeded() {
        if (overlayView != null) return
        if (AppForegroundTracker.isInForeground) return
        if (!settings.overlayEnabled || !Settings.canDrawOverlays(this)) return

        val view = KaraokeLyricView(this)
        val (screenWidth, screenHeight) = screenSize()
        val widthPx = desiredOverlayWidth(lastLyricState, screenWidth)
        val heightPx = desiredOverlayHeight(lastLyricState, widthPx)
        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        val initialPosition = initialPosition(
            screenWidth,
            screenHeight,
            widthPx,
            heightPx
        )
        params.x = initialPosition.x
        params.y = initialPosition.y
        view.isClickable = true
        view.setOnTouchListener { _, event -> handleOverlayTouch(view, event) }
        overlayView = view
        overlayParams = params
        windowManager?.addView(view, params)
        updateDragHandleVisibility()
        lastLyricState?.let { view.update(it) }
    }

    private fun removeOverlay() {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        overlayParams = null
        lastLayoutTextKey = ""
    }

    private fun handleOverlayTouch(view: KaraokeLyricView, event: MotionEvent): Boolean {
        val params = overlayParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragTouchActive = true
                dragDownRawX = event.rawX
                dragDownRawY = event.rawY
                dragStartX = params.x
                dragStartY = params.y
                dragMoved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - dragDownRawX
                val deltaY = event.rawY - dragDownRawY
                if (!dragMoved) {
                    val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
                    dragMoved = hypot(deltaX.toDouble(), deltaY.toDouble()) >= touchSlop
                }
                if (dragMoved) {
                    moveOverlayTo(dragStartX + deltaX.toInt(), dragStartY + deltaY.toInt())
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragMoved) {
                    saveOverlayPosition()
                } else {
                    view.performClick()
                }
                dragTouchActive = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragMoved) saveOverlayPosition()
                dragTouchActive = false
                return true
            }
            else -> return true
        }
    }

    private fun moveOverlayTo(x: Int, y: Int) {
        val view = overlayView ?: return
        val params = overlayParams ?: return
        val (screenWidth, screenHeight) = screenSize()
        val clamped = OverlayPositioning.clamp(
            x,
            y,
            screenWidth,
            screenHeight,
            params.width,
            params.height
        )
        params.x = clamped.x
        params.y = clamped.y
        runCatching { windowManager?.updateViewLayout(view, params) }
        updateDragHandleVisibility()
    }

    private fun updateDragHandleVisibility() {
        val view = overlayView ?: return
        val params = overlayParams ?: return
        view.setDragHandleVisible(params.y <= statusBarHeightPx())
    }

    private fun statusBarHeightPx(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            (24f * resources.displayMetrics.density).toInt()
        }
    }

    private fun saveOverlayPosition() {
        val params = overlayParams ?: return
        val (screenWidth, screenHeight) = screenSize()
        val center = OverlayPositioning.toCenterFractions(
            params.x,
            params.y,
            screenWidth,
            screenHeight,
            params.width,
            params.height
        )
        settings.saveCustomOverlayPosition(center.x, center.y)
    }

    private fun initialPosition(
        screenWidth: Int,
        screenHeight: Int,
        overlayWidth: Int,
        overlayHeight: Int
    ): OverlayPositioning.PixelPosition {
        if (settings.hasCustomOverlayPosition) {
            return OverlayPositioning.fromCenterFractions(
                settings.overlayCenterXFraction,
                settings.overlayCenterYFraction,
                screenWidth,
                screenHeight,
                overlayWidth,
                overlayHeight
            )
        }
        val density = resources.displayMetrics.density
        val x = (screenWidth - overlayWidth) / 2
        val y = when (settings.overlayPosition) {
            SettingsRepository.POSITION_TOP -> (24 * density).toInt()
            SettingsRepository.POSITION_BOTTOM ->
                screenHeight - overlayHeight - (48 * density).toInt()
            else -> (screenHeight - overlayHeight) / 2
        }
        return OverlayPositioning.clamp(
            x,
            y,
            screenWidth,
            screenHeight,
            overlayWidth,
            overlayHeight
        )
    }

    private fun desiredOverlayWidth(state: LyricState?, screenWidth: Int): Int {
        val density = resources.displayMetrics.density
        val scaledDensity = resources.displayMetrics.scaledDensity
        val minWidth = (120f * density).toInt().coerceAtMost(screenWidth)
        val maxWidth = (screenWidth * 0.92f).toInt().coerceAtLeast(minWidth)
        if (state == null) return (screenWidth * 0.72f).toInt().coerceIn(minWidth, maxWidth)

        val style = LyricRenderer.styleFrom(this, settings, scaledDensity, forPlayer = false)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = style.typeface }
        var widest = 0f

        fun measure(text: String, textSize: Float) {
            paint.textSize = textSize
            text.split('\n').forEach { line -> widest = maxOf(widest, paint.measureText(line)) }
        }

        state.currentLine?.let { measure(it.text, style.currentSizePx) }
        if (settings.maxLyricVisualLines > 1) {
            state.nextLine?.let { measure(it.text, style.nextSizePx) }
        }
        val horizontalPadding = 48f * density
        return (widest + horizontalPadding).toInt().coerceIn(minWidth, maxWidth)
    }

    private fun desiredOverlayHeight(state: LyricState?, width: Int): Int {
        val density = resources.displayMetrics.density
        val scaledDensity = resources.displayMetrics.scaledDensity
        val style = LyricRenderer.styleFrom(this, settings, scaledDensity, forPlayer = false)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val contentHeight = LyricRenderer.measureOverlayContentHeight(
            state?.currentLine,
            state?.nextLine,
            width,
            style,
            paint
        )
        return ceil(contentHeight + OVERLAY_VERTICAL_PADDING_DP * density).toInt()
    }

    private fun screenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager?.currentWindowMetrics?.bounds?.let { bounds ->
                return bounds.width().coerceAtLeast(1) to bounds.height().coerceAtLeast(1)
            }
        }
        val metrics = resources.displayMetrics
        return metrics.widthPixels.coerceAtLeast(1) to metrics.heightPixels.coerceAtLeast(1)
    }

    private fun updateOverlay(state: LyricState) {
        lastLyricState = state
        overlayView?.update(state)
        updateOverlayLayout(state)
    }

    private fun updateOverlayLayout(state: LyricState) {
        val view = overlayView ?: return
        val params = overlayParams ?: return
        val layoutKey = buildString {
            append(state.currentLine?.text)
            append('|')
            append(state.nextLine?.text)
        }
        if (layoutKey == lastLayoutTextKey) return
        lastLayoutTextKey = layoutKey

        val (screenWidth, screenHeight) = screenSize()
        val newWidth = desiredOverlayWidth(state, screenWidth)
        val newHeight = desiredOverlayHeight(state, newWidth)
        if (newWidth == params.width && newHeight == params.height) return
        val centerX = params.x + params.width / 2
        params.width = newWidth
        params.height = newHeight
        val clamped = if (settings.hasCustomOverlayPosition && !dragTouchActive) {
            OverlayPositioning.fromCenterFractions(
                settings.overlayCenterXFraction,
                settings.overlayCenterYFraction,
                screenWidth,
                screenHeight,
                newWidth,
                params.height
            )
        } else {
            OverlayPositioning.clamp(
                centerX - newWidth / 2,
                params.y,
                screenWidth,
                screenHeight,
                newWidth,
                params.height
            )
        }
        params.x = clamped.x
        params.y = clamped.y
        runCatching { windowManager?.updateViewLayout(view, params) }
        updateDragHandleVisibility()
    }

    private fun refreshLayout() {
        val state = lastLyricState
        removeOverlay()
        showOverlayIfNeeded()
        state?.let { overlayView?.update(it) }
    }

    private fun onForegroundChanged(inForeground: Boolean) {
        if (inForeground) {
            removeOverlay()
        } else if (settings.overlayEnabled && Settings.canDrawOverlays(this)) {
            showOverlayIfNeeded()
            lastLyricState?.let { overlayView?.update(it) }
        }
    }

    companion object {
        private const val OVERLAY_VERTICAL_PADDING_DP = 12f
        private var instance: LyricsOverlayService? = null

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            context.startService(Intent(context, LyricsOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LyricsOverlayService::class.java))
        }

        fun updateLyrics(context: Context, state: LyricState) {
            instance?.updateOverlay(state)
        }

        fun refresh(context: Context) {
            instance?.refreshLayout()
        }

        fun onAppForegroundChanged(inForeground: Boolean) {
            instance?.onForegroundChanged(inForeground)
        }
    }
}
