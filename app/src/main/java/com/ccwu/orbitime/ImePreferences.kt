package com.ccwu.orbitime

import android.content.Context

/** Persistent user-facing IME preferences. */
object ImePreferences {
    private const val PREFS = "orbit_ime_preferences"
    private const val KEY_INPUT_MODE = "input_mode"
    private const val KEY_QUICK_PHRASES_ENABLED = "quick_phrases_enabled"
    private const val KEY_BUILTIN_PHRASES_ENABLED = "builtin_phrases_enabled"
    private const val KEY_ASSOCIATIONS_ENABLED = "associations_enabled"

    const val MODE_PINYIN = "pinyin"
    const val MODE_ENGLISH = "english"

    fun inputMode(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_INPUT_MODE, MODE_PINYIN) ?: MODE_PINYIN

    fun setInputMode(context: Context, mode: String) {
        val safe = if (mode == MODE_ENGLISH) MODE_ENGLISH else MODE_PINYIN
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_INPUT_MODE, safe).apply()
    }

    fun quickPhrasesEnabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_QUICK_PHRASES_ENABLED, true)

    fun setQuickPhrasesEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_QUICK_PHRASES_ENABLED, enabled).apply()
    }

    fun builtInPhrasesEnabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_BUILTIN_PHRASES_ENABLED, true)

    fun setBuiltInPhrasesEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_BUILTIN_PHRASES_ENABLED, enabled).apply()
    }

    fun associationsEnabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_ASSOCIATIONS_ENABLED, true)

    fun setAssociationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASSOCIATIONS_ENABLED, enabled).apply()
    }
}
