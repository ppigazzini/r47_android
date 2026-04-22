package com.example.r47

import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View

internal object ReplicaKeypadLayout {
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
        performHapticClick: () -> Unit,
        dispatchKey: (Int) -> Unit,
    ) {
        overlay.removeAllViews()
        addDynamicKeypad(activity, overlay, performHapticClick, dispatchKey)
    }

    fun updateDynamicKeys(
        activity: MainActivity,
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
        val snapshot = activity.currentKeypadSnapshot()

        addKey(
            activity = activity,
            overlay = overlay,
            fonts = fonts,
            snapshot = snapshot,
            code = 38,
            isFunctionKey = true,
            x = SOFTKEY_START_X,
            y = SOFTKEY_START_Y,
            width = SOFTKEY_WIDTH,
            height = SOFTKEY_HEIGHT,
            performHapticClick = performHapticClick,
            dispatchKey = dispatchKey,
        )
        addKey(activity, overlay, fonts, snapshot, 39, true, SOFTKEY_START_X + SOFTKEY_STEP_X, SOFTKEY_START_Y, SOFTKEY_WIDTH, SOFTKEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, snapshot, 40, true, SOFTKEY_START_X + SOFTKEY_STEP_X * 2f, SOFTKEY_START_Y, SOFTKEY_WIDTH, SOFTKEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, snapshot, 41, true, SOFTKEY_START_X + SOFTKEY_STEP_X * 3f, SOFTKEY_START_Y, SOFTKEY_WIDTH, SOFTKEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, snapshot, 42, true, SOFTKEY_START_X + SOFTKEY_STEP_X * 4f, SOFTKEY_START_Y, SOFTKEY_WIDTH, SOFTKEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, snapshot, 43, true, SOFTKEY_START_X + SOFTKEY_STEP_X * 5f, SOFTKEY_START_Y, SOFTKEY_WIDTH, SOFTKEY_HEIGHT, performHapticClick, dispatchKey)

        addSmallRow(activity, overlay, fonts, snapshot, 1, FIRST_SMALL_ROW_Y, performHapticClick, dispatchKey)
        addSmallRow(activity, overlay, fonts, snapshot, 7, SECOND_SMALL_ROW_Y, performHapticClick, dispatchKey)

        addKey(activity, overlay, fonts, snapshot, 13, false, SMALL_ROW_START_X, ENTER_ROW_Y, 125f, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, snapshot, 14, false, 201f, ENTER_ROW_Y, SMALL_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, snapshot, 15, false, 279f, ENTER_ROW_Y, SMALL_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, snapshot, 16, false, 357f, ENTER_ROW_Y, SMALL_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, snapshot, 17, false, 435f, ENTER_ROW_Y, SMALL_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)

        addLargeRow(activity, overlay, fonts, snapshot, 18, FIRST_LARGE_ROW_Y, performHapticClick, dispatchKey)
        addLargeRow(activity, overlay, fonts, snapshot, 23, SECOND_LARGE_ROW_Y, performHapticClick, dispatchKey)
        addLargeRow(activity, overlay, fonts, snapshot, 28, THIRD_LARGE_ROW_Y, performHapticClick, dispatchKey)
        addLargeRow(activity, overlay, fonts, snapshot, 33, FOURTH_LARGE_ROW_Y, performHapticClick, dispatchKey)
    }

    private fun addSmallRow(
        activity: MainActivity,
        overlay: ReplicaOverlay,
        fonts: KeypadFontSet,
        snapshot: KeypadSnapshot,
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
                snapshot = snapshot,
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
        snapshot: KeypadSnapshot,
        codeStart: Int,
        y: Float,
        performHapticClick: () -> Unit,
        dispatchKey: (Int) -> Unit,
    ) {
        addKey(activity, overlay, fonts, snapshot, codeStart, false, LARGE_ROW_START_X, y, LARGE_ROW_FIRST_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, snapshot, codeStart + 1, false, LARGE_ROW_SECOND_X, y, LARGE_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, snapshot, codeStart + 2, false, LARGE_ROW_THIRD_X, y, LARGE_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, snapshot, codeStart + 3, false, LARGE_ROW_FOURTH_X, y, LARGE_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, snapshot, codeStart + 4, false, LARGE_ROW_FIFTH_X, y, LARGE_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
    }

    private fun addKey(
        activity: MainActivity,
        overlay: ReplicaOverlay,
        fonts: KeypadFontSet,
        snapshot: KeypadSnapshot,
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
        keyView.updateLabels(snapshot)
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