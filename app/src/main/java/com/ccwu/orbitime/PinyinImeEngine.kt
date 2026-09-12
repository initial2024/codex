package com.ccwu.orbitime

import android.content.Context
import java.util.LinkedHashMap
import kotlin.math.ln
import kotlin.math.min

class PinyinImeEngine(
    context: Context,
    private val userDictionary: UserDictionaryStore,
) {
    private val appContext = context.applicationContext
    private val lexicon = CompactLexiconAsset(appContext)
    private val languageModel = NGramLanguageModel(appContext)

    private val candidateCache = object : LinkedHashMap<String, List<String>>(96, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean = size > MAX_QUERY_CACHE
    }

    private val lexicalCache = object : LinkedHashMap<String, List<LexicalEntry>>(768, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<LexicalEntry>>?): Boolean = size > MAX_LEXICAL_CACHE
    }

    @Synchronized
    fun candidates(rawInput: String, contextBeforeCursor: String? = null, limit: Int = MAX_RESULTS): List<String> {
        val query = PinyinDictionary.normalize(rawInput)
        if (query.isEmpty()) return emptyList()
        val fuzzyLevel = ImePreferences.fuzzyLevel(appContext)
        val cacheKey = cacheKey(query, contextBeforeCursor, limit, fuzzyLevel)
        candidateCache[cacheKey]?.let { return it }

        val contextTokens = extractContextTokens(contextBeforeCursor.orEmpty())
        val pool = mutableListOf<CandidateRanker.Candidate>()
        addUser(query, contextTokens, pool, exactOnly = false)
        addExact(query, contextTokens, pool)
        addSegmented(query, contextTokens, pool)
        addPrefixPredictions(query, contextTokens, pool)
        if (fuzzyLevel != ImePreferences.FUZZY_OFF && query.length <= MAX_CORRECTION_QUERY_CHARS) {
            addCorrections(query, contextTokens, pool, fuzzyLevel == ImePreferences.FUZZY_ENHANCED)
        }

        val ranked = CandidateRanker.rank(pool, limit).map { it.text }.distinct()
        val result = if (ranked.isNotEmpty()) ranked.take(limit) else {
            (PinyinSentenceDictionary.candidatesFor(query) + PinyinDictionary.candidatesFor(query)).distinct().take(limit)
        }
        candidateCache[cacheKey] = result
        return result
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

    @Synchronized
    fun clearCandidateCache() { candidateCache.clear() }

    fun debugSegmentation(rawInput: String): List<PinyinSegmenter.Segmentation> = PinyinSegmenter.segment(rawInput)

    private fun addUser(query: String, contextTokens: List<String>, pool: MutableList<CandidateRanker.Candidate>, exactOnly: Boolean) {
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

    private fun addExact(query: String, contextTokens: List<String>, pool: MutableList<CandidateRanker.Candidate>) {
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

    private fun addSegmented(query: String, contextTokens: List<String>, pool: MutableList<CandidateRanker.Candidate>) {
        val segmentLimit = segmentationLimitFor(query.length)
        PinyinSegmenter.segment(query, segmentLimit).forEachIndexed { segmentationIndex, segmentation ->
            beamGenerate(segmentation, contextTokens).forEachIndexed { beamIndex, hypothesis ->
                if (hypothesis.text.isBlank()) return@forEachIndexed
                val averageFrequency = if (hypothesis.parts == 0) 1 else hypothesis.totalFrequency / hypothesis.parts
                pool += makeCandidate(
                    query = query,
                    text = hypothesis.text,
                    tokens = hypothesis.tokens,
                    staticFrequency = averageFrequency.coerceAtLeast(1),
                    segmentationScore = segmentation.score - segmentationIndex * 0.28,
                    contextTokens = contextTokens,
                    sourcePriority = (5 - min(beamIndex, 3)).coerceAtLeast(1),
                )
            }
        }
    }

    private fun addPrefixPredictions(query: String, contextTokens: List<String>, pool: MutableList<CandidateRanker.Candidate>) {
        if (query.length !in 2..MAX_PREFIX_QUERY_CHARS) return
        lexicon.prefix(query, PREFIX_POOL_LIMIT).forEachIndexed { index, entry ->
            if (entry.pinyin == query) return@forEachIndexed
            val remaining = (entry.pinyin.length - query.length).coerceAtLeast(0)
            pool += makeCandidate(
                query = query,
                text = entry.text,
                tokens = listOf(entry.text),
                staticFrequency = entry.frequency,
                segmentationScore = 0.25,
                contextTokens = contextTokens,
                correctionPenalty = 1.15 + remaining.coerceAtMost(10) * 0.08 + index * 0.012,
                sourcePriority = 2,
            )
        }
    }

    private fun addCorrections(
        query: String,
        contextTokens: List<String>,
        pool: MutableList<CandidateRanker.Candidate>,
        enhanced: Boolean,
    ) {
        val variantLimit = if (enhanced) 72 else 36
        val entriesPerVariant = if (enhanced) 7 else MAX_CORRECTION_ENTRIES_PER_VARIANT
        PinyinCorrectionEngine.queryVariants(query, variantLimit, enhanced).forEach { variant ->
            lexicon.exact(variant.pinyin).take(entriesPerVariant).forEachIndexed { index, entry ->
                pool += makeCandidate(
                    query = query,
                    text = entry.text,
                    tokens = listOf(entry.text),
                    staticFrequency = entry.frequency,
                    segmentationScore = 0.0,
                    contextTokens = contextTokens,
                    correctionPenalty = 2.1 + variant.penalty + index * 0.18,
                    sourcePriority = 2,
                )
            }
        }

        PinyinCorrectionEngine.candidatesFor(query, enhanced).forEachIndexed { index, text ->
            pool += makeCandidate(
                query = query,
                text = text,
                tokens = listOf(text),
                staticFrequency = syntheticFrequency(index, 520_000),
                segmentationScore = 0.0,
                contextTokens = contextTokens,
                correctionPenalty = 3.0 + index * 0.18,
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

    private fun beamGenerate(segmentation: PinyinSegmenter.Segmentation, contextTokens: List<String>): List<Hypothesis> {
        val syllables = segmentation.syllables
        if (syllables.isEmpty()) return emptyList()
        val beamWidth = beamWidthFor(syllables.size)
        val maxPhraseSpan = phraseSpanFor(syllables.size)
        val entriesPerSpan = entriesPerSpanFor(syllables.size)
        val resultLimit = beamResultLimitFor(syllables.size)

        var beam = listOf(Hypothesis(0, "", emptyList(), 0, 0, 0.0))
        while (beam.any { it.position < syllables.size }) {
            val next = mutableListOf<Hypothesis>()
            beam.forEach { hypothesis ->
                if (hypothesis.position >= syllables.size) {
                    next += hypothesis
                    return@forEach
                }
                val maxSpan = min(maxPhraseSpan, syllables.size - hypothesis.position)
                for (span in 1..maxSpan) {
                    val key = syllables.subList(hypothesis.position, hypothesis.position + span).joinToString("")
                    lexicalEntriesFor(key, span).take(entriesPerSpan).forEach { entry ->
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
                            beamScore = hypothesis.beamScore + lm + freqScore + span * 0.22,
                        )
                    }
                }
            }
            if (next.isEmpty()) break
            beam = next.sortedByDescending { it.beamScore }.distinctBy { it.position to it.text }.take(beamWidth)
        }
        return beam.filter { it.position == syllables.size }.sortedByDescending { it.beamScore }.take(resultLimit)
    }

    private fun lexicalEntriesFor(key: String, syllableSpan: Int): List<LexicalEntry> {
        val cacheKey = "$syllableSpan:$key"
        lexicalCache[cacheKey]?.let { return it }
        val merged = LinkedHashMap<String, Int>()
        lexicon.exact(key).forEach { entry -> merged[entry.text] = maxOf(merged[entry.text] ?: 0, entry.frequency) }
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
        val result = merged.entries.sortedByDescending { it.value }.map { LexicalEntry(it.key, it.value) }
        lexicalCache[cacheKey] = result
        return result
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
        val charScore = if (chars.size >= 2) languageModel.scoreSequence(contextTokens, chars) * CHARACTER_NGRAM_WEIGHT else 0.0
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

    private fun segmentationLimitFor(queryLength: Int): Int = when {
        queryLength >= 96 -> 2
        queryLength >= 64 -> 3
        queryLength >= 36 -> 4
        else -> MAX_SEGMENTATIONS
    }

    private fun beamWidthFor(syllableCount: Int): Int = when {
        syllableCount >= 32 -> 22
        syllableCount >= 22 -> 30
        syllableCount >= 14 -> 42
        syllableCount >= 9 -> 58
        else -> BEAM_WIDTH
    }

    private fun phraseSpanFor(syllableCount: Int): Int = if (syllableCount >= 28) 6 else MAX_PHRASE_SYLLABLES

    private fun entriesPerSpanFor(syllableCount: Int): Int = when {
        syllableCount >= 28 -> 4
        syllableCount >= 18 -> 5
        syllableCount >= 10 -> 6
        else -> MAX_ENTRIES_PER_SPAN
    }

    private fun beamResultLimitFor(syllableCount: Int): Int = when {
        syllableCount >= 28 -> 14
        syllableCount >= 18 -> 20
        else -> MAX_BEAM_RESULTS
    }

    private fun cacheKey(query: String, contextBeforeCursor: String?, limit: Int, fuzzyLevel: String): String =
        query + '\u0000' + contextBeforeCursor.orEmpty().takeLast(64) + '\u0000' + limit + '\u0000' + fuzzyLevel

    private fun extractContextTokens(raw: String): List<String> {
        val tail = raw.takeLast(96)
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
                isCjk(char) -> { flushLatin(); tokens += char.toString() }
                else -> flushLatin()
            }
        }
        flushLatin()
        return tokens.takeLast(12)
    }

    private fun isCjk(char: Char): Boolean {
        val block = Character.UnicodeBlock.of(char)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
    }

    private fun syntheticFrequency(index: Int, base: Int): Int = (base - index * 24_000).coerceAtLeast(25_000)

    companion object {
        private const val MAX_RESULTS = 32
        private const val MAX_SEGMENTATIONS = 8
        private const val MAX_PHRASE_SYLLABLES = 8
        private const val MAX_ENTRIES_PER_SPAN = 8
        private const val BEAM_WIDTH = 72
        private const val MAX_BEAM_RESULTS = 32
        private const val MAX_QUERY_CACHE = 96
        private const val MAX_LEXICAL_CACHE = 768
        private const val MAX_CORRECTION_QUERY_CHARS = 48
        private const val MAX_CORRECTION_ENTRIES_PER_VARIANT = 5
        private const val MAX_PREFIX_QUERY_CHARS = 24
        private const val PREFIX_POOL_LIMIT = 80
        private const val USER_BASE_STATIC_FREQUENCY = 700_000
        private const val CHARACTER_NGRAM_WEIGHT = 0.85
    }
}
