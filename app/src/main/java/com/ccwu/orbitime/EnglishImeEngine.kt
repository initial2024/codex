package com.ccwu.orbitime

import android.content.Context
import java.util.LinkedHashMap
import kotlin.math.ln

/** English composing candidate engine with frequency ranking and bounded typo recovery. */
class EnglishImeEngine(context: Context) {
    private val appContext = context.applicationContext
    private val asset = CompactEnglishAsset(appContext)

    private val cache = object : LinkedHashMap<String, List<String>>(96, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean = size > MAX_CACHE
    }

    @Synchronized
    fun candidatesFor(rawInput: String, limit: Int = DEFAULT_LIMIT): List<String> {
        val query = EnglishDictionary.normalize(rawInput)
        if (query.isEmpty()) return emptyList()
        val fuzzyLevel = ImePreferences.fuzzyLevel(appContext)
        val safeLimit = limit.coerceIn(1, MAX_LIMIT)
        val cacheKey = "$query\u0000$fuzzyLevel\u0000$safeLimit"
        cache[cacheKey]?.let { return it }

        // Fetch a much larger prefix pool than the visible row. Ranking then keeps
        // common completions ahead of obscure SCOWL entries without reducing coverage.
        val primary = asset.candidatesFor(query, safeLimit * PRIMARY_MULTIPLIER)
        val legacy = EnglishDictionary.candidatesFor(rawInput)
        val fuzzy = if (fuzzyLevel == ImePreferences.FUZZY_OFF || query.length > MAX_FUZZY_QUERY_CHARS) {
            emptyList()
        } else {
            val enhanced = fuzzyLevel == ImePreferences.FUZZY_ENHANCED
            EnglishFuzzyEngine.variants(query, enhanced, if (enhanced) 96 else 48)
                .take(if (enhanced) 64 else 32)
                .flatMap { variant -> asset.candidatesFor(variant, if (enhanced) 7 else 5) }
        }

        val ranked = (primary + legacy + fuzzy)
            .distinct()
            .sortedByDescending { candidate -> score(query, candidate) }
            .take(safeLimit)
        cache[cacheKey] = ranked
        return ranked
    }

    @Synchronized
    fun clearCache() = cache.clear()

    private fun score(query: String, candidate: String): Double {
        val normalizedCandidate = EnglishDictionary.normalize(candidate)
        val exactBoost = if (normalizedCandidate == query) 5.0 else 0.0
        val prefixBoost = if (normalizedCandidate.startsWith(query)) {
            val extra = (normalizedCandidate.length - query.length).coerceAtLeast(0)
            2.8 - extra.coerceAtMost(16) * 0.035
        } else 0.0
        val packagedFreq = asset.frequencyFor(normalizedCandidate)
        val frequencyScore = if (packagedFreq > 0) ln(1.0 + packagedFreq) else 0.0
        val distancePenalty = normalizedEditDistancePenalty(query, normalizedCandidate)
        val phrasePenalty = when {
            candidate.length > 64 -> 1.3
            candidate.length > 40 -> 0.65
            else -> 0.0
        }
        return exactBoost + prefixBoost + frequencyScore - distancePenalty - phrasePenalty
    }

    private fun normalizedEditDistancePenalty(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty() || b.startsWith(a)) return 0.0
        val lengthGap = kotlin.math.abs(a.length - b.length)
        var mismatches = lengthGap
        val shared = minOf(a.length, b.length)
        for (index in 0 until shared) if (a[index] != b[index]) mismatches++
        return mismatches.coerceAtMost(6) * 0.44
    }

    companion object {
        const val DEFAULT_LIMIT = 48
        const val MAX_LIMIT = 64
        private const val PRIMARY_MULTIPLIER = 5
        private const val MAX_FUZZY_QUERY_CHARS = 36
        private const val MAX_CACHE = 96
    }
}
