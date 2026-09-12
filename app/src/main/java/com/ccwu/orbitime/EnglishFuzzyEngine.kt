package com.ccwu.orbitime

/** Generates bounded typo variants; the packaged lexicon decides which variants are real words. */
object EnglishFuzzyEngine {
    private val neighbors: Map<Char, String> = mapOf(
        'q' to "wa", 'w' to "qase", 'e' to "wsdr", 'r' to "edft", 't' to "rfgy",
        'y' to "tghu", 'u' to "yhji", 'i' to "ujko", 'o' to "iklp", 'p' to "ol",
        'a' to "qwsz", 's' to "awedxz", 'd' to "serfcx", 'f' to "drtgvc",
        'g' to "ftyhbv", 'h' to "gyujnb", 'j' to "huikmn", 'k' to "jiolm", 'l' to "kop",
        'z' to "asx", 'x' to "zsdc", 'c' to "xdfv", 'v' to "cfgb", 'b' to "vghn",
        'n' to "bhjm", 'm' to "njk",
    )

    fun variants(rawInput: String, enhanced: Boolean, limit: Int = if (enhanced) 56 else 28): List<String> {
        val query = EnglishDictionary.normalize(rawInput)
        if (query.length < 2) return emptyList()
        val result = LinkedHashSet<String>()
        fun add(value: String) {
            val normalized = EnglishDictionary.normalize(value)
            if (normalized.isNotBlank() && normalized != query) result += normalized
        }

        // Adjacent transposition.
        for (index in 0 until query.lastIndex) {
            if (query[index] == query[index + 1]) continue
            val chars = query.toCharArray()
            val old = chars[index]
            chars[index] = chars[index + 1]
            chars[index + 1] = old
            add(String(chars))
            if (result.size >= limit) return result.take(limit)
        }

        // Accidental extra key.
        if (query.length >= 3) {
            for (index in query.indices) {
                add(query.removeRange(index, index + 1))
                if (result.size >= limit) return result.take(limit)
            }
        }

        // Neighbor substitution.
        outer@ for (index in query.indices) {
            for (replacement in neighbors[query[index]].orEmpty()) {
                val chars = query.toCharArray()
                chars[index] = replacement
                add(String(chars))
                if (result.size >= limit) break@outer
            }
        }

        if (enhanced && query.length <= 18) {
            val collapsed = buildString {
                query.forEach { ch -> if (isEmpty() || last() != ch) append(ch) }
            }
            add(collapsed)
            // Missing-key candidates are bounded to common vowels plus neighboring keys.
            outer@ for (index in 0..query.length) {
                val local = linkedSetOf<Char>().apply {
                    addAll("aeiou".toList())
                    if (index > 0) addAll(neighbors[query[index - 1]].orEmpty().toList())
                    if (index < query.length) addAll(neighbors[query[index]].orEmpty().toList())
                }
                for (inserted in local) {
                    add(query.substring(0, index) + inserted + query.substring(index))
                    if (result.size >= limit) break@outer
                }
            }
        }
        return result.take(limit)
    }
}
