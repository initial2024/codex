package com.ccwu.orbitime

import android.content.Context
import java.io.FileNotFoundException
import kotlin.math.ln

/** Predicts next Chinese words/short phrases from precomputed associations + local N-grams. */
class NextAssociationEngine(context: Context) {
    private val appContext = context.applicationContext
    private val associationAsset = AssociationAsset(appContext)

    private data class TokenScore(val token: String, val count: Int, val weight: Double)
    private data class Hypothesis(val tokens: List<String>, val score: Double)

    private val unigram: Map<String, Int> by lazy { loadNGramAsset(1) }
    private val bigram: Map<String, Int> by lazy { loadNGramAsset(2) }
    private val trigram: Map<String, Int> by lazy { loadNGramAsset(3) }

    private val unigramTop: List<TokenScore> by lazy {
        unigram.entries.sortedByDescending { it.value }.take(160)
            .map { TokenScore(it.key, it.value, 0.16) }
    }

    private val bigramNext: Map<String, List<TokenScore>> by lazy { buildNextIndex(bigram, 2, 0.76) }
    private val trigramNext: Map<String, List<TokenScore>> by lazy { buildNextIndex(trigram, 3, 1.14) }

    fun suggestions(rawContext: String, limit: Int = DEFAULT_LIMIT): List<String> {
        val safeLimit = limit.coerceIn(1, MAX_LIMIT)
        val fast = associationAsset.suggestions(rawContext, safeLimit * 3).map { it.text }
        val curated = NextPhraseData.suggestions(rawContext, safeLimit)
        val contextTokens = extractContextTokens(rawContext)
        if (contextTokens.isEmpty()) return (fast + curated).distinct().take(safeLimit)

        // Mature IMEs make next-word prediction cheap by front-loading indexed results.
        // The precomputed association pack stays first; bounded N-gram Beam only fills gaps.
        var beam = listOf(Hypothesis(emptyList(), 0.0))
        val generated = mutableListOf<Pair<String, Double>>()
        repeat(MAX_CONTINUATION_TOKENS) { step ->
            val nextBeam = mutableListOf<Hypothesis>()
            beam.forEach { hypothesis ->
                val history = contextTokens + hypothesis.tokens
                nextOptions(history).take(MAX_BRANCHES).forEach { option ->
                    if (option.token.isBlank()) return@forEach
                    val tokens = hypothesis.tokens + option.token
                    val score = hypothesis.score + option.weight * ln(1.0 + option.count) + (step + 1) * 0.08
                    val next = Hypothesis(tokens, score)
                    nextBeam += next
                    val text = joinTokens(tokens)
                    if (text.length in 1..MAX_SUGGESTION_CHARS) generated += text to score
                }
            }
            if (nextBeam.isEmpty()) return@repeat
            beam = nextBeam.sortedByDescending { it.score }
                .distinctBy { joinTokens(it.tokens) }
                .take(BEAM_WIDTH)
        }

        val ngram = generated.sortedByDescending { it.second }
            .map { it.first }
            .filter { it.isNotBlank() }
            .distinct()

        return (fast + curated + ngram)
            .distinct()
            .filterNot { rawContext.trimEnd().endsWith(it) }
            .take(safeLimit)
    }

    private fun nextOptions(history: List<String>): List<TokenScore> {
        val merged = LinkedHashMap<String, TokenScore>()
        if (history.size >= 2) {
            val key = key(history[history.size - 2], history.last())
            trigramNext[key].orEmpty().forEach { score -> merged[score.token] = score }
        }
        history.lastOrNull()?.let { last ->
            bigramNext[last].orEmpty().forEach { score ->
                val old = merged[score.token]
                if (old == null || score.count * score.weight > old.count * old.weight) merged[score.token] = score
            }
        }
        if (merged.size < MIN_CONTEXTUAL_OPTIONS) {
            unigramTop.forEach { score -> merged.putIfAbsent(score.token, score) }
        }
        return merged.values.sortedByDescending { it.weight * ln(1.0 + it.count) }
    }

    private fun buildNextIndex(source: Map<String, Int>, n: Int, weight: Double): Map<String, List<TokenScore>> {
        val temp = HashMap<String, MutableMap<String, Int>>()
        source.forEach { (rawKey, count) ->
            val parts = rawKey.split(SEPARATOR)
            if (parts.size != n) return@forEach
            val prefix = parts.dropLast(1).joinToString(SEPARATOR)
            val token = parts.last()
            val bucket = temp.getOrPut(prefix) { HashMap() }
            bucket[token] = maxOf(bucket[token] ?: 0, count)
        }
        return temp.mapValues { (_, bucket) ->
            bucket.entries.sortedByDescending { it.value }.take(MAX_NEXT_PER_PREFIX)
                .map { TokenScore(it.key, it.value, weight) }
        }
    }

    private fun extractContextTokens(raw: String): List<String> {
        val tail = raw.takeLast(MAX_CONTEXT_SCAN_CHARS)
        val result = mutableListOf<String>()
        val latin = StringBuilder()
        fun flushLatin() {
            if (latin.isNotEmpty()) {
                result += latin.toString().lowercase()
                latin.clear()
            }
        }
        tail.forEach { char ->
            when {
                char.code < 128 && (char.isLetterOrDigit() || char == '\'') -> latin.append(char)
                isCjk(char) -> { flushLatin(); result += char.toString() }
                else -> flushLatin()
            }
        }
        flushLatin()
        return result.takeLast(MAX_CONTEXT_TOKENS)
    }

    private fun joinTokens(tokens: List<String>): String {
        if (tokens.isEmpty()) return ""
        val allCjk = tokens.all { token -> token.all(::isCjk) }
        return if (allCjk) tokens.joinToString("") else tokens.joinToString(" ")
    }

    private fun loadNGramAsset(n: Int): Map<String, Int> {
        return try {
            val result = HashMap<String, Int>()
            appContext.assets.open("ime/ngram$n.odict").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trimEnd()
                    if (line.isBlank() || line.startsWith("#")) return@forEach
                    val parts = line.split('\t')
                    if (parts.size != n + 1) return@forEach
                    val tokens = parts.dropLast(1)
                    val count = runCatching { parts.last().lowercase().toLong(36) }
                        .getOrDefault(1L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
                    if (tokens.all { it.isNotBlank() }) result[key(*tokens.toTypedArray())] = count
                }
            }
            result
        } catch (_: FileNotFoundException) {
            emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun key(vararg tokens: String): String = tokens.joinToString(SEPARATOR)

    private fun isCjk(char: Char): Boolean {
        val block = Character.UnicodeBlock.of(char)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
    }

    companion object {
        private const val SEPARATOR = "\u0001"
        const val DEFAULT_LIMIT = 48
        const val MAX_LIMIT = 64
        private const val MAX_CONTINUATION_TOKENS = 6
        private const val MAX_BRANCHES = 16
        private const val BEAM_WIDTH = 40
        private const val MAX_SUGGESTION_CHARS = 48
        private const val MAX_NEXT_PER_PREFIX = 96
        private const val MIN_CONTEXTUAL_OPTIONS = 20
        private const val MAX_CONTEXT_SCAN_CHARS = 220
        private const val MAX_CONTEXT_TOKENS = 18
    }
}
