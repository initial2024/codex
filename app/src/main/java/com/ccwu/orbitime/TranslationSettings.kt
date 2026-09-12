package com.ccwu.orbitime

import android.content.Context

/** Local translation preferences. Context translation is opt-in and Pro-gated. */
object TranslationSettings {
    private const val PREFS = "orbit_translation_settings"
    private const val KEY_CONTEXT_ENABLED = "context_translation_enabled"

    fun isContextTranslationAvailable(context: Context): Boolean = ProGate.isProUnlocked(context)

    fun isContextTranslationEnabled(context: Context): Boolean {
        if (!isContextTranslationAvailable(context)) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_CONTEXT_ENABLED, false)
    }

    fun setContextTranslationEnabled(context: Context, enabled: Boolean): Boolean {
        if (!isContextTranslationAvailable(context)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CONTEXT_ENABLED, enabled)
            .apply()
        return true
    }

    fun toggleContextTranslation(context: Context): Boolean {
        val next = !isContextTranslationEnabled(context)
        return setContextTranslationEnabled(context, next)
    }
}
