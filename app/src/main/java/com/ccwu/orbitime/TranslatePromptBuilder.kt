package com.ccwu.orbitime

object TranslatePromptBuilder {
    enum class Direction(
        val label: String,
        val promptTitle: String,
    ) {
        ZH_TO_EN(
            label = "中→英",
            promptTitle = "Translate the following Chinese into natural English. Keep the meaning accurate and avoid stiff literal wording.",
        ),
        EN_TO_ZH(
            label = "英→中",
            promptTitle = "Translate the following English into natural Chinese. Keep the meaning accurate and avoid machine-translation wording.",
        ),
    }

    private const val MAX_SOURCE_LENGTH = 1200

    fun canUseSource(source: String?): Boolean {
        val text = source?.trim().orEmpty()
        return text.isNotEmpty() && text.length <= MAX_SOURCE_LENGTH && PrivacyGuard.isSafeToUseForPrompt(text)
    }

    fun build(source: String, direction: Direction): String {
        val cleanSource = source.trim()
        return buildString {
            append(direction.promptTitle)
            append("\n\nText:\n")
            append(cleanSource)
        }
    }

    fun sourceTooLongMessage(): String = "Text is too long for local prompt preview"

    fun unsafeSourceMessage(): String = "Skipped sensitive text"
}
