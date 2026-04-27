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

private data class MainKeySurfaceSpec(
    val cellWidth: Float,
    val buttonWidth: Float,
    val letterRatio: Float,
    val visualWidthBonus: Float,
)

private data class MainKeyStyleSpec(
    val fontSize: Float,
    val primaryTextColor: Int,
    val idleFillColor: Int,
    val pressedFillColor: Int,
)

class CalculatorKeyView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    companion object {
        private val defaultPrimaryColor = Color.WHITE
        private val defaultPrimaryDarkColor = Color.BLACK
        private val fAccentColor = Color.parseColor("#F2A33D")
        private val fHoverColor = Color.parseColor("#FFC24A")
        private val gAccentColor = Color.parseColor("#63ABD9")
        private val gHoverColor = Color.parseColor("#74C7F2")
        private val fgAccentColor = Color.parseColor("#E8B14A")
        private val fgHoverColor = Color.parseColor("#F1D28D")
        private val alphaAccentColor = Color.parseColor("#E36C50")
        private val alphaHoverColor = Color.parseColor("#FF8669")
        private val letterColor = Color.parseColor("#A5A5A5")
        private val fourthLabelColor = Color.rgb(223, 223, 223)
        private val longPressColor = Color.parseColor("#D4D8DD")
        private val mainKeyFillColor = Color.rgb(31, 31, 31)
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
        private val MAIN_KEY_BUTTON_HEIGHT_RATIO =
            R47MeasuredGeometry.ROW_HEIGHT / MAIN_KEY_VIEW_HEIGHT
        private val SMALL_KEY_CELL_WIDTH = R47MeasuredGeometry.STANDARD_PITCH
        private val SMALL_KEY_BUTTON_WIDTH = R47MeasuredGeometry.STANDARD_KEY_WIDTH
        private val LARGE_KEY_CELL_WIDTH = R47MeasuredGeometry.MATRIX_PITCH
        private val LARGE_KEY_BUTTON_WIDTH = R47MeasuredGeometry.MATRIX_KEY_WIDTH
        private val WIDE_KEY_BUTTON_WIDTH = R47MeasuredGeometry.ENTER_WIDTH
        private const val STANDARD_KEY_FONT_SIZE = 22f
        private const val NUMERIC_KEY_FONT_SIZE = 33f
        private const val SHIFT_KEY_FONT_SIZE = 27f
        private const val SHIFT_LABEL_FONT_SIZE = 18f
        private const val LETTER_LABEL_FONT_SIZE = 19f
        private const val PRIMARY_TEXT_HEIGHT_BOOST = 1.06f
        private const val PRIMARY_TEXT_WIDTH_COMPENSATION = 0.96f
        private const val NUMERIC_TEXT_WIDTH_COMPENSATION = 0.94f
        private const val FACEPLATE_GAP = 3f
        private const val FACEPLATE_LABEL_OFFSET = 25f
        private const val LETTER_X_OFFSET = 3f
        private const val LETTER_Y_OFFSET = 18f
        private const val LETTER_SHIFT_X_FRACTION = 0.2f
        private const val LETTER_SHIFT_Y_FRACTION = 0.25f
        private const val SMALL_KEY_VISUAL_WIDTH_BONUS = 2f
        private const val LARGE_KEY_VISUAL_WIDTH_BONUS = 2f
        private const val WIDE_KEY_VISUAL_WIDTH_BONUS = 2f
        private val SMALL_KEY_LETTER_RATIO =
            (SMALL_KEY_CELL_WIDTH - SMALL_KEY_BUTTON_WIDTH) / SMALL_KEY_CELL_WIDTH
        private val LARGE_KEY_LETTER_RATIO =
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
    private var buttonVisualWidthBonus = 0f
    private var drawKeySurfaces = true
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
        isSubpixelText = true
        isLinearText = false
        setHinting(Paint.HINTING_ON)
    }
    private val softkeyAuxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
        isLinearText = false
        setHinting(Paint.HINTING_ON)
    }
    private val softkeyValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        isSubpixelText = true
        isLinearText = false
        setHinting(Paint.HINTING_ON)
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
    private val surfaceShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
        letterLabel.setTextColor(fourthLabelColor)
        letterLabel.gravity = Gravity.START or Gravity.TOP
        letterLabel.includeFontPadding = false
        letterLabel.maxLines = 1
        configureTextRendering(letterLabel)
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
        configureTextRendering(primaryLabel)
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
        configureTextRendering(fLabel)
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
        configureTextRendering(gLabel)
        val gParams = LayoutParams(LayoutParams.WRAP_CONTENT, 0)
        gParams.topToTop = buttonView.id
        gParams.bottomToBottom = buttonView.id
        gParams.startToEnd = fLabel.id
        gParams.endToEnd = LayoutParams.PARENT_ID
        addView(gLabel, gParams)

        // Alpha label (NOT USED inside key)
        alphaLabel.id = View.generateViewId()
        alphaLabel.visibility = View.GONE
        configureTextRendering(alphaLabel)
        addView(alphaLabel)
        
        isClickable = false
        buttonView.isClickable = false
    }

    private fun configureTextRendering(labelView: TextView) {
        labelView.paintFlags = (labelView.paintFlags or Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG) and
            Paint.LINEAR_TEXT_FLAG.inv()
        labelView.paint.setAntiAlias(true)
        labelView.paint.setSubpixelText(true)
        labelView.paint.setLinearText(false)
        labelView.paint.setHinting(Paint.HINTING_ON)
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
        val primarySize = mainKeyStyleSpec(mainKeyState.styleRole).fontSize * cellScale

        primaryLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, primarySize)
        primaryLabel.textScaleX = 1f
        fLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, SHIFT_LABEL_FONT_SIZE * cellScale)
        gLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, SHIFT_LABEL_FONT_SIZE * cellScale)
        letterLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, LETTER_LABEL_FONT_SIZE * cellScale)
        primaryLabel.translationX = 0f
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
        val buttonWidth = buttonView.width.toFloat()
        if (buttonWidth <= 0f || buttonView.height <= 0) {
            return
        }

        val buttonScale = if (designButtonWidth > 0f) buttonWidth / designButtonWidth else 1f
        updateMainKeySurfaceRect(mainKeyRect, buttonScale)
        val buttonCenterX = mainKeyRect.centerX()
        val rawButtonCenterX = buttonView.left + buttonWidth / 2f
        primaryLabel.translationX = buttonCenterX - rawButtonCenterX
        val fWidth = maxOf(fLabel.width, fLabel.measuredWidth).toFloat()
        val gWidth = maxOf(gLabel.width, gLabel.measuredWidth).toFloat()
        val hasFLabel = fLabel.visibility == View.VISIBLE && fLabel.text.isNotBlank()
        val hasGLabel = hasFLabel && gLabel.visibility == View.VISIBLE && gLabel.text.isNotBlank()
        val groupWidth = when {
            hasGLabel -> fWidth + gap + gWidth
            hasFLabel -> fWidth
            else -> 0f
        }
        val groupLeft = buttonCenterX - groupWidth / 2f

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

        val letterText = letterLabel.text?.toString().orEmpty()
        val letterTextWidth = if (letterText.isNotBlank()) {
            letterLabel.paint.measureText(letterText)
        } else {
            0f
        }
        val letterMetrics = letterLabel.paint.fontMetrics
        val letterTextHeight = letterMetrics.bottom - letterMetrics.top
        val letterShiftX = letterTextWidth * LETTER_SHIFT_X_FRACTION
        val letterShiftY = letterTextHeight * LETTER_SHIFT_Y_FRACTION
        val letterLeft = mainKeyRect.right + LETTER_X_OFFSET * cellScale + letterShiftX
        val letterTop = LETTER_Y_OFFSET * cellScale + letterShiftY
        letterLabel.translationX = letterLeft - letterLabel.left.toFloat()
        letterLabel.translationY = letterTop - letterLabel.top.toFloat()
    }

    private fun updateMainKeySurfaceRect(targetRect: RectF, buttonScale: Float) {
        val inset = buttonScale
        val widthBonus = buttonVisualWidthBonus * buttonScale
        val halfWidthBonus = widthBonus * 0.5f
        targetRect.set(
            (buttonView.left.toFloat() + inset - halfWidthBonus).coerceAtLeast(inset),
            buttonView.top.toFloat() + inset,
            (buttonView.right.toFloat() - inset + halfWidthBonus).coerceAtMost(width.toFloat() - inset),
            buttonView.bottom.toFloat() - inset,
        )
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

    internal fun setKey(slot: KeypadSlotSpec, fonts: KeypadFontSet) {
        this.keyCode = slot.code
        this.isFnKey = slot.isFunctionKey
        this.fontSet = fonts
        primaryLabel.typeface = fonts.standard
        fLabel.typeface = fonts.standard
        gLabel.typeface = fonts.standard
        letterLabel.typeface = fonts.standard
        
        if (slot.isFunctionKey) {
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
            configureMainKeySurface(slot.family)
            usesLetterSpacer = slot.usesLetterSpacer
            keepLetterSpacerInvisible = slot.keepLetterSpacerInvisible

            if (!slot.usesLetterSpacer) {
                letterLabel.visibility = View.GONE
                val lp = buttonView.layoutParams as LayoutParams
                lp.endToStart = LayoutParams.UNSET
                lp.endToEnd = LayoutParams.PARENT_ID
                buttonView.layoutParams = lp
            } else {
                val lp = buttonView.layoutParams as LayoutParams
                lp.endToStart = letterLabel.id
                lp.endToEnd = LayoutParams.UNSET
                buttonView.layoutParams = lp
                letterLabel.visibility = if (keepLetterSpacerInvisible) View.INVISIBLE else View.VISIBLE
            }

            primaryLabel.setTextColor(defaultPrimaryColor)
            mainKeyState = KeypadKeySnapshot.EMPTY
        }
    }

    internal fun setDrawKeySurfaces(draw: Boolean) {
        if (drawKeySurfaces == draw) {
            return
        }
        drawKeySurfaces = draw
        invalidate()
    }

    private fun mainKeyStyleSpec(styleRole: Int): MainKeyStyleSpec {
        return when (styleRole) {
            KeypadSceneContract.STYLE_SHIFT_F -> MainKeyStyleSpec(
                fontSize = SHIFT_KEY_FONT_SIZE,
                primaryTextColor = defaultPrimaryDarkColor,
                idleFillColor = fAccentColor,
                pressedFillColor = fHoverColor,
            )

            KeypadSceneContract.STYLE_SHIFT_G -> MainKeyStyleSpec(
                fontSize = SHIFT_KEY_FONT_SIZE,
                primaryTextColor = defaultPrimaryDarkColor,
                idleFillColor = gAccentColor,
                pressedFillColor = gHoverColor,
            )

            KeypadSceneContract.STYLE_SHIFT_FG -> MainKeyStyleSpec(
                fontSize = SHIFT_KEY_FONT_SIZE,
                primaryTextColor = defaultPrimaryDarkColor,
                idleFillColor = fgAccentColor,
                pressedFillColor = fgHoverColor,
            )

            KeypadSceneContract.STYLE_ALPHA -> MainKeyStyleSpec(
                fontSize = STANDARD_KEY_FONT_SIZE,
                primaryTextColor = defaultPrimaryDarkColor,
                idleFillColor = alphaAccentColor,
                pressedFillColor = alphaHoverColor,
            )

            KeypadSceneContract.STYLE_NUMERIC -> MainKeyStyleSpec(
                fontSize = NUMERIC_KEY_FONT_SIZE,
                primaryTextColor = defaultPrimaryColor,
                idleFillColor = mainKeyFillColor,
                pressedFillColor = mainKeyPressedColor,
            )

            else -> MainKeyStyleSpec(
                fontSize = STANDARD_KEY_FONT_SIZE,
                primaryTextColor = defaultPrimaryColor,
                idleFillColor = mainKeyFillColor,
                pressedFillColor = mainKeyPressedColor,
            )
        }
    }

    private fun configureMainKeySurface(family: KeypadKeyFamily) {
        val surfaceSpec = when (family) {
            KeypadKeyFamily.STANDARD -> MainKeySurfaceSpec(
                cellWidth = SMALL_KEY_CELL_WIDTH,
                buttonWidth = SMALL_KEY_BUTTON_WIDTH,
                letterRatio = SMALL_KEY_LETTER_RATIO,
                visualWidthBonus = SMALL_KEY_VISUAL_WIDTH_BONUS,
            )

            KeypadKeyFamily.ENTER -> MainKeySurfaceSpec(
                cellWidth = WIDE_KEY_BUTTON_WIDTH,
                buttonWidth = WIDE_KEY_BUTTON_WIDTH,
                letterRatio = 0f,
                visualWidthBonus = WIDE_KEY_VISUAL_WIDTH_BONUS,
            )

            KeypadKeyFamily.NUMERIC_MATRIX -> MainKeySurfaceSpec(
                cellWidth = LARGE_KEY_CELL_WIDTH,
                buttonWidth = LARGE_KEY_BUTTON_WIDTH,
                letterRatio = LARGE_KEY_LETTER_RATIO,
                visualWidthBonus = LARGE_KEY_VISUAL_WIDTH_BONUS,
            )

            KeypadKeyFamily.BASE_OPERATOR -> MainKeySurfaceSpec(
                cellWidth = SMALL_KEY_BUTTON_WIDTH,
                buttonWidth = SMALL_KEY_BUTTON_WIDTH,
                letterRatio = 0f,
                visualWidthBonus = SMALL_KEY_VISUAL_WIDTH_BONUS,
            )

            KeypadKeyFamily.SOFTKEY -> error("Softkeys use the dedicated function-key drawing path")
        }

        designCellWidth = surfaceSpec.cellWidth
        designButtonWidth = surfaceSpec.buttonWidth
        buttonVisualWidthBonus = surfaceSpec.visualWidthBonus

        val buttonParams = buttonView.layoutParams as LayoutParams
        val letterParams = letterLabel.layoutParams as LayoutParams
        letterParams.topToTop = buttonView.id
        letterParams.bottomToBottom = buttonView.id
        letterParams.matchConstraintPercentWidth = surfaceSpec.letterRatio
        letterParams.matchConstraintPercentHeight = MAIN_KEY_BUTTON_HEIGHT_RATIO

        if (surfaceSpec.letterRatio > 0f) {
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

    private fun primaryTypefaceFor(): Typeface? {
        return fontSet.standard
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
        labelView.typeface = when (labelView) {
            primaryLabel -> primaryTypefaceFor()
            fLabel, gLabel, letterLabel -> fontSet.standard
            else -> fontSet.standard
        }
        labelView.setTextColor(
            when (role) {
                KeypadSceneContract.TEXT_ROLE_LONGPRESS -> longPressColor
                else -> defaultColor
            }
        )
    }

    private fun applySceneStyling(keyState: KeypadKeySnapshot) {
        primaryLabel.setTextColor(mainKeyStyleSpec(keyState.styleRole).primaryTextColor)
        primaryLabel.typeface = primaryTypefaceFor()
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
            fourthLabelColor,
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
        if (!drawKeySurfaces || buttonView.width <= 0 || buttonView.height <= 0) {
            return
        }

        val keyState = mainKeyState
        val buttonScale = if (designButtonWidth > 0f) {
            buttonView.width.toFloat() / designButtonWidth
        } else {
            1f
        }
        val cornerRadius = 3f * buttonScale
        mainKeyStrokePaint.strokeWidth = 2f * buttonScale
        updateMainKeySurfaceRect(mainKeyRect, buttonScale)

        val styleSpec = mainKeyStyleSpec(keyState.styleRole)
        val fillColor = if (isPressed) styleSpec.pressedFillColor else styleSpec.idleFillColor

        mainKeyFillPaint.color = fillColor
        mainKeyStrokePaint.color = mainKeyStrokeColor

        canvas.drawRoundRect(mainKeyRect, cornerRadius, cornerRadius, mainKeyFillPaint)
        canvas.drawRoundRect(mainKeyRect, cornerRadius, cornerRadius, mainKeyStrokePaint)
        drawSurfaceHighlight(canvas, mainKeyRect, buttonScale, cornerRadius, darkSurface = true)
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

        val cornerRadius = 8f
        if (drawKeySurfaces) {
            canvas.drawRoundRect(softkeyRect, cornerRadius, cornerRadius, softkeyFillPaint)
            canvas.drawRoundRect(softkeyRect, cornerRadius, cornerRadius, softkeyStrokePaint)
            drawSurfaceHighlight(
                canvas,
                softkeyRect,
                width / SMALL_KEY_BUTTON_WIDTH,
                cornerRadius,
                darkSurface = true,
            )
        }
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
            val softkeyPrimaryScale = softkeyRect.width() / SMALL_KEY_BUTTON_WIDTH
            drawFittedText(
                canvas = canvas,
                text = keyState.primaryLabel,
                paint = softkeyTextPaint,
                typeface = fontSet.standard,
                baseSize = STANDARD_KEY_FONT_SIZE * softkeyPrimaryScale,
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
        cornerRadius: Float,
        darkSurface: Boolean,
    ) {
        val resolvedScale = scale.coerceAtLeast(0.8f)
        val strokeWidth = 1.35f * resolvedScale
        val inset = 1.2f * resolvedScale
        val left = rect.left + inset
        val top = rect.top + inset
        val right = rect.right - inset
        val bottom = rect.bottom - inset
        if (right <= left || bottom <= top) {
            return
        }
        val innerRadius = (cornerRadius - inset).coerceAtLeast(0f)
        surfaceHighlightPaint.strokeWidth = strokeWidth
        surfaceHighlightPaint.color = if (darkSurface) {
            surfaceHighlightColor
        } else {
            lightSurfaceHighlightColor
        }
        surfaceShadowPaint.strokeWidth = strokeWidth
        surfaceShadowPaint.color = Color.BLACK

        if (innerRadius <= 0f) {
            canvas.drawLine(left, top, right, top, surfaceHighlightPaint)
            canvas.drawLine(left, top, left, bottom, surfaceShadowPaint)
            canvas.drawLine(left, bottom, right, bottom, surfaceShadowPaint)
            canvas.drawLine(right, top, right, bottom, surfaceShadowPaint)
            return
        }

        val topLeftOval = RectF(left, top, left + 2f * innerRadius, top + 2f * innerRadius)
        val topRightOval = RectF(right - 2f * innerRadius, top, right, top + 2f * innerRadius)
        val bottomLeftOval = RectF(left, bottom - 2f * innerRadius, left + 2f * innerRadius, bottom)
        val bottomRightOval = RectF(right - 2f * innerRadius, bottom - 2f * innerRadius, right, bottom)

        canvas.drawArc(topLeftOval, 180f, 90f, false, surfaceHighlightPaint)
        canvas.drawLine(left + innerRadius, top, right - innerRadius, top, surfaceHighlightPaint)
        canvas.drawArc(topRightOval, 270f, 90f, false, surfaceHighlightPaint)

        canvas.drawLine(left, top + innerRadius, left, bottom - innerRadius, surfaceShadowPaint)
        canvas.drawArc(bottomLeftOval, 90f, 90f, false, surfaceShadowPaint)
        canvas.drawLine(left + innerRadius, bottom, right - innerRadius, bottom, surfaceShadowPaint)
        canvas.drawArc(bottomRightOval, 0f, 90f, false, surfaceShadowPaint)
        canvas.drawLine(right, top + innerRadius, right, bottom - innerRadius, surfaceShadowPaint)
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
