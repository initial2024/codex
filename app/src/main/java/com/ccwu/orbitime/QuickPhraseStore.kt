package com.ccwu.orbitime

import android.content.Context
import org.json.JSONArray

/** User-customizable quick phrases. Stored locally and independently for Chinese/English. */
class QuickPhraseStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun phrasesForPinyin(): List<String> = merged(KEY_PINYIN, TemplateLibrary.quickPhrasesForPinyin())

    fun phrasesForEnglish(): List<String> = merged(KEY_ENGLISH, TemplateLibrary.quickPhrasesForEnglish())

    fun customPinyin(): List<String> = read(KEY_PINYIN)

    fun customEnglish(): List<String> = read(KEY_ENGLISH)

    fun addPinyin(raw: String): Boolean = add(KEY_PINYIN, raw)

    fun addEnglish(raw: String): Boolean = add(KEY_ENGLISH, raw)

    fun removePinyin(value: String): Boolean = remove(KEY_PINYIN, value)

    fun removeEnglish(value: String): Boolean = remove(KEY_ENGLISH, value)

    fun clearCustom() {
        prefs.edit().remove(KEY_PINYIN).remove(KEY_ENGLISH).apply()
    }

    private fun merged(key: String, builtIn: List<String>): List<String> {
        if (!ImePreferences.quickPhrasesEnabled(context)) return emptyList()
        val custom = read(key)
        val base = if (ImePreferences.builtInPhrasesEnabled(context)) builtIn else emptyList()
        return (custom + base).distinct().take(ProGate.maxTemplates(context))
    }

    private fun add(key: String, raw: String): Boolean {
        val value = raw.replace("\n", " ").trim().replace(Regex("\\s{2,}"), " ")
        if (value.length !in 1..MAX_PHRASE_LENGTH) return false
        if (!PrivacyGuard.isSafeToUseForPrompt(value)) return false
        val current = read(key).toMutableList()
        current.remove(value)
        current.add(0, value)
        write(key, current.take(ProGate.maxTemplates(context)))
        return true
    }

    private fun remove(key: String, value: String): Boolean {
        val current = read(key).toMutableList()
        val changed = current.remove(value)
        if (changed) write(key, current)
        return changed
    }

    private fun read(key: String): List<String> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val value = array.optString(i).trim()
                    if (value.isNotEmpty()) add(value)
                }
            }.distinct().take(ProGate.maxTemplates(context))
        }.getOrElse { emptyList() }
    }

    private fun write(key: String, values: List<String>) {
        val array = JSONArray()
        values.forEach(array::put)
        prefs.edit().putString(key, array.toString()).apply()
    }

    companion object {
        private const val PREFS = "orbit_quick_phrases"
        private const val KEY_PINYIN = "custom_pinyin"
        private const val KEY_ENGLISH = "custom_english"
        private const val MAX_PHRASE_LENGTH = 180
    }
}
