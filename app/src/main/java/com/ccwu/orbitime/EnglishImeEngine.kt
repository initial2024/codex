package com.ccwu.orbitime

import android.content.Context
import kotlin.math.ln

/** English composing candidate engine with frequency ranking and bounded typo recovery. */
class EnglishImeEngine(context: Context) {
    private val appContext = context.applicationContext
    private val asset = CompactEnglishAsset(appContext)

    fun candidatesFor(rawInput: String, limit: Int = 32): List<String> {
        val query = EnglishDictionary.normalize(rawInput)
        if (query.isEmpty()) return emptyList()

        val primary = asset.candidatesFor(query, limit * 3)
        val legacy = EnglishDictionary.candidatesFor(rawInput)
        val fuzzyLevel = ImePreferences.fuzzyLevel(appContext)
        val fuzzy = if (fuzzyLevel == ImePreferences.FUZZY_OFF) {
            emptyList()
        } else {
            val enhanced = fuzzyLevel == ImePreferences.FUZZY_ENHANCED
            EnglishFuzzyEngine.variants(query, enhanced)
                .take(if (enhanced) 24 else 12)
                .flatMap { variant -> asset.candidatesFor(variant, if (enhanced) 4 else 3) }
        }

        return (primary + legacy + fuzzy)
            .distinct()
            .sortedByDescending { candidate -> score(query, candidate) }
            .take(limit)
    }

    private fun score(query: String, candidate: String): Double {
        val normalizedCandidate = EnglishDictionary.normalize(candidate)
        val exactBoost = if (normalizedCandidate == query) 4.0 else 0.0
        val prefixBoost = if (normalizedCandidate.startsWith(query)) 2.0 else 0.0
        val packagedFreq = asset.frequencyFor(normalizedCandidate)
        val frequencyScore = if (packagedFreq > 0) ln(1.0 + packagedFreq) else 0.0
        val distancePenalty = normalizedEditDistancePenalty(query, normalizedCandidate)
        val phrasePenalty = if (candidate.length > 36) 0.8 else 0.0
        return exactBoost + prefixBoost + frequencyScore - distancePenalty - phrasePenalty
    }

    private fun normalizedEditDistancePenalty(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty() || b.startsWith(a)) return 0.0
        val lengthGap = kotlin.math.abs(a.length - b.length)
        var mismatches = lengthGap
        val shared = minOf(a.length, b.length)
        for (index in 0 until shared) if (a[index] != b[index]) mismatches++
        return mismatches.coerceAtMost(4) * 0.42
    }
}
