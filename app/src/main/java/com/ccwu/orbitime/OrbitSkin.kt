package com.ccwu.orbitime

import android.graphics.Color

data class OrbitSkin(
    val id: String,
    val name: String,
    val isPro: Boolean = false,
    val background: String,
    val panel: String,
    val panelAlt: String,
    val key: String,
    val keyPressed: String,
    val controlKey: String,
    val text: String,
    val mutedText: String,
    val accent: String,
    val warning: String,
    val border: String,
) {
    val backgroundColor: Int get() = parse(background)
    val panelColor: Int get() = parse(panel)
    val panelAltColor: Int get() = parse(panelAlt)
    val keyColor: Int get() = parse(key)
    val keyPressedColor: Int get() = parse(keyPressed)
    val controlKeyColor: Int get() = parse(controlKey)
    val textColor: Int get() = parse(text)
    val mutedTextColor: Int get() = parse(mutedText)
    val accentColor: Int get() = parse(accent)
    val warningColor: Int get() = parse(warning)
    val borderColor: Int get() = parse(border)

    private fun parse(hex: String): Int = Color.parseColor(hex)
}
