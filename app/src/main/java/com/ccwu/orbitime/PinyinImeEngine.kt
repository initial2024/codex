package com.ccwu.orbitime

import android.content.Context
import kotlin.math.ln
import kotlin.math.min

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
        addUser(query, contextTokens, pool, exactOnly = false)
        addExact(query, contextTokens, pool)
        addSegmented(query, contextTokens, pool)
        addCorrections(query, contextTokens, pool)
        val ranked = CandidateRanker.rank(pool, limit).map { it.text }.distinct()
        if (ranked.isNotEmpty()) return ranked.take(limit)
        return (PinyinSentenceDictionary.candidatesFor(query) + PinyinDictionary.candidatesFor(query))
            .distinct().take(limit)
    }

    fun exactCandidates(rawInput: String, contextBeforeCursor: String? = null, limit: Int = MAX_RESULTS): List<String> {
        val query = PinyinDictionary.normalize(rawInput)
        if (query.isEmpty()) return emptyList()
        val contextTokens = extractContextTokens(contextBeforeCursor.orEmpty())
        val pool = mutableListOf<CandidateRanker.Candidate>()
        addUser(query, contextTokens, pool, exactOnly = true)
        addExact(query, contextTokens, pool)
        return CandidateRanker.rank(pool, limit).map { it.text }.distinct().take(limit)
    }

    fun debugSegmentation(rawInput: String): List<PinyinSegmenter.Segmentation> = PinyinSegmenter.segment(rawInput)

    private fun addUser(
        query: String,
        contextTokens: List<String>,
        pool: MutableList<CandidateRanker.Candidate>,
        exactOnly: Boolean,
    ) {
        userDictionary.learnedEntriesFor(query, MAX_RESULTS * 2)
            .asSequence()
            .filter { !exactOnly || it.pinyin == query }
            .forEach { entry ->
                pool += makeCandidate(
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

    private fun addExact(
        query: String,
        contextTokens: List<String>,
        pool: MutableList<CandidateRanker.Candidate>,
    ) {
        lexicon.exact(query).forEachIndexed { index, entry ->
            pool += makeCandidate(
                query, entry.text, listOf(entry.text), entry.frequency, 4.0, contextTokens,
                sourcePriority = 6 - min(index, 3),
            )
        }
        val legacy = linkedSetOf<String>().apply {
            addAll(PinyinSentenceDictionary.exactCandidatesFor(query))
            addAll(PinyinExpandedData.entries[query].orEmpty())
            addAll(PinyinBoostData.entries[query].orEmpty())
            addAll(PinyinDictionary.exactCandidatesFor(query))
        }
        legacy.forEachIndexed { index, text ->
            pool += makeCandidate(
                query, text, listOf(text), syntheticFrequency(index, 820_000), 3.5, contextTokens,
                sourcePriority = 5,
            )
        }
    }

    private fun addSegmented(
        query: String,
        contextTokens: List<String>,
        pool: MutableList<CandidateRanker.Candidate>,
    ) {
        PinyinSegmenter.segment(query, MAX_SEGMENTATIONS).forEachIndexed { segmentationIndex, segmentation ->
            beamGenerate(segmentation, contextTokens).forEachIndexed { beamIndex, hypothesis ->
                if (hypothesis.text.isBlank()) return@forEachIndexed
                val averageFrequency = if (hypothesis.parts == 0) 1 else hypothesis.totalFrequency / hypothesis.parts
                pool += makeCandidate(
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

    private fun addCorrections(
        query: String,
        contextTokens: List<String>,
        pool: MutableList<CandidateRanker.Candidate>,
    ) {
        PinyinCorrectionEngine.candidatesFor(query).forEachIndexed { index, text ->
            pool += makeCandidate(
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

    private data class LexicalEntry(val text: String, val frequency: Int)

    private fun beamGenerate(
        segmentation: PinyinSegmenter.Segmentation,
        contextTokens: List<String>,
    ): List<Hypothesis> {
        val syllables = segmentation.syllables
        if (syllables.isEmpty()) return emptyList()
        var beam = listOf(Hypothesis(0, "", emptyList(), 0, 0, 0.0))
        while (beam.any { it.position < syllables.size }) {
            val next = mutableListOf<Hypothesis>()
            beam.forEach { hypothesis ->
                if (hypothesis.position >= syllables.size) {
                    next += hypothesis
                    return@forEach
                }
                val maxSpan = min(MAX_PHRASE_SYLLABLES, syllables.size - hypothesis.position)
                for (span in 1..maxSpan) {
                    val key = syllables.subList(hypothesis.position, hypothesis.position + span).joinToString("")
                    lexicalEntriesFor(key, span).take(MAX_ENTRIES_PER_SPAN).forEach { entry ->
                        val history = contextTokens + hypothesis.tokens
                        val previous2 = history.getOrNull(history.size - 2)
                        val previous1 = history.lastOrNull()
                        val lm = languageModel.transitionScore(previous2, previous1, entry.text)
                        val freqScore = ln(1.0 + entry.frequency.coerceAtLeast(1)) * 0.75
                        next += Hypothesis(
                            position = hypothesis.position + span,
                            text = hypothesis.text + entry.text,
                            tokens = hypothesis.tokens + entry.text,
                            totalFrequency = hypothesis.totalFrequency + entry.frequency,
                            parts = hypothesis.parts + 1,
                            beamScore = hypothesis.beamScore + lm + freqScore + span * 0.20,
                        )
                    }
                }
            }
            if (next.isEmpty()) break
            beam = next
                .sortedByDescending { it.beamScore }
                .distinctBy { it.position to it.text }
                .take(BEAM_WIDTH)
        }
        return beam.filter { it.position == syllables.size }
            .sortedByDescending { it.beamScore }
            .take(MAX_BEAM_RESULTS)
    }

    private fun lexicalEntriesFor(key: String, syllableSpan: Int): List<LexicalEntry> {
        val merged = LinkedHashMap<String, Int>()
        lexicon.exact(key).forEach { entry ->
            merged[entry.text] = maxOf(merged[entry.text] ?: 0, entry.frequency)
        }
        val legacy = linkedSetOf<String>().apply {
            addAll(PinyinExpandedData.entries[key].orEmpty())
            addAll(PinyinBoostData.entries[key].orEmpty())
            addAll(PinyinSentenceDictionary.exactCandidatesFor(key))
            if (syllableSpan == 1) addAll(PinyinDictionary.exactCandidatesFor(key))
        }
        legacy.forEachIndexed { index, text ->
            val frequency = syntheticFrequency(index, if (syllableSpan > 1) 650_000 else 540_000)
            merged[text] = maxOf(merged[text] ?: 0, frequency)
        }
        return merged.entries
            .sortedByDescending { it.value }
            .map { LexicalEntry(it.key, it.value) }
    }

    private fun makeCandidate(
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
        val tokenScore = languageModel.scoreSequence(contextTokens, tokens)
        val chars = text.filter(::isCjk).map(Char::toString)
        val charScore = if (chars.size >= 2) {
            languageModel.scoreSequence(contextTokens, chars) * CHARACTER_NGRAM_WEIGHT
        } else 0.0
        return CandidateRanker.Candidate(
            text = text,
            tokens = tokens,
            staticFrequency = staticFrequency,
            segmentationScore = segmentationScore,
            ngramScore = maxOf(tokenScore, charScore),
            userFrequency = userFrequencyOverride ?: userDictionary.frequencyFor(query, text),
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

    private fun syntheticFrequency(index: Int, base: Int): Int =
        (base - index * 28_000).coerceAtLeast(25_000)

    companion object {
        private const val MAX_RESULTS = 12
        private const val MAX_SEGMENTATIONS = 5
        private const val MAX_PHRASE_SYLLABLES = 4
        private const val MAX_ENTRIES_PER_SPAN = 5
        private const val BEAM_WIDTH = 36
        private const val MAX_BEAM_RESULTS = 16
        private const val USER_BASE_STATIC_FREQUENCY = 700_000
        private const val CHARACTER_NGRAM_WEIGHT = 0.85
    }
}
