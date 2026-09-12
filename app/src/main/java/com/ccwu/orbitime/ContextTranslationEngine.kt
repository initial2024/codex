package com.ccwu.orbitime

/**
 * Local context translation helper. Reads a bounded previous context in memory only.
 * v0.23 keeps sentence/paragraph boundaries instead of gluing neighboring sentences together.
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
                OfflineTranslationPack.Result(
                    TranslationOutputNormalizer.normalize(it.translatedText, direction),
                    "fluent-local",
                    "v0.23 local context fallback",
                )
            }
            ?: return null
        val previous = extractPreviousSentences(rawContext).takeLast(MAX_CONTEXT_SENTENCES)
        if (previous.isEmpty()) {
            return Bundle(current.translatedText, "", "", 0)
        }

        // Newlines are intentional: the long-form engine preserves paragraph boundaries,
        // which prevents several source sentences from becoming one malformed lookup key.
        val contextSource = previous.joinToString("\n")
        val blockSource = (previous + source.trim()).joinToString("\n")
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

    private val SENTENCE_ENDINGS = setOf('。', '！', '？', '.', '!', '?', ';', '；', '\n')
    private const val MAX_CONTEXT_SENTENCES = 4
    private const val MAX_CONTEXT_SOURCE_CHARS = 1800
    private const val MAX_CONTEXT_BLOCK_CHARS = 3200
    private const val MAX_SINGLE_SENTENCE_CHARS = 700
    private const val MAX_CONTEXT_PREVIEW_CHARS = 360
    private const val MAX_TRANSLATION_PREVIEW_CHARS = 700
}
