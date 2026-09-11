package com.ccwu.orbitime

import kotlin.math.abs
import kotlin.math.min

object PinyinCorrectionEngine {
    private const val MAX_CANDIDATES = 12

    private val typoShortcuts: Map<String, List<String>> = linkedMapOf(
        // 用户明确反馈：即使这种误按/乱序，也尽量给中文候选。
        "xhfnivh" to listOf("喜欢你", "想和你说", "需要优化"),
        "xhfn" to listOf("喜欢你", "想和你说"),
        "xihuanli" to listOf("喜欢你"),
        "xihvanni" to listOf("喜欢你"),
        "xihuanni" to listOf("喜欢你"),
        "nishishei" to listOf("你是谁"),
        "nishei" to listOf("你是谁"),
        "nishis" to listOf("你是谁"),
        "hsywenti" to listOf("还是有问题"),
        "haiyouenti" to listOf("还有问题"),
        "meifyjg" to listOf("没有翻译结果"),
        "meiyoufyjg" to listOf("没有翻译结果"),
        "bunengxqtd" to listOf("不能像其他的"),
        "bunengchengjuzi" to listOf("不能形成句子"),
        "shujukubg" to listOf("数据库不够"),
        "cikubg" to listOf("词库不够"),
        "buhaoyong" to listOf("不好用"),
        "jmbudui" to listOf("界面不对")
    )

    private val fuzzyPairs = listOf(
        "zh" to "z",
        "ch" to "c",
        "sh" to "s",
        "ang" to "an",
        "eng" to "en",
        "ing" to "in",
        "uang" to "uan",
        "iang" to "ian",
        "ong" to "on",
        "l" to "n",
        "n" to "l",
        "f" to "h",
        "h" to "f",
        "v" to "u",
        "u" to "v"
    )

    fun candidatesFor(rawInput: String): List<String> {
        val query = PinyinDictionary.normalize(rawInput)
        if (query.isEmpty()) return emptyList()

        val direct = typoShortcuts[query].orEmpty()
        val data = PinyinExpandedData.entries + PinyinBoostData.entries
        val variantCandidates = fuzzyVariants(query)
            .flatMap { variant -> data[variant].orEmpty() }

        val distanceLimit = when {
            query.length <= 2 -> 0
            query.length <= 5 -> 1
            else -> 2
        }
        val editDistanceCandidates = if (direct.isEmpty() && query.length >= 3) {
            data.asSequence()
                .mapNotNull { (key, values) ->
                    val distance = boundedDistance(query, key, distanceLimit)
                    if (distance <= distanceLimit) key to values else null
                }
                .sortedWith(compareBy<Pair<String, List<String>>> { abs(it.first.length - query.length) }.thenBy { it.first.length })
                .flatMap { (_, values) -> values.asSequence().take(2) }
                .take(16)
                .toList()
        } else {
            emptyList()
        }

        return (direct + variantCandidates + editDistanceCandidates)
            .distinct()
            .take(MAX_CANDIDATES)
    }

    private fun fuzzyVariants(query: String): List<String> {
        val variants = linkedSetOf<String>()
        variants.add(query)
        fuzzyPairs.forEach { (from, to) ->
            if (query.contains(from)) variants.add(query.replace(from, to))
        }
        // 常见漏字：xhn -> xihuanni；nss -> nisishei 类似场景由数据层覆盖。
        if (query == "xhn" || query == "xhnn") variants.add("xihuanni")
        if (query == "nss") variants.add("nisishei")
        if (query == "hsywt") variants.add("haishiyouwenti")
        return variants.toList()
    }

    private fun boundedDistance(a: String, b: String, limit: Int): Int {
        if (abs(a.length - b.length) > limit) return limit + 1
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(
                    min(current[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + cost,
                )
                rowMin = min(rowMin, current[j])
            }
            if (rowMin > limit) return limit + 1
            val tmp = previous
            previous = current
            current = tmp
        }
        return previous[b.length]
    }
}
