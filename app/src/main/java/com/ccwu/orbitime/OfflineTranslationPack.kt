package com.ccwu.orbitime

object OfflineTranslationPack {
    data class Result(
        val translatedText: String,
        val confidence: String,
        val note: String,
    )

    fun translateOrNull(source: String, direction: TranslatePromptBuilder.Direction): Result? {
        val text = source.trim().trimEnd('。', '！', '？', '.', '!', '?')
        if (!PrivacyGuard.isSafeToUseForPrompt(text)) return null

        val exact = when (direction) {
            TranslatePromptBuilder.Direction.ZH_TO_EN -> zhToEn[text]
            TranslatePromptBuilder.Direction.EN_TO_ZH -> enToZh[text.lowercase()]
        }
        if (exact != null) {
            return Result(exact, "exact-local", "本地短句精确匹配")
        }

        val rough = when (direction) {
            TranslatePromptBuilder.Direction.ZH_TO_EN -> roughZhToEn(text)
            TranslatePromptBuilder.Direction.EN_TO_ZH -> roughEnToZh(text)
        }
        return rough?.let { Result(it, "rough-local", "本地短语保守拼接") }
    }

    fun unavailableMessage(): String = "暂无离线译文，可插入提示词。"

    private fun roughZhToEn(text: String): String? {
        if (text.length > 30) return null
        var remaining = text
        val output = mutableListOf<String>()
        while (remaining.isNotEmpty()) {
            val match = zhTokens.keys.sortedByDescending { it.length }.firstOrNull { remaining.startsWith(it) } ?: return null
            output.add(zhTokens.getValue(match))
            remaining = remaining.removePrefix(match)
        }
        if (output.isEmpty()) return null
        return output.joinToString(" ").replace(Regex("\\s+"), " ").trim().replaceFirstChar { it.uppercase() } + "."
    }

    private fun roughEnToZh(text: String): String? {
        if (text.length > 80) return null
        val tokens = text.lowercase()
            .replace(Regex("[^a-z0-9\\s']"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        val mapped = tokens.map { enTokens[it] ?: return null }
        return mapped.joinToString("") + "。"
    }

    private val zhToEn = mapOf(
        "你好" to "Hello.",
        "你是谁" to "Who are you?",
        "我是谁" to "Who am I?",
        "我是" to "I am",
        "谢谢" to "Thank you.",
        "收到" to "Got it.",
        "好的" to "Okay.",
        "可以" to "That works.",
        "不行" to "That will not work.",
        "没问题" to "No problem.",
        "没有问题" to "There is no problem.",
        "还是有问题" to "There is still a problem.",
        "稍等" to "Please wait a moment.",
        "稍等一下" to "Please wait a moment.",
        "等一下" to "Wait a moment.",
        "我知道" to "I know.",
        "我来处理" to "I will handle it.",
        "我晚点处理" to "I will handle it later.",
        "晚点处理" to "I will handle it later.",
        "晚点再处理" to "I will handle it later.",
        "请给出可执行步骤" to "Please provide actionable steps.",
        "请给我完整指令" to "Please give me the complete instruction.",
        "先不要扩大范围" to "Do not expand the scope yet.",
        "先完成当前版本" to "Finish the current version first.",
        "不要继续加新功能" to "Do not continue adding new features.",
        "构建是否成功" to "Did the build succeed?",
        "日志关键错误是什么" to "What is the key error in the log?",
        "有没有新增权限" to "Were any new permissions added?",
        "请检查" to "Please check it.",
        "请修复" to "Please fix it.",
        "请确认" to "Please confirm it.",
        "请不要改其他地方" to "Please do not change anything else.",
        "只做最小修复" to "Only make the minimum necessary fix."
    )

    private val enToZh = mapOf(
        "hello" to "你好。",
        "who are you" to "你是谁？",
        "who am i" to "我是谁？",
        "thank you" to "谢谢。",
        "thanks" to "谢谢。",
        "got it" to "收到。",
        "okay" to "好的。",
        "ok" to "好的。",
        "no problem" to "没问题。",
        "there is still a problem" to "还是有问题。",
        "please wait a moment" to "请稍等。",
        "wait a moment" to "等一下。",
        "i know" to "我知道。",
        "i will handle it" to "我来处理。",
        "i will handle it later" to "我晚点处理。",
        "please provide actionable steps" to "请给出可执行步骤。",
        "please give me the complete instruction" to "请给我完整指令。",
        "do not expand the scope yet" to "先不要扩大范围。",
        "finish the current version first" to "先完成当前版本。",
        "do not continue adding new features" to "不要继续加新功能。",
        "did the build succeed" to "构建是否成功？",
        "please check it" to "请检查。",
        "please fix it" to "请修复。",
        "please confirm it" to "请确认。"
    )

    private val zhTokens = mapOf(
        "我" to "I",
        "你" to "you",
        "我们" to "we",
        "这个" to "this",
        "还是" to "still",
        "有" to "have",
        "没有" to "do not have",
        "问题" to "problem",
        "处理" to "handle",
        "稍等" to "wait a moment",
        "现在" to "now",
        "之后" to "later",
        "晚点" to "later",
        "可以" to "can",
        "不" to "not",
        "需要" to "need",
        "步骤" to "steps",
        "风险" to "risks"
    )

    private val enTokens = mapOf(
        "i" to "我",
        "you" to "你",
        "we" to "我们",
        "this" to "这个",
        "still" to "仍然",
        "have" to "有",
        "problem" to "问题",
        "issue" to "问题",
        "handle" to "处理",
        "later" to "之后",
        "now" to "现在",
        "can" to "可以",
        "not" to "不",
        "need" to "需要",
        "steps" to "步骤",
        "risks" to "风险"
    )
}
