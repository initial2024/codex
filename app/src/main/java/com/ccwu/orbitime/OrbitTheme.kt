package com.ccwu.orbitime

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.TextView

object OrbitTheme {
    const val BACKGROUND: Int = -0x00F6F2E8 // #090D18
    const val PANEL: Int = -0x00EDE5D6 // #121A2A
    const val PANEL_ALT: Int = -0x00E4D7C0 // #1B2840
    const val ACCENT: Int = -0x00754B01 // #8AB4FF
    const val TEXT: Int = -0x000B0805 // #F4F7FB
    const val MUTED: Int = -0x00564830 // #A9B7D0
    const val DANGER: Int = -0x00225046 // #DDAFBA roughly muted red

    fun rounded(color: Int, radiusPx: Float, strokeColor: Int? = null, strokeWidthPx: Int = 1): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(color)
            strokeColor?.let { setStroke(strokeWidthPx, it) }
        }
    }

    fun label(view: TextView, sizeSp: Float = 14f, muted: Boolean = false, bold: Boolean = false) {
        view.setTextColor(if (muted) MUTED else TEXT)
        view.textSize = sizeSp
        view.gravity = Gravity.CENTER
        if (bold) view.typeface = Typeface.DEFAULT_BOLD
        view.includeFontPadding = false
    }

    fun parse(hex: String): Int = Color.parseColor(hex)
}
