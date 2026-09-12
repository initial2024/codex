package com.ccwu.orbitime

import android.content.Context

/** Persistent user-facing IME preferences. */
object ImePreferences {
    private const val PREFS = "orbit_ime_preferences"
    private const val KEY_INPUT_MODE = "input_mode"
    private const val KEY_QUICK_PHRASES_ENABLED = "quick_phrases_enabled"
    private const val KEY_BUILTIN_PHRASES_ENABLED = "builtin_phrases_enabled"
    private const val KEY_ASSOCIATIONS_ENABLED = "associations_enabled"
    private const val KEY_FUZZY_LEVEL = "fuzzy_level"

    const val MODE_PINYIN = "pinyin"
    const val MODE_ENGLISH = "english"

    const val FUZZY_OFF = "off"
    const val FUZZY_STANDARD = "standard"
    const val FUZZY_ENHANCED = "enhanced"

    fun inputMode(context: Context): String = prefs(context).getString(KEY_INPUT_MODE, MODE_PINYIN) ?: MODE_PINYIN

    fun setInputMode(context: Context, mode: String) {
        val safe = if (mode == MODE_ENGLISH) MODE_ENGLISH else MODE_PINYIN
        prefs(context).edit().putString(KEY_INPUT_MODE, safe).apply()
    }

    fun quickPhrasesEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_QUICK_PHRASES_ENABLED, true)
    fun setQuickPhrasesEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_QUICK_PHRASES_ENABLED, enabled).apply()
    }

    fun builtInPhrasesEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_BUILTIN_PHRASES_ENABLED, true)
    fun setBuiltInPhrasesEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BUILTIN_PHRASES_ENABLED, enabled).apply()
    }

    fun associationsEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ASSOCIATIONS_ENABLED, true)
    fun setAssociationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ASSOCIATIONS_ENABLED, enabled).apply()
    }

    fun fuzzyLevel(context: Context): String {
        val value = prefs(context).getString(KEY_FUZZY_LEVEL, FUZZY_STANDARD)
        return when (value) {
            FUZZY_OFF, FUZZY_STANDARD, FUZZY_ENHANCED -> value
            else -> FUZZY_STANDARD
        }
    }

    fun setFuzzyLevel(context: Context, level: String) {
        val safe = when (level) {
            FUZZY_OFF, FUZZY_STANDARD, FUZZY_ENHANCED -> level
            else -> FUZZY_STANDARD
        }
        prefs(context).edit().putString(KEY_FUZZY_LEVEL, safe).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
