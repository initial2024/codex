package com.ccwu.orbitime

import android.content.Context
import kotlin.math.ln

/**
 * English composing candidate engine.
 * Packaged frequency assets rank first; existing project-authored candidates
 * remain a fail-safe and typo/phrase layer.
 */
class EnglishImeEngine(context: Context) {
    private val asset = CompactEnglishAsset(context.applicationContext)

    fun candidatesFor(rawInput: String, limit: Int = 12): List<String> {
        val query = EnglishDictionary.normalize(rawInput)
        if (query.isEmpty()) return emptyList()

        val assetCandidates = asset.candidatesFor(query, limit * 2)
        val legacyCandidates = EnglishDictionary.candidatesFor(rawInput)

        return (assetCandidates + legacyCandidates)
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
        val phrasePenalty = if (candidate.length > 36) 0.8 else 0.0
        return exactBoost + prefixBoost + frequencyScore - phrasePenalty
    }
}
