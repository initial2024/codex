package com.ccwu.orbitime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class UserDictionaryStore(private val context: Context) {
    data class Entry(
        val pinyin: String,
        val text: String,
        val frequency: Int,
        val updatedAt: Long,
    )

    data class Stats(
        val entryCount: Int,
        val totalFrequency: Int,
        val maxEntries: Int,
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val imeEngine: PinyinImeEngine by lazy(LazyThreadSafetyMode.NONE) {
        PinyinImeEngine(context.applicationContext, this)
    }

    fun candidatesFor(rawInput: String, staticCandidates: List<String>): List<String> {
        val query = PinyinDictionary.normalize(rawInput)
        if (query.isEmpty()) return staticCandidates.take(MAX_CANDIDATES)

        val engineCandidates = runCatching { imeEngine.candidates(query, limit = MAX_CANDIDATES) }
            .getOrElse { emptyList() }
        if (engineCandidates.isNotEmpty()) {
            return (engineCandidates + staticCandidates).distinct().take(MAX_CANDIDATES)
        }

        // Fail-safe legacy path: the keyboard must keep producing candidates even
        // if a packaged asset is malformed or the new engine rejects a query.
        val entries = loadEntries()
        val exactUser = entries
            .filter { it.pinyin == query }
            .sortedWith(compareByDescending<Entry> { it.frequency }.thenByDescending { it.updatedAt })
            .map { it.text }
        val prefixUser = entries
            .filter { it.pinyin != query && it.pinyin.startsWith(query) }
            .sortedWith(compareByDescending<Entry> { it.frequency }.thenByDescending { it.updatedAt })
            .map { it.text }
        val containsUser = if (exactUser.isEmpty() && prefixUser.size < 4) {
            entries
                .filter { it.pinyin != query && it.pinyin.contains(query) }
                .sortedWith(compareByDescending<Entry> { it.frequency }.thenByDescending { it.updatedAt })
                .map { it.text }
        } else {
            emptyList()
        }
        return (exactUser + staticCandidates + prefixUser + containsUser)
            .distinct()
            .take(MAX_CANDIDATES)
    }

    fun exactCandidatesFor(rawInput: String, staticCandidates: List<String>): List<String> {
        val query = PinyinDictionary.normalize(rawInput)
        if (query.isEmpty()) return staticCandidates.take(MAX_CANDIDATES)
        val engineCandidates = runCatching { imeEngine.exactCandidates(query, limit = MAX_CANDIDATES) }
            .getOrElse { emptyList() }
        if (engineCandidates.isNotEmpty()) {
            return (engineCandidates + staticCandidates).distinct().take(MAX_CANDIDATES)
        }
        val exactUser = loadEntries()
            .filter { it.pinyin == query }
            .sortedWith(compareByDescending<Entry> { it.frequency }.thenByDescending { it.updatedAt })
            .map { it.text }
        return (exactUser + staticCandidates)
            .distinct()
            .take(MAX_CANDIDATES)
    }

    /** Exact learned frequency used as a personalization feature by CandidateRanker. */
    fun frequencyFor(rawPinyin: String, rawText: String): Int {
        val pinyin = PinyinDictionary.normalize(rawPinyin)
        val text = rawText.trim()
        if (pinyin.isEmpty() || text.isEmpty()) return 0
        return loadEntries()
            .asSequence()
            .filter { it.pinyin == pinyin && it.text == text }
            .maxOfOrNull { it.frequency }
            ?: 0
    }

    /**
     * Returns learned entries relevant to the current composition without
     * exposing or persisting surrounding text.
     */
    fun learnedEntriesFor(rawInput: String, limit: Int = MAX_CANDIDATES): List<Entry> {
        val query = PinyinDictionary.normalize(rawInput)
        if (query.isEmpty()) return emptyList()
        val entries = loadEntries()
        val exact = entries.filter { it.pinyin == query }
        val prefix = entries.filter { it.pinyin != query && it.pinyin.startsWith(query) }
        return (exact + prefix)
            .sortedWith(compareByDescending<Entry> { it.frequency }.thenByDescending { it.updatedAt })
            .distinctBy { it.pinyin to it.text }
            .take(limit)
    }

    fun learn(rawPinyin: String, rawText: String): Boolean {
        val pinyin = PinyinDictionary.normalize(rawPinyin)
        val text = rawText.trim()
        if (!canLearn(pinyin, text)) return false

        val now = System.currentTimeMillis()
        val entries = loadEntries().toMutableList()
        val index = entries.indexOfFirst { it.pinyin == pinyin && it.text == text }
        if (index >= 0) {
            val old = entries[index]
            entries[index] = old.copy(
                frequency = (old.frequency + 1).coerceAtMost(MAX_FREQUENCY),
                updatedAt = now,
            )
        } else {
            entries.add(
                Entry(
                    pinyin = pinyin,
                    text = text,
                    frequency = 1,
                    updatedAt = now,
                ),
            )
        }

        saveEntries(trimEntries(entries))
        return true
    }

    fun stats(): Stats {
        val entries = loadEntries()
        return Stats(
            entryCount = entries.size,
            totalFrequency = entries.sumOf { it.frequency },
            maxEntries = maxEntries(),
        )
    }

    fun clear() {
        prefs.edit().remove(KEY_ENTRIES_JSON).apply()
    }

    private fun canLearn(pinyin: String, text: String): Boolean {
        if (pinyin.length !in 1..64) return false
        if (text.length !in 1..40) return false
        if (text == pinyin) return false
        if (!containsCjk(text)) return false
        if (!PrivacyGuard.isSafeToUseForPrompt(text)) return false
        return true
    }

    private fun containsCjk(text: String): Boolean {
        return text.any { char ->
            val block = Character.UnicodeBlock.of(char)
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
                block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
        }
    }

    private fun trimEntries(entries: List<Entry>): List<Entry> {
        return entries
            .sortedWith(compareByDescending<Entry> { it.frequency }.thenByDescending { it.updatedAt })
            .take(maxEntries())
    }

    private fun maxEntries(): Int = ProGate.maxUserDictionaryItems(context)

    private fun loadEntries(): List<Entry> {
        val raw = prefs.getString(KEY_ENTRIES_JSON, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val pinyin = PinyinDictionary.normalize(item.optString("pinyin"))
                    val text = item.optString("text").trim()
                    val frequency = item.optInt("frequency", 1).coerceIn(1, MAX_FREQUENCY)
                    val updatedAt = item.optLong("updatedAt", 0L)
                    if (canLearn(pinyin, text)) {
                        add(Entry(pinyin, text, frequency, updatedAt))
                    }
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun saveEntries(entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("pinyin", entry.pinyin)
                    .put("text", entry.text)
                    .put("frequency", entry.frequency)
                    .put("updatedAt", entry.updatedAt),
            )
        }
        prefs.edit().putString(KEY_ENTRIES_JSON, array.toString()).apply()
    }

    companion object {
        private const val PREFS = "orbit_user_dictionary"
        private const val KEY_ENTRIES_JSON = "entries_json"
        private const val MAX_CANDIDATES = 12
        private const val MAX_FREQUENCY = 9999
    }
}
