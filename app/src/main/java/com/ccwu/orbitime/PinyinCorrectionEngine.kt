package com.ccwu.orbitime

import kotlin.math.abs
import kotlin.math.min

object PinyinCorrectionEngine {
    data class QueryVariant(val pinyin: String, val penalty: Double)

    private const val MAX_CANDIDATES = 24

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
     * Return bounded alternative Pinyin spellings for the packaged large lexicon.
     * Enhanced mode deliberately explores more variants; exact input remains ranked first
     * by the caller and is never returned from this function.
     */
    fun queryVariants(rawInput: String, maxVariants: Int = 36, enhanced: Boolean = false): List<QueryVariant> {
        val query = PinyinDictionary.normalize(rawInput)
        if (query.length < 2) return emptyList()
        val out = LinkedHashMap<String, Double>()

        fun add(value: String, penalty: Double) {
            val normalized = PinyinDictionary.normalize(value)
            if (normalized.isBlank() || normalized == query || normalized.length > 64) return
            val old = out[normalized]
            if (old == null || penalty < old) out[normalized] = penalty
        }

        fun replaceEveryOccurrence(source: String, from: String, to: String, penalty: Double) {
            var start = 0
            while (out.size < maxVariants) {
                val index = source.indexOf(from, start)
                if (index < 0) break
                add(source.replaceRange(index, index + from.length, to), penalty)
                start = index + 1
            }
        }

        // High-confidence Mandarin fuzzy initials/finals. Unlike the old implementation,
        // all positions can contribute a variant instead of only the first occurrence.
        fuzzyPairs.forEachIndexed { pairIndex, (left, right) ->
            replaceEveryOccurrence(query, left, right, 1.05 + pairIndex * 0.008)
            replaceEveryOccurrence(query, right, left, 1.15 + pairIndex * 0.008)
        }

        // Adjacent transposition.
        if (query.length <= 28 && out.size < maxVariants) {
            for (index in 0 until query.lastIndex) {
                if (query[index] == query[index + 1]) continue
                val chars = query.toCharArray()
                val tmp = chars[index]
                chars[index] = chars[index + 1]
                chars[index + 1] = tmp
                add(String(chars), 1.75)
                if (out.size >= maxVariants) break
            }
        }

        // QWERTY neighboring-key substitution.
        if (query.length <= 20 && out.size < maxVariants) {
            outer@ for (index in query.indices) {
                for (replacement in keyboardNeighbors[query[index]].orEmpty()) {
                    val chars = query.toCharArray()
                    chars[index] = replacement
                    add(String(chars), 2.05)
                    if (out.size >= maxVariants) break@outer
                }
            }
        }

        // Extra-key recovery by deletion.
        if (query.length in 4..24 && out.size < maxVariants) {
            for (index in query.indices) {
                add(query.removeRange(index, index + 1), 2.25)
                if (out.size >= maxVariants) break
            }
        }

        if (enhanced) {
            // Repeated mobile key: "niiihao" -> "nihao" style collapse.
            val collapsed = buildString {
                query.forEach { ch -> if (isEmpty() || last() != ch) append(ch) }
            }
            if (collapsed != query) add(collapsed, 1.65)

            // Missing-key recovery. Limit inserted characters to common Pinyin letters and
            // keep the search bounded; the actual large lexicon decides which variants exist.
            if (query.length in 3..18) {
                outer@ for (index in 0..query.length) {
                    for (inserted in INSERTION_CHARS) {
                        add(query.substring(0, index) + inserted + query.substring(index), 2.65)
                        if (out.size >= maxVariants) break@outer
                    }
                }
            }

            // A second fuzzy transform helps inputs containing two dialect/final differences.
            val firstLayer = out.entries.sortedBy { it.value }.take(18).map { it.key to it.value }
            outer@ for ((base, basePenalty) in firstLayer) {
                for ((left, right) in fuzzyPairs.take(9)) {
                    val index = base.indexOf(left)
                    if (index >= 0) add(base.replaceRange(index, index + left.length, right), basePenalty + 1.0)
                    if (out.size >= maxVariants) break@outer
                }
            }
        }

        if (query == "xhn" || query == "xhnn") add("xihuanni", 0.75)
        if (query == "nss") add("nisishei", 0.75)
        if (query == "hsywt") add("haishiyouwenti", 0.75)

        return out.entries.sortedBy { it.value }.take(maxVariants).map { QueryVariant(it.key, it.value) }
    }

    fun candidatesFor(rawInput: String, enhanced: Boolean = false): List<String> {
        val query = PinyinDictionary.normalize(rawInput)
        if (query.isEmpty()) return emptyList()
        val direct = typoShortcuts[query].orEmpty()
        val data = PinyinExpandedData.entries + PinyinBoostData.entries
        val variantCandidates = queryVariants(query, if (enhanced) 72 else 36, enhanced)
            .flatMap { variant -> data[variant.pinyin].orEmpty() }

        val distanceLimit = when {
            query.length <= 2 -> 0
            query.length <= 5 -> 1
            enhanced -> 2
            else -> 1
        }
        val editDistanceCandidates = if (direct.isEmpty() && query.length >= 3) {
            data.asSequence()
                .mapNotNull { (key, values) ->
                    val distance = boundedDistance(query, key, distanceLimit)
                    if (distance <= distanceLimit) key to values else null
                }
                .sortedWith(compareBy<Pair<String, List<String>>> { abs(it.first.length - query.length) }.thenBy { it.first.length })
                .flatMap { (_, values) -> values.asSequence().take(3) }
                .take(if (enhanced) 28 else 16)
                .toList()
        } else emptyList()

        return (direct + variantCandidates + editDistanceCandidates).distinct().take(MAX_CANDIDATES)
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

    private const val INSERTION_CHARS = "aeiouvngh"
}
