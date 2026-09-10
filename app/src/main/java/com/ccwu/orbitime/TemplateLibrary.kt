package com.ccwu.orbitime

object TemplateLibrary {
    data class HubAction(
        val label: String,
        val insertText: String,
    )

    val defaultActions: List<HubAction> = listOf(
        HubAction("Polish", "请润色下面这段文字，保持原意，减少废话："),
        HubAction("Explain", "解释下面这句话，先给结论，再拆结构："),
        HubAction("Study", "按考研英语一标准分析这个长难句："),
        HubAction("Prompt", "请按【明确结论 -> 关键依据 -> 推理链路 -> 风险与熔断条件】回答："),
        HubAction("Pro", "Orbit Pro 占位：高级模板、5000 条本地剪贴板、标签、主题皮肤、离线翻译包。"),
    )

    val quickPhrases: List<String> = listOf(
        "收到，我晚点处理。",
        "请给出可执行步骤。",
        "先不要扩大范围，只完成当前版本。",
        "请指出风险和熔断条件。",
        "请用更严谨的方式重写。",
    )
}
