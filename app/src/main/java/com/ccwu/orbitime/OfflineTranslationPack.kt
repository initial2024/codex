package com.ccwu.orbitime

import android.content.Context

object OfflineTranslationPack {
    data class Result(
        val translatedText: String,
        val confidence: String,
        val note: String,
    )

    fun isAvailable(context: Context): Boolean = ProGate.isProUnlocked(context)

    fun translateOrNull(source: String, direction: TranslatePromptBuilder.Direction): Result? {
        val text = source.trim()
        if (!PrivacyGuard.isSafeToUseForPrompt(text)) return null
        val exact = when (direction) {
            TranslatePromptBuilder.Direction.ZH_TO_EN -> zhToEn[text]
            TranslatePromptBuilder.Direction.EN_TO_ZH -> enToZh[text.lowercase()]
        }
        if (exact != null) {
            return Result(
                translatedText = exact,
                confidence = "exact-local",
                note = "Offline Pack exact phrase match",
            )
        }

        val wordByWord = when (direction) {
            TranslatePromptBuilder.Direction.ZH_TO_EN -> roughZhToEn(text)
            TranslatePromptBuilder.Direction.EN_TO_ZH -> roughEnToZh(text)
        }
        return wordByWord?.let {
            Result(
                translatedText = it,
                confidence = "rough-local",
                note = "Offline Pack rough local phrase assembly",
            )
        }
    }

    fun lockedMessage(): String = "Offline Pack is a Pro local feature"

    private fun roughZhToEn(text: String): String? {
        if (text.length > 40) return null
        val tokens = zhTokens.entries.fold(text) { acc, item -> acc.replace(item.key, " ${item.value} ") }
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        if (tokens.any { it.any { char -> char.code > 127 } }) return null
        return tokens.joinToString(" ").replace(Regex("\\s+"), " ").trim()
    }

    private fun roughEnToZh(text: String): String? {
        if (text.length > 80) return null
        val tokens = text.lowercase()
            .replace(Regex("[^a-z0-9\\s']"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        val mapped = tokens.map { enTokens[it] ?: return null }
        return mapped.joinToString("")
    }

    private val zhToEn = mapOf(
        "你好" to "Hello.",
        "谢谢" to "Thank you.",
        "收到" to "Got it.",
        "晚点处理" to "I will handle it later.",
        "我晚点处理" to "I will handle it later.",
        "请稍等" to "Please wait a moment.",
        "没问题" to "No problem.",
        "可以" to "That works.",
        "不行" to "That will not work.",
        "请给出可执行步骤" to "Please provide actionable steps.",
        "先不要扩大范围" to "Do not expand the scope yet.",
        "请指出风险" to "Please point out the risks.",
    )

    private val enToZh = mapOf(
        "hello" to "你好。",
        "thank you" to "谢谢。",
        "thanks" to "谢谢。",
        "got it" to "收到。",
        "no problem" to "没问题。",
        "please wait a moment" to "请稍等。",
        "i will handle it later" to "我晚点处理。",
        "please provide actionable steps" to "请给出可执行步骤。",
        "do not expand the scope yet" to "先不要扩大范围。",
    )

    private val zhTokens = mapOf(
        "我" to "I",
        "你" to "you",
        "我们" to "we",
        "这个" to "this",
        "问题" to "issue",
        "处理" to "handle",
        "稍等" to "wait a moment",
        "现在" to "now",
        "之后" to "later",
        "可以" to "can",
        "不" to "not",
        "需要" to "need",
        "步骤" to "steps",
        "风险" to "risks",
    )

    private val enTokens = mapOf(
        "i" to "我",
        "you" to "你",
        "we" to "我们",
        "this" to "这个",
        "issue" to "问题",
        "problem" to "问题",
        "handle" to "处理",
        "later" to "之后",
        "now" to "现在",
        "can" to "可以",
        "not" to "不",
        "need" to "需要",
        "steps" to "步骤",
        "risks" to "风险",
    )
}
