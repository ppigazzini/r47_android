package com.example.r47

internal data class KeypadKeySnapshot(
    val primaryLabel: String,
    val fLabel: String,
    val gLabel: String,
    val letterLabel: String,
    val isEnabled: Boolean,
) {
    companion object {
        val EMPTY = KeypadKeySnapshot(
            primaryLabel = "",
            fLabel = "",
            gLabel = "",
            letterLabel = "",
            isEnabled = false,
        )
    }
}

internal data class KeypadSnapshot(
    val keyboardState: KeyboardStateSnapshot,
    val softmenuId: Int,
    val softmenuFirstItem: Int,
    val softmenuItemCount: Int,
    val softmenuVisibleRowOffset: Int,
    val softmenuPage: Int,
    val softmenuPageCount: Int,
    val softmenuHasPreviousPage: Boolean,
    val softmenuHasNextPage: Boolean,
    private val keyStates: List<KeypadKeySnapshot>,
) {
    val shiftF: Boolean
        get() = keyboardState.shiftF

    val shiftG: Boolean
        get() = keyboardState.shiftG

    val calcMode: Int
        get() = keyboardState.calcMode

    val userModeEnabled: Boolean
        get() = keyboardState.userModeEnabled

    val alphaOn: Boolean
        get() = keyboardState.alphaOn

    fun keyStateFor(code: Int): KeypadKeySnapshot {
        if (code !in 1..KEY_COUNT) {
            return KeypadKeySnapshot.EMPTY
        }
        return keyStates[code - 1]
    }

    companion object {
        private const val KEY_COUNT = 43
        private const val LABELS_PER_KEY = 4

        private const val META_SHIFT_F = 0
        private const val META_SHIFT_G = 1
        private const val META_CALC_MODE = 2
        private const val META_USER_MODE = 3
        private const val META_ALPHA = 4
        private const val META_SOFTMENU_ID = 5
        private const val META_SOFTMENU_FIRST_ITEM = 6
        private const val META_SOFTMENU_ITEM_COUNT = 7
        private const val META_SOFTMENU_VISIBLE_ROW = 8
        private const val META_SOFTMENU_PAGE = 9
        private const val META_SOFTMENU_PAGE_COUNT = 10
        private const val META_SOFTMENU_HAS_PREVIOUS = 11
        private const val META_SOFTMENU_HAS_NEXT = 12
        private const val META_KEY_ENABLED_OFFSET = 13
        private const val META_LENGTH = META_KEY_ENABLED_OFFSET + KEY_COUNT

        private val EMPTY_KEYS = List(KEY_COUNT) { KeypadKeySnapshot.EMPTY }

        val EMPTY = KeypadSnapshot(
            keyboardState = KeyboardStateSnapshot.EMPTY,
            softmenuId = 0,
            softmenuFirstItem = 0,
            softmenuItemCount = 0,
            softmenuVisibleRowOffset = 0,
            softmenuPage = 0,
            softmenuPageCount = 0,
            softmenuHasPreviousPage = false,
            softmenuHasNextPage = false,
            keyStates = EMPTY_KEYS,
        )

        fun fromNative(meta: IntArray?, labels: Array<String>?): KeypadSnapshot {
            if (meta == null || meta.size < META_LENGTH) {
                return EMPTY
            }

            val resolvedLabels = labels ?: emptyArray()
            val keyStates = List(KEY_COUNT) { index ->
                val labelIndex = index * LABELS_PER_KEY
                KeypadKeySnapshot(
                    primaryLabel = resolvedLabels.getOrElse(labelIndex) { "" },
                    fLabel = resolvedLabels.getOrElse(labelIndex + 1) { "" },
                    gLabel = resolvedLabels.getOrElse(labelIndex + 2) { "" },
                    letterLabel = resolvedLabels.getOrElse(labelIndex + 3) { "" },
                    isEnabled = meta[META_KEY_ENABLED_OFFSET + index] != 0,
                )
            }

            return KeypadSnapshot(
                keyboardState = KeyboardStateSnapshot.fromMeta(meta),
                softmenuId = meta[META_SOFTMENU_ID],
                softmenuFirstItem = meta[META_SOFTMENU_FIRST_ITEM],
                softmenuItemCount = meta[META_SOFTMENU_ITEM_COUNT],
                softmenuVisibleRowOffset = meta[META_SOFTMENU_VISIBLE_ROW],
                softmenuPage = meta[META_SOFTMENU_PAGE],
                softmenuPageCount = meta[META_SOFTMENU_PAGE_COUNT],
                softmenuHasPreviousPage = meta[META_SOFTMENU_HAS_PREVIOUS] != 0,
                softmenuHasNextPage = meta[META_SOFTMENU_HAS_NEXT] != 0,
                keyStates = keyStates,
            )
        }
    }
}