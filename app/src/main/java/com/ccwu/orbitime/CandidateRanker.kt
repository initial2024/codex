package com.ccwu.orbitime

import kotlin.math.ln

object CandidateRanker {
    data class Candidate(
        val text: String,
        val tokens: List<String>,
        val staticFrequency: Int,
        val segmentationScore: Double,
        val ngramScore: Double,
        val userFrequency: Int,
        val correctionPenalty: Double = 0.0,
        val sourcePriority: Int = 0,
    )

    data class Ranked(
        val text: String,
        val score: Double,
        val candidate: Candidate,
    )

    fun rank(candidates: List<Candidate>, limit: Int = 12): List<Ranked> {
        val bestByText = LinkedHashMap<String, Ranked>()
        candidates.forEach { candidate ->
            if (candidate.text.isBlank()) return@forEach
            val score = score(candidate)
            val ranked = Ranked(candidate.text, score, candidate)
            val previous = bestByText[candidate.text]
            if (previous == null || ranked.score > previous.score) {
                bestByText[candidate.text] = ranked
            }
        }
        return bestByText.values
            .sortedWith(compareByDescending<Ranked> { it.score }.thenBy { it.text.length })
            .take(limit)
    }

    fun score(candidate: Candidate): Double {
        val staticPart = STATIC_FREQ_WEIGHT * ln(1.0 + candidate.staticFrequency.coerceAtLeast(0))
        val userPart = USER_FREQ_WEIGHT * ln(1.0 + candidate.userFrequency.coerceAtLeast(0))
        val sourcePart = candidate.sourcePriority * SOURCE_PRIORITY_WEIGHT
        val lengthBonus = candidate.text.length.coerceAtMost(12) * LENGTH_WEIGHT
        return staticPart +
            userPart +
            candidate.segmentationScore +
            candidate.ngramScore +
            sourcePart +
            lengthBonus -
            candidate.correctionPenalty
    }

    private const val STATIC_FREQ_WEIGHT = 1.00
    private const val USER_FREQ_WEIGHT = 2.25
    private const val SOURCE_PRIORITY_WEIGHT = 0.55
    private const val LENGTH_WEIGHT = 0.035
}
