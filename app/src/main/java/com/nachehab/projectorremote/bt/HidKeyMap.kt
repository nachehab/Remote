package com.nachehab.projectorremote.bt

/** Maps printable characters to (modifier, keyboard usage) for typing over Bluetooth HID (US layout). */
object HidKeyMap {
    const val MOD_LEFT_SHIFT = 0x02

    data class Stroke(val modifier: Int, val usage: Int)

    private val unshifted = mapOf(
        ' ' to 0x2C, '\n' to 0x28, '\t' to 0x2B,
        '-' to 0x2D, '=' to 0x2E, '[' to 0x2F, ']' to 0x30, '\\' to 0x31,
        ';' to 0x33, '\'' to 0x34, '`' to 0x35, ',' to 0x36, '.' to 0x37, '/' to 0x38,
    )

    private val shifted = mapOf(
        '!' to 0x1E, '@' to 0x1F, '#' to 0x20, '$' to 0x21, '%' to 0x22,
        '^' to 0x23, '&' to 0x24, '*' to 0x25, '(' to 0x26, ')' to 0x27,
        '_' to 0x2D, '+' to 0x2E, '{' to 0x2F, '}' to 0x30, '|' to 0x31,
        ':' to 0x33, '"' to 0x34, '~' to 0x35, '<' to 0x36, '>' to 0x37, '?' to 0x38,
    )

    fun strokeFor(c: Char): Stroke? = when (c) {
        in 'a'..'z' -> Stroke(0, 0x04 + (c - 'a'))
        in 'A'..'Z' -> Stroke(MOD_LEFT_SHIFT, 0x04 + (c - 'A'))
        in '1'..'9' -> Stroke(0, 0x1E + (c - '1'))
        '0' -> Stroke(0, 0x27)
        else -> unshifted[c]?.let { Stroke(0, it) }
            ?: shifted[c]?.let { Stroke(MOD_LEFT_SHIFT, it) }
    }
}
