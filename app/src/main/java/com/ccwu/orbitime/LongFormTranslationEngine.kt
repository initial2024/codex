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
        val source = rawSource.replace("\r\n", "\n").replace('\r', '\n').take(maxChars).trim()
        if (source.isBlank() || !PrivacyGuard.isSafeForLocalLongForm(source, maxChars)) return null

        var translated = 0
        var uncovered = 0
        var attempted = 0
        val outputLines = source.split('\n').map { line ->
            if (line.isBlank()) return@map ""
            val units = splitForTranslation(line)
            val rendered = units.map { segment ->
                // Never drop source merely because the block contains many clauses. After the
                // work budget is reached, retain the remaining source verbatim.
                if (attempted >= MAX_TRANSLATION_SEGMENTS) {
                    uncovered++
                    return@map sourceSegment(segment)
                }
                attempted++
                val result = translateSegment(segment.text, direction)
                val value = result?.trim().orEmpty()
                if (value.isNotBlank()) {
                    translated++
                    val clean = value.trimEnd('。', '.', '！', '!', '？', '?', '；', ';', '，', ',')
                    clean + translatedEnd(segment.end, direction)
                } else {
                    uncovered++
                    sourceSegment(segment)
                }
            }
            val joined = if (direction == TranslatePromptBuilder.Direction.ZH_TO_EN) {
                rendered.joinToString(" ")
            } else {
                rendered.joinToString("")
            }
            TranslationOutputNormalizer.normalize(joined, direction)
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

    private fun translateSegment(text: String, direction: TranslatePromptBuilder.Direction): String? {
        val clipped = text.trim().take(MAX_SENTENCE_CHARS)
        if (clipped.isBlank()) return null
        OfflineTranslationPack.translateOrNull(clipped, direction)?.translatedText?.let { return it }
        val fluent = FluentLocalTranslationEngine.translateOrNull(clipped, direction)
        if (fluent != null && fluent.coverage >= MIN_PARTIAL_COVERAGE) {
            return TranslationOutputNormalizer.normalize(fluent.translatedText, direction)
        }
        return null
    }

    private fun splitForTranslation(source: String): List<Segment> {
        val strong = splitStrongSentences(source)
        return strong.flatMap { segment ->
            if (segment.text.length <= TARGET_CLAUSE_CHARS) listOf(segment) else splitLongSegment(segment)
        }
    }

    private fun splitStrongSentences(source: String): List<Segment> {
        val result = mutableListOf<Segment>()
        val buffer = StringBuilder()
        source.forEach { ch ->
            if (ch in STRONG_ENDINGS) {
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

    private fun splitLongSegment(segment: Segment): List<Segment> {
        val result = mutableListOf<Segment>()
        val buffer = StringBuilder()
        segment.text.forEach { ch ->
            if (ch in CLAUSE_ENDINGS && buffer.length >= MIN_CLAUSE_CHARS) {
                val value = buffer.toString().trim()
                if (value.isNotBlank()) result += Segment(value, ch)
                buffer.clear()
            } else {
                buffer.append(ch)
                if (buffer.length >= HARD_CHUNK_CHARS) {
                    val value = buffer.toString().trim()
                    if (value.isNotBlank()) result += Segment(value, null)
                    buffer.clear()
                }
            }
        }
        val tail = buffer.toString().trim()
        if (tail.isNotBlank()) result += Segment(tail, segment.end)
        else if (result.isNotEmpty() && segment.end != null) {
            val last = result.last()
            result[result.lastIndex] = last.copy(end = segment.end)
        }
        return result.ifEmpty { listOf(segment) }
    }

    private fun sourceSegment(segment: Segment): String = segment.text + segment.end?.toString().orEmpty()

    private fun translatedEnd(sourceEnd: Char?, direction: TranslatePromptBuilder.Direction): String = when (direction) {
        TranslatePromptBuilder.Direction.ZH_TO_EN -> when (sourceEnd) {
            null -> ""
            '？', '?' -> "?"
            '！', '!' -> "!"
            ';', '；' -> ";"
            ',', '，', '、' -> ","
            ':', '：' -> ":"
            else -> "."
        }
        TranslatePromptBuilder.Direction.EN_TO_ZH -> when (sourceEnd) {
            null -> ""
            '?', '？' -> "？"
            '!', '！' -> "！"
            ';', '；' -> "；"
            ',', '，', '、' -> "，"
            ':', '：' -> "："
            else -> "。"
        }
    }

    const val MAX_SOURCE_CHARS = 8000
    private const val MAX_SENTENCE_CHARS = 1800
    private const val MAX_TRANSLATION_SEGMENTS = 480
    private const val TARGET_CLAUSE_CHARS = 320
    private const val HARD_CHUNK_CHARS = 520
    private const val MIN_CLAUSE_CHARS = 48
    private const val MIN_PARTIAL_COVERAGE = 0.28
    private val STRONG_ENDINGS = setOf('。', '！', '？', '.', '!', '?', ';', '；')
    private val CLAUSE_ENDINGS = setOf(',', '，', '、', ':', '：')
}
