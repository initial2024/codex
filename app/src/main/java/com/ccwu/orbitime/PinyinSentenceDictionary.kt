package com.ccwu.orbitime

object PinyinSentenceDictionary {
    private const val MAX_CANDIDATES = 12

    private val sentenceShortcuts: Map<String, List<String>> = linkedMapOf(
        // 高频聊天整句 / 简拼
        "nh" to listOf("你好", "你好吗", "那好"),
        "nhao" to listOf("你好"),
        "nss" to listOf("你是谁"),
        "nisishei" to listOf("你是谁"),
        "wss" to listOf("我是谁"),
        "wsm" to listOf("为什么", "我什么"),
        "wsd" to listOf("我收到"),
        "wzd" to listOf("我知道"),
        "wzdl" to listOf("我知道了"),
        "wdcl" to listOf("晚点处理", "我等会处理"),
        "wdzcl" to listOf("晚点再处理"),
        "wlaicl" to listOf("我来处理"),
        "wcl" to listOf("我来处理", "我处理"),
        "wxyx" to listOf("我想一下"),
        "wkyx" to listOf("我看一下"),
        "wqr" to listOf("我确认一下"),
        "wzjc" to listOf("我再检查一遍"),
        "sdyx" to listOf("稍等一下"),
        "dyx" to listOf("等一下"),
        "dhy" to listOf("等会呀"),
        "hsywt" to listOf("还是有问题"),
        "hywt" to listOf("还有问题"),
        "mywt" to listOf("没有问题", "没问题"),
        "mwt" to listOf("没问题"),
        "bky" to listOf("不可以"),
        "bx" to listOf("不行"),
        "ky" to listOf("可以"),
        "hd" to listOf("好的"),
        "sd" to listOf("收到"),
        "xiexie" to listOf("谢谢"),
        "xx" to listOf("谢谢", "学习", "消息"),
        "mfnl" to listOf("麻烦你了"),
        "jtxdzl" to listOf("今天先到这里"),
        "mtjx" to listOf("明天继续"),

        // 开发/协作整句
        "qjc" to listOf("请检查一下"),
        "qxf" to listOf("请修复一下"),
        "qgz" to listOf("请改正", "请告知"),
        "qgy" to listOf("请给我"),
        "qgzwz" to listOf("请给出完整指令"),
        "qgckzxbz" to listOf("请给出可执行步骤"),
        "qzxwt" to listOf("请指出问题"),
        "qzxfx" to listOf("请指出风险"),
        "qfsm" to listOf("请说明失败原因"),
        "qxzxd" to listOf("请只做最小修复"),
        "qxbd" to listOf("请先做只读检查"),
        "bykdfw" to listOf("不要扩大范围"),
        "xbykdfw" to listOf("先不要扩大范围"),
        "byjxjgn" to listOf("不要继续加新功能"),
        "byaqx" to listOf("不要添加权限"),
        "byaint" to listOf("不要添加 INTERNET 权限"),
        "byaad" to listOf("不要添加广告 SDK"),
        "byac" to listOf("不要添加云端 API"),
        "bcjqb" to listOf("保存剪贴板"),
        "qkydck" to listOf("清空用户词库"),
        "gxreadme" to listOf("更新 README"),
        "gxyinsi" to listOf("更新隐私政策"),
        "gxissue" to listOf("更新 Issue"),
        "ksgj" to listOf("开始构建"),
        "xbygj" to listOf("先不要构建"),
        "gjcg" to listOf("构建成功"),
        "gjsb" to listOf("构建失败"),
        "apkdz" to listOf("APK 地址", "APK 路径"),

        // 输入法/翻译问题整句
        "sry" to listOf("输入法"),
        "srf" to listOf("输入法"),
        "jqb" to listOf("剪贴板", "剪切板"),
        "fyjg" to listOf("翻译结果"),
        "myfyjg" to listOf("没有翻译结果"),
        "bscgfy" to listOf("不是成功翻译"),
        "zsts" to listOf("只是提示词"),
        "cbbg" to listOf("词表不够"),
        "sjkb" to listOf("数据库不够"),
        "bnhcjz" to listOf("不能形成句子"),
        "bnhxqtd" to listOf("不能像其他的"),
        "sywt" to listOf("是有问题"),
        "hxywt" to listOf("还是有问题"),
        "bty" to listOf("不好用"),
        "bzy" to listOf("不专业"),
        "xyz" to listOf("需要专业一点"),
        "xhfnivh" to listOf("喜欢你", "想和你说", "需要优化"),

        // 学习/写作整句
        "qygypwz" to listOf("请用更严谨的方式重写"),
        "qxgl" to listOf("请先给结论"),
        "qfbzt" to listOf("请分步骤推导"),
        "bydgsk" to listOf("不要用大公式框"),
        "ywbwb" to listOf("用普通文本展示"),
        "qgbsb" to listOf("请给我背诵版"),
        "qysqdd" to listOf("请用少量重点"),
        "zdynd" to listOf("重点与难点")
    )

    fun candidatesFor(rawInput: String): List<String> {
        val query = PinyinDictionary.normalize(rawInput)
        if (query.isEmpty()) return emptyList()

        val base = sentenceShortcuts[query].orEmpty()
        val expanded = PinyinExpandedData.candidatesFor(query)
        val fuzzy = PinyinCorrectionEngine.candidatesFor(query)
        val prefix = sentenceShortcuts.asSequence()
            .filter { (key, _) -> key != query && key.startsWith(query) }
            .flatMap { (_, values) -> values.asSequence().take(2) }
            .toList()

        return (base + expanded + fuzzy + prefix)
            .distinct()
            .take(MAX_CANDIDATES)
    }
}
