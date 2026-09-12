package com.ccwu.orbitime

/** Bounded local long-form translation for Pro users. No network or persistence. */
object LongFormTranslationEngine {
    data class Result(
        val translatedText: String,
        val translatedSegments: Int,
        val uncoveredSegments: Int,
        val sourceChars: Int,
        val coverage: Double,
    )

    fun translate(
        rawSource: String,
        direction: TranslatePromptBuilder.Direction,
        maxChars: Int = MAX_SOURCE_CHARS,
    ): Result? {
        val source = rawSource.replace("\r\n", "\n").replace('\r', '\n').trim().take(maxChars)
        if (source.isBlank() || !PrivacyGuard.isSafeForLocalLongForm(source, maxChars)) return null

        var translated = 0
        var uncovered = 0
        var totalSegments = 0
        val outputLines = source.split('\n').map { line ->
            if (line.isBlank()) return@map ""
            val segments = splitSentences(line).take((MAX_SEGMENTS - totalSegments).coerceAtLeast(0))
            totalSegments += segments.size
            val rendered = segments.map { segment ->
                val result = OfflineTranslationPack.translateOrNull(segment.text.take(MAX_SENTENCE_CHARS), direction)
                val value = result?.translatedText?.trim().orEmpty()
                if (value.isNotBlank()) {
                    translated++
                    value.trimEnd('。', '.', '！', '!', '？', '?', '；', ';') + translatedEnd(segment.end, direction)
                } else {
                    uncovered++
                    segment.text + (segment.end?.toString().orEmpty())
                }
            }
            if (direction == TranslatePromptBuilder.Direction.ZH_TO_EN) rendered.joinToString(" ") else rendered.joinToString("")
        }

        val denominator = (translated + uncovered).coerceAtLeast(1)
        return Result(
            translatedText = outputLines.joinToString("\n").trim(),
            translatedSegments = translated,
            uncoveredSegments = uncovered,
            sourceChars = source.length,
            coverage = translated.toDouble() / denominator,
        )
    }

    private data class Segment(val text: String, val end: Char?)

    private fun splitSentences(source: String): List<Segment> {
        val result = mutableListOf<Segment>()
        val buffer = StringBuilder()
        source.forEach { ch ->
            if (ch in SENTENCE_ENDINGS) {
                val value = buffer.toString().trim()
                if (value.isNotBlank()) result += Segment(value, ch)
                buffer.clear()
            } else {
                buffer.append(ch)
            }
        }
        val tail = buffer.toString().trim()
        if (tail.isNotBlank()) result += Segment(tail, null)
        return result
    }

    private fun translatedEnd(sourceEnd: Char?, direction: TranslatePromptBuilder.Direction): String = when (direction) {
        TranslatePromptBuilder.Direction.ZH_TO_EN -> when (sourceEnd) {
            '？', '?' -> "?"
            '！', '!' -> "!"
            ';', '；' -> ";"
            else -> "."
        }
        TranslatePromptBuilder.Direction.EN_TO_ZH -> when (sourceEnd) {
            '?', '？' -> "？"
            '!', '！' -> "！"
            ';', '；' -> "；"
            else -> "。"
        }
    }

    const val MAX_SOURCE_CHARS = 8000
    private const val MAX_SENTENCE_CHARS = 1400
    private const val MAX_SEGMENTS = 240
    private val SENTENCE_ENDINGS = setOf('。', '！', '？', '.', '!', '?', ';', '；')
}
