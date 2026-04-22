package com.example.r47

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout

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
        private val gAccentColor = Color.parseColor("#7EB6BA")
        private val letterColor = Color.parseColor("#808080")
        private val longPressColor = Color.parseColor("#D4D8DD")
    }

    private val buttonView = View(context)
    val primaryLabel = TextView(context)
    val fLabel = TextView(context)
    val gLabel = TextView(context)
    val alphaLabel = TextView(context)
    val letterLabel = TextView(context)
    
    var keyCode: Int = 0
    private var isFnKey: Boolean = false
    private var baseMainSize = 0f
    private var lastLayoutClass: Int? = null
    private var usesLetterSpacer = true
    private var keepLetterSpacerInvisible = false
    private var fontSet = KeypadFontSet(null, null, null)

    init {
        // Critical: Allow drawing outside bounds
        clipChildren = false
        clipToPadding = false
        
        // Letter label (Right side of the view, bottom aligned with button)
        letterLabel.id = View.generateViewId()
        letterLabel.setTextColor(letterColor)
        letterLabel.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        letterLabel.includeFontPadding = false
        letterLabel.maxLines = 1
        val letterParams = LayoutParams(0, 0)
        letterParams.endToEnd = LayoutParams.PARENT_ID
        letterParams.bottomToBottom = LayoutParams.PARENT_ID
        letterParams.matchConstraintPercentWidth = 0.20f
        letterParams.matchConstraintPercentHeight = 0.65f 
        addView(letterLabel, letterParams)

        // Button background view (Left side)
        buttonView.id = View.generateViewId()
        val btnParams = LayoutParams(0, 0)
        btnParams.bottomToBottom = LayoutParams.PARENT_ID
        btnParams.startToStart = LayoutParams.PARENT_ID
        btnParams.endToStart = letterLabel.id
        btnParams.matchConstraintPercentHeight = 0.65f 
        buttonView.setBackgroundResource(R.drawable.calculator_key_background)
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
        fLabel.gravity = Gravity.START or Gravity.BOTTOM
        fLabel.includeFontPadding = false
        fLabel.maxLines = 1
        val fParams = LayoutParams(LayoutParams.WRAP_CONTENT, 0)
        fParams.bottomToTop = buttonView.id
        fParams.topToTop = LayoutParams.PARENT_ID
        fParams.startToStart = LayoutParams.PARENT_ID
        fParams.endToStart = gLabel.id
        fParams.horizontalChainStyle = LayoutParams.CHAIN_PACKED
        fParams.bottomMargin = 8 // Push up approx 2mm
        addView(fLabel, fParams)

        // G label (Above button)
        gLabel.id = View.generateViewId()
        gLabel.setTextColor(gAccentColor)
        gLabel.gravity = Gravity.END or Gravity.BOTTOM
        gLabel.includeFontPadding = false
        gLabel.maxLines = 1
        val gParams = LayoutParams(LayoutParams.WRAP_CONTENT, 0)
        gParams.bottomToTop = buttonView.id
        gParams.topToTop = LayoutParams.PARENT_ID
        gParams.startToEnd = fLabel.id
        gParams.endToEnd = LayoutParams.PARENT_ID
        gParams.bottomMargin = 8 // Push up approx 2mm
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
        val btnH = h * 0.65f
        
        var mainLabelFactor = 0.75f
        val smallSize = h * 0.30f 
        val letterSize = btnH * 0.42f
        
        // Specific sizing rules
        val isNumber = keyCode in 18..20 || keyCode in 23..25 || keyCode in 28..30 || keyCode == 34
        val isOperator = keyCode in 21..22 || keyCode in 26..27 || keyCode in 31..32 || keyCode in 35..37
        val isSpecificFunction = keyCode == 18 || keyCode == 33 || keyCode == 36 || keyCode == 15 || keyCode == 16 || keyCode == 10
        
        if (keyCode in 7..9 || keyCode == 14 || keyCode == 18 || keyCode == 36) {
            mainLabelFactor *= 0.85f 
        } else if (isNumber || isOperator) {
            mainLabelFactor *= 1.15f
        } else if (isSpecificFunction) {
            mainLabelFactor *= 0.85f
        } else if (keyCode in 1..6) {
            mainLabelFactor *= 0.95f
        } else if (keyCode == 13 || keyCode == 17 || keyCode == 23 || keyCode == 28) {
            mainLabelFactor *= 0.95f
        }
        
        baseMainSize = btnH * mainLabelFactor
        
        // Initial sync of font size (will be updated again in updateLabels)
        updateFontSize(fOn = false, gOn = false)
        fLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallSize)
        gLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallSize)
        letterLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, letterSize)
    }

    private fun updateFontSize(fOn: Boolean, gOn: Boolean) {
        var factor = if (fOn || gOn) 0.70f else 1.0f

        if (fOn && keyCode == 14) factor = 0.60f
        if (gOn && keyCode == 35) factor = 0.60f
        
        val size = baseMainSize * factor
        primaryLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, if (isFnKey) size * 0.8f else size)
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
    }

    private fun updateLayoutPositioning(layoutClass: Int) {
        if (lastLayoutClass == layoutClass) return
        lastLayoutClass = layoutClass

        val fParams = fLabel.layoutParams as LayoutParams
        val gParams = gLabel.layoutParams as LayoutParams
        resetLabelLayout()

        when (layoutClass) {
            KeypadSceneContract.LAYOUT_CLASS_STATIC_SINGLE -> {
                fParams.endToStart = LayoutParams.UNSET
                fParams.endToEnd = LayoutParams.PARENT_ID
                fParams.horizontalBias = 0.5f
                gLabel.visibility = View.GONE
            }
            KeypadSceneContract.LAYOUT_CLASS_PACKED,
            KeypadSceneContract.LAYOUT_CLASS_ALPHA,
            KeypadSceneContract.LAYOUT_CLASS_TAM -> {
                fParams.horizontalChainStyle = LayoutParams.CHAIN_PACKED
                fParams.marginEnd = 6
                fParams.horizontalBias = 0.5f
            }
            KeypadSceneContract.LAYOUT_CLASS_OFFSET -> {
                fParams.endToStart = LayoutParams.UNSET
                fParams.endToEnd = LayoutParams.UNSET
                fParams.horizontalBias = 0f
                fParams.marginStart = -12
                gParams.startToStart = LayoutParams.UNSET
                gParams.startToEnd = LayoutParams.UNSET
                gParams.horizontalBias = 1f
                gParams.marginEnd = -8
            }
            KeypadSceneContract.LAYOUT_CLASS_EDGE -> {
                fParams.endToStart = LayoutParams.UNSET
                fParams.endToEnd = LayoutParams.UNSET
                fParams.horizontalBias = 0f
                fParams.marginStart = 0
                gParams.startToStart = LayoutParams.UNSET
                gParams.startToEnd = LayoutParams.UNSET
                gParams.horizontalBias = 1f
            }
        }

        fLabel.layoutParams = fParams
        gLabel.layoutParams = gParams
    }

    internal fun setKey(code: Int, isFn: Boolean, fonts: KeypadFontSet) {
        this.keyCode = code
        this.isFnKey = isFn
        this.fontSet = fonts
        primaryLabel.typeface = fonts.standard
        fLabel.typeface = fonts.tiny ?: fonts.standard
        gLabel.typeface = fonts.tiny ?: fonts.standard
        letterLabel.typeface = fonts.tiny ?: fonts.standard
        
        if (isFn) {
            fLabel.visibility = View.GONE
            gLabel.visibility = View.GONE
            letterLabel.visibility = View.GONE
            primaryLabel.visibility = View.GONE
            buttonView.visibility = View.INVISIBLE
        } else {
            buttonView.visibility = View.VISIBLE
            fLabel.visibility = View.VISIBLE
            gLabel.visibility = View.VISIBLE
            letterLabel.visibility = View.VISIBLE
            primaryLabel.visibility = View.VISIBLE

            lastLayoutClass = null
            resetLabelLayout()

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

            buttonView.setBackgroundResource(R.drawable.calculator_key_background)
            primaryLabel.setTextColor(defaultPrimaryColor)
        }
    }

    private fun applyStyleRole(styleRole: Int) {
        when (styleRole) {
            KeypadSceneContract.STYLE_SHIFT_F -> {
                buttonView.setBackgroundResource(R.drawable.calculator_key_f_background)
                primaryLabel.setTextColor(defaultPrimaryDarkColor)
            }
            KeypadSceneContract.STYLE_SHIFT_G -> {
                buttonView.setBackgroundResource(R.drawable.calculator_key_g_background)
                primaryLabel.setTextColor(defaultPrimaryDarkColor)
            }
            KeypadSceneContract.STYLE_SHIFT_FG -> {
                buttonView.setBackgroundResource(R.drawable.calculator_key_fg_background)
                primaryLabel.setTextColor(defaultPrimaryDarkColor)
            }
            else -> {
                buttonView.setBackgroundResource(R.drawable.calculator_key_background)
                primaryLabel.setTextColor(defaultPrimaryColor)
            }
        }
    }

    private fun applyLabelRole(labelView: TextView, role: Int, defaultColor: Int) {
        val baseFlags = labelView.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
        labelView.paintFlags = when (role) {
            KeypadSceneContract.TEXT_ROLE_F_UNDERLINE,
            KeypadSceneContract.TEXT_ROLE_G_UNDERLINE -> baseFlags or Paint.UNDERLINE_TEXT_FLAG
            else -> baseFlags
        }
        labelView.typeface = when (role) {
            KeypadSceneContract.TEXT_ROLE_F,
            KeypadSceneContract.TEXT_ROLE_G,
            KeypadSceneContract.TEXT_ROLE_F_UNDERLINE,
            KeypadSceneContract.TEXT_ROLE_G_UNDERLINE,
            KeypadSceneContract.TEXT_ROLE_LETTER,
            KeypadSceneContract.TEXT_ROLE_LONGPRESS -> fontSet.tiny ?: fontSet.standard
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
        applyStyleRole(keyState.styleRole)
        primaryLabel.typeface = when (keyState.styleRole) {
            KeypadSceneContract.STYLE_NUMERIC -> fontSet.numeric ?: fontSet.standard
            else -> fontSet.standard
        }
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
        fLabel.visibility = if (keyState.fLabel.isBlank()) View.INVISIBLE else View.VISIBLE
        gLabel.visibility = if (keyState.gLabel.isBlank()) View.INVISIBLE else View.VISIBLE
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
        alpha = if (enabled || isFnKey) 1f else 0.45f
        buttonView.alpha = if (enabled) 1f else 0.45f
        primaryLabel.alpha = if (enabled) 1f else 0.6f
        fLabel.alpha = if (enabled) 1f else 0.6f
        gLabel.alpha = if (enabled) 1f else 0.6f
        letterLabel.alpha = if (enabled) 1f else 0.6f
    }

    internal fun updateLabels(snapshot: KeypadSnapshot) {
        val keyState = snapshot.keyStateFor(keyCode)
        applyEnabledState(keyState.isEnabled)

        if (isFnKey) {
            contentDescription = keyState.primaryLabel
        } else {
            primaryLabel.text = keyState.primaryLabel
            fLabel.text = keyState.fLabel
            gLabel.text = keyState.gLabel
            letterLabel.text = keyState.letterLabel
            updateFontSize(snapshot.shiftF, snapshot.shiftG || snapshot.alphaOn)
            updateLayoutPositioning(keyState.layoutClass)
            applySceneStyling(keyState)
            applyLabelVisibility(keyState)
            contentDescription = buildString {
                append(keyState.primaryLabel)
                if (keyState.fLabel.isNotBlank()) {
                    append(", f ")
                    append(keyState.fLabel)
                }
                if (keyState.gLabel.isNotBlank()) {
                    append(", g ")
                    append(keyState.gLabel)
                }
            }
        }
    }
    
    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        buttonView.isPressed = pressed
    }
}
