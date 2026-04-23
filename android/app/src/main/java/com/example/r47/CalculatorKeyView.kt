package com.example.r47

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.math.abs

internal data class KeypadFontSet(
    val standard: Typeface?,
    val numeric: Typeface?,
    val tiny: Typeface?,
)

class CalculatorKeyView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    companion object {
        private val defaultPrimaryColor = Color.WHITE
        private val defaultPrimaryDarkColor = Color.BLACK
        private val fAccentColor = Color.parseColor("#E5AE5A")
        private val fHoverColor = Color.parseColor("#FFFF00")
        private val gAccentColor = Color.parseColor("#7EB6BA")
        private val gHoverColor = Color.parseColor("#00FFFF")
        private val fgAccentColor = Color.parseColor("#F1B053")
        private val fgHoverColor = Color.parseColor("#E7D7A0")
        private val alphaAccentColor = Color.parseColor("#E36C50")
        private val alphaHoverColor = Color.parseColor("#FF8669")
        private val letterColor = Color.parseColor("#A5A5A5")
        private val longPressColor = Color.parseColor("#D4D8DD")
        private val mainKeyFillColor = Color.parseColor("#212121")
        private val mainKeyPressedColor = Color.parseColor("#744A2E")
        private val mainKeyStrokeColor = Color.parseColor("#25292F")
        private val softkeyFillColor = Color.parseColor("#D7DDE2")
        private val softkeyPressedColor = Color.parseColor("#C8D0D7")
        private val softkeyDisabledColor = Color.parseColor("#BCC5CD")
        private val softkeyReverseColor = Color.parseColor("#182027")
        private val softkeyReversePressedColor = Color.parseColor("#24323C")
        private val softkeyDarkTextColor = Color.parseColor("#151B20")
        private val softkeyLightTextColor = Color.parseColor("#F4F7F9")
        private val softkeyMetaDarkColor = Color.parseColor("#5A6670")
        private val softkeyMetaLightColor = Color.parseColor("#C9D0D6")
        private val softkeyValueDarkColor = Color.parseColor("#71451D")
        private val softkeyValueLightColor = Color.parseColor("#F0C77A")
        private val softkeyPreviewColor = Color.parseColor("#E5AE5A")
        private val surfaceHighlightColor = Color.argb(136, 255, 255, 255)
        private val lightSurfaceHighlightColor = Color.argb(92, 255, 255, 255)
        private const val MAIN_KEY_VIEW_HEIGHT = 68f
        private const val MAIN_KEY_BUTTON_HEIGHT_RATIO = 43f / MAIN_KEY_VIEW_HEIGHT
        private const val SMALL_KEY_CELL_WIDTH = 78f
        private const val SMALL_KEY_BUTTON_WIDTH = 47f
        private const val LARGE_KEY_CELL_WIDTH = 95f
        private const val LARGE_KEY_BUTTON_WIDTH = 56f
        private const val WIDE_KEY_BUTTON_WIDTH = 125f
        private const val STANDARD_KEY_FONT_SIZE = 22f
        private const val NUMERIC_KEY_FONT_SIZE = 33f
        private const val SHIFT_KEY_FONT_SIZE = 27f
        private const val SHIFT_LABEL_FONT_SIZE = 18f
        private const val LETTER_LABEL_FONT_SIZE = 19f
        private const val FACEPLATE_GAP = 3f
        private const val FACEPLATE_LABEL_OFFSET = 25f
        private const val LETTER_X_OFFSET = 3f
        private const val LETTER_Y_OFFSET = 18f
        private const val SMALL_KEY_LETTER_RATIO =
            (SMALL_KEY_CELL_WIDTH - SMALL_KEY_BUTTON_WIDTH) / SMALL_KEY_CELL_WIDTH
        private const val LARGE_KEY_LETTER_RATIO =
            (LARGE_KEY_CELL_WIDTH - LARGE_KEY_BUTTON_WIDTH) / LARGE_KEY_CELL_WIDTH
        private const val TEXT_ANCHOR_CENTER = 0
        private const val TEXT_ANCHOR_TOP = 1
        private const val TEXT_ANCHOR_BOTTOM = 2
    }

    private val buttonView = View(context)
    val primaryLabel = TextView(context)
    val fLabel = TextView(context)
    val gLabel = TextView(context)
    val alphaLabel = TextView(context)
    val letterLabel = TextView(context)
    
    var keyCode: Int = 0
    private var isFnKey: Boolean = false
    private var lastLayoutClass: Int? = null
    private var usesLetterSpacer = true
    private var keepLetterSpacerInvisible = false
    private var fontSet = KeypadFontSet(null, null, null)
    private var softkeyState = KeypadKeySnapshot.EMPTY
    private var mainKeyState = KeypadKeySnapshot.EMPTY
    private var currentShiftFOn = false
    private var currentShiftGOn = false
    private var designCellWidth = SMALL_KEY_CELL_WIDTH
    private var designButtonWidth = SMALL_KEY_BUTTON_WIDTH
    private val softkeyRect = RectF()
    private val mainKeyRect = RectF()
    private val mainKeyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val mainKeyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val softkeyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val softkeyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val softkeyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val softkeyAuxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val softkeyValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
    }
    private val softkeyDecorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2f
    }
    private val softkeyDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val surfaceHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val faceplateOffsetUpdater = Runnable { updateFaceplateOffsets() }

    init {
        // Critical: Allow drawing outside bounds
        clipChildren = false
        clipToPadding = false
        setWillNotDraw(false)
        
        // Letter label (Right side of the view, bottom aligned with button)
        letterLabel.id = View.generateViewId()
        letterLabel.setTextColor(letterColor)
        letterLabel.gravity = Gravity.START or Gravity.TOP
        letterLabel.includeFontPadding = false
        letterLabel.maxLines = 1
        val letterParams = LayoutParams(0, 0)
        letterParams.topToTop = buttonView.id
        letterParams.endToEnd = LayoutParams.PARENT_ID
        letterParams.bottomToBottom = LayoutParams.PARENT_ID
        letterParams.matchConstraintPercentWidth = SMALL_KEY_LETTER_RATIO
        letterParams.matchConstraintPercentHeight = MAIN_KEY_BUTTON_HEIGHT_RATIO
        addView(letterLabel, letterParams)

        // Button background view (Left side)
        buttonView.id = View.generateViewId()
        val btnParams = LayoutParams(0, 0)
        btnParams.topToTop = LayoutParams.PARENT_ID
        btnParams.bottomToBottom = LayoutParams.PARENT_ID
        btnParams.startToStart = LayoutParams.PARENT_ID
        btnParams.endToStart = letterLabel.id
        btnParams.verticalBias = 0f
        btnParams.matchConstraintPercentHeight = MAIN_KEY_BUTTON_HEIGHT_RATIO
        buttonView.setBackgroundColor(Color.TRANSPARENT)
        addView(buttonView, btnParams)

        // Primary label (Center of button)
        primaryLabel.id = View.generateViewId()
        primaryLabel.setTextColor(Color.WHITE)
        primaryLabel.gravity = Gravity.CENTER
        primaryLabel.includeFontPadding = false
        primaryLabel.maxLines = 1
        val primaryParams = LayoutParams(0, 0)
        primaryParams.topToTop = buttonView.id
        primaryParams.bottomToBottom = buttonView.id
        primaryParams.startToStart = buttonView.id
        primaryParams.endToEnd = buttonView.id
        addView(primaryLabel, primaryParams)
        
        // F label (Above button)
        fLabel.id = View.generateViewId()
        fLabel.setTextColor(fAccentColor)
        fLabel.gravity = Gravity.START or Gravity.TOP
        fLabel.includeFontPadding = false
        fLabel.maxLines = 1
        val fParams = LayoutParams(LayoutParams.WRAP_CONTENT, 0)
        fParams.topToTop = buttonView.id
        fParams.bottomToBottom = buttonView.id
        fParams.startToStart = LayoutParams.PARENT_ID
        fParams.endToStart = gLabel.id
        fParams.horizontalChainStyle = LayoutParams.CHAIN_PACKED
        addView(fLabel, fParams)

        // G label (Above button)
        gLabel.id = View.generateViewId()
        gLabel.setTextColor(gAccentColor)
        gLabel.gravity = Gravity.END or Gravity.TOP
        gLabel.includeFontPadding = false
        gLabel.maxLines = 1
        val gParams = LayoutParams(LayoutParams.WRAP_CONTENT, 0)
        gParams.topToTop = buttonView.id
        gParams.bottomToBottom = buttonView.id
        gParams.startToEnd = fLabel.id
        gParams.endToEnd = LayoutParams.PARENT_ID
        addView(gLabel, gParams)

        // Alpha label (NOT USED inside key)
        alphaLabel.id = View.generateViewId()
        alphaLabel.visibility = View.GONE
        addView(alphaLabel)
        
        isClickable = false
        buttonView.isClickable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateFontSize(currentShiftFOn, currentShiftGOn)
        updateFaceplateOffsets()
    }

    private fun updateFontSize(fOn: Boolean, gOn: Boolean) {
        currentShiftFOn = fOn
        currentShiftGOn = gOn

        if (isFnKey || width <= 0) {
            return
        }

        val cellScale = if (designCellWidth > 0f) width.toFloat() / designCellWidth else 1f
        var primarySize = when (mainKeyState.styleRole) {
            KeypadSceneContract.STYLE_NUMERIC -> NUMERIC_KEY_FONT_SIZE
            KeypadSceneContract.STYLE_SHIFT_F,
            KeypadSceneContract.STYLE_SHIFT_G,
            KeypadSceneContract.STYLE_SHIFT_FG -> SHIFT_KEY_FONT_SIZE
            else -> STANDARD_KEY_FONT_SIZE
        } * cellScale

        primaryLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, primarySize)
        fLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, SHIFT_LABEL_FONT_SIZE * cellScale)
        gLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, SHIFT_LABEL_FONT_SIZE * cellScale)
        letterLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, LETTER_LABEL_FONT_SIZE * cellScale)
    }

    private fun resetLabelLayout() {
        val fParams = fLabel.layoutParams as LayoutParams
        val gParams = gLabel.layoutParams as LayoutParams

        fParams.horizontalChainStyle = LayoutParams.CHAIN_PACKED
        fParams.horizontalBias = 0.5f
        fParams.marginStart = 0
        fParams.marginEnd = 0
        gParams.horizontalBias = 0.5f
        gParams.marginEnd = 0
        gParams.marginStart = 0
        fParams.startToStart = LayoutParams.PARENT_ID
        fParams.endToStart = gLabel.id
        gParams.startToEnd = fLabel.id
        gParams.endToEnd = LayoutParams.PARENT_ID

        fLabel.layoutParams = fParams
        gLabel.layoutParams = gParams
        scheduleFaceplateOffsetUpdate()
    }

    private fun scheduleFaceplateOffsetUpdate() {
        removeCallbacks(faceplateOffsetUpdater)
        post(faceplateOffsetUpdater)
    }

    private fun updateFaceplateOffsets() {
        if (isFnKey || width <= 0) {
            return
        }

        val cellScale = if (designCellWidth > 0f) width.toFloat() / designCellWidth else 1f
        val labelOffset = FACEPLATE_LABEL_OFFSET * cellScale
        val gap = FACEPLATE_GAP * cellScale
        val centerBias = 2f * cellScale
        val buttonWidth = buttonView.width.toFloat()
        if (buttonWidth <= 0f) {
            return
        }

        val buttonCenterX = buttonView.left + buttonWidth / 2f
        val fWidth = maxOf(fLabel.width, fLabel.measuredWidth).toFloat()
        val gWidth = maxOf(gLabel.width, gLabel.measuredWidth).toFloat()
        val hasFLabel = fLabel.visibility == View.VISIBLE && fLabel.text.isNotBlank()
        val hasGLabel = hasFLabel && gLabel.visibility == View.VISIBLE && gLabel.text.isNotBlank()
        val groupWidth = when {
            hasGLabel -> fWidth + gap + gWidth
            hasFLabel -> fWidth
            else -> 0f
        }
        val groupLeft = buttonCenterX - groupWidth / 2f + centerBias / 2f

        when {
            hasGLabel -> {
                val fLeft = groupLeft
                val gLeft = groupLeft + fWidth + gap
                fLabel.translationX = fLeft - fLabel.left.toFloat()
                gLabel.translationX = gLeft - gLabel.left.toFloat()
            }
            hasFLabel -> {
                fLabel.translationX = groupLeft - fLabel.left.toFloat()
                gLabel.translationX = 0f
            }
            else -> {
                fLabel.translationX = 0f
                gLabel.translationX = 0f
            }
        }

        fLabel.translationY = -labelOffset - fLabel.top.toFloat()
        gLabel.translationY = -labelOffset - gLabel.top.toFloat()

        val letterLeft = buttonWidth + LETTER_X_OFFSET * cellScale
        val letterTop = LETTER_Y_OFFSET * cellScale
        letterLabel.translationX = letterLeft - letterLabel.left.toFloat()
        letterLabel.translationY = letterTop - letterLabel.top.toFloat()
    }

    private fun updateLayoutPositioning(layoutClass: Int) {
        if (lastLayoutClass == layoutClass) return
        lastLayoutClass = layoutClass

        resetLabelLayout()
        val fParams = fLabel.layoutParams as LayoutParams
        val gParams = gLabel.layoutParams as LayoutParams

        when (layoutClass) {
            KeypadSceneContract.LAYOUT_CLASS_STATIC_SINGLE -> {
                fParams.endToStart = LayoutParams.UNSET
                fParams.endToEnd = LayoutParams.PARENT_ID
                fParams.horizontalBias = 0.5f
                gLabel.visibility = View.GONE
            }
        }

        fLabel.layoutParams = fParams
        gLabel.layoutParams = gParams
        scheduleFaceplateOffsetUpdate()
    }

    internal fun setKey(code: Int, isFn: Boolean, fonts: KeypadFontSet) {
        this.keyCode = code
        this.isFnKey = isFn
        this.fontSet = fonts
        primaryLabel.typeface = fonts.standard
        fLabel.typeface = fonts.standard
        gLabel.typeface = fonts.standard
        letterLabel.typeface = fonts.standard
        
        if (isFn) {
            softkeyState = KeypadKeySnapshot.EMPTY
            mainKeyState = KeypadKeySnapshot.EMPTY
            fLabel.visibility = View.GONE
            gLabel.visibility = View.GONE
            letterLabel.visibility = View.GONE
            primaryLabel.visibility = View.GONE
            buttonView.visibility = View.INVISIBLE
            invalidate()
        } else {
            buttonView.visibility = View.VISIBLE
            fLabel.visibility = View.VISIBLE
            gLabel.visibility = View.VISIBLE
            letterLabel.visibility = View.VISIBLE
            primaryLabel.visibility = View.VISIBLE

            lastLayoutClass = null
            resetLabelLayout()
            configureMainKeySurface(code)

            if (code == 13 || code == 18 || code == 23 || code == 28 || code == 33) {
                usesLetterSpacer = false
                keepLetterSpacerInvisible = false
                letterLabel.visibility = View.GONE
                val lp = buttonView.layoutParams as LayoutParams
                lp.endToEnd = LayoutParams.PARENT_ID
                buttonView.layoutParams = lp
            } else {
                usesLetterSpacer = true
                val lp = buttonView.layoutParams as LayoutParams
                lp.endToStart = letterLabel.id
                buttonView.layoutParams = lp
                keepLetterSpacerInvisible = code == 17
                letterLabel.visibility = if (keepLetterSpacerInvisible) View.INVISIBLE else View.VISIBLE
            }

            primaryLabel.setTextColor(defaultPrimaryColor)
            mainKeyState = KeypadKeySnapshot.EMPTY
        }
    }

    private fun applyStyleRole(styleRole: Int) {
        when (styleRole) {
            KeypadSceneContract.STYLE_SHIFT_F -> {
                primaryLabel.setTextColor(defaultPrimaryDarkColor)
            }
            KeypadSceneContract.STYLE_SHIFT_G -> {
                primaryLabel.setTextColor(defaultPrimaryDarkColor)
            }
            KeypadSceneContract.STYLE_SHIFT_FG -> {
                primaryLabel.setTextColor(defaultPrimaryDarkColor)
            }
            KeypadSceneContract.STYLE_ALPHA -> {
                primaryLabel.setTextColor(defaultPrimaryDarkColor)
            }
            else -> {
                primaryLabel.setTextColor(defaultPrimaryColor)
            }
        }
    }

    private fun configureMainKeySurface(code: Int) {
        val (cellWidth, buttonWidth, letterRatio) = when {
            code in 1..12 || code in 14..17 -> Triple(
                SMALL_KEY_CELL_WIDTH,
                SMALL_KEY_BUTTON_WIDTH,
                SMALL_KEY_LETTER_RATIO,
            )

            code == 13 -> Triple(
                WIDE_KEY_BUTTON_WIDTH,
                WIDE_KEY_BUTTON_WIDTH,
                0f,
            )

            code in 19..22 || code in 24..27 || code in 29..32 || code in 34..37 -> Triple(
                LARGE_KEY_CELL_WIDTH,
                LARGE_KEY_BUTTON_WIDTH,
                LARGE_KEY_LETTER_RATIO,
            )

            code == 18 || code == 23 || code == 28 || code == 33 -> Triple(
                SMALL_KEY_BUTTON_WIDTH,
                SMALL_KEY_BUTTON_WIDTH,
                0f,
            )

            else -> Triple(
                SMALL_KEY_CELL_WIDTH,
                SMALL_KEY_BUTTON_WIDTH,
                SMALL_KEY_LETTER_RATIO,
            )
        }

        designCellWidth = cellWidth
        designButtonWidth = buttonWidth

        val buttonParams = buttonView.layoutParams as LayoutParams
        val letterParams = letterLabel.layoutParams as LayoutParams
        letterParams.topToTop = buttonView.id
        letterParams.bottomToBottom = buttonView.id
        letterParams.matchConstraintPercentWidth = letterRatio

        if (letterRatio > 0f) {
            buttonParams.endToStart = letterLabel.id
            buttonParams.endToEnd = LayoutParams.UNSET
        } else {
            buttonParams.endToStart = LayoutParams.UNSET
            buttonParams.endToEnd = LayoutParams.PARENT_ID
        }

        buttonView.layoutParams = buttonParams
        letterLabel.layoutParams = letterParams
        scheduleFaceplateOffsetUpdate()
    }

    private fun applyLabelRole(labelView: TextView, role: Int, defaultColor: Int) {
        var paintFlags = labelView.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
        if (
            role == KeypadSceneContract.TEXT_ROLE_F_UNDERLINE ||
                role == KeypadSceneContract.TEXT_ROLE_G_UNDERLINE
        ) {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
        }
        labelView.paintFlags = paintFlags
        labelView.typeface = fontSet.standard
        labelView.setTextColor(
            when (role) {
                KeypadSceneContract.TEXT_ROLE_LONGPRESS -> longPressColor
                else -> defaultColor
            }
        )
    }

    private fun applySceneStyling(keyState: KeypadKeySnapshot) {
        applyStyleRole(keyState.styleRole)
        primaryLabel.typeface = fontSet.standard
        applyLabelRole(
            fLabel,
            keyState.labelRole(KeypadSceneContract.LABEL_F),
            if (keyState.layoutClass == KeypadSceneContract.LAYOUT_CLASS_STATIC_SINGLE) letterColor else fAccentColor,
        )
        applyLabelRole(
            gLabel,
            keyState.labelRole(KeypadSceneContract.LABEL_G),
            gAccentColor,
        )
        applyLabelRole(
            letterLabel,
            keyState.labelRole(KeypadSceneContract.LABEL_LETTER),
            letterColor,
        )
    }

    private fun applyLabelVisibility(keyState: KeypadKeySnapshot) {
        val hasFLabel = keyState.fLabel.isNotBlank()
        fLabel.visibility = if (hasFLabel) View.VISIBLE else View.INVISIBLE
        gLabel.visibility = if (hasFLabel && keyState.gLabel.isNotBlank()) View.VISIBLE else View.INVISIBLE
        if (!usesLetterSpacer) {
            letterLabel.visibility = View.GONE
        } else if (keepLetterSpacerInvisible || keyState.letterLabel.isBlank()) {
            letterLabel.visibility = View.INVISIBLE
        } else {
            letterLabel.visibility = View.VISIBLE
        }

        if (keyState.layoutClass == KeypadSceneContract.LAYOUT_CLASS_STATIC_SINGLE) {
            gLabel.visibility = View.GONE
        }
    }

    private fun applyEnabledState(enabled: Boolean) {
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.45f
        if (!isFnKey) {
            buttonView.alpha = if (enabled) 1f else 0.45f
            primaryLabel.alpha = if (enabled) 1f else 0.6f
            fLabel.alpha = if (enabled) 1f else 0.6f
            gLabel.alpha = if (enabled) 1f else 0.6f
            letterLabel.alpha = if (enabled) 1f else 0.6f
        }
    }

    internal fun updateLabels(snapshot: KeypadSnapshot) {
        val keyState = snapshot.keyStateFor(keyCode)
        applyEnabledState(keyState.isEnabled)

        if (isFnKey) {
            softkeyState = keyState
            mainKeyState = KeypadKeySnapshot.EMPTY
            contentDescription = buildSoftkeyContentDescription(keyState)
            invalidate()
        } else {
            mainKeyState = keyState
            primaryLabel.text = keyState.primaryLabel
            fLabel.text = keyState.fLabel
            gLabel.text = keyState.gLabel
            letterLabel.text = keyState.letterLabel
            currentShiftFOn = snapshot.shiftF
            currentShiftGOn = snapshot.shiftG || snapshot.alphaOn
            updateFontSize(currentShiftFOn, currentShiftGOn)
            updateLayoutPositioning(keyState.layoutClass)
            applySceneStyling(keyState)
            applyLabelVisibility(keyState)
            scheduleFaceplateOffsetUpdate()
            contentDescription = buildString {
                append(keyState.primaryLabel)
                if (keyState.fLabel.isNotBlank()) {
                    append(", f ")
                    append(keyState.fLabel)
                }
                if (keyState.fLabel.isNotBlank() && keyState.gLabel.isNotBlank()) {
                    append(", g ")
                    append(keyState.gLabel)
                }
            }
        }
    }
    
    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isFnKey) {
            drawSoftkey(canvas)
        } else {
            drawMainKey(canvas)
        }
    }

    private fun drawMainKey(canvas: Canvas) {
        if (buttonView.width <= 0 || buttonView.height <= 0) {
            return
        }

        val keyState = mainKeyState
        val buttonScale = if (designButtonWidth > 0f) {
            buttonView.width.toFloat() / designButtonWidth
        } else {
            1f
        }
        val inset = buttonScale
        val cornerRadius = 3f * buttonScale
        mainKeyStrokePaint.strokeWidth = 2f * buttonScale
        mainKeyRect.set(
            buttonView.left.toFloat() + inset,
            buttonView.top.toFloat() + inset,
            buttonView.right.toFloat() - inset,
            buttonView.bottom.toFloat() - inset,
        )

        val fillColor = when (keyState.styleRole) {
            KeypadSceneContract.STYLE_SHIFT_F -> if (isPressed) fHoverColor else fAccentColor
            KeypadSceneContract.STYLE_SHIFT_G -> if (isPressed) gHoverColor else gAccentColor
            KeypadSceneContract.STYLE_SHIFT_FG -> if (isPressed) fgHoverColor else fgAccentColor
            KeypadSceneContract.STYLE_ALPHA -> if (isPressed) alphaHoverColor else alphaAccentColor
            else -> if (isPressed) mainKeyPressedColor else mainKeyFillColor
        }

        mainKeyFillPaint.color = fillColor
        mainKeyStrokePaint.color = mainKeyStrokeColor

        canvas.drawRoundRect(mainKeyRect, cornerRadius, cornerRadius, mainKeyFillPaint)
        canvas.drawRoundRect(mainKeyRect, cornerRadius, cornerRadius, mainKeyStrokePaint)
        drawSurfaceHighlight(canvas, mainKeyRect, buttonScale, darkSurface = true)
    }

    private fun drawSoftkey(canvas: Canvas) {
        val keyState = softkeyState
        val reverseVideo = keyState.hasSceneFlag(KeypadSceneContract.SCENE_FLAG_REVERSE_VIDEO)
        val showText = keyState.hasSceneFlag(KeypadSceneContract.SCENE_FLAG_SHOW_TEXT) &&
            keyState.auxLabel.isNotBlank()
        val showValue = keyState.hasSceneFlag(KeypadSceneContract.SCENE_FLAG_SHOW_VALUE) &&
            keyState.showValue != KeypadKeySnapshot.NO_VALUE
        val showOverlay = keyState.hasSceneFlag(KeypadSceneContract.SCENE_FLAG_SHOW_CB) &&
            keyState.overlayState >= 0

        softkeyRect.set(2f, 2f, width - 2f, height - 2f)

        val fillColor = when {
            reverseVideo && isPressed -> softkeyReversePressedColor
            reverseVideo -> softkeyReverseColor
            isPressed -> mainKeyPressedColor
            else -> mainKeyFillColor
        }
        val strokeColor = mainKeyStrokeColor
        val decorColor = if (reverseVideo) softkeyLightTextColor else defaultPrimaryColor
        val primaryTextColor = if (reverseVideo) softkeyLightTextColor else defaultPrimaryColor
        val metaTextColor = if (reverseVideo) softkeyMetaLightColor else letterColor
        val valueTextColor = softkeyValueLightColor

        softkeyFillPaint.color = fillColor
        softkeyStrokePaint.color = strokeColor
        softkeyDecorPaint.color = decorColor
        softkeyDotPaint.color = decorColor

        canvas.drawRoundRect(softkeyRect, 8f, 8f, softkeyFillPaint)
        canvas.drawRoundRect(softkeyRect, 8f, 8f, softkeyStrokePaint)
        drawSurfaceHighlight(canvas, softkeyRect, width / SMALL_KEY_BUTTON_WIDTH, darkSurface = true)
        if (keyState.hasSceneFlag(KeypadSceneContract.SCENE_FLAG_DOTTED_ROW)) {
            drawSoftkeyDots(canvas, decorColor)
        }
        if (keyState.hasSceneFlag(KeypadSceneContract.SCENE_FLAG_PREVIEW_TARGET)) {
            softkeyDecorPaint.color = softkeyPreviewColor
            canvas.drawLine(
                softkeyRect.left + 10f,
                softkeyRect.bottom - 4f,
                softkeyRect.right - 10f,
                softkeyRect.bottom - 4f,
                softkeyDecorPaint,
            )
            softkeyDecorPaint.color = decorColor
        }

        if (showValue) {
            val valueText = formatSoftkeyValue(keyState.showValue)
            if (valueText.isNotBlank()) {
                drawFittedText(
                    canvas = canvas,
                    text = valueText,
                    paint = softkeyValuePaint,
                    typeface = fontSet.numeric ?: fontSet.tiny ?: fontSet.standard,
                    baseSize = height * 0.18f,
                    maxWidth = softkeyRect.width() * 0.34f,
                    x = softkeyRect.right - 7f,
                    anchorY = softkeyRect.top + 6f,
                    color = valueTextColor,
                    align = Paint.Align.RIGHT,
                    verticalAnchor = TEXT_ANCHOR_TOP,
                )
            }
        }

        if (showOverlay) {
            drawSoftkeyOverlay(
                canvas = canvas,
                overlayState = keyState.overlayState,
                centerX = softkeyRect.right - 10f,
                centerY = softkeyRect.bottom - 12f,
                color = decorColor,
            )
        }

        if (keyState.primaryLabel.isNotBlank()) {
            val primaryCenterY = if (showText) softkeyRect.centerY() - 6f else softkeyRect.centerY()
            val reservedRight = when {
                showOverlay -> 16f
                else -> 8f
            }
            drawFittedText(
                canvas = canvas,
                text = keyState.primaryLabel,
                paint = softkeyTextPaint,
                typeface = fontSet.standard,
                baseSize = height * 0.22f,
                maxWidth = softkeyRect.width() - reservedRight - 8f,
                x = softkeyRect.centerX(),
                anchorY = primaryCenterY,
                color = primaryTextColor,
            )
        }

        if (showText) {
            drawFittedText(
                canvas = canvas,
                text = keyState.auxLabel,
                paint = softkeyAuxPaint,
                typeface = fontSet.tiny ?: fontSet.standard,
                baseSize = height * 0.16f,
                maxWidth = softkeyRect.width() - 12f,
                x = softkeyRect.centerX(),
                anchorY = softkeyRect.bottom - 11f,
                color = metaTextColor,
                verticalAnchor = TEXT_ANCHOR_BOTTOM,
            )
        }

        if (keyState.hasSceneFlag(KeypadSceneContract.SCENE_FLAG_STRIKE_THROUGH)) {
            canvas.drawLine(
                softkeyRect.left + 8f,
                softkeyRect.centerY(),
                softkeyRect.right - 8f,
                softkeyRect.centerY(),
                softkeyDecorPaint,
            )
        }
        if (keyState.hasSceneFlag(KeypadSceneContract.SCENE_FLAG_STRIKE_OUT)) {
            canvas.drawLine(
                softkeyRect.left + 7f,
                softkeyRect.top + 10f,
                softkeyRect.right - 7f,
                softkeyRect.bottom - 10f,
                softkeyDecorPaint,
            )
        }
    }

    private fun drawSurfaceHighlight(
        canvas: Canvas,
        rect: RectF,
        scale: Float,
        darkSurface: Boolean,
    ) {
        val resolvedScale = scale.coerceAtLeast(0.8f)
        surfaceHighlightPaint.strokeWidth = 1.35f * resolvedScale
        surfaceHighlightPaint.color = if (darkSurface) {
            surfaceHighlightColor
        } else {
            lightSurfaceHighlightColor
        }
        val inset = 5f * resolvedScale
        val y = rect.top + 3.2f * resolvedScale
        canvas.drawLine(
            rect.left + inset,
            y,
            rect.right - inset,
            y,
            surfaceHighlightPaint,
        )
    }

    private fun drawSoftkeyDots(canvas: Canvas, color: Int) {
        softkeyDotPaint.color = color
        val spacing = 8f
        val startX = softkeyRect.centerX() - spacing
        val y = softkeyRect.top + 4f
        for (index in 0..2) {
            canvas.drawCircle(startX + index * spacing, y, 1.7f, softkeyDotPaint)
        }
    }

    private fun drawSoftkeyOverlay(
        canvas: Canvas,
        overlayState: Int,
        centerX: Float,
        centerY: Float,
        color: Int,
    ) {
        val size = 7f
        softkeyDecorPaint.color = color

        when (overlayState) {
            KeypadSceneContract.OVERLAY_RB_FALSE -> {
                canvas.drawCircle(centerX, centerY, size * 0.7f, softkeyDecorPaint)
            }
            KeypadSceneContract.OVERLAY_RB_TRUE -> {
                canvas.drawCircle(centerX, centerY, size * 0.7f, softkeyDecorPaint)
                canvas.drawCircle(centerX, centerY, size * 0.28f, softkeyDotPaint.apply { this.color = color })
            }
            KeypadSceneContract.OVERLAY_CB_FALSE -> {
                canvas.drawRect(centerX - size * 0.7f, centerY - size * 0.7f, centerX + size * 0.7f, centerY + size * 0.7f, softkeyDecorPaint)
            }
            KeypadSceneContract.OVERLAY_CB_TRUE -> {
                canvas.drawRect(centerX - size * 0.7f, centerY - size * 0.7f, centerX + size * 0.7f, centerY + size * 0.7f, softkeyDecorPaint)
                canvas.drawLine(centerX - 3f, centerY, centerX - 1f, centerY + 3f, softkeyDecorPaint)
                canvas.drawLine(centerX - 1f, centerY + 3f, centerX + 4f, centerY - 3f, softkeyDecorPaint)
            }
            KeypadSceneContract.OVERLAY_MB_FALSE,
            KeypadSceneContract.OVERLAY_MB_TRUE -> {
                val rect = RectF(centerX - 6.5f, centerY - 5.2f, centerX + 6.5f, centerY + 5.2f)
                canvas.drawRoundRect(rect, 3f, 3f, softkeyDecorPaint)
                drawFittedText(
                    canvas = canvas,
                    text = "M",
                    paint = softkeyAuxPaint,
                    typeface = fontSet.tiny ?: fontSet.standard,
                    baseSize = height * 0.12f,
                    maxWidth = 9f,
                    x = centerX,
                    anchorY = centerY - 0.5f,
                    color = color,
                )
                if (overlayState == KeypadSceneContract.OVERLAY_MB_TRUE) {
                    canvas.drawLine(centerX + 1f, centerY + 4f, centerX + 6f, centerY + 4f, softkeyDecorPaint)
                }
            }
        }
    }

    private fun drawFittedText(
        canvas: Canvas,
        text: String,
        paint: Paint,
        typeface: Typeface?,
        baseSize: Float,
        maxWidth: Float,
        x: Float,
        anchorY: Float,
        color: Int,
        align: Paint.Align = Paint.Align.CENTER,
        verticalAnchor: Int = TEXT_ANCHOR_CENTER,
    ) {
        if (text.isBlank()) {
            return
        }

        paint.typeface = typeface
        paint.textAlign = align
        paint.color = color
        paint.textSize = baseSize

        val measured = paint.measureText(text)
        if (measured > maxWidth && measured > 0f) {
            paint.textSize = (baseSize * (maxWidth / measured)).coerceAtLeast(baseSize * 0.58f)
        }

        val metrics = paint.fontMetrics
        val baseline = when (verticalAnchor) {
            TEXT_ANCHOR_TOP -> anchorY - metrics.ascent
            TEXT_ANCHOR_BOTTOM -> anchorY - metrics.descent
            else -> anchorY - ((metrics.ascent + metrics.descent) / 2f)
        }

        canvas.drawText(text, x, baseline, paint)
    }

    private fun formatSoftkeyValue(showValue: Int): String {
        return when (showValue) {
            KeypadKeySnapshot.NO_VALUE,
            -127 -> ""
            else -> {
                val prefix = if (showValue < 0) "-" else ""
                prefix + abs(showValue).toString()
            }
        }
    }

    private fun buildSoftkeyContentDescription(keyState: KeypadKeySnapshot): String {
        return buildString {
            append(keyState.primaryLabel)
            if (keyState.hasSceneFlag(KeypadSceneContract.SCENE_FLAG_SHOW_TEXT) && keyState.auxLabel.isNotBlank()) {
                append(", ")
                append(keyState.auxLabel)
            }
            val valueText = formatSoftkeyValue(keyState.showValue)
            if (valueText.isNotBlank()) {
                append(", ")
                append(valueText)
            }
        }
    }
}
