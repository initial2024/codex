package com.ccwu.orbitime

import android.content.Context
import kotlin.math.ln
import kotlin.math.min

/**
 * Offline Pinyin candidate engine used by the keyboard service.
 *
 * Pipeline:
 * 1. exact sentence / user / asset candidates
 * 2. DP Pinyin segmentation
 * 3. phrase-level beam search
 * 4. static frequency + N-gram + local user-frequency ranking
 * 5. fuzzy / typo candidates with an explicit penalty
 */
class PinyinImeEngine(
    context: Context,
    private val userDictionary: UserDictionaryStore,
) {
    private val lexicon = CompactLexiconAsset(context.applicationContext)
    private val languageModel = NGramLanguageModel(context.applicationContext)

    fun candidates(rawInput: String, contextBeforeCursor: String? = null, limit: Int = MAX_RESULTS): List<String> {
        val query = PinyinDictionary.normalize(rawInput)
        if (query.isEmpty()) return emptyList()

        val contextTokens = extractContextTokens(contextBeforeCursor.orEmpty())
        val pool = mutableListOf<CandidateRanker.Candidate>()

        addUserCandidates(query, contextTokens, pool)
        addExactCandidates(query, contextTokens, pool)
        addSegmentedCandidates(query, contextTokens, pool)
        addCorrectionCandidates(query, contextTokens, pool)

        val ranked = CandidateRanker.rank(pool, limit)
            .map { it.text }
            .distinct()
            .toMutableList()

        if (ranked.isEmpty()) {
            ranked += PinyinSentenceDictionary.candidatesFor(query)
            ranked += PinyinDictionary.candidatesFor(query)
        }
        return ranked.distinct().take(limit)
    }

    fun exactCandidates(rawInput: String, contextBeforeCursor: String? = null, limit: Int = MAX_RESULTS): List<String> {
        val query = PinyinDictionary.normalize(rawInput)
        if (query.isEmpty()) return emptyList()
        val contextTokens = extractContextTokens(contextBeforeCursor.orEmpty())
        val pool = mutableListOf<CandidateRanker.Candidate>()
        addUserCandidates(query, contextTokens, pool, exactOnly = true)
        addExactCandidates(query, contextTokens, pool)
        return CandidateRanker.rank(pool, limit).map { it.text }.distinct().take(limit)
    }

    fun debugSegmentation(rawInput: String): List<PinyinSegmenter.Segmentation> = PinyinSegmenter.segment(rawInput)

    private fun addUserCandidates(
        query: String,
        contextTokens: List<String>,
        pool: MutableList<CandidateRanker.Candidate>,
        exactOnly: Boolean = false,
    ) {
        userDictionary.learnedEntriesFor(query, MAX_RESULTS * 2)
            .filter { !exactOnly || it.pinyin == query }
            .forEach { entry ->
                pool += candidate(
                    query = query,
                    text = entry.text,
                    tokens = listOf(entry.text),
                    staticFrequency = USER_BASE_STATIC_FREQUENCY,
                    segmentationScore = if (entry.pinyin == query) 3.0 else 1.5,
                    contextTokens = contextTokens,
                    correctionPenalty = if (entry.pinyin == query) 0.0 else 0.8,
                    sourcePriority = 7,
                    userFrequencyOverride = entry.frequency,
                )
            }
    }

    private fun addExactCandidates(
        query: String,
        contextTokens: List<String>,
        pool: MutableList<CandidateRanker.Candidate>,
    ) {
        lexicon.exact(query).forEachIndexed { index, entry ->
            pool += candidate(
                query = query,
                text = entry.text,
                tokens = listOf(entry.text),
                staticFrequency = entry.frequency,
                segmentationScore = 4.0,
                contextTokens = contextTokens,
                sourcePriority = 6 - min(index, 3),
            )
        }

        val legacyExact = linkedSetOf<String>().apply {
            addAll(PinyinSentenceDictionary.exactCandidatesFor(query))
            addAll(PinyinExpandedData.entries[query].orEmpty())
            addAll(PinyinBoostData.entries[query].orEmpty())
            addAll(PinyinDictionary.exactCandidatesFor(query))
        }
        legacyExact.forEachIndexed { index, text ->
            pool += candidate(
                query = query,
                text = text,
                tokens = listOf(text),
                staticFrequency = syntheticFrequency(index, 820_000),
                segmentationScore = 3.5,
                contextTokens = contextTokens,
                sourcePriority = 5,
            )
        }
    }

    private fun addSegmentedCandidates(
        query: String,
        contextTokens: List<String>,
        pool: MutableList<CandidateRanker.Candidate>,
    ) {
        val segmentations = PinyinSegmenter.segment(query, MAX_SEGMENTATIONS)
        segmentations.forEachIndexed { segmentationIndex, segmentation ->
            val generated = beamGenerate(segmentation, contextTokens)
            generated.forEachIndexed { beamIndex, hypothesis ->
                if (hypothesis.text.isBlank()) return@forEachIndexed
                val averageFrequency = if (hypothesis.parts == 0) 1 else hypothesis.totalFrequency / hypothesis.parts
                pool += candidate(
                    query = query,
                    text = hypothesis.text,
                    tokens = hypothesis.tokens,
                    staticFrequency = averageFrequency.coerceAtLeast(1),
                    segmentationScore = segmentation.score - segmentationIndex * 0.35,
                    contextTokens = contextTokens,
                    sourcePriority = (4 - min(beamIndex, 2)).coerceAtLeast(1),
                )
            }
        }
    }

    private fun addCorrectionCandidates(
        query: String,
        contextTokens: List<String>,
        pool: MutableList<CandidateRanker.Candidate>,
    ) {
        PinyinCorrectionEngine.candidatesFor(query).forEachIndexed { index, text ->
            pool += candidate(
                query = query,
                text = text,
                tokens = listOf(text),
                staticFrequency = syntheticFrequency(index, 520_000),
                segmentationScore = 0.0,
                contextTokens = contextTokens,
                correctionPenalty = 3.2 + index * 0.18,
                sourcePriority = 2,
            )
        }
    }

    private data class Hypothesis(
        val position: Int,
        val text: String,
        val tokens: List<String>,
        val totalFrequency: Int,
        val parts: Int,
        val beamScore: Double,
    )

    private fun beamGenerate(
        segmentation: PinyinSegmenter.Segmentation,
        contextTokens: List<String>,
    ): List<Hypothesis> {
        val syllables = segmentation.syllables
        if (syllables.isEmpty()) return emptyList()
        var beam = listOf(Hypothesis(0, "", emptyList(), 0, 0, 0.0))

        while (beam.isNotEmpty() && beam.any { it.position < syllables.size }) {
            val next = mutableListOf<Hypothesis>()
            beam.forEach { hypothesis ->
                if (hypothesis.position >= syllables.size) {
                    next += hypothesis
                    return@forEach
                }
                val maxSpan = min(MAX_PHRASE_SYLLABLES, syllables.size - hypothesis.position)
                for (span in 1..maxSpan) {
                    val key = syllables.subList(hypothesis.position, hypothesis.position + span).joinToString("")
                    val entries = lexicalEntriesFor(key, span)
                    entries.take(MAX_ENTRIES_PER_SPAN).forEach { entry ->
                        val previous2 = (contextTokens + hypothesis.tokens).getOrNull((contextTokens + hypothesis.tokens).size - 2)
                        val previous1 = (contextTokens + hypothesis.tokens).lastOrNull()
                        val lm = languageModel.transitionScore(previous2, previous1, entry.text)
                        val freq = ln(1.0 + entry.frequency.coerceAtLeast(1)) * 0.75
                        next += Hypothesis(
                            position = hypothesis.position + span,
                            text = hypothesis.text + entry.text,
                            tokens = hypothesis.tokens + entry.text,
                            totalFrequency = hypothesis.totalFrequency + entry.frequency,
                            parts = hypothesis.parts + 1,
                            beamScore = hypothesis.beamScore + lm + freq + span * 0.20,
                        )
                    }
                }
            }
            beam = next
                .sortedByDescending { it.beamScore }
                .distinctBy { it.position to it.text }
                .take(BEAM_WIDTH)
            if (beam.isEmpty()) break
        }

        return beam
            .filter { it.position == syllables.size }
            .sortedByDescending { it.beamScore }
            .take(MAX_BEAM_RESULTS)
    }

    private data class LexicalEntry(val text: String, val frequency: Int)

    private fun lexicalEntriesFor(key: String, syllableSpan: Int): List<LexicalEntry> {
        val asset = lexicon.exact(key).map { LexicalEntry(it.text, it.frequency) }
        if (asset.isNotEmpty()) return asset

        val legacy = linkedSetOf<String>().apply {
            addAll(PinyinExpandedData.entries[key].orEmpty())
            addAll(PinyinBoostData.entries[key].orEmpty())
            addAll(PinyinSentenceDictionary.exactCandidatesFor(key))
            if (syllableSpan == 1) addAll(PinyinDictionary.exactCandidatesFor(key))
        }
        return legacy.mapIndexed { index, text ->
            LexicalEntry(text, syntheticFrequency(index, if (syllableSpan > 1) 650_000 else 540_000))
        }
    }

    private fun candidate(
        query: String,
        text: String,
        tokens: List<String>,
        staticFrequency: Int,
        segmentationScore: Double,
        contextTokens: List<String>,
        correctionPenalty: Double = 0.0,
        sourcePriority: Int = 0,
        userFrequencyOverride: Int? = null,
    ): CandidateRanker.Candidate {
        val userFrequency = userFrequencyOverride ?: userDictionary.frequencyFor(query, text)
        return CandidateRanker.Candidate(
            text = text,
            tokens = tokens,
            staticFrequency = staticFrequency,
            segmentationScore = segmentationScore,
            ngramScore = languageModel.scoreSequence(contextTokens, tokens),
            userFrequency = userFrequency,
            correctionPenalty = correctionPenalty,
            sourcePriority = sourcePriority,
        )
    }

    private fun extractContextTokens(raw: String): List<String> {
        val tail = raw.takeLast(48)
        if (tail.isBlank()) return emptyList()
        val tokens = mutableListOf<String>()
        val latin = StringBuilder()
        fun flushLatin() {
            if (latin.isNotEmpty()) {
                tokens += latin.toString().lowercase()
                latin.clear()
            }
        }
        tail.forEach { char ->
            when {
                char.isLetterOrDigit() && char.code < 128 -> latin.append(char)
                isCjk(char) -> {
                    flushLatin()
                    tokens += char.toString()
                }
                else -> flushLatin()
            }
        }
        flushLatin()
        return tokens.takeLast(4)
    }

    private fun isCjk(char: Char): Boolean {
        val block = Character.UnicodeBlock.of(char)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
    }

    private fun syntheticFrequency(index: Int, base: Int): Int {
        return (base - index * 28_000).coerceAtLeast(25_000)
    }

    companion object {
        private const val MAX_RESULTS = 12
        private const val MAX_SEGMENTATIONS = 5
        private const val MAX_PHRASE_SYLLABLES = 4
        private const val MAX_ENTRIES_PER_SPAN = 5
        private const val BEAM_WIDTH = 36
        private const val MAX_BEAM_RESULTS = 16
        private const val USER_BASE_STATIC_FREQUENCY = 700_000
    }
}
