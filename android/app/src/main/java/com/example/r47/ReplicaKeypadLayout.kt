package com.example.r47

import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View

internal object ReplicaKeypadLayout {
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
                keyView.setKey(code, isFunctionKey, fonts)
                keyView.updateLabels(snapshot)
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