package com.ccwu.orbitime

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.TextView

object OrbitTheme {
    private val fallbackSkin: OrbitSkin = OrbitSkins.OrbitDark

    val BACKGROUND: Int get() = fallbackSkin.backgroundColor
    val PANEL: Int get() = fallbackSkin.panelColor
    val PANEL_ALT: Int get() = fallbackSkin.panelAltColor
    val ACCENT: Int get() = fallbackSkin.accentColor
    val TEXT: Int get() = fallbackSkin.textColor
    val MUTED: Int get() = fallbackSkin.mutedTextColor
    val DANGER: Int get() = fallbackSkin.warningColor

    fun rounded(color: Int, radiusPx: Float, strokeColor: Int? = null, strokeWidthPx: Int = 1): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(color)
            strokeColor?.let { setStroke(strokeWidthPx, it) }
        }
    }

    fun keyboardBackground(skin: OrbitSkin): GradientDrawable {
        return rounded(
            color = skin.backgroundColor,
            radiusPx = 0f,
            strokeColor = skin.borderColor,
            strokeWidthPx = 2,
        )
    }

    fun label(
        view: TextView,
        sizeSp: Float = 14f,
        muted: Boolean = false,
        bold: Boolean = false,
        skin: OrbitSkin = fallbackSkin,
    ) {
        view.setTextColor(if (muted) skin.mutedTextColor else skin.textColor)
        view.textSize = sizeSp
        view.gravity = Gravity.CENTER
        view.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        view.includeFontPadding = false
    }

    fun parse(hex: String): Int = Color.parseColor(hex)
}
