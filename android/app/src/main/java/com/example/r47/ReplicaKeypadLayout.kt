package com.example.r47

import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View

internal object R47MeasuredGeometry {
    private const val REFERENCE_WIDTH = 1820f
    private const val REFERENCE_HEIGHT = 3403f
    private const val SHARED_SHELL_WIDTH = 526f
    private const val SHARED_SHELL_HEIGHT = 980f
    private const val SCALE_X = SHARED_SHELL_WIDTH / REFERENCE_WIDTH
    private const val SCALE_Y = SHARED_SHELL_HEIGHT / REFERENCE_HEIGHT

    const val STANDARD_LEFT = 134f * SCALE_X
    const val STANDARD_PITCH = 272f * SCALE_X
    const val STANDARD_KEY_WIDTH = 192f * SCALE_X
    const val MATRIX_FIRST_VISIBLE_LEFT = 465f * SCALE_X
    const val MATRIX_PITCH = 331f * SCALE_X
    const val MATRIX_KEY_WIDTH = 228f * SCALE_X
    const val ENTER_WIDTH = 462f * SCALE_X
    const val ROW_HEIGHT = 144f * SCALE_Y
    const val ROW_STEP = 260f * SCALE_Y
    const val SOFTKEY_ROW_TOP = 1290f * SCALE_Y
    const val FIRST_SMALL_ROW_TOP = 1550f * SCALE_Y
    const val ENTER_ROW_TOP = 2070f * SCALE_Y
    const val FIRST_LARGE_ROW_TOP = 2330f * SCALE_Y
}

internal object ReplicaKeypadLayout {
    private const val CHROME_MODE_TEXTURE = "r47_texture"
    private const val TEXTURE_SHELL_WIDTH = 537f
    private const val TEXTURE_SHELL_HEIGHT = 1005f
    private const val SHARED_SHELL_WIDTH = 526f
    private const val SHARED_SHELL_HEIGHT = 980f
    private const val REFERENCE_SMALL_ROW_START_X = R47MeasuredGeometry.STANDARD_LEFT
    private const val REFERENCE_SOFTKEY_TOP = R47MeasuredGeometry.SOFTKEY_ROW_TOP
    private const val REFERENCE_SMALL_CELL_WIDTH = R47MeasuredGeometry.STANDARD_PITCH
    private const val REFERENCE_SMALL_BUTTON_WIDTH = R47MeasuredGeometry.STANDARD_KEY_WIDTH
    private const val REFERENCE_SOFTKEY_WIDTH = R47MeasuredGeometry.STANDARD_KEY_WIDTH + 4f
    private const val REFERENCE_SOFTKEY_HEIGHT = R47MeasuredGeometry.ROW_HEIGHT + 4f
    private const val REFERENCE_MAIN_KEY_HEIGHT = 68f
    private const val REFERENCE_FIRST_SMALL_ROW_TOP = R47MeasuredGeometry.FIRST_SMALL_ROW_TOP
    private const val REFERENCE_MAIN_ROW_STEP_Y = R47MeasuredGeometry.ROW_STEP
    private const val REFERENCE_ENTER_ROW_TOP = R47MeasuredGeometry.ENTER_ROW_TOP
    private const val REFERENCE_FIRST_LARGE_ROW_TOP = R47MeasuredGeometry.FIRST_LARGE_ROW_TOP
    private const val REFERENCE_LARGE_ROW_STEP_Y = R47MeasuredGeometry.ROW_STEP
    private const val REFERENCE_LARGE_FIRST_COLUMN_WIDTH = REFERENCE_SMALL_BUTTON_WIDTH
    private const val REFERENCE_LARGE_WIDE_CELL_WIDTH = R47MeasuredGeometry.MATRIX_PITCH
    private const val REFERENCE_LARGE_WIDE_COLUMNS = 4
    private const val REFERENCE_ENTER_WIDTH = R47MeasuredGeometry.ENTER_WIDTH
    private const val REFERENCE_LARGE_WIDE_GRID_START_X =
        R47MeasuredGeometry.MATRIX_FIRST_VISIBLE_LEFT

    private data class DynamicGridSpec(
        val softkeyStartX: Float,
        val softkeyTopY: Float,
        val softkeyStepX: Float,
        val softkeyWidth: Float,
        val softkeyHeight: Float,
        val smallRowStartX: Float,
        val smallRowCellWidth: Float,
        val mainKeyHeight: Float,
        val firstSmallRowY: Float,
        val mainRowStepY: Float,
        val enterRowY: Float,
        val largeRowFirstX: Float,
        val largeRowWideStartX: Float,
        val largeRowWideCellWidth: Float,
        val largeRowFirstWidth: Float,
        val firstLargeRowY: Float,
        val largeRowStepY: Float,
        val enterWidth: Float,
    )

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
        val startX: Float,
        val columnWidth: Float,
        val cells: List<TouchCellSpec>,
    )

    private val baseTouchZones = buildBaseTouchZones()
    private val baseTouchZonesByCode = baseTouchZones.associateBy { it.code }

    private fun dynamicGridSpec(): DynamicGridSpec {
        return DynamicGridSpec(
            softkeyStartX = REFERENCE_SMALL_ROW_START_X,
            softkeyTopY = REFERENCE_SOFTKEY_TOP,
            softkeyStepX = REFERENCE_SMALL_CELL_WIDTH,
            softkeyWidth = REFERENCE_SOFTKEY_WIDTH,
            softkeyHeight = REFERENCE_SOFTKEY_HEIGHT,
            smallRowStartX = REFERENCE_SMALL_ROW_START_X,
            smallRowCellWidth = REFERENCE_SMALL_CELL_WIDTH,
            mainKeyHeight = REFERENCE_MAIN_KEY_HEIGHT,
            firstSmallRowY = REFERENCE_FIRST_SMALL_ROW_TOP,
            mainRowStepY = REFERENCE_MAIN_ROW_STEP_Y,
            enterRowY = REFERENCE_ENTER_ROW_TOP,
            largeRowFirstX = REFERENCE_SMALL_ROW_START_X,
            largeRowWideStartX = REFERENCE_LARGE_WIDE_GRID_START_X,
            largeRowWideCellWidth = REFERENCE_LARGE_WIDE_CELL_WIDTH,
            largeRowFirstWidth = REFERENCE_LARGE_FIRST_COLUMN_WIDTH,
            firstLargeRowY = REFERENCE_FIRST_LARGE_ROW_TOP,
            largeRowStepY = REFERENCE_LARGE_ROW_STEP_Y,
            enterWidth = REFERENCE_ENTER_WIDTH,
        )
    }

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
        val grid = dynamicGridSpec()
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
            x = grid.softkeyStartX,
            y = grid.softkeyTopY,
            width = grid.softkeyWidth,
            height = grid.softkeyHeight,
            performHapticClick = performHapticClick,
            dispatchKey = dispatchKey,
        )
        addKey(activity, overlay, fonts, initialSnapshot, 39, true, chromeMode, grid.softkeyStartX + grid.softkeyStepX, grid.softkeyTopY, grid.softkeyWidth, grid.softkeyHeight, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 40, true, chromeMode, grid.softkeyStartX + grid.softkeyStepX * 2f, grid.softkeyTopY, grid.softkeyWidth, grid.softkeyHeight, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 41, true, chromeMode, grid.softkeyStartX + grid.softkeyStepX * 3f, grid.softkeyTopY, grid.softkeyWidth, grid.softkeyHeight, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 42, true, chromeMode, grid.softkeyStartX + grid.softkeyStepX * 4f, grid.softkeyTopY, grid.softkeyWidth, grid.softkeyHeight, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 43, true, chromeMode, grid.softkeyStartX + grid.softkeyStepX * 5f, grid.softkeyTopY, grid.softkeyWidth, grid.softkeyHeight, performHapticClick, dispatchKey)

        addSmallRow(activity, overlay, fonts, initialSnapshot, chromeMode, grid, 1, grid.firstSmallRowY, performHapticClick, dispatchKey)
        addSmallRow(activity, overlay, fonts, initialSnapshot, chromeMode, grid, 7, grid.firstSmallRowY + grid.mainRowStepY, performHapticClick, dispatchKey)

        addKey(activity, overlay, fonts, initialSnapshot, 13, false, chromeMode, grid.smallRowStartX, grid.enterRowY, grid.enterWidth, grid.mainKeyHeight, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 14, false, chromeMode, grid.smallRowStartX + grid.smallRowCellWidth * 2f, grid.enterRowY, grid.smallRowCellWidth, grid.mainKeyHeight, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 15, false, chromeMode, grid.smallRowStartX + grid.smallRowCellWidth * 3f, grid.enterRowY, grid.smallRowCellWidth, grid.mainKeyHeight, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 16, false, chromeMode, grid.smallRowStartX + grid.smallRowCellWidth * 4f, grid.enterRowY, grid.smallRowCellWidth, grid.mainKeyHeight, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, 17, false, chromeMode, grid.smallRowStartX + grid.smallRowCellWidth * 5f, grid.enterRowY, grid.smallRowCellWidth, grid.mainKeyHeight, performHapticClick, dispatchKey)

        addLargeRow(activity, overlay, fonts, initialSnapshot, chromeMode, grid, 18, grid.firstLargeRowY, performHapticClick, dispatchKey)
        addLargeRow(activity, overlay, fonts, initialSnapshot, chromeMode, grid, 23, grid.firstLargeRowY + grid.largeRowStepY, performHapticClick, dispatchKey)
        addLargeRow(activity, overlay, fonts, initialSnapshot, chromeMode, grid, 28, grid.firstLargeRowY + grid.largeRowStepY * 2f, performHapticClick, dispatchKey)
        addLargeRow(activity, overlay, fonts, initialSnapshot, chromeMode, grid, 33, grid.firstLargeRowY + grid.largeRowStepY * 3f, performHapticClick, dispatchKey)
    }

    private fun addSmallRow(
        activity: MainActivity,
        overlay: ReplicaOverlay,
        fonts: KeypadFontSet,
        initialSnapshot: KeypadSnapshot?,
        chromeMode: String,
        grid: DynamicGridSpec,
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
                x = grid.smallRowStartX + grid.smallRowCellWidth * column,
                y = y,
                width = grid.smallRowCellWidth,
                height = grid.mainKeyHeight,
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
        grid: DynamicGridSpec,
        codeStart: Int,
        y: Float,
        performHapticClick: () -> Unit,
        dispatchKey: (Int) -> Unit,
    ) {
        addKey(activity, overlay, fonts, initialSnapshot, codeStart, false, chromeMode, grid.largeRowFirstX, y, grid.largeRowFirstWidth, grid.mainKeyHeight, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, codeStart + 1, false, chromeMode, grid.largeRowWideStartX, y, grid.largeRowWideCellWidth, grid.mainKeyHeight, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, codeStart + 2, false, chromeMode, grid.largeRowWideStartX + grid.largeRowWideCellWidth, y, grid.largeRowWideCellWidth, grid.mainKeyHeight, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, codeStart + 3, false, chromeMode, grid.largeRowWideStartX + grid.largeRowWideCellWidth * 2f, y, grid.largeRowWideCellWidth, grid.mainKeyHeight, performHapticClick, dispatchKey)
        addKey(activity, overlay, fonts, initialSnapshot, codeStart + 4, false, chromeMode, grid.largeRowWideStartX + grid.largeRowWideCellWidth * 3f, y, grid.largeRowWideCellWidth, grid.mainKeyHeight, performHapticClick, dispatchKey)
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
            TEXTURE_SHELL_WIDTH / SHARED_SHELL_WIDTH
        } else {
            1f
        }
        val scaleY = if (chromeMode == CHROME_MODE_TEXTURE) {
            TEXTURE_SHELL_HEIGHT / SHARED_SHELL_HEIGHT
        } else {
            1f
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
        val grid = dynamicGridSpec()
        val upperColumnBounds = centerlineBoundaries(
            (0 until 6).map { index ->
                grid.smallRowStartX + REFERENCE_SMALL_BUTTON_WIDTH / 2f + grid.smallRowCellWidth * index
            }
        )
        val lowerColumnBounds = centerlineBoundaries(
            (0 until 5).map { index ->
                grid.largeRowWideStartX + R47MeasuredGeometry.MATRIX_KEY_WIDTH / 2f +
                    grid.largeRowWideCellWidth * (index - 1)
            }
        )
        val upperRowBounds = centerlineBoundaries(
            listOf(
                grid.softkeyTopY + R47MeasuredGeometry.ROW_HEIGHT / 2f,
                grid.firstSmallRowY + R47MeasuredGeometry.ROW_HEIGHT / 2f,
                grid.firstSmallRowY + grid.mainRowStepY + R47MeasuredGeometry.ROW_HEIGHT / 2f,
                grid.enterRowY + R47MeasuredGeometry.ROW_HEIGHT / 2f,
            )
        )
        val lowerRowBounds = centerlineBoundaries(
            (0 until 4).map { index ->
                grid.firstLargeRowY + R47MeasuredGeometry.ROW_HEIGHT / 2f + grid.largeRowStepY * index
            }
        )
        val upperColumnWidth = upperColumnBounds[1] - upperColumnBounds[0]
        val lowerColumnWidth = lowerColumnBounds[1] - lowerColumnBounds[0]
        val rows = listOf(
            TouchRowSpec(
                top = upperRowBounds[0],
                bottom = upperRowBounds[1],
                startX = upperColumnBounds[0],
                columnWidth = upperColumnWidth,
                cells = (38..43).mapIndexed { index, code ->
                    TouchCellSpec(code = code, startColumn = index)
                },
            ),
            TouchRowSpec(
                top = upperRowBounds[1],
                bottom = upperRowBounds[2],
                startX = upperColumnBounds[0],
                columnWidth = upperColumnWidth,
                cells = (1..6).mapIndexed { index, code ->
                    TouchCellSpec(code = code, startColumn = index)
                },
            ),
            TouchRowSpec(
                top = upperRowBounds[2],
                bottom = upperRowBounds[3],
                startX = upperColumnBounds[0],
                columnWidth = upperColumnWidth,
                cells = (7..12).mapIndexed { index, code ->
                    TouchCellSpec(code = code, startColumn = index)
                },
            ),
            TouchRowSpec(
                top = upperRowBounds[3],
                bottom = upperRowBounds[4],
                startX = upperColumnBounds[0],
                columnWidth = upperColumnWidth,
                cells = listOf(
                    TouchCellSpec(code = 13, startColumn = 0, columnSpan = 2),
                    TouchCellSpec(code = 14, startColumn = 2),
                    TouchCellSpec(code = 15, startColumn = 3),
                    TouchCellSpec(code = 16, startColumn = 4),
                    TouchCellSpec(code = 17, startColumn = 5),
                ),
            ),
            TouchRowSpec(
                top = lowerRowBounds[0],
                bottom = lowerRowBounds[1],
                startX = lowerColumnBounds[0],
                columnWidth = lowerColumnWidth,
                cells = (18..22).mapIndexed { index, code ->
                    TouchCellSpec(code = code, startColumn = index)
                },
            ),
            TouchRowSpec(
                top = lowerRowBounds[1],
                bottom = lowerRowBounds[2],
                startX = lowerColumnBounds[0],
                columnWidth = lowerColumnWidth,
                cells = (23..27).mapIndexed { index, code ->
                    TouchCellSpec(code = code, startColumn = index)
                },
            ),
            TouchRowSpec(
                top = lowerRowBounds[2],
                bottom = lowerRowBounds[3],
                startX = lowerColumnBounds[0],
                columnWidth = lowerColumnWidth,
                cells = (28..32).mapIndexed { index, code ->
                    TouchCellSpec(code = code, startColumn = index)
                },
            ),
            TouchRowSpec(
                top = lowerRowBounds[3],
                bottom = lowerRowBounds[4],
                startX = lowerColumnBounds[0],
                columnWidth = lowerColumnWidth,
                cells = (33..37).mapIndexed { index, code ->
                    TouchCellSpec(code = code, startColumn = index)
                },
            ),
        )

        return rows.flatMap(::buildRowTouchZones)
    }

    private fun centerlineBoundaries(centers: List<Float>): List<Float> {
        require(centers.size >= 2) { "Need at least two centers to compute touch boundaries" }

        val boundaries = MutableList(centers.size + 1) { 0f }
        boundaries[0] = centers[0] - (centers[1] - centers[0]) / 2f
        for (index in 0 until centers.lastIndex) {
            boundaries[index + 1] = (centers[index] + centers[index + 1]) / 2f
        }
        boundaries[boundaries.lastIndex] = centers.last() + (centers.last() - centers[centers.lastIndex - 1]) / 2f
        return boundaries
    }

    private fun buildRowTouchZones(row: TouchRowSpec): List<TouchZoneSpec> {
        val rowHeight = row.bottom - row.top
        return row.cells.map { cell ->
            TouchZoneSpec(
                code = cell.code,
                x = row.startX + row.columnWidth * cell.startColumn,
                y = row.top,
                width = row.columnWidth * cell.columnSpan,
                height = rowHeight,
            )
        }
    }
}