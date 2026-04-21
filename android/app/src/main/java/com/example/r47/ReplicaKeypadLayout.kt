package com.example.r47

import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.widget.Button

internal object ReplicaKeypadLayout {
    private const val DYNAMIC_SKIN = "r47_background_v2"

    fun rebuild(
        activity: MainActivity,
        overlay: ReplicaOverlay,
        currentSkin: String,
        performHapticClick: () -> Unit,
        dispatchKey: (Int) -> Unit,
    ) {
        overlay.removeAllViews()

        if (currentSkin == DYNAMIC_SKIN) {
            addDynamicKeypad(activity, overlay, performHapticClick, dispatchKey)
        } else {
            addClassicKeypad(activity, overlay, performHapticClick, dispatchKey)
        }
    }

    fun updateDynamicKeys(
        activity: MainActivity,
        overlay: ReplicaOverlay,
        currentSkin: String,
        snapshot: KeyboardStateSnapshot,
    ) {
        if (currentSkin != DYNAMIC_SKIN) {
            return
        }

        for (index in 0 until overlay.childCount) {
            val child = overlay.getChildAt(index)
            if (child is CalculatorKeyView) {
                child.updateLabels(activity, snapshot)
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
            var keyHeight = deltaY - 10

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
                        actualWidth = deltaX * 2 * 0.95f
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

                val keyButton = Button(activity).apply {
                    background = null
                    isFocusable = false
                    isFocusableInTouchMode = false
                    setOnTouchListener(
                        createTouchListener(
                            code = code,
                            performHapticClick = performHapticClick,
                            dispatchKey = dispatchKey,
                        )
                    )
                }

                overlay.addReplicaView(keyButton, actualX, y, actualWidth, keyHeight)
            }
        }
    }

    private fun addDynamicKeypad(
        activity: MainActivity,
        overlay: ReplicaOverlay,
        performHapticClick: () -> Unit,
        dispatchKey: (Int) -> Unit,
    ) {
        val font = try {
            Typeface.createFromAsset(activity.assets, "fonts/C47__StandardFont.ttf")
        } catch (_: Exception) {
            null
        }
        val snapshot = activity.currentKeyboardStateSnapshot()

        val xLeft = 32f
        val yStart = 336f
        val deltaX = 80f
        val deltaY = 75f
        val keyWidthFn = 66f
        val keyWidthMain = 82f
        val leftSpacing = 18f
        val innerSpacing = 17f
        val keyHeight = 68f
        val rowSpecs = arrayOf(
            intArrayOf(38, 6),
            intArrayOf(1, 6),
            intArrayOf(7, 6),
            intArrayOf(13, 5),
            intArrayOf(18, 5),
            intArrayOf(23, 5),
            intArrayOf(28, 5),
            intArrayOf(33, 5),
        )

        for (row in 0 until 8) {
            val codeStart = rowSpecs[row][0]
            val count = rowSpecs[row][1]
            val y = when (row) {
                0 -> 357f
                1 -> 425f
                2 -> 493f
                3 -> 561f
                else -> yStart + row * deltaY
            }

            var x = xLeft
            for (column in 0 until count) {
                val code = codeStart + column
                val isFunctionKey = row == 0
                val keyWidth = if (row < 4) {
                    if (row == 3 && column == 0) {
                        (deltaX * 2) - leftSpacing
                    } else {
                        keyWidthFn
                    }
                } else {
                    if (column == 0) 74f else keyWidthMain
                }

                val keyView = CalculatorKeyView(activity)
                keyView.setKey(code, isFunctionKey, font)
                keyView.updateLabels(activity, snapshot)
                keyView.setOnTouchListener(
                    createTouchListener(
                        code = code,
                        performHapticClick = performHapticClick,
                        dispatchKey = dispatchKey,
                    )
                )

                overlay.addReplicaView(keyView, x, y, keyWidth, keyHeight)

                x += if (row < 4) {
                    if (row == 3 && column == 0) {
                        2 * deltaX
                    } else {
                        deltaX
                    }
                } else {
                    if (column == 0) {
                        keyWidth + leftSpacing
                    } else {
                        keyWidthMain + innerSpacing
                    }
                }
            }
        }
    }

    private fun createTouchListener(
        code: Int,
        performHapticClick: () -> Unit,
        dispatchKey: (Int) -> Unit,
    ): View.OnTouchListener {
        return View.OnTouchListener { view, event ->
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