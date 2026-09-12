package com.ccwu.orbitime

/**
 * Local context translation helper. Reads at most the previous two sentences in memory.
 * v0.23 uses the paragraph-aware long-form engine for the context block while keeping
 * the current sentence translation separate for safe insertion/replacement.
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
        val current = OfflineTranslationPack.translateOrNull(source, direction)
            ?: FluentLocalTranslationEngine.translateOrNull(source, direction)?.let {
                OfflineTranslationPack.Result(it.translatedText, "fluent-local", "v0.23 local context fallback")
            }
            ?: return null
        val previous = extractPreviousSentences(rawContext).takeLast(MAX_CONTEXT_SENTENCES)
        if (previous.isEmpty()) {
            return Bundle(current.translatedText, "", "", 0)
        }

        val separator = separatorFor(direction)
        val contextSource = previous.joinToString(separator)
        val blockSource = (previous + source.trim()).joinToString(separator)
        val block = LongFormTranslationEngine.translate(blockSource, direction, MAX_CONTEXT_BLOCK_CHARS)
        val previousOnly = LongFormTranslationEngine.translate(contextSource, direction, MAX_CONTEXT_BLOCK_CHARS)

        return Bundle(
            currentTranslation = current.translatedText,
            contextSourcePreview = contextSource.takeLast(MAX_CONTEXT_PREVIEW_CHARS),
            contextTranslationPreview = (block?.translatedText ?: previousOnly?.translatedText.orEmpty())
                .take(MAX_TRANSLATION_PREVIEW_CHARS),
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
    private const val MAX_CONTEXT_SOURCE_CHARS = 900
    private const val MAX_CONTEXT_BLOCK_CHARS = 1400
    private const val MAX_SINGLE_SENTENCE_CHARS = 360
    private const val MAX_CONTEXT_PREVIEW_CHARS = 220
    private const val MAX_TRANSLATION_PREVIEW_CHARS = 360
}
