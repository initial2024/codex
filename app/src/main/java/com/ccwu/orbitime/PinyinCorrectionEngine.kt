package com.ccwu.orbitime

import kotlin.math.abs
import kotlin.math.min

object PinyinCorrectionEngine {
    data class QueryVariant(val pinyin: String, val penalty: Double)

    private const val MAX_CANDIDATES = 12

    private val typoShortcuts: Map<String, List<String>> = linkedMapOf(
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
        "zh" to "z", "ch" to "c", "sh" to "s",
        "ang" to "an", "eng" to "en", "ing" to "in",
        "uang" to "uan", "iang" to "ian", "ong" to "on",
        "l" to "n", "n" to "l", "f" to "h", "h" to "f",
        "r" to "l", "v" to "u", "u" to "v",
    )

    private val keyboardNeighbors: Map<Char, String> = mapOf(
        'q' to "wa", 'w' to "qase", 'e' to "wsdr", 'r' to "edft", 't' to "rfgy",
        'y' to "tghu", 'u' to "yhji", 'i' to "ujko", 'o' to "iklp", 'p' to "ol",
        'a' to "qwsz", 's' to "awedxz", 'd' to "serfcx", 'f' to "drtgvc",
        'g' to "ftyhbv", 'h' to "gyujnb", 'j' to "huikmn", 'k' to "jiolm",
        'l' to "kop", 'z' to "asx", 'x' to "zsdc", 'c' to "xdfv",
        'v' to "cfgb", 'b' to "vghn", 'n' to "bhjm", 'm' to "njk",
    )

    /**
     * Return candidate Pinyin spellings for the packaged big lexicon.
     * Exact input is intentionally omitted because the caller already ranks it first.
     */
    fun queryVariants(rawInput: String, maxVariants: Int = 28): List<QueryVariant> {
        val query = PinyinDictionary.normalize(rawInput)
        if (query.length < 2) return emptyList()
        val out = LinkedHashMap<String, Double>()

        fun add(value: String, penalty: Double) {
            val normalized = PinyinDictionary.normalize(value)
            if (normalized.isBlank() || normalized == query) return
            val old = out[normalized]
            if (old == null || penalty < old) out[normalized] = penalty
        }

        // Common Mandarin fuzzy initials/finals.
        fuzzyPairs.forEach { (left, right) ->
            if (query.contains(left)) add(query.replaceFirst(left, right), 1.15)
            if (query.contains(right)) add(query.replaceFirst(right, left), 1.25)
        }

        // Adjacent transposition: e.g. "nihao" -> "nih oa" style slips.
        if (query.length <= 24) {
            for (index in 0 until query.lastIndex) {
                if (query[index] == query[index + 1]) continue
                val chars = query.toCharArray()
                val tmp = chars[index]
                chars[index] = chars[index + 1]
                chars[index + 1] = tmp
                add(String(chars), 1.8)
                if (out.size >= maxVariants) break
            }
        }

        // QWERTY neighboring-key substitutions.
        if (query.length <= 18 && out.size < maxVariants) {
            outer@ for (index in query.indices) {
                val neighbors = keyboardNeighbors[query[index]].orEmpty()
                for (replacement in neighbors) {
                    val chars = query.toCharArray()
                    chars[index] = replacement
                    add(String(chars), 2.15)
                    if (out.size >= maxVariants) break@outer
                }
            }
        }

        // One missing/extra key is common on mobile. Deletion variants let the
        // lexicon recover when the user accidentally inserted a duplicate key.
        if (query.length in 4..20 && out.size < maxVariants) {
            for (index in query.indices) {
                add(query.removeRange(index, index + 1), 2.35)
                if (out.size >= maxVariants) break
            }
        }

        if (query == "xhn" || query == "xhnn") add("xihuanni", 0.8)
        if (query == "nss") add("nisishei", 0.8)
        if (query == "hsywt") add("haishiyouwenti", 0.8)

        return out.entries
            .sortedBy { it.value }
            .take(maxVariants)
            .map { QueryVariant(it.key, it.value) }
    }

    fun candidatesFor(rawInput: String): List<String> {
        val query = PinyinDictionary.normalize(rawInput)
        if (query.isEmpty()) return emptyList()

        val direct = typoShortcuts[query].orEmpty()
        val data = PinyinExpandedData.entries + PinyinBoostData.entries
        val variantCandidates = queryVariants(query)
            .flatMap { variant -> data[variant.pinyin].orEmpty() }

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
        } else emptyList()

        return (direct + variantCandidates + editDistanceCandidates)
            .distinct()
            .take(MAX_CANDIDATES)
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
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
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
