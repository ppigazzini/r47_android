package com.example.r47

import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View

internal object ReplicaKeypadLayout {
    private const val CHROME_MODE_TEXTURE = "r47_texture"
    private const val TEXTURE_SHELL_WIDTH = 537f
    private const val TEXTURE_SHELL_HEIGHT = 1005f
    private const val SHARED_SHELL_WIDTH = 526f
    private const val SHARED_SHELL_HEIGHT = 980f
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

    private const val TOUCH_GRID_LEFT = 22.5f
    private const val TOUCH_GRID_RIGHT = 521f
    private const val TOUCH_GRID_WIDTH = TOUCH_GRID_RIGHT - TOUCH_GRID_LEFT
    private const val SOFTKEY_TOUCH_TOP = 366.5f
    private const val SOFTKEY_TOUCH_BOTTOM = 443.5f
    private const val FIRST_SMALL_TOUCH_TOP = SOFTKEY_TOUCH_BOTTOM
    private const val FIRST_SMALL_TOUCH_BOTTOM = 515.5f
    private const val SECOND_SMALL_TOUCH_TOP = FIRST_SMALL_TOUCH_BOTTOM
    private const val SECOND_SMALL_TOUCH_BOTTOM = 587.5f
    private const val ENTER_TOUCH_TOP = SECOND_SMALL_TOUCH_BOTTOM
    private const val ENTER_TOUCH_BOTTOM = 669.5f
    private const val FIRST_LARGE_TOUCH_TOP = ENTER_TOUCH_BOTTOM
    private const val FIRST_LARGE_TOUCH_BOTTOM = 744.5f
    private const val SECOND_LARGE_TOUCH_TOP = FIRST_LARGE_TOUCH_BOTTOM
    private const val SECOND_LARGE_TOUCH_BOTTOM = 819.5f
    private const val THIRD_LARGE_TOUCH_TOP = SECOND_LARGE_TOUCH_BOTTOM
    private const val THIRD_LARGE_TOUCH_BOTTOM = 894.5f
    private const val FOURTH_LARGE_TOUCH_TOP = THIRD_LARGE_TOUCH_BOTTOM
    private const val FOURTH_LARGE_TOUCH_BOTTOM = 975.5f

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

    private data class TouchZoneSpec(
        val code: Int,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
    )

    private data class TouchCellSpec(
        val code: Int,
        val startColumn: Int,
        val columnSpan: Int = 1,
    )

    private data class TouchRowSpec(
        val top: Float,
        val bottom: Float,
        val columns: Int,
        val cells: List<TouchCellSpec>,
    )

    private val baseTouchZones = buildBaseTouchZones()
    private val baseTouchZonesByCode = baseTouchZones.associateBy { it.code }

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
            addDynamicKeypad(activity, overlay, chromeMode, performHapticClick, dispatchKey)
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
        for (touchZone in baseTouchZones) {
            addTouchZone(
                activity = activity,
                overlay = overlay,
                chromeMode = CHROME_MODE_TEXTURE,
                code = touchZone.code,
                performHapticClick = performHapticClick,
                dispatchKey = dispatchKey,
            )
        }
    }

    private fun addDynamicKeypad(
        activity: MainActivity,
        overlay: ReplicaOverlay,
        chromeMode: String,
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
            chromeMode = chromeMode,
            x = SOFTKEY_START_X,
            y = SOFTKEY_START_Y,
            width = SOFTKEY_WIDTH,
            height = SOFTKEY_HEIGHT,
            performHapticClick = performHapticClick,
            dispatchKey = dispatchKey,
        )
        addKey(activity, overlay, fonts, initialSnapshot, 39, true, chromeMode, SOFTKEY_START_X + SOFTKEY_STEP_X, SOFTKEY_START_Y, SOFTKEY_WIDTH, SOFTKEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 40, true, chromeMode, SOFTKEY_START_X + SOFTKEY_STEP_X * 2f, SOFTKEY_START_Y, SOFTKEY_WIDTH, SOFTKEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 41, true, chromeMode, SOFTKEY_START_X + SOFTKEY_STEP_X * 3f, SOFTKEY_START_Y, SOFTKEY_WIDTH, SOFTKEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 42, true, chromeMode, SOFTKEY_START_X + SOFTKEY_STEP_X * 4f, SOFTKEY_START_Y, SOFTKEY_WIDTH, SOFTKEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 43, true, chromeMode, SOFTKEY_START_X + SOFTKEY_STEP_X * 5f, SOFTKEY_START_Y, SOFTKEY_WIDTH, SOFTKEY_HEIGHT, performHapticClick, dispatchKey)

        addSmallRow(activity, overlay, fonts, initialSnapshot, chromeMode, 1, FIRST_SMALL_ROW_Y, performHapticClick, dispatchKey)
        addSmallRow(activity, overlay, fonts, initialSnapshot, chromeMode, 7, SECOND_SMALL_ROW_Y, performHapticClick, dispatchKey)

        addKey(activity, overlay, fonts, initialSnapshot, 13, false, chromeMode, SMALL_ROW_START_X, ENTER_ROW_Y, 125f, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 14, false, chromeMode, 201f, ENTER_ROW_Y, SMALL_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 15, false, chromeMode, 279f, ENTER_ROW_Y, SMALL_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 16, false, chromeMode, 357f, ENTER_ROW_Y, SMALL_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 17, false, chromeMode, 435f, ENTER_ROW_Y, SMALL_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)

        addLargeRow(activity, overlay, fonts, initialSnapshot, chromeMode, 18, FIRST_LARGE_ROW_Y, performHapticClick, dispatchKey)
        addLargeRow(activity, overlay, fonts, initialSnapshot, chromeMode, 23, SECOND_LARGE_ROW_Y, performHapticClick, dispatchKey)
        addLargeRow(activity, overlay, fonts, initialSnapshot, chromeMode, 28, THIRD_LARGE_ROW_Y, performHapticClick, dispatchKey)
        addLargeRow(activity, overlay, fonts, initialSnapshot, chromeMode, 33, FOURTH_LARGE_ROW_Y, performHapticClick, dispatchKey)
    }

    private fun addSmallRow(
        activity: MainActivity,
        overlay: ReplicaOverlay,
        fonts: KeypadFontSet,
        initialSnapshot: KeypadSnapshot?,
        chromeMode: String,
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
                chromeMode = chromeMode,
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
        chromeMode: String,
        codeStart: Int,
        y: Float,
        performHapticClick: () -> Unit,
        dispatchKey: (Int) -> Unit,
    ) {
        addKey(activity, overlay, fonts, initialSnapshot, codeStart, false, chromeMode, LARGE_ROW_START_X, y, LARGE_ROW_FIRST_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, codeStart + 1, false, chromeMode, LARGE_ROW_SECOND_X, y, LARGE_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, codeStart + 2, false, chromeMode, LARGE_ROW_THIRD_X, y, LARGE_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, codeStart + 3, false, chromeMode, LARGE_ROW_FOURTH_X, y, LARGE_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, codeStart + 4, false, chromeMode, LARGE_ROW_FIFTH_X, y, LARGE_ROW_CELL_WIDTH, SMALL_ROW_KEY_HEIGHT, performHapticClick, dispatchKey)
    }

    private fun addKey(
        activity: MainActivity,
        overlay: ReplicaOverlay,
        fonts: KeypadFontSet,
        initialSnapshot: KeypadSnapshot?,
        code: Int,
        isFunctionKey: Boolean,
        chromeMode: String,
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
        addTouchZone(
            activity = activity,
            overlay = overlay,
            chromeMode = chromeMode,
            code = code,
            performHapticClick = performHapticClick,
            dispatchKey = dispatchKey,
            pressedView = keyView,
        )
        keyView.setOnTouchListener(
            createTouchListener(
                code = code,
                performHapticClick = performHapticClick,
                dispatchKey = dispatchKey,
                pressedView = keyView,
            )
        )
        overlay.addReplicaView(keyView, x, y, width, height)
    }

    private fun addTouchZone(
        activity: MainActivity,
        overlay: ReplicaOverlay,
        chromeMode: String,
        code: Int,
        performHapticClick: () -> Unit,
        dispatchKey: (Int) -> Unit,
        pressedView: View? = null,
    ) {
        val touchZoneSpec = touchZoneFor(chromeMode, code)
        val touchZone = View(activity).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = null
            setOnTouchListener(
                createTouchListener(
                    code = code,
                    performHapticClick = performHapticClick,
                    dispatchKey = dispatchKey,
                    pressedView = pressedView,
                )
            )
        }

        overlay.addReplicaView(
            touchZone,
            touchZoneSpec.x,
            touchZoneSpec.y,
            touchZoneSpec.width,
            touchZoneSpec.height,
            showTouchZone = true,
        )
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
        pressedView: View? = null,
    ): View.OnTouchListener {
        return View.OnTouchListener { view, event ->
            val feedbackView = pressedView ?: view
            if (!feedbackView.isEnabled) {
                feedbackView.isPressed = false
                return@OnTouchListener true
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    feedbackView.isPressed = true
                    performHapticClick()
                    dispatchKey(code)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    feedbackView.isPressed = false
                    dispatchKey(0)
                }
            }
            true
        }
    }

    private fun touchZoneFor(chromeMode: String, code: Int): TouchZoneSpec {
        val base = baseTouchZonesByCode.getValue(code)
        val scaleX = if (chromeMode == CHROME_MODE_TEXTURE) {
            1f
        } else {
            SHARED_SHELL_WIDTH / TEXTURE_SHELL_WIDTH
        }
        val scaleY = if (chromeMode == CHROME_MODE_TEXTURE) {
            1f
        } else {
            SHARED_SHELL_HEIGHT / TEXTURE_SHELL_HEIGHT
        }
        return TouchZoneSpec(
            code = code,
            x = base.x * scaleX,
            y = base.y * scaleY,
            width = base.width * scaleX,
            height = base.height * scaleY,
        )
    }

    private fun buildBaseTouchZones(): List<TouchZoneSpec> {
        val rows = listOf(
            TouchRowSpec(
                top = SOFTKEY_TOUCH_TOP,
                bottom = SOFTKEY_TOUCH_BOTTOM,
                columns = 6,
                cells = (38..43).mapIndexed { index, code ->
                    TouchCellSpec(code = code, startColumn = index)
                },
            ),
            TouchRowSpec(
                top = FIRST_SMALL_TOUCH_TOP,
                bottom = FIRST_SMALL_TOUCH_BOTTOM,
                columns = 6,
                cells = (1..6).mapIndexed { index, code ->
                    TouchCellSpec(code = code, startColumn = index)
                },
            ),
            TouchRowSpec(
                top = SECOND_SMALL_TOUCH_TOP,
                bottom = SECOND_SMALL_TOUCH_BOTTOM,
                columns = 6,
                cells = (7..12).mapIndexed { index, code ->
                    TouchCellSpec(code = code, startColumn = index)
                },
            ),
            TouchRowSpec(
                top = ENTER_TOUCH_TOP,
                bottom = ENTER_TOUCH_BOTTOM,
                columns = 6,
                cells = listOf(
                    TouchCellSpec(code = 13, startColumn = 0, columnSpan = 2),
                    TouchCellSpec(code = 14, startColumn = 2),
                    TouchCellSpec(code = 15, startColumn = 3),
                    TouchCellSpec(code = 16, startColumn = 4),
                    TouchCellSpec(code = 17, startColumn = 5),
                ),
            ),
            TouchRowSpec(
                top = FIRST_LARGE_TOUCH_TOP,
                bottom = FIRST_LARGE_TOUCH_BOTTOM,
                columns = 5,
                cells = (18..22).mapIndexed { index, code ->
                    TouchCellSpec(code = code, startColumn = index)
                },
            ),
            TouchRowSpec(
                top = SECOND_LARGE_TOUCH_TOP,
                bottom = SECOND_LARGE_TOUCH_BOTTOM,
                columns = 5,
                cells = (23..27).mapIndexed { index, code ->
                    TouchCellSpec(code = code, startColumn = index)
                },
            ),
            TouchRowSpec(
                top = THIRD_LARGE_TOUCH_TOP,
                bottom = THIRD_LARGE_TOUCH_BOTTOM,
                columns = 5,
                cells = (28..32).mapIndexed { index, code ->
                    TouchCellSpec(code = code, startColumn = index)
                },
            ),
            TouchRowSpec(
                top = FOURTH_LARGE_TOUCH_TOP,
                bottom = FOURTH_LARGE_TOUCH_BOTTOM,
                columns = 5,
                cells = (33..37).mapIndexed { index, code ->
                    TouchCellSpec(code = code, startColumn = index)
                },
            ),
        )

        return rows.flatMap(::buildRowTouchZones)
    }

    private fun buildRowTouchZones(row: TouchRowSpec): List<TouchZoneSpec> {
        val columnWidth = TOUCH_GRID_WIDTH / row.columns.toFloat()
        val rowHeight = row.bottom - row.top
        return row.cells.map { cell ->
            TouchZoneSpec(
                code = cell.code,
                x = TOUCH_GRID_LEFT + columnWidth * cell.startColumn,
                y = row.top,
                width = columnWidth * cell.columnSpan,
                height = rowHeight,
            )
        }
    }
}