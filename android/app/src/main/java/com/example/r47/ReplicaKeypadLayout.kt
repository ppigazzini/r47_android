package com.example.r47

import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View

internal object ReplicaKeypadLayout {
    private const val CHROME_MODE_TEXTURE = "r47_texture"
    private const val SOFTKEY_START_X = 45f
    private const val SOFTKEY_START_Y = 376f
    private const val SOFTKEY_STEP_X = 78f
    private const val SOFTKEY_WIDTH = 47f
    private const val SOFTKEY_HEIGHT = 49f
    private const val MAIN_ROW_STEP_Y = 74f
    private const val LARGE_ROW_STEP_Y = 75f
    private const val FIRST_SMALL_ROW_Y = SOFTKEY_START_Y + MAIN_ROW_STEP_Y
    private const val SECOND_SMALL_ROW_Y = FIRST_SMALL_ROW_Y + MAIN_ROW_STEP_Y
    private const val ENTER_ROW_Y = SECOND_SMALL_ROW_Y + MAIN_ROW_STEP_Y
    private const val FIRST_LARGE_ROW_Y = ENTER_ROW_Y + LARGE_ROW_STEP_Y
    private const val SECOND_LARGE_ROW_Y = FIRST_LARGE_ROW_Y + LARGE_ROW_STEP_Y
    private const val THIRD_LARGE_ROW_Y = SECOND_LARGE_ROW_Y + LARGE_ROW_STEP_Y
    private const val FOURTH_LARGE_ROW_Y = THIRD_LARGE_ROW_Y + LARGE_ROW_STEP_Y

    private const val SMALL_ROW_START_X = 45f
    private const val SMALL_ROW_CELL_WIDTH = 78f
    private const val SMALL_ROW_KEY_HEIGHT = 68f

    private const val LARGE_ROW_START_X = 45f
    private const val LARGE_ROW_SECOND_X = 141f
    private const val LARGE_ROW_THIRD_X = 236f
    private const val LARGE_ROW_FOURTH_X = 331f
    private const val LARGE_ROW_FIFTH_X = 426f
    private const val LARGE_ROW_FIRST_WIDTH = 47f
    private const val LARGE_ROW_CELL_WIDTH = 95f

    fun rebuild(
        activity: MainActivity,
        overlay: ReplicaOverlay,
        chromeMode: String,
        performHapticClick: () -> Unit,
        dispatchKey: (Int) -> Unit,
    ) {
        overlay.removeAllViews()
        if (chromeMode == CHROME_MODE_TEXTURE) {
            addClassicKeypad(activity, overlay, performHapticClick, dispatchKey)
        } else {
            addDynamicKeypad(activity, overlay, performHapticClick, dispatchKey)
        }
    }

    fun updateDynamicKeys(
        overlay: ReplicaOverlay,
        snapshot: KeypadSnapshot,
    ) {
        for (index in 0 until overlay.childCount) {
            val child = overlay.getChildAt(index)
            if (child is CalculatorKeyView) {
                child.updateLabels(snapshot)
            }
        }
    }

    private fun addClassicKeypad(
        activity: MainActivity,
        overlay: ReplicaOverlay,
        performHapticClick: () -> Unit,
        dispatchKey: (Int) -> Unit,
    ) {
        val xLeft = 22.5f
        val yTop = 339.5f
        val deltaY = 82f
        val totalGridWidth = 492f
        val rowSpecs = arrayOf(
            intArrayOf(38, 6, 6),
            intArrayOf(1, 6, 6),
            intArrayOf(7, 6, 6),
            intArrayOf(13, 5, 6),
            intArrayOf(18, 5, 5),
            intArrayOf(23, 5, 5),
            intArrayOf(28, 5, 5),
            intArrayOf(33, 5, 5),
        )

        for (row in 0 until 8) {
            val spec = rowSpecs[row]
            val codeStart = spec[0]
            val count = spec[1]
            val gridColumns = spec[2]
            val deltaX = totalGridWidth / gridColumns
            var y = yTop + (row * deltaY)
            var keyHeight = deltaY - 10f

            when (row) {
                0 -> {
                    y += 27f
                    keyHeight += 5f
                }
                1 -> y += 22f
                2 -> y += 12f
                3 -> y += 10f
                4 -> {
                    y += 10f
                    keyHeight -= 10f
                }
                7 -> y -= 10f
            }

            for (column in 0 until count) {
                val code = codeStart + column
                var actualX = xLeft + (column * deltaX)
                var actualWidth = deltaX * 0.90f

                if (row < 3) {
                    val shift = 8f
                    when (column) {
                        0 -> actualWidth += shift
                        1 -> actualX += shift
                        2 -> {
                            actualX += shift
                            actualWidth -= shift
                        }
                    }
                }

                if (row == 3) {
                    if (column == 0) {
                        actualWidth = deltaX * 2f * 0.95f
                    } else {
                        actualX = xLeft + ((column + 1) * deltaX)
                    }
                }

                if (row >= 4) {
                    when (column) {
                        0 -> actualWidth = deltaX * 0.95f
                        1 -> {
                            actualX = xLeft + deltaX * 0.95f
                            actualWidth = deltaX * 1.05f
                        }
                        2, 3 -> actualWidth = deltaX
                    }
                }

                val touchZone = View(activity).apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                    background = null
                    setOnTouchListener(
                        createTouchListener(
                            code = code,
                            performHapticClick = performHapticClick,
                            dispatchKey = dispatchKey,
                        )
                    )
                }

                overlay.addReplicaView(touchZone, actualX, y, actualWidth, keyHeight)
            }
        }
    }

    private fun addDynamicKeypad(
        activity: MainActivity,
        overlay: ReplicaOverlay,
        performHapticClick: () -> Unit,
        dispatchKey: (Int) -> Unit,
    ) {
        val fonts = KeypadFontSet(
            standard = loadTypeface(activity, "fonts/C47__StandardFont.ttf"),
            numeric = loadTypeface(activity, "fonts/C47__NumericFont.ttf"),
            tiny = loadTypeface(activity, "fonts/C47__TinyFont.ttf"),
        )
        val initialSnapshot = activity.currentKeypadSnapshot().takeIf {
            it.sceneContractVersion > 0
        }

        addKey(
            activity = activity,
            overlay = overlay,
            fonts = fonts,
            initialSnapshot = initialSnapshot,
            code = 38,
            isFunctionKey = true,
            x = SOFTKEY_START_X,
            y = SOFTKEY_START_Y,
            width = SOFTKEY_WIDTH,
            height = SOFTKEY_HEIGHT,
            performHapticClick = performHapticClick,
            dispatchKey = dispatchKey,
        )
        addKey(activity, overlay, fonts, initialSnapshot, 39, true, SOFTKEY_START_X + SOFTKEY_STEP_X, SOFTKEY_START_Y, SOFTKEY_WIDTH, SOFTKEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 40, true, SOFTKEY_START_X + SOFTKEY_STEP_X * 2f, SOFTKEY_START_Y, SOFTKEY_WIDTH, SOFTKEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 41, true, SOFTKEY_START_X + SOFTKEY_STEP_X * 3f, SOFTKEY_START_Y, SOFTKEY_WIDTH, SOFTKEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 42, true, SOFTKEY_START_X + SOFTKEY_STEP_X * 4f, SOFTKEY_START_Y, SOFTKEY_WIDTH, SOFTKEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 43, true, SOFTKEY_START_X + SOFTKEY_STEP_X * 5f, SOFTKEY_START_Y, SOFTKEY_WIDTH, SOFTKEY_HEIGHT, performHapticClick, dispatchKey)

        addSmallRow(activity, overlay, fonts, initialSnapshot, 1, FIRST_SMALL_ROW_Y, performHapticClick, dispatchKey)
        addSmallRow(activity, overlay, fonts, initialSnapshot, 7, SECOND_SMALL_ROW_Y, performHapticClick, dispatchKey)

        addKey(activity, overlay, fonts, initialSnapshot, 13, false, SMALL_ROW_START_X, ENTER_ROW_Y, 125f, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 14, false, 201f, ENTER_ROW_Y, SMALL_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 15, false, 279f, ENTER_ROW_Y, SMALL_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 16, false, 357f, ENTER_ROW_Y, SMALL_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 17, false, 435f, ENTER_ROW_Y, SMALL_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)

        addLargeRow(activity, overlay, fonts, initialSnapshot, 18, FIRST_LARGE_ROW_Y, performHapticClick, dispatchKey)
        addLargeRow(activity, overlay, fonts, initialSnapshot, 23, SECOND_LARGE_ROW_Y, performHapticClick, dispatchKey)
        addLargeRow(activity, overlay, fonts, initialSnapshot, 28, THIRD_LARGE_ROW_Y, performHapticClick, dispatchKey)
        addLargeRow(activity, overlay, fonts, initialSnapshot, 33, FOURTH_LARGE_ROW_Y, performHapticClick, dispatchKey)
    }

    private fun addSmallRow(
        activity: MainActivity,
        overlay: ReplicaOverlay,
        fonts: KeypadFontSet,
        initialSnapshot: KeypadSnapshot?,
        codeStart: Int,
        y: Float,
        performHapticClick: () -> Unit,
        dispatchKey: (Int) -> Unit,
    ) {
        for (column in 0 until 6) {
            addKey(
                activity = activity,
                overlay = overlay,
                fonts = fonts,
                initialSnapshot = initialSnapshot,
                code = codeStart + column,
                isFunctionKey = false,
                x = SMALL_ROW_START_X + SMALL_ROW_CELL_WIDTH * column,
                y = y,
                width = SMALL_ROW_CELL_WIDTH,
                height = SMALL_ROW_KEY_HEIGHT,
                performHapticClick = performHapticClick,
                dispatchKey = dispatchKey,
            )
        }
    }

    private fun addLargeRow(
        activity: MainActivity,
        overlay: ReplicaOverlay,
        fonts: KeypadFontSet,
        initialSnapshot: KeypadSnapshot?,
        codeStart: Int,
        y: Float,
        performHapticClick: () -> Unit,
        dispatchKey: (Int) -> Unit,
    ) {
        addKey(activity, overlay, fonts, initialSnapshot, codeStart, false, LARGE_ROW_START_X, y, LARGE_ROW_FIRST_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, codeStart + 1, false, LARGE_ROW_SECOND_X, y, LARGE_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, codeStart + 2, false, LARGE_ROW_THIRD_X, y, LARGE_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, codeStart + 3, false, LARGE_ROW_FOURTH_X, y, LARGE_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, codeStart + 4, false, LARGE_ROW_FIFTH_X, y, LARGE_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
    }

    private fun addKey(
        activity: MainActivity,
        overlay: ReplicaOverlay,
        fonts: KeypadFontSet,
        initialSnapshot: KeypadSnapshot?,
        code: Int,
        isFunctionKey: Boolean,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        performHapticClick: () -> Unit,
        dispatchKey: (Int) -> Unit,
    ) {
        val keyView = CalculatorKeyView(activity)
        keyView.setKey(code, isFunctionKey, fonts)
        initialSnapshot?.let { keyView.updateLabels(it) }
        keyView.setOnTouchListener(
            createTouchListener(
                code = code,
                performHapticClick = performHapticClick,
                dispatchKey = dispatchKey,
            )
        )
        overlay.addReplicaView(keyView, x, y, width, height)
    }

    private fun loadTypeface(activity: MainActivity, assetPath: String): Typeface? {
        return try {
            Typeface.createFromAsset(activity.assets, assetPath)
        } catch (_: Exception) {
            null
        }
    }

    private fun createTouchListener(
        code: Int,
        performHapticClick: () -> Unit,
        dispatchKey: (Int) -> Unit,
    ): View.OnTouchListener {
        return View.OnTouchListener { view, event ->
            if (!view.isEnabled) {
                view.isPressed = false
                return@OnTouchListener true
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    performHapticClick()
                    dispatchKey(code)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    dispatchKey(0)
                }
            }
            true
        }
    }
}