package com.ccwu.orbitime

/** Generates bounded typo variants; the packaged large lexicon decides which variants are real words. */
object EnglishFuzzyEngine {
    private val neighbors: Map<Char, String> = mapOf(
        'q' to "wa", 'w' to "qase", 'e' to "wsdr", 'r' to "edft", 't' to "rfgy",
        'y' to "tghu", 'u' to "yhji", 'i' to "ujko", 'o' to "iklp", 'p' to "ol",
        'a' to "qwsz", 's' to "awedxz", 'd' to "serfcx", 'f' to "drtgvc",
        'g' to "ftyhbv", 'h' to "gyujnb", 'j' to "huikmn", 'k' to "jiolm", 'l' to "kop",
        'z' to "asx", 'x' to "zsdc", 'c' to "xdfv", 'v' to "cfgb", 'b' to "vghn",
        'n' to "bhjm", 'm' to "njk",
    )

    fun variants(rawInput: String, enhanced: Boolean, limit: Int = if (enhanced) 96 else 48): List<String> {
        val query = EnglishDictionary.normalize(rawInput)
        if (query.length < 2) return emptyList()
        val result = LinkedHashSet<String>()

        fun add(value: String) {
            val normalized = EnglishDictionary.normalize(value)
            if (normalized.isNotBlank() && normalized != query && normalized.length <= MAX_VARIANT_CHARS) result += normalized
        }

        fun transpositions(source: String, maxAdds: Int) {
            var added = 0
            for (index in 0 until source.lastIndex) {
                if (source[index] == source[index + 1]) continue
                val chars = source.toCharArray()
                val old = chars[index]
                chars[index] = chars[index + 1]
                chars[index + 1] = old
                val before = result.size
                add(String(chars))
                if (result.size > before) added++
                if (result.size >= limit || added >= maxAdds) break
            }
        }

        // High-confidence mobile typos first.
        transpositions(query, if (enhanced) 24 else 14)

        // Accidental extra key.
        if (query.length >= 3 && result.size < limit) {
            for (index in query.indices) {
                add(query.removeRange(index, index + 1))
                if (result.size >= limit) break
            }
        }

        // QWERTY neighboring-key substitution.
        if (result.size < limit) {
            outer@ for (index in query.indices) {
                for (replacement in neighbors[query[index]].orEmpty()) {
                    val chars = query.toCharArray()
                    chars[index] = replacement
                    add(String(chars))
                    if (result.size >= limit) break@outer
                }
            }
        }

        val collapsed = buildString {
            query.forEach { ch -> if (isEmpty() || last() != ch) append(ch) }
        }
        if (collapsed != query) add(collapsed)

        if (enhanced && query.length <= 22 && result.size < limit) {
            // Missing-key recovery. Prefer vowels and physical neighbors near the insertion point.
            outer@ for (index in 0..query.length) {
                val local = linkedSetOf<Char>().apply {
                    addAll("aeiouy".toList())
                    if (index > 0) addAll(neighbors[query[index - 1]].orEmpty().toList())
                    if (index < query.length) addAll(neighbors[query[index]].orEmpty().toList())
                }
                for (inserted in local) {
                    add(query.substring(0, index) + inserted + query.substring(index))
                    if (result.size >= limit) break@outer
                }
            }

            // Second-stage recovery is deliberately bounded. This catches two-key errors
            // without exploding the lexicon lookup count.
            val firstLayer = result.take(20)
            outer@ for (base in firstLayer) {
                if (base.length >= 3) {
                    for (index in base.indices) {
                        add(base.removeRange(index, index + 1))
                        if (result.size >= limit) break@outer
                    }
                }
                transpositions(base, 3)
                if (result.size >= limit) break
            }
        }
        return result.take(limit)
    }

    private const val MAX_VARIANT_CHARS = 48
}
