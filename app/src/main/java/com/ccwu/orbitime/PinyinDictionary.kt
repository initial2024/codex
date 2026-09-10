package com.ccwu.orbitime

import java.util.Locale

object PinyinDictionary {
    private const val MAX_CANDIDATES = 10

    private val coreEntries: Map<String, List<String>> = linkedMapOf(
        // Core single syllables. The boost data adds phrase and shortcut coverage.
        "a" to listOf("啊", "阿"),
        "ai" to listOf("爱", "哎", "矮"),
        "an" to listOf("安", "按", "案"),
        "ao" to listOf("奥", "熬"),
        "ba" to listOf("吧", "把", "八", "爸"),
        "bai" to listOf("白", "百", "摆"),
        "ban" to listOf("办", "班", "半", "版"),
        "bao" to listOf("包", "保", "报", "宝"),
        "bei" to listOf("被", "北", "背"),
        "ben" to listOf("本", "笨"),
        "bi" to listOf("比", "笔", "必"),
        "bian" to listOf("边", "变", "便"),
        "bie" to listOf("别"),
        "bing" to listOf("并", "病", "冰", "兵"),
        "bu" to listOf("不", "部", "步"),
        "cai" to listOf("才", "菜", "材"),
        "ce" to listOf("测", "策"),
        "cha" to listOf("查", "差", "茶"),
        "chang" to listOf("长", "常", "场"),
        "cheng" to listOf("成", "程", "城"),
        "chu" to listOf("出", "处"),
        "cuo" to listOf("错"),
        "da" to listOf("大", "答", "打"),
        "dai" to listOf("带", "待"),
        "dan" to listOf("但", "单", "担"),
        "dao" to listOf("到", "道"),
        "de" to listOf("的", "得", "德"),
        "deng" to listOf("等"),
        "dian" to listOf("点", "电"),
        "dong" to listOf("动", "懂", "东"),
        "dui" to listOf("对", "队"),
        "duo" to listOf("多"),
        "en" to listOf("嗯"),
        "fa" to listOf("发", "法"),
        "fan" to listOf("反", "饭", "翻"),
        "fang" to listOf("方", "放"),
        "fei" to listOf("非", "费"),
        "fen" to listOf("分", "份"),
        "fu" to listOf("复", "付", "服"),
        "gai" to listOf("该", "改"),
        "gan" to listOf("干", "感"),
        "gao" to listOf("高", "搞"),
        "ge" to listOf("个", "各"),
        "gei" to listOf("给"),
        "gong" to listOf("工", "公", "功"),
        "gou" to listOf("够", "购"),
        "guo" to listOf("过", "国", "果"),
        "hai" to listOf("还", "海", "孩"),
        "hao" to listOf("好", "号", "浩"),
        "he" to listOf("和", "喝", "合"),
        "hen" to listOf("很"),
        "hou" to listOf("后", "候"),
        "hui" to listOf("会", "回", "汇"),
        "huo" to listOf("或", "活"),
        "ji" to listOf("机", "几", "及", "记"),
        "jia" to listOf("加", "家", "假"),
        "jian" to listOf("件", "间", "建", "检"),
        "jiang" to listOf("讲", "将", "降"),
        "jiao" to listOf("叫", "教", "交"),
        "jie" to listOf("解", "接", "节"),
        "jin" to listOf("进", "近", "今"),
        "jiu" to listOf("就", "九"),
        "ju" to listOf("据", "句", "局"),
        "kao" to listOf("考", "靠"),
        "ke" to listOf("可", "课", "科"),
        "lai" to listOf("来"),
        "le" to listOf("了", "乐"),
        "li" to listOf("里", "理", "力"),
        "ma" to listOf("吗", "嘛", "马"),
        "mei" to listOf("没", "美", "每"),
        "men" to listOf("们", "门"),
        "ming" to listOf("明", "名"),
        "na" to listOf("那", "拿"),
        "nan" to listOf("南", "难"),
        "neng" to listOf("能"),
        "ni" to listOf("你", "尼"),
        "nian" to listOf("年", "念"),
        "o" to listOf("哦"),
        "pa" to listOf("怕", "爬"),
        "pin" to listOf("拼", "品"),
        "qi" to listOf("起", "其", "期"),
        "qian" to listOf("前", "钱"),
        "qing" to listOf("请", "清"),
        "qu" to listOf("去", "区"),
        "ran" to listOf("然"),
        "rang" to listOf("让"),
        "ren" to listOf("人", "认"),
        "ru" to listOf("如", "入"),
        "shang" to listOf("上"),
        "shao" to listOf("少"),
        "shen" to listOf("什", "深", "申"),
        "sheng" to listOf("生", "省", "升"),
        "shi" to listOf("是", "时", "事", "式"),
        "shou" to listOf("手", "收"),
        "shu" to listOf("书", "数", "输"),
        "suan" to listOf("算"),
        "ta" to listOf("他", "她", "它"),
        "ti" to listOf("题", "体"),
        "tian" to listOf("天", "填"),
        "ting" to listOf("听", "停"),
        "tong" to listOf("同", "通"),
        "wan" to listOf("完", "晚"),
        "wei" to listOf("为", "位", "微"),
        "wen" to listOf("问", "文"),
        "wo" to listOf("我"),
        "wu" to listOf("五", "无", "物"),
        "xi" to listOf("习", "西", "细"),
        "xia" to listOf("下"),
        "xian" to listOf("先", "线", "现"),
        "xiang" to listOf("想", "向", "像"),
        "xiao" to listOf("小", "校"),
        "xie" to listOf("写", "些", "谢"),
        "xin" to listOf("新", "心", "信"),
        "xue" to listOf("学", "雪"),
        "yao" to listOf("要"),
        "ye" to listOf("也", "页"),
        "yi" to listOf("一", "已", "以", "易"),
        "ying" to listOf("应", "英", "影"),
        "yong" to listOf("用"),
        "you" to listOf("有", "又", "由"),
        "yu" to listOf("与", "语", "于"),
        "zai" to listOf("在", "再"),
        "zhe" to listOf("这"),
        "zhen" to listOf("真"),
        "zheng" to listOf("正", "证", "政"),
        "zhi" to listOf("只", "知", "直"),
        "zhong" to listOf("中", "种"),
        "zhu" to listOf("主", "住"),
        "zhuan" to listOf("专", "转"),
        "zi" to listOf("字", "自"),
        "zuo" to listOf("做", "作", "左")
    )

    fun exactCandidatesFor(rawInput: String): List<String> {
        val query = normalize(rawInput)
        if (query.isEmpty()) return emptyList()
        return candidatesForKey(query).take(MAX_CANDIDATES)
    }

    fun candidatesFor(rawInput: String): List<String> {
        val query = normalize(rawInput)
        if (query.isEmpty()) return emptyList()

        val exact = exactCandidatesFor(query)
        val prefixMatches = allEntries()
            .filter { (key, _) -> key != query && key.startsWith(query) }
            .flatMap { (_, values) -> values.asSequence().take(2) }
            .distinct()
            .toList()

        val containsMatches = if (exact.isEmpty() && prefixMatches.size < MAX_CANDIDATES / 2) {
            allEntries()
                .filter { (key, _) -> key != query && key.contains(query) }
                .flatMap { (_, values) -> values.asSequence().take(1) }
                .distinct()
                .toList()
        } else {
            emptyList()
        }

        return (exact + prefixMatches + containsMatches + query)
            .distinct()
            .take(MAX_CANDIDATES)
            .ifEmpty { listOf(query) }
    }

    fun normalize(value: String): String {
        return value.lowercase(Locale.ROOT).filter { it in 'a'..'z' }
    }

    private fun candidatesForKey(query: String): List<String> {
        return (PinyinBoostData.entries[query].orEmpty() + coreEntries[query].orEmpty()).distinct()
    }

    private fun allEntries(): Sequence<Map.Entry<String, List<String>>> {
        return PinyinBoostData.entries.asSequence() + coreEntries.asSequence()
    }
}
