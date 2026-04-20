package com.example.r47

import android.view.KeyEvent

internal sealed interface PhysicalKeyboardAction {
    data class NativeKey(
        val id: String,
        val isFunctionKey: Boolean = false,
    ) : PhysicalKeyboardAction

    data class Shortcut(val id: String) : PhysicalKeyboardAction
}

internal object PhysicalKeyboardMapper {
    private val ignoredModifierKeyCodes = setOf(
        KeyEvent.KEYCODE_SHIFT_LEFT,
        KeyEvent.KEYCODE_SHIFT_RIGHT,
        KeyEvent.KEYCODE_CTRL_LEFT,
        KeyEvent.KEYCODE_CTRL_RIGHT,
    )

    private val characterBindings = mapOf(
        '+' to key("36"),
        '-' to key("31"),
        '*' to key("26"),
        '/' to key("21"),
        '.' to key("34"),
        '=' to shortcut("SEQ_DOTD"),
        '0' to key("33"),
        '1' to key("28"),
        '2' to key("29"),
        '3' to key("30"),
        '4' to key("23"),
        '5' to key("24"),
        '6' to key("25"),
        '7' to key("18"),
        '&' to shortcut("SEQ_toI"),
        '8' to key("19"),
        '9' to key("20"),
        '%' to shortcut("SEQ_PERCENT"),
        '!' to shortcut("SEQ_FACTORIAL"),
        '@' to shortcut("SEQ_DOTD"),
        '#' to shortcut("SEQ_HASH"),
        '$' to shortcut("SEQ_MS"),
        '^' to key("03"),
        'q' to key("01"),
        'Q' to key("00"),
        'w' to key("13"),
        'W' to shortcut("SEQ_LASTX"),
        'e' to key("15"),
        'E' to shortcut("SEQ_ECONST"),
        'r' to key("07"),
        'R' to shortcut("SEQ_toREC"),
        't' to shortcut("SEQ_TAN"),
        'T' to shortcut("SEQ_ATAN"),
        'y' to shortcut("SEQ_XTHROOT"),
        'Y' to key("03"),
        'u' to shortcut("SEQ_UNDO"),
        'U' to shortcut("SEQ_USER"),
        'i' to shortcut("SEQ_IMAG_J"),
        'I' to shortcut("SEQ_DISP"),
        'o' to key("04"),
        'O' to shortcut("SEQ_10X"),
        'p' to shortcut("SEQ_PI"),
        'P' to shortcut("SEQ_toPOL"),
        'a' to shortcut("SEQ_SIGMAP"),
        'A' to shortcut("SEQ_ANGLE"),
        's' to shortcut("SEQ_SIN"),
        'S' to shortcut("SEQ_ASIN"),
        'd' to key("08"),
        'D' to shortcut("SEQ_RUP"),
        'f' to key("10"),
        'F' to shortcut("SEQ_PREFIX"),
        'g' to key("11"),
        'G' to shortcut("SEQ_GTO"),
        'H' to shortcut("SEQ_HOME"),
        'j' to shortcut("SEQ_IMAG_J"),
        'J' to shortcut("SEQ_EXP"),
        'k' to shortcut("SEQ_IMAG_POL"),
        'K' to shortcut("SEQ_STK"),
        'l' to key("05"),
        'L' to shortcut("SEQ_EXP_E"),
        'z' to key("35"),
        'Z' to shortcut("SEQ_ABS"),
        'x' to key("17"),
        'X' to shortcut("SEQ_COMPLEX"),
        'c' to shortcut("SEQ_COS"),
        'C' to shortcut("SEQ_ACOS"),
        'v' to key("02"),
        'V' to key("02"),
        'b' to shortcut("SEQ_LBL"),
        'B' to shortcut("SEQ_MYMENU"),
        'n' to key("14"),
        'N' to shortcut("SEQ_PRGM"),
        'm' to key("06"),
        'M' to shortcut("SEQ_PREF"),
        ',' to key("34"),
        '<' to shortcut("SEQ_RTN"),
        '>' to shortcut("SEQ_DRG"),
        ':' to shortcut("SEQ_TGLFRT"),
        '\'' to shortcut("SEQ_ALPHA"),
        '"' to shortcut("SEQ_HASH"),
        '\\' to key("35"),
        '|' to shortcut("SEQ_ABS"),
    )

    private val keyCodeBindings = mapOf(
        KeyEvent.KEYCODE_ENTER to key("12"),
        KeyEvent.KEYCODE_NUMPAD_ENTER to key("12"),
        KeyEvent.KEYCODE_ESCAPE to key("32"),
        KeyEvent.KEYCODE_DEL to key("16"),
        KeyEvent.KEYCODE_FORWARD_DEL to key("16"),
        KeyEvent.KEYCODE_TAB to key("13"),
        KeyEvent.KEYCODE_DPAD_UP to key("22"),
        KeyEvent.KEYCODE_DPAD_DOWN to key("27"),
        KeyEvent.KEYCODE_F1 to functionKey("1"),
        KeyEvent.KEYCODE_F2 to functionKey("2"),
        KeyEvent.KEYCODE_F3 to functionKey("3"),
        KeyEvent.KEYCODE_F4 to functionKey("4"),
        KeyEvent.KEYCODE_F5 to functionKey("5"),
        KeyEvent.KEYCODE_F6 to functionKey("6"),
        KeyEvent.KEYCODE_F7 to shortcut("SEQ_SI_n"),
        KeyEvent.KEYCODE_F8 to shortcut("SEQ_SI_u"),
        KeyEvent.KEYCODE_F9 to shortcut("SEQ_SI_m"),
        KeyEvent.KEYCODE_F10 to shortcut("SEQ_SI_k"),
        KeyEvent.KEYCODE_F11 to shortcut("SEQ_SI_M"),
        KeyEvent.KEYCODE_NUMPAD_ADD to key("36"),
        KeyEvent.KEYCODE_NUMPAD_SUBTRACT to key("31"),
        KeyEvent.KEYCODE_NUMPAD_MULTIPLY to key("26"),
        KeyEvent.KEYCODE_NUMPAD_DIVIDE to key("21"),
    )

    fun resolve(keyCode: Int, event: KeyEvent?): PhysicalKeyboardAction? {
        if (event == null || keyCode in ignoredModifierKeyCodes) {
            return null
        }

        val unicode = event.getUnicodeChar(event.metaState)
        if (unicode != 0) {
            characterBindings[unicode.toChar()]?.let { return it }
        }

        return keyCodeBindings[keyCode]
    }

    private fun key(id: String) = PhysicalKeyboardAction.NativeKey(id)

    private fun functionKey(id: String) = PhysicalKeyboardAction.NativeKey(
        id,
        isFunctionKey = true,
    )

    private fun shortcut(id: String) = PhysicalKeyboardAction.Shortcut(id)
}

internal object PhysicalKeyboardShortcuts {
    private const val TAP_DELAY_MS = 50L
    private const val LONG_PAUSE_MS = 100L

    fun dispatch(
        action: PhysicalKeyboardAction.Shortcut,
        offerCoreTask: (Runnable) -> Unit,
        sendKey: (String, Boolean, Boolean) -> Unit,
        sendMenu: (Int) -> Unit,
    ) {
        offerCoreTask {
            when (action.id) {
                "SEQ_PERCENT" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "07")
                }
                "SEQ_FACTORIAL" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "02")
                }
                "SEQ_DOTD" -> {
                    tap(sendKey, "11", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "03")
                }
                "SEQ_HASH" -> {
                    tap(sendKey, "11", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "05")
                }
                "SEQ_MS" -> {
                    tap(sendKey, "11", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "02")
                }
                "SEQ_LASTX" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "13")
                }
                "SEQ_ECONST" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "36", pauseAfterReleaseMs = LONG_PAUSE_MS)
                    tap(
                        sendKey,
                        "2",
                        isFunctionKey = true,
                        pauseAfterReleaseMs = LONG_PAUSE_MS,
                    )
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "2", isFunctionKey = true)
                }
                "SEQ_toREC" -> {
                    tap(sendKey, "11", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "00")
                }
                "SEQ_TAN" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "20")
                }
                "SEQ_ATAN" -> {
                    tap(sendKey, "11", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "20")
                }
                "SEQ_XTHROOT" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "03")
                }
                "SEQ_UNDO" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "16")
                }
                "SEQ_USER" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "09")
                }
                "SEQ_IMAG_J" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "00")
                }
                "SEQ_DISP" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "14")
                }
                "SEQ_10X" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "04")
                }
                "SEQ_PI" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "08")
                }
                "SEQ_toI" -> {
                    tap(sendKey, "11", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "04")
                }
                "SEQ_HOME" -> sendMenu(-1921)
                "SEQ_MYMENU" -> sendMenu(-1349)
                "SEQ_toPOL" -> {
                    tap(sendKey, "11", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "01")
                }
                "SEQ_IMAG_POL" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "01")
                }
                "SEQ_ALPHA" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "17")
                }
                "SEQ_SIGMAP" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "21", pauseAfterReleaseMs = LONG_PAUSE_MS)
                    tap(sendKey, "1", isFunctionKey = true)
                }
                "SEQ_ANGLE" -> {
                    tap(sendKey, "11", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "06")
                }
                "SEQ_SIN" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "18")
                }
                "SEQ_ASIN" -> {
                    tap(sendKey, "11", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "18")
                }
                "SEQ_RUP" -> {
                    tap(sendKey, "11", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "08")
                }
                "SEQ_PREFIX" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "15")
                }
                "SEQ_GTO" -> {
                    tap(sendKey, "11", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "17")
                }
                "SEQ_EXP" -> {
                    tap(sendKey, "11", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "15")
                }
                "SEQ_COMPLEX" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "12")
                }
                "SEQ_STK" -> {
                    tap(sendKey, "11", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "13")
                }
                "SEQ_EXP_E" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "05")
                }
                "SEQ_COS" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "19")
                }
                "SEQ_ACOS" -> {
                    tap(sendKey, "11", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "19")
                }
                "SEQ_LBL" -> {
                    tap(sendKey, "11", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "05")
                }
                "SEQ_PRGM" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "35")
                }
                "SEQ_PREF" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "28")
                }
                "SEQ_RTN" -> {
                    tap(sendKey, "11", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "35")
                }
                "SEQ_DRG" -> press(sendKey, "09")
                "SEQ_SI_n" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "15", pauseAfterReleaseMs = LONG_PAUSE_MS)
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "3", isFunctionKey = true)
                }
                "SEQ_SI_u" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "15", pauseAfterReleaseMs = LONG_PAUSE_MS)
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "2", isFunctionKey = true)
                }
                "SEQ_SI_m" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "15", pauseAfterReleaseMs = LONG_PAUSE_MS)
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "1", isFunctionKey = true)
                }
                "SEQ_SI_k" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "15", pauseAfterReleaseMs = LONG_PAUSE_MS)
                    tap(sendKey, "1", isFunctionKey = true)
                }
                "SEQ_SI_M" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "15", pauseAfterReleaseMs = LONG_PAUSE_MS)
                    tap(sendKey, "2", isFunctionKey = true)
                }
                "SEQ_TGLFRT" -> {
                    tap(sendKey, "11", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "34")
                }
                "SEQ_AIM" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "17")
                }
                "SEQ_ABS" -> {
                    tap(sendKey, "10", pauseAfterReleaseMs = TAP_DELAY_MS)
                    tap(sendKey, "06")
                }
                else -> return@offerCoreTask
            }
        }
    }

    private fun tap(
        sendKey: (String, Boolean, Boolean) -> Unit,
        id: String,
        isFunctionKey: Boolean = false,
        pauseAfterReleaseMs: Long = 0L,
    ) {
        press(sendKey, id, isFunctionKey)
        Thread.sleep(TAP_DELAY_MS)
        sendKey(id, isFunctionKey, true)
        if (pauseAfterReleaseMs > 0L) {
            Thread.sleep(pauseAfterReleaseMs)
        }
    }

    private fun press(
        sendKey: (String, Boolean, Boolean) -> Unit,
        id: String,
        isFunctionKey: Boolean = false,
    ) {
        sendKey(id, isFunctionKey, false)
    }
}