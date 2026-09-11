package com.ccwu.orbitime

import android.content.Context
import java.io.FileNotFoundException
import kotlin.math.ln

/**
 * Lightweight offline language model.
 *
 * Runtime assets are optional; when absent, the model falls back to a small
 * project-authored seed table so the engine remains usable before a large pack
 * is imported.
 */
class NGramLanguageModel(private val context: Context) {
    private val unigram: Map<String, Int> by lazy { loadNGramAsset(1).ifEmpty { FALLBACK_UNIGRAM } }
    private val bigram: Map<String, Int> by lazy { loadNGramAsset(2).ifEmpty { FALLBACK_BIGRAM } }
    private val trigram: Map<String, Int> by lazy { loadNGramAsset(3).ifEmpty { FALLBACK_TRIGRAM } }

    fun scoreSequence(contextTokens: List<String>, candidateTokens: List<String>): Double {
        if (candidateTokens.isEmpty()) return 0.0
        val history = contextTokens.takeLast(2).toMutableList()
        var score = 0.0
        candidateTokens.forEach { token ->
            val u = unigram[token].orZero()
            val b = history.lastOrNull()?.let { prev -> bigram[key(prev, token)].orZero() } ?: 0
            val t = if (history.size >= 2) {
                trigram[key(history[history.size - 2], history[history.size - 1], token)].orZero()
            } else {
                0
            }
            score += WEIGHT_UNIGRAM * ln(1.0 + u)
            score += WEIGHT_BIGRAM * ln(1.0 + b)
            score += WEIGHT_TRIGRAM * ln(1.0 + t)
            history.add(token)
            while (history.size > 2) history.removeAt(0)
        }
        return score
    }

    fun transitionScore(previous2: String?, previous1: String?, token: String): Double {
        var score = WEIGHT_UNIGRAM * ln(1.0 + unigram[token].orZero())
        if (!previous1.isNullOrBlank()) {
            score += WEIGHT_BIGRAM * ln(1.0 + bigram[key(previous1, token)].orZero())
        }
        if (!previous2.isNullOrBlank() && !previous1.isNullOrBlank()) {
            score += WEIGHT_TRIGRAM * ln(1.0 + trigram[key(previous2, previous1, token)].orZero())
        }
        return score
    }

    private fun loadNGramAsset(n: Int): Map<String, Int> {
        val path = "ime/ngram$n.odict"
        return try {
            val map = HashMap<String, Int>()
            context.assets.open(path).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trimEnd()
                    if (line.isBlank() || line.startsWith("#")) return@forEach
                    val parts = line.split('\t')
                    if (parts.size != n + 1) return@forEach
                    val count = parseBase36(parts.last())
                    val tokens = parts.dropLast(1)
                    if (tokens.all { it.isNotBlank() }) {
                        map[key(*tokens.toTypedArray())] = count
                    }
                }
            }
            map
        } catch (_: FileNotFoundException) {
            emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseBase36(raw: String): Int {
        val value = runCatching { raw.trim().lowercase().toLong(36) }.getOrDefault(1L)
        return value.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun key(vararg tokens: String): String = tokens.joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = "\u0001"
        private const val WEIGHT_UNIGRAM = 0.18
        private const val WEIGHT_BIGRAM = 0.70
        private const val WEIGHT_TRIGRAM = 1.05

        private val FALLBACK_UNIGRAM = mapOf(
            "你" to 980000,
            "我" to 995000,
            "好" to 970000,
            "是" to 990000,
            "的" to 1000000,
            "问题" to 880000,
            "翻译" to 800000,
            "结果" to 850000,
            "输入法" to 850000,
            "学习" to 850000,
            "优化" to 810000,
            "修改" to 830000,
            "本地" to 790000,
            "词库" to 760000,
            "候选" to 730000,
            "继续" to 800000,
            "完善" to 740000,
        )

        private val FALLBACK_BIGRAM = mapOf(
            "你${SEPARATOR}好" to 950000,
            "我${SEPARATOR}知道" to 850000,
            "还是${SEPARATOR}有问题" to 840000,
            "本地${SEPARATOR}词库" to 630000,
            "用户${SEPARATOR}词库" to 620000,
            "输入法${SEPARATOR}设置" to 640000,
            "继续${SEPARATOR}优化" to 630000,
            "继续${SEPARATOR}完善" to 620000,
            "翻译${SEPARATOR}结果" to 720000,
        )

        private val FALLBACK_TRIGRAM = mapOf(
            "你${SEPARATOR}是${SEPARATOR}谁" to 900000,
            "你${SEPARATOR}好${SEPARATOR}吗" to 880000,
            "我${SEPARATOR}来${SEPARATOR}处理" to 820000,
            "晚点${SEPARATOR}再${SEPARATOR}处理" to 760000,
            "没有${SEPARATOR}翻译${SEPARATOR}结果" to 720000,
            "不能${SEPARATOR}形成${SEPARATOR}句子" to 610000,
        )
    }
}
