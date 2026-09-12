package com.ccwu.orbitime

import android.content.Context
import android.content.res.Configuration

object SkinManager {
    private const val PREFS = "orbit_skin_prefs"
    private const val KEY_SELECTED_SKIN_ID = "selected_skin_id"
    private const val KEY_APPEARANCE_MODE = "appearance_mode"

    const val APPEARANCE_SYSTEM = "system"
    const val APPEARANCE_LIGHT = "light"
    const val APPEARANCE_DARK = "dark"
    const val APPEARANCE_AMOLED = "amoled"
    const val APPEARANCE_CUSTOM = "custom"

    fun current(context: Context): OrbitSkin = when (appearanceMode(context)) {
        APPEARANCE_LIGHT -> OrbitSkins.OrbitLight
        APPEARANCE_DARK -> OrbitSkins.OrbitDark
        APPEARANCE_AMOLED -> OrbitSkins.AmoledBlack
        APPEARANCE_CUSTOM -> OrbitSkins.byId(savedSkinId(context))
        else -> if (systemIsNight(context)) OrbitSkins.OrbitDark else OrbitSkins.OrbitLight
    }

    fun apply(context: Context, skinId: String): Boolean {
        val target = OrbitSkins.byId(skinId)
        if (target.isPro && !ProGate.isProUnlocked(context)) return false
        prefs(context).edit()
            .putString(KEY_SELECTED_SKIN_ID, target.id)
            .putString(KEY_APPEARANCE_MODE, APPEARANCE_CUSTOM)
            .apply()
        return true
    }

    fun appearanceMode(context: Context): String {
        val value = prefs(context).getString(KEY_APPEARANCE_MODE, APPEARANCE_SYSTEM)
        return when (value) {
            APPEARANCE_SYSTEM, APPEARANCE_LIGHT, APPEARANCE_DARK, APPEARANCE_AMOLED, APPEARANCE_CUSTOM -> value
            else -> APPEARANCE_SYSTEM
        }
    }

    fun setAppearanceMode(context: Context, mode: String) {
        val safe = when (mode) {
            APPEARANCE_SYSTEM, APPEARANCE_LIGHT, APPEARANCE_DARK, APPEARANCE_AMOLED, APPEARANCE_CUSTOM -> mode
            else -> APPEARANCE_SYSTEM
        }
        prefs(context).edit().putString(KEY_APPEARANCE_MODE, safe).apply()
    }

    fun appearanceLabel(context: Context): String = when (appearanceMode(context)) {
        APPEARANCE_LIGHT -> "浅色"
        APPEARANCE_DARK -> "深色"
        APPEARANCE_AMOLED -> "AMOLED 黑"
        APPEARANCE_CUSTOM -> "自定义皮肤"
        else -> "跟随系统"
    }

    /** Saved explicit skin, independent from a resolved system light/dark appearance. */
    fun selectedSkinId(context: Context): String = savedSkinId(context)

    fun keyboardSkin(context: Context, privacyMode: Boolean): OrbitSkin {
        val current = current(context)
        return if (!privacyMode) {
            current
        } else {
            current.copy(
                panel = current.background,
                panelAlt = current.controlKey,
                key = current.controlKey,
                controlKey = current.background,
                accent = current.warning,
                border = current.warning,
            )
        }
    }

    private fun savedSkinId(context: Context): String = prefs(context)
        .getString(KEY_SELECTED_SKIN_ID, OrbitSkins.OrbitDark.id) ?: OrbitSkins.OrbitDark.id

    private fun systemIsNight(context: Context): Boolean {
        val mask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mask == Configuration.UI_MODE_NIGHT_YES
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
