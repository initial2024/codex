package com.ccwu.orbitime

import android.content.Context

object SkinManager {
    private const val PREFS = "orbit_skin_prefs"
    private const val KEY_SELECTED_SKIN_ID = "selected_skin_id"

    fun current(context: Context): OrbitSkin {
        val selectedId = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_SKIN_ID, OrbitSkins.OrbitDark.id)
        return OrbitSkins.byId(selectedId)
    }

    fun apply(context: Context, skinId: String): Boolean {
        val target = OrbitSkins.byId(skinId)
        if (target.isPro && !ProGate.isProUnlocked(context)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_SKIN_ID, target.id)
            .apply()
        return true
    }

    fun selectedSkinId(context: Context): String = current(context).id

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
}
