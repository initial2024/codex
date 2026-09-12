package com.ccwu.orbitime

object OfflineTranslationPack {
    data class Result(
        val translatedText: String,
        val confidence: String,
        val note: String,
    )

    fun translateOrNull(source: String, direction: TranslatePromptBuilder.Direction): Result? {
        val text = source.trim()
        if (text.isBlank() || !PrivacyGuard.isSafeToUseForPrompt(text)) return null

        val exact = when (direction) {
            TranslatePromptBuilder.Direction.ZH_TO_EN -> findExactZhToEn(text)
            TranslatePromptBuilder.Direction.EN_TO_ZH -> findExactEnToZh(text)
        }
        if (exact != null) return Result(exact, "exact-local", "本地短句精确匹配")

        val composed = when (direction) {
            TranslatePromptBuilder.Direction.ZH_TO_EN -> LocalTranslationComposer.translateZhToEn(text)
            TranslatePromptBuilder.Direction.EN_TO_ZH -> LocalTranslationComposer.translateEnToZh(text)
        }
        return composed?.let { Result(it, "composed-local", "本地词组切分与句子拼接") }
    }

    fun unavailableMessage(): String = "离线词库暂未覆盖这句话。可继续编辑，或使用提示词交给外部模型翻译。"

    private fun findExactZhToEn(raw: String): String? {
        val variants = exactVariants(raw)
        for (text in variants) {
            ProfessionalTranslationData.zhToEn[text]?.let { return it }
            TranslationExpansionData.zhToEn[text]?.let { return it }
            TranslationBoostData.zhToEn[text]?.let { return it }
            TranslationLexiconData.zhToEn[text]?.let { return normalizeExactEnglish(it, raw) }
            BASE_ZH_TO_EN[text]?.let { return it }
        }
        return null
    }

    private fun findExactEnToZh(raw: String): String? {
        val key = normalizeEnglishKey(raw)
        ProfessionalTranslationData.enToZh[key]?.let { return it }
        TranslationExpansionData.enToZh[key]?.let { return it }
        TranslationBoostData.enToZh[key]?.let { return it }
        TranslationLexiconData.enToZh[key]?.let { return ensureChineseEnding(it, raw) }
        BASE_EN_TO_ZH[key]?.let { return it }
        return null
    }

    private fun exactVariants(raw: String): List<String> {
        val trimmed = raw.trim()
        val withoutEnd = trimmed.trimEnd('。', '！', '？', '.', '!', '?', ' ', '\n', '\r', '\t')
        return listOf(trimmed, withoutEnd).filter { it.isNotBlank() }.distinct()
    }

    private fun normalizeEnglishKey(text: String): String = text.lowercase()
        .replace(Regex("[^a-z0-9\\s']"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalizeExactEnglish(value: String, source: String): String {
        var text = value.trim()
        if (text.isEmpty()) return text
        text = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val end = source.trim().lastOrNull()
        if (text.lastOrNull() !in setOf('.', '!', '?', ';', ':')) {
            text += when (end) {
                '？', '?' -> "?"
                '！', '!' -> "!"
                else -> "."
            }
        }
        return text
    }

    private fun ensureChineseEnding(value: String, source: String): String {
        var text = value.trim()
        if (text.isEmpty()) return text
        if (text.lastOrNull() !in setOf('。', '！', '？', '；', '：')) {
            text += when (source.trim().lastOrNull()) {
                '?' -> "？"
                '!' -> "！"
                else -> "。"
            }
        }
        return text
    }

    private val BASE_ZH_TO_EN = mapOf(
        "你好" to "Hello.",
        "你是谁" to "Who are you?",
        "我是谁" to "Who am I?",
        "谢谢" to "Thank you.",
        "收到" to "Got it.",
        "好的" to "Okay.",
        "可以" to "That works.",
        "不行" to "That will not work.",
        "没问题" to "No problem.",
        "没有问题" to "There is no problem.",
        "还是有问题" to "There is still a problem.",
        "稍等" to "Please wait a moment.",
        "我知道" to "I know.",
        "我来处理" to "I will handle it.",
        "我晚点处理" to "I will handle it later.",
        "请给出可执行步骤" to "Please provide actionable steps.",
        "请给我完整指令" to "Please give me the complete instructions.",
        "先不要扩大范围" to "Do not expand the scope yet.",
        "先完成当前版本" to "Finish the current version first."
    )

    private val BASE_EN_TO_ZH = mapOf(
        "hello" to "你好。",
        "who are you" to "你是谁？",
        "who am i" to "我是谁？",
        "thank you" to "谢谢。",
        "thanks" to "谢谢。",
        "got it" to "收到。",
        "okay" to "好的。",
        "no problem" to "没问题。",
        "there is still a problem" to "还是有问题。",
        "please wait a moment" to "请稍等。",
        "i know" to "我知道。",
        "i will handle it" to "我来处理。",
        "i will handle it later" to "我晚点处理。",
        "please provide actionable steps" to "请给出可执行步骤。",
        "please give me the complete instructions" to "请给我完整指令。"
    )
}
