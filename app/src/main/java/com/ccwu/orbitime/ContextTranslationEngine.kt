package com.ccwu.orbitime

/**
 * Local context translation helper.
 *
 * This is deliberately conservative: it reads at most the previous two sentences,
 * translates the block locally, and keeps the current sentence translation separate
 * for insertion. Nothing is persisted.
 */
object ContextTranslationEngine {
    data class Bundle(
        val currentTranslation: String,
        val contextSourcePreview: String,
        val contextTranslationPreview: String,
        val contextSentenceCount: Int,
    )

    fun translate(
        source: String,
        rawContext: String,
        direction: TranslatePromptBuilder.Direction,
    ): Bundle? {
        val current = OfflineTranslationPack.translateOrNull(source, direction) ?: return null
        val previous = extractPreviousSentences(rawContext).takeLast(MAX_CONTEXT_SENTENCES)
        if (previous.isEmpty()) {
            return Bundle(
                currentTranslation = current.translatedText,
                contextSourcePreview = "",
                contextTranslationPreview = "",
                contextSentenceCount = 0,
            )
        }

        val contextSource = previous.joinToString(separatorFor(direction))
        val blockSource = (previous + source.trim()).joinToString(separatorFor(direction))

        val translatedBlock = OfflineTranslationPack.translateOrNull(blockSource, direction)?.translatedText
        val translatedPrevious = previous.mapNotNull {
            OfflineTranslationPack.translateOrNull(it, direction)?.translatedText
        }.joinToString(if (direction == TranslatePromptBuilder.Direction.ZH_TO_EN) " " else "")

        return Bundle(
            currentTranslation = current.translatedText,
            contextSourcePreview = contextSource.takeLast(MAX_CONTEXT_PREVIEW_CHARS),
            contextTranslationPreview = (translatedBlock ?: translatedPrevious).take(MAX_TRANSLATION_PREVIEW_CHARS),
            contextSentenceCount = previous.size,
        )
    }

    fun extractPreviousSentences(raw: String): List<String> {
        val cleaned = raw.takeLast(MAX_CONTEXT_SOURCE_CHARS).trim()
        if (cleaned.isBlank()) return emptyList()
        val out = mutableListOf<String>()
        val current = StringBuilder()
        cleaned.forEach { ch ->
            current.append(ch)
            if (ch in SENTENCE_ENDINGS) {
                val value = current.toString().trim()
                if (value.isNotBlank()) out += value
                current.clear()
            }
        }
        val tail = current.toString().trim()
        if (tail.isNotBlank()) out += tail
        return out.filter { it.length in 1..MAX_SINGLE_SENTENCE_CHARS }.takeLast(MAX_CONTEXT_SENTENCES)
    }

    private fun separatorFor(direction: TranslatePromptBuilder.Direction): String =
        if (direction == TranslatePromptBuilder.Direction.ZH_TO_EN) "" else " "

    private val SENTENCE_ENDINGS = setOf('。', '！', '？', '.', '!', '?', '\n')
    private const val MAX_CONTEXT_SENTENCES = 2
    private const val MAX_CONTEXT_SOURCE_CHARS = 720
    private const val MAX_SINGLE_SENTENCE_CHARS = 280
    private const val MAX_CONTEXT_PREVIEW_CHARS = 180
    private const val MAX_TRANSLATION_PREVIEW_CHARS = 260
}
