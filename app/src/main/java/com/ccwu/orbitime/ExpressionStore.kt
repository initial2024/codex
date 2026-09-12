package com.ccwu.orbitime

import android.content.Context
import org.json.JSONArray

class ExpressionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun recent(limit: Int = MAX_RECENT): List<String> {
        val raw = prefs.getString(KEY_RECENT, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optString(index).trim()
                    if (value.isNotBlank()) add(value)
                }
            }.distinct().take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun record(value: String) {
        val text = value.trim()
        if (text.isBlank()) return
        val items = recent(MAX_RECENT).toMutableList()
        items.removeAll { it == text }
        items.add(0, text)
        val array = JSONArray()
        items.take(MAX_RECENT).forEach(array::put)
        prefs.edit().putString(KEY_RECENT, array.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_RECENT).apply()
    }

    companion object {
        private const val PREFS = "orbit_expression_store"
        private const val KEY_RECENT = "recent"
        private const val MAX_RECENT = 48
    }
}
