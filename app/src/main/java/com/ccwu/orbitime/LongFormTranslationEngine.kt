package com.ccwu.orbitime

/** Bounded local long-form translation for Pro users. No network or persistence. */
object LongFormTranslationEngine {
    data class Result(
        val translatedText: String,
        val translatedSegments: Int,
        val uncoveredSegments: Int,
        val sourceChars: Int,
    )

    fun translate(
        rawSource: String,
        direction: TranslatePromptBuilder.Direction,
        maxChars: Int = MAX_SOURCE_CHARS,
    ): Result? {
        val source = rawSource.trim().take(maxChars)
        if (source.isBlank() || !PrivacyGuard.isSafeForLocalLongForm(source, maxChars)) return null
        val segments = splitSentences(source)
        if (segments.isEmpty()) return null

        var translated = 0
        var uncovered = 0
        val output = buildString {
            segments.forEach { segment ->
                val result = OfflineTranslationPack.translateOrNull(segment.text.take(1200), direction)
                val value = result?.translatedText?.trim().orEmpty()
                if (value.isNotBlank()) {
                    append(value.trimEnd('。', '.', '！', '!', '？', '?'))
                    append(translatedEnd(segment.end, direction))
                    translated++
                } else {
                    // Preserve uncovered source instead of silently inventing a translation.
                    append(segment.text)
                    if (segment.end != null && !segment.text.endsWith(segment.end)) append(segment.end)
                    uncovered++
                }
                if (segment.lineBreak) append('\n') else append(' ')
            }
        }.trim()

        return Result(output, translated, uncovered, source.length)
    }

    private data class Segment(val text: String, val end: Char?, val lineBreak: Boolean)

    private fun splitSentences(source: String): List<Segment> {
        val result = mutableListOf<Segment>()
        val buffer = StringBuilder()
        source.forEach { ch ->
            when (ch) {
                '。', '！', '？', '.', '!', '?', ';', '；' -> {
                    val value = buffer.toString().trim()
                    if (value.isNotBlank()) result += Segment(value, ch, false)
                    buffer.clear()
                }
                '\n', '\r' -> {
                    val value = buffer.toString().trim()
                    if (value.isNotBlank()) result += Segment(value, null, true)
                    buffer.clear()
                }
                else -> buffer.append(ch)
            }
        }
        val tail = buffer.toString().trim()
        if (tail.isNotBlank()) result += Segment(tail, null, false)
        return result.take(MAX_SEGMENTS)
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
    private const val MAX_SEGMENTS = 120
}
