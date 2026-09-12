package com.ccwu.orbitime

import kotlin.math.max

/**
 * A bounded dynamic-programming translator built on Orbit's audited local bilingual data.
 * It prefers longer exact phrases, preserves uncovered source instead of fabricating text,
 * and reports coverage so callers can distinguish a strong translation from a fallback.
 */
object FluentLocalTranslationEngine {
    data class Result(
        val translatedText: String,
        val coverage: Double,
        val translatedUnits: Int,
        val totalUnits: Int,
    )

    private data class State(
        val score: Double,
        val output: String,
        val translated: Int,
    )

    fun translateOrNull(source: String, direction: TranslatePromptBuilder.Direction): Result? {
        val trimmed = source.trim()
        if (trimmed.isBlank()) return null
        return when (direction) {
            TranslatePromptBuilder.Direction.ZH_TO_EN -> translateChinese(trimmed)
            TranslatePromptBuilder.Direction.EN_TO_ZH -> translateEnglish(trimmed)
        }
    }

    private fun translateChinese(source: String): Result? {
        val terminal = source.lastOrNull()?.takeIf { it in TERMINALS }
        val core = if (terminal != null) source.dropLast(1).trim() else source
        if (core.isBlank()) return null
        val dp = arrayOfNulls<State>(core.length + 1)
        dp[0] = State(0.0, "", 0)

        for (index in core.indices) {
            val state = dp[index] ?: continue
            val maxSpan = minOf(MAX_ZH_SPAN, core.length - index)
            for (span in maxSpan downTo 1) {
                val fragment = core.substring(index, index + span)
                val translated = OfflineTranslationPack.exactOnly(fragment, TranslatePromptBuilder.Direction.ZH_TO_EN)
                if (!translated.isNullOrBlank()) {
                    val clean = stripEnding(translated)
                    val next = State(
                        score = state.score + span * 11.0 + span * span * 1.8 + if (span >= 2) 5.0 else 0.0,
                        output = joinEnglish(state.output, clean),
                        translated = state.translated + span,
                    )
                    putBetter(dp, index + span, next)
                }
            }
            // Unknown source is retained with a substantial penalty. This makes a partial
            // translation possible without pretending the uncovered character was translated.
            val raw = core[index].toString()
            putBetter(
                dp,
                index + 1,
                State(state.score - 6.0, joinEnglish(state.output, raw), state.translated),
            )
        }

        val best = dp[core.length] ?: return null
        if (best.translated == 0) return null
        val translatedText = best.output.trim() + targetEnding(terminal, TranslatePromptBuilder.Direction.ZH_TO_EN)
        return Result(translatedText, best.translated.toDouble() / core.length.coerceAtLeast(1), best.translated, core.length)
    }

    private fun translateEnglish(source: String): Result? {
        val terminal = source.lastOrNull()?.takeIf { it in TERMINALS }
        val core = if (terminal != null) source.dropLast(1).trim() else source
        val words = WORD_REGEX.findAll(core).map { it.value }.toList()
        if (words.isEmpty()) return null
        val dp = arrayOfNulls<State>(words.size + 1)
        dp[0] = State(0.0, "", 0)

        for (index in words.indices) {
            val state = dp[index] ?: continue
            val maxSpan = minOf(MAX_EN_SPAN, words.size - index)
            for (span in maxSpan downTo 1) {
                val fragment = words.subList(index, index + span).joinToString(" ")
                val translated = OfflineTranslationPack.exactOnly(fragment, TranslatePromptBuilder.Direction.EN_TO_ZH)
                if (!translated.isNullOrBlank()) {
                    val clean = stripEnding(translated)
                    val next = State(
                        score = state.score + span * 11.0 + span * span * 1.8 + if (span >= 2) 5.0 else 0.0,
                        output = joinChinese(state.output, clean),
                        translated = state.translated + span,
                    )
                    putBetter(dp, index + span, next)
                }
            }
            putBetter(
                dp,
                index + 1,
                State(state.score - 5.0, joinChinese(state.output, words[index]), state.translated),
            )
        }

        val best = dp[words.size] ?: return null
        if (best.translated == 0) return null
        val translatedText = best.output.trim() + targetEnding(terminal, TranslatePromptBuilder.Direction.EN_TO_ZH)
        return Result(translatedText, best.translated.toDouble() / words.size.coerceAtLeast(1), best.translated, words.size)
    }

    private fun putBetter(dp: Array<State?>, position: Int, candidate: State) {
        val old = dp[position]
        if (old == null || candidate.score > old.score ||
            (candidate.score == old.score && candidate.translated > old.translated)
        ) dp[position] = candidate
    }

    private fun stripEnding(value: String): String = value.trim().trimEnd('。', '.', '！', '!', '？', '?', '；', ';')

    private fun joinEnglish(left: String, right: String): String {
        if (left.isBlank()) return right
        if (right.isBlank()) return left
        val leftAscii = left.last().code < 128
        val rightAscii = right.first().code < 128
        return if (leftAscii || rightAscii) "$left $right" else left + right
    }

    private fun joinChinese(left: String, right: String): String {
        if (left.isBlank()) return right
        if (right.isBlank()) return left
        val leftAscii = left.last().code < 128 && left.last().isLetterOrDigit()
        val rightAscii = right.first().code < 128 && right.first().isLetterOrDigit()
        return if (leftAscii && rightAscii) "$left $right" else left + right
    }

    private fun targetEnding(sourceEnd: Char?, direction: TranslatePromptBuilder.Direction): String = when (direction) {
        TranslatePromptBuilder.Direction.ZH_TO_EN -> when (sourceEnd) {
            '？', '?' -> "?"
            '！', '!' -> "!"
            '；', ';' -> ";"
            else -> "."
        }
        TranslatePromptBuilder.Direction.EN_TO_ZH -> when (sourceEnd) {
            '？', '?' -> "？"
            '！', '!' -> "！"
            '；', ';' -> "；"
            else -> "。"
        }
    }

    private val WORD_REGEX = Regex("[A-Za-z0-9]+(?:'[A-Za-z]+)?")
    private val TERMINALS = setOf('。', '！', '？', '.', '!', '?', '；', ';')
    private const val MAX_ZH_SPAN = 12
    private const val MAX_EN_SPAN = 8
}
