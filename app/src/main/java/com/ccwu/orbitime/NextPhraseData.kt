package com.ccwu.orbitime

/** Small project-authored high-confidence association overlay. N-gram predictions fill the long tail. */
object NextPhraseData {
    private val entries = linkedMapOf(
        "你好" to listOf("，很高兴认识你", "，有什么可以帮你", "吗", "！"),
        "可以" to listOf("的", "，没问题", "，继续", "这样处理"),
        "好的" to listOf("，收到", "，我知道了", "，马上处理", "。"),
        "收到" to listOf("，我来处理", "，稍后回复", "，谢谢", "。"),
        "谢谢" to listOf("！", "你的帮助", "，麻烦你了", "。"),
        "问题" to listOf("已经解决", "还没有解决", "出在哪里", "比较明显"),
        "还是有问题" to listOf("，继续检查", "，需要修复", "，没有完全解决", "。"),
        "数据库" to listOf("不够", "需要扩充", "已经更新", "仍然有问题"),
        "词库" to listOf("不够", "需要扩充", "已经加载", "需要优化排序"),
        "输入法" to listOf("设置", "候选", "词库", "需要继续优化"),
        "候选" to listOf("太少", "排序", "联想", "需要更多"),
        "翻译" to listOf("结果", "功能", "模式", "需要上下文"),
        "本地" to listOf("词库", "翻译", "学习", "处理"),
        "继续" to listOf("优化", "完善", "检查", "处理"),
        "需要" to listOf("继续优化", "重新检查", "增加数据", "进一步完善"),
        "学习" to listOf("计划", "进度", "效率", "资料"),
        "考研" to listOf("英语", "数学", "复习", "计划"),
        "数学" to listOf("二", "复习", "练习", "错题"),
        "英语" to listOf("阅读", "精读", "词汇", "翻译"),
        "材料" to listOf("科学基础", "工程", "化学", "性能"),
        "代码" to listOf("已经修改", "需要检查", "编译失败", "可以提交"),
        "构建" to listOf("成功", "失败", "APK", "日志"),
        "GitHub" to listOf("仓库", "Actions", "提交", "Issue"),
        "Codex" to listOf("指令", "构建", "检查", "修复"),
        "ChatGPT" to listOf("可以", "功能", "会员", "设置"),
        "微信" to listOf("聊天", "支付", "小程序", "文件"),
        "QQ" to listOf("聊天", "音乐", "邮箱", "文件"),
        "支付宝" to listOf("支付", "账单", "余额", "小程序"),
        "淘宝" to listOf("订单", "购物", "退款", "店铺"),
        "京东" to listOf("订单", "物流", "购物", "售后"),
        "抖音" to listOf("视频", "直播", "评论", "账号"),
        "小红书" to listOf("笔记", "搜索", "评论", "账号"),
        "知乎" to listOf("回答", "问题", "文章", "搜索"),
        "百度" to listOf("搜索", "网盘", "地图", "文库"),
        "高德地图" to listOf("导航", "路线", "定位", "公交"),
        "哔哩哔哩" to listOf("视频", "评论", "动态", "直播"),
        "OpenAI" to listOf("API", "ChatGPT", "Codex", "模型"),
        "DeepSeek" to listOf("模型", "API", "本地部署", "推理"),
        "Kotlin" to listOf("代码", "Android", "编译", "项目"),
        "Python" to listOf("脚本", "环境", "代码", "数据处理"),
        "Android" to listOf("应用", "输入法", "系统", "开发"),
        "Windows" to listOf("系统", "设置", "文件", "应用"),
    )

    fun suggestions(context: String, limit: Int = 16): List<String> {
        val normalized = context.trimEnd()
        if (normalized.isBlank()) return emptyList()
        return entries.entries.asSequence()
            .filter { (key, _) -> normalized.endsWith(key) }
            .sortedByDescending { it.key.length }
            .flatMap { it.value.asSequence() }
            .distinct()
            .take(limit)
            .toList()
    }
}
