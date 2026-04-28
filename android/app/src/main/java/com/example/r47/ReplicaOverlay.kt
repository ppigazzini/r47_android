package com.example.r47

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.util.Log
import kotlin.math.min
import kotlin.math.roundToInt

class ReplicaOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    companion object {
        const val CHROME_MODE_NATIVE = "native"
        const val CHROME_MODE_TEXTURE = "r47_texture"
        const val CHROME_MODE_BACKGROUND = "r47_background"
    }

    private data class Projection(val scale: Float, val offsetX: Float, val offsetY: Float)
    private data class FitFrame(val left: Float, val top: Float, val width: Float, val height: Float)
    private data class ChromeSpec(
        val mode: String,
        val shellWidth: Float,
        val shellHeight: Float,
        val bezelHeight: Float,
        val settingsTouchHeight: Float = bezelHeight,
        val lcdLeft: Float,
        val lcdTop: Float,
        val lcdWidth: Float,
        val lcdHeight: Float,
        val adaptiveTrimLeft: Float = 0f,
        val adaptiveTrimTop: Float = 0f,
        val adaptiveTrimRight: Float = 0f,
        val adaptiveTrimBottom: Float = 0f,
        val imageResId: Int? = null,
        val drawNativeChrome: Boolean = false,
    )

    private val shellCorner = 48f
    private val lcdCorner = 14f
    private val sharedTextureScaleX = 526f / 537f
    private val sharedTextureScaleY = 980f / 1005f
    private val sharedAdaptiveTrimLeft = 12f
    private val sharedAdaptiveTrimTop = 14f
    private val sharedAdaptiveTrimRight = 12f
    private val sharedAdaptiveTrimBottom = 16f
    private val sharedSettingsTouchHeight = 67.5f * sharedTextureScaleY
    private val sharedVirtualLcdLeft = 25.5f * sharedTextureScaleX
    private val sharedVirtualLcdTop = 67.5f * sharedTextureScaleY
    private val sharedVirtualLcdWidth = 486f * sharedTextureScaleX
    private val sharedVirtualLcdHeight = 266.7f * sharedTextureScaleY
    // Keep the adaptive texture crop in the same visible frame as the shared-shell modes.
    private val textureAdaptiveTrimLeft = sharedAdaptiveTrimLeft / sharedTextureScaleX
    private val textureAdaptiveTrimTop = sharedAdaptiveTrimTop / sharedTextureScaleY
    private val textureAdaptiveTrimRight = sharedAdaptiveTrimRight / sharedTextureScaleX
    private val textureAdaptiveTrimBottom = sharedAdaptiveTrimBottom / sharedTextureScaleY
    private val sharedChromeSpec = ChromeSpec(
        mode = CHROME_MODE_NATIVE,
        shellWidth = 526f,
        shellHeight = 980f,
        bezelHeight = 72f,
        settingsTouchHeight = sharedSettingsTouchHeight,
        lcdLeft = sharedVirtualLcdLeft,
        lcdTop = sharedVirtualLcdTop,
        lcdWidth = sharedVirtualLcdWidth,
        lcdHeight = sharedVirtualLcdHeight,
        adaptiveTrimLeft = sharedAdaptiveTrimLeft,
        adaptiveTrimTop = sharedAdaptiveTrimTop,
        adaptiveTrimRight = sharedAdaptiveTrimRight,
        adaptiveTrimBottom = sharedAdaptiveTrimBottom,
        drawNativeChrome = true,
    )
    private val nativeChromeSpec = sharedChromeSpec
    private val backgroundChromeSpec = sharedChromeSpec.copy(
        mode = CHROME_MODE_BACKGROUND,
        imageResId = R.drawable.r47_background,
    )
    private val textureChromeSpec = ChromeSpec(
        mode = CHROME_MODE_TEXTURE,
        shellWidth = 537f,
        shellHeight = 1005f,
        bezelHeight = 67.5f,
        settingsTouchHeight = 67.5f,
        lcdLeft = 25.5f,
        lcdTop = 67.5f,
        lcdWidth = 486f,
        lcdHeight = 266.7f,
        adaptiveTrimLeft = textureAdaptiveTrimLeft,
        adaptiveTrimTop = textureAdaptiveTrimTop,
        adaptiveTrimRight = textureAdaptiveTrimRight,
        adaptiveTrimBottom = textureAdaptiveTrimBottom,
        imageResId = R.drawable.r47_texture,
    )
    private val backgroundVisualChromeSpec = textureChromeSpec.copy(
        mode = CHROME_MODE_BACKGROUND,
        imageResId = R.drawable.r47_background,
    )

    private var isPiPMode = false
    private var chromeMode = CHROME_MODE_NATIVE
    private var scalingMode = "full_width"
    private var showTouchZones = false

    private val lcdBitmap = Bitmap.createBitmap(400, 240, Bitmap.Config.ARGB_8888)
    private val chromeBitmapCache = mutableMapOf<Int, Bitmap?>()
    private val lastLcdPixels = IntArray(400 * 240)
    private val lcdRect = Rect(0, 0, 400, 240)
    private val lcdDestRect = RectF()
    private val dirtyRect = Rect()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shellRect = RectF()
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(31, 31, 31)
    }
    private val lcdFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
    }
    private val lcdFrameStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#46515C")
        style = Paint.Style.STROKE
        strokeWidth = dp(1.25f)
    }
    private val zonePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        alpha = 180
    }

    var onPiPKeyEvent: ((Int) -> Unit)? = null
    var onLongPressListener: ((Float, Float) -> Unit)? = null
    var onSettingsTapListener: (() -> Unit)? = null
    
    private val gestureDetector: GestureDetector

    init {
        setBackgroundColor(Color.BLACK)
        // Allow drawing outside individual key boundaries
        clipChildren = false
        clipToPadding = false
        
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                onLongPressListener?.invoke(e.x, e.y)
            }
        })
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics,
        )
    }

    fun setPiPMode(enabled: Boolean) {
        isPiPMode = enabled
        requestLayout()
        invalidate()
    }

    fun setScalingMode(mode: String) {
        scalingMode = mode
        requestLayout()
        invalidate()
    }

    fun setShowTouchZones(show: Boolean) {
        showTouchZones = show
        invalidate()
    }

    fun setChromeMode(mode: String) {
        val resolvedMode = resolveChromeSpec(mode).mode
        if (chromeMode == resolvedMode) {
            return
        }
        chromeMode = resolvedMode
        requestLayout()
        invalidate()
    }

    fun setNativeChrome() {
        setChromeMode(CHROME_MODE_NATIVE)
    }

    private fun resolveChromeSpec(mode: String): ChromeSpec {
        return when {
            mode == CHROME_MODE_TEXTURE -> textureChromeSpec
            mode == "image" -> backgroundChromeSpec
            mode.startsWith(CHROME_MODE_BACKGROUND) -> backgroundChromeSpec
            else -> nativeChromeSpec
        }
    }

    private fun currentChromeSpec(): ChromeSpec {
        return resolveChromeSpec(chromeMode)
    }

    private fun currentVisualChromeSpec(): ChromeSpec {
        val layoutSpec = currentChromeSpec()
        return if (layoutSpec.mode == CHROME_MODE_BACKGROUND) {
            backgroundVisualChromeSpec
        } else {
            layoutSpec
        }
    }

    private fun currentVisualLcdChromeSpec(): ChromeSpec {
        return textureChromeSpec
    }

    private fun chromeBitmapFor(spec: ChromeSpec): Bitmap? {
        val resId = spec.imageResId ?: return null
        return chromeBitmapCache.getOrPut(resId) {
            BitmapFactory.decodeResource(resources, resId)
        }
    }

    fun isPointInLcd(x: Float, y: Float): Boolean {
        if (width <= 0 || height <= 0) {
            return false
        }

        val spec = currentVisualLcdChromeSpec()
        val projection = createProjection(spec, width.toFloat(), height.toFloat())
        val localX = (x - projection.offsetX) / projection.scale
        val localY = (y - projection.offsetY) / projection.scale

        return localX >= spec.lcdLeft &&
            localX <= spec.lcdLeft + spec.lcdWidth &&
            localY >= spec.lcdTop &&
            localY <= spec.lcdTop + spec.lcdHeight
    }

    fun updateLcd(pixels: IntArray) {
        var minX = 400
        var maxX = 0
        var minY = 240
        var maxY = 0
        var changed = false

        for (i in pixels.indices) {
            if (pixels[i] != lastLcdPixels[i]) {
                val x = i % 400
                val y = i / 400
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                lastLcdPixels[i] = pixels[i]
                changed = true
            }
        }

        if (!changed) return

        lcdBitmap.setPixels(pixels, 0, 400, 0, 0, 400, 240)

        // Calculate screen-space dirty rect
        val spec = currentVisualLcdChromeSpec()
        val projection = createProjection(spec, width.toFloat(), height.toFloat())

        // Convert LCD coordinates to Screen coordinates
        val left = projection.offsetX + (spec.lcdLeft + (minX.toFloat() / 400f) * spec.lcdWidth) * projection.scale
        val top = projection.offsetY + (spec.lcdTop + (minY.toFloat() / 240f) * spec.lcdHeight) * projection.scale
        val right = projection.offsetX + (spec.lcdLeft + ((maxX.toFloat() + 1f) / 400f) * spec.lcdWidth) * projection.scale
        val bottom = projection.offsetY + (spec.lcdTop + ((maxY.toFloat() + 1f) / 240f) * spec.lcdHeight) * projection.scale

        dirtyRect.set(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
        postInvalidateOnAnimation(dirtyRect.left, dirtyRect.top, dirtyRect.right, dirtyRect.bottom)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isPiPMode) return false
        gestureDetector.onTouchEvent(ev)

        val projection = createProjection(width.toFloat(), height.toFloat())
        val lY = (ev.y - projection.offsetY) / projection.scale
        val spec = currentChromeSpec()

        // Intercept touches in the settings area (top bezel)
        if (lY < spec.settingsTouchHeight && lY > 0) {
            return true
        }

        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)

        if (isPiPMode) {
            val fKey = (event.x / width * 6).toInt() + 38
            if (event.action == MotionEvent.ACTION_DOWN) {
                onPiPKeyEvent?.invoke(fKey)
            } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                onPiPKeyEvent?.invoke(0)
            }
            return true
        }

        val projection = createProjection(width.toFloat(), height.toFloat())
        val lY = (event.y - projection.offsetY) / projection.scale
        val spec = currentChromeSpec()

        // If we intercepted this (or no one else took it), and it's in the bezel area
        if (lY < spec.settingsTouchHeight && lY > 0) {
            if (event.action == MotionEvent.ACTION_UP) {
                Log.i("ReplicaOverlay", "Settings area tap received")
                onSettingsTapListener?.invoke()
            }
            return true
        }

        return super.onTouchEvent(event)
    }

    class LayoutParams(
        val logicalX: Float,
        val logicalY: Float,
        val logicalWidth: Float,
        val logicalHeight: Float,
        val showTouchZone: Boolean = false,
    ) : ViewGroup.LayoutParams(0, 0)

    fun addReplicaView(
        view: View,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        showTouchZone: Boolean = false,
    ) {
        addView(view, LayoutParams(x, y, w, h, showTouchZone))
    }

    private fun getFitFrame(spec: ChromeSpec): FitFrame {
        return if (scalingMode == "physical") {
            FitFrame(0f, 0f, spec.shellWidth, spec.shellHeight)
        } else {
            FitFrame(
                left = spec.adaptiveTrimLeft,
                top = spec.adaptiveTrimTop,
                width = spec.shellWidth - spec.adaptiveTrimLeft - spec.adaptiveTrimRight,
                height = spec.shellHeight - spec.adaptiveTrimTop - spec.adaptiveTrimBottom,
            )
        }
    }

    private fun createProjection(spec: ChromeSpec, availableWidth: Float, availableHeight: Float): Projection {
        val fitFrame = getFitFrame(spec)
        val fitScale = min(availableWidth / fitFrame.width, availableHeight / fitFrame.height)
        val scale = if (scalingMode == "physical") {
            val dpi = resources.displayMetrics.xdpi
            val physicalScale = (2.83f * dpi) / spec.shellWidth
            min(physicalScale, fitScale)
        } else {
            fitScale
        }
        val offsetX = (availableWidth - fitFrame.width * scale) / 2f - fitFrame.left * scale
        val offsetY = (availableHeight - fitFrame.height * scale) / 2f - fitFrame.top * scale
        return Projection(scale, offsetX, offsetY)
    }

    private fun createProjection(availableWidth: Float, availableHeight: Float): Projection {
        return createProjection(currentChromeSpec(), availableWidth, availableHeight)
    }

    private fun drawNativeShellChrome(
        canvas: Canvas,
        rect: RectF,
        cornerRadius: Float,
    ) {
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bodyPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)

        if (!isPiPMode) {
            val projection = createProjection(w.toFloat(), h.toFloat())
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val lp = child.layoutParams as LayoutParams
                val childWidth = (lp.logicalWidth * projection.scale).roundToInt().coerceAtLeast(0)
                val childHeight = (lp.logicalHeight * projection.scale).roundToInt().coerceAtLeast(0)
                child.measure(
                    MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
                )
            }
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (isPiPMode) {
            for (i in 0 until childCount) {
                getChildAt(i).layout(0, 0, 0, 0)
            }
            return
        }

        val w = (r - l).toFloat()
        val h = (b - t).toFloat()
        val projection = createProjection(w, h)

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as LayoutParams
            val left = (projection.offsetX + lp.logicalX * projection.scale).roundToInt()
            val top = (projection.offsetY + lp.logicalY * projection.scale).roundToInt()
            child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        if (isPiPMode) {
            lcdDestRect.set(0f, 0f, w, h)
            canvas.drawBitmap(lcdBitmap, lcdRect, lcdDestRect, paint)
            return
        }

        val layoutProjection = createProjection(w, h)
        val layoutSpec = currentChromeSpec()
        val visualSpec = currentVisualChromeSpec()
        val visualProjection = createProjection(visualSpec, w, h)
        val lcdVisualSpec = currentVisualLcdChromeSpec()
        val lcdVisualProjection = createProjection(lcdVisualSpec, w, h)

        shellRect.set(
            visualProjection.offsetX,
            visualProjection.offsetY,
            visualProjection.offsetX + visualSpec.shellWidth * visualProjection.scale,
            visualProjection.offsetY + visualSpec.shellHeight * visualProjection.scale,
        )
        val backgroundBitmap = chromeBitmapFor(visualSpec)
        if (backgroundBitmap != null) {
            canvas.drawBitmap(backgroundBitmap, null, shellRect, paint)
        } else {
            drawNativeShellChrome(
                canvas = canvas,
                rect = shellRect,
                cornerRadius = shellCorner * visualProjection.scale,
            )
        }

        lcdDestRect.set(
            lcdVisualProjection.offsetX + lcdVisualSpec.lcdLeft * lcdVisualProjection.scale,
            lcdVisualProjection.offsetY + lcdVisualSpec.lcdTop * lcdVisualProjection.scale,
            lcdVisualProjection.offsetX + (lcdVisualSpec.lcdLeft + lcdVisualSpec.lcdWidth) * lcdVisualProjection.scale,
            lcdVisualProjection.offsetY + (lcdVisualSpec.lcdTop + lcdVisualSpec.lcdHeight) * lcdVisualProjection.scale
        )
        if (layoutSpec.drawNativeChrome) {
            canvas.drawRoundRect(lcdDestRect, lcdCorner * lcdVisualProjection.scale, lcdCorner * lcdVisualProjection.scale, lcdFramePaint)
            canvas.drawRoundRect(lcdDestRect, lcdCorner * lcdVisualProjection.scale, lcdCorner * lcdVisualProjection.scale, lcdFrameStrokePaint)
        }
        canvas.drawBitmap(lcdBitmap, lcdRect, lcdDestRect, paint)

        if (showTouchZones) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val lp = child.layoutParams as? LayoutParams ?: continue
                if (!lp.showTouchZone) {
                    continue
                }
                canvas.drawRect(
                    child.left.toFloat(), child.top.toFloat(),
                    child.right.toFloat(), child.bottom.toFloat(),
                    zonePaint
                )
            }
            // Show settings zone
            canvas.drawRect(
                layoutProjection.offsetX,
                layoutProjection.offsetY,
                layoutProjection.offsetX + layoutSpec.shellWidth * layoutProjection.scale,
                layoutProjection.offsetY + layoutSpec.settingsTouchHeight * layoutProjection.scale,
                zonePaint,
            )
        }

        super.dispatchDraw(canvas)
    }
}
