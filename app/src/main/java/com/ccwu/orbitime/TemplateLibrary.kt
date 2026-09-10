package com.ccwu.orbitime

object TemplateLibrary {
    data class HubAction(
        val label: String,
        val insertText: String,
    )

    // Kept for future secondary panels. Hidden from the first-level Hub to keep typing fast.
    val defaultActions: List<HubAction> = listOf(
        HubAction("润色", "请润色下面这段文字，保持原意，减少废话："),
        HubAction("解释", "解释下面这句话，先给结论，再拆结构："),
        HubAction("学习", "按考研英语一标准分析这个长难句："),
        HubAction("提示词", "请按【明确结论 -> 关键依据 -> 推理链路 -> 风险与熔断条件】回答："),
        HubAction("Pro", "Orbit Pro 占位：高级模板、5000 条本地剪贴板、标签、主题皮肤、离线翻译包。"),
    )

    val chineseQuickPhrases: List<String> = listOf(
        // 高频确认
        "收到。",
        "好的。",
        "可以。",
        "没问题。",
        "我知道了。",
        "明白了。",
        "稍等一下。",
        "我晚点处理。",
        "我现在处理。",
        "我看一下。",
        "我确认一下。",
        "我再检查一遍。",
        "先这样。",
        "先不要扩大范围。",
        "先完成当前版本。",

        // 沟通推进
        "请给出可执行步骤。",
        "请直接说结论。",
        "请指出关键问题。",
        "请列出修改点。",
        "请说明失败原因。",
        "请给我完整指令。",
        "请发给 Codex 的指令。",
        "请按步骤执行。",
        "请不要改其他地方。",
        "请只做最小修复。",
        "请先做只读检查。",
        "请不要继续加新功能。",
        "请同步文档。",
        "请更新构建任务。",
        "请返回验收结果。",

        // 项目/开发
        "构建是否成功？",
        "APK 路径是什么？",
        "日志关键错误是什么？",
        "有没有新增权限？",
        "不要添加 INTERNET 权限。",
        "不要添加广告 SDK。",
        "不要添加 analytics。",
        "不要添加 Accessibility。",
        "不要添加悬浮窗权限。",
        "不要接云端 API。",
        "不要后台采集剪贴板。",
        "只修 Kotlin 编译错误。",
        "只修资源引用问题。",
        "保持当前架构。",
        "不要重构。",
        "提交到 GitHub。",
        "更新 README。",
        "更新隐私政策。",
        "更新 Issue。",
        "先不要构建。",
        "可以开始构建。",

        // 学习/写作
        "请用更严谨的方式重写。",
        "请按考试要求整理。",
        "请给出重点和难点。",
        "请先给结论，再解释。",
        "请分步骤推导。",
        "请不要用大公式框。",
        "请用普通文本展示。",
        "请给我背诵版。",
        "请压缩成清单。",
        "请保留核心依据。",

        // 日常
        "我一会回复你。",
        "我现在不方便。",
        "晚点再说。",
        "今天先到这里。",
        "明天继续。",
        "麻烦你了。",
        "谢谢。",
        "不用了。",
        "先不用管。",
        "后续再处理。",
    )

    val englishQuickPhrases: List<String> = listOf(
        // Confirmation
        "Got it.",
        "Okay.",
        "Sounds good.",
        "No problem.",
        "I understand.",
        "Let me check.",
        "One moment, please.",
        "I will handle it later.",
        "I will handle it now.",
        "I will confirm first.",
        "I will check it again.",
        "Let's keep the scope small.",
        "Let's finish this version first.",

        // Requests
        "Please give actionable steps.",
        "Please give the conclusion first.",
        "Please list the key issues.",
        "Please explain the failure reason.",
        "Please provide the exact command.",
        "Please write the Codex instruction.",
        "Please do a read-only check first.",
        "Please do not expand the scope.",
        "Please make the smallest fix possible.",
        "Please update the documentation.",
        "Please return the acceptance result.",

        // Development
        "Did the build pass?",
        "What is the APK path?",
        "What is the key error log?",
        "Do not add INTERNET permission.",
        "Do not add any ad SDK.",
        "Do not add analytics.",
        "Do not add Accessibility permission.",
        "Do not add overlay permission.",
        "Do not add a cloud API.",
        "Do not collect clipboard data in the background.",
        "Only fix Kotlin compile errors.",
        "Only fix resource reference errors.",
        "Keep the current architecture.",
        "Do not refactor this now.",
        "Commit the changes to GitHub.",
        "Update the README.",
        "Update the privacy policy.",
        "Update the issue.",
        "Do not build yet.",
        "You can start the build now.",

        // Writing and study
        "Please rewrite this more rigorously.",
        "Please summarize the key points.",
        "Please explain step by step.",
        "Please avoid large formula blocks.",
        "Please keep the core reasoning.",
        "Please make it concise.",
        "Please make it more natural.",
        "Please translate this into natural English.",
        "Please translate this into Chinese.",

        // Daily
        "I will reply later.",
        "I am not available right now.",
        "Let's continue tomorrow.",
        "Thanks.",
        "No need for now.",
        "We can handle this later.",
    )

    fun quickPhrasesForPinyin(): List<String> = chineseQuickPhrases

    fun quickPhrasesForEnglish(): List<String> = englishQuickPhrases
}
