package com.ccwu.orbitime

import java.util.Locale

/**
 * Deterministic offline sentence composer. It is intentionally conservative:
 * it returns null instead of pretending to translate when local lexical
 * coverage is too low.
 */
object LocalTranslationComposer {
    private val zhLexicon: Map<String, String> = buildMap {
        putAll(TranslationLexiconData.zhToEn)
        putAll(TranslationBoostData.zhTokens)
    }
    private val zhKeys: List<String> = zhLexicon.keys.sortedWith(compareByDescending<String> { it.length }.thenBy { it })

    private val enLexicon: Map<String, String> = buildMap {
        putAll(TranslationLexiconData.enToZh)
        putAll(TranslationBoostData.enTokens)
    }
    private val enKeys: List<List<String>> = enLexicon.keys
        .map { it.lowercase(Locale.ROOT).split(Regex("\\s+")).filter(String::isNotBlank) }
        .filter(List<String>::isNotEmpty)
        .sortedWith(compareByDescending<List<String>> { it.size }.thenByDescending { it.joinToString(" ").length })

    fun translateZhToEn(raw: String): String? {
        val source = raw.trim()
        if (source.isEmpty() || source.length > MAX_SOURCE_CHARS) return null
        val result = StringBuilder()
        val clause = StringBuilder()

        fun flush(punctuation: Char? = null): Boolean {
            val text = clause.toString().trim()
            clause.clear()
            if (text.isNotEmpty()) {
                val translated = translateZhClause(text) ?: return false
                if (result.isNotEmpty() && result.lastOrNull()?.isWhitespace() == false) result.append(' ')
                result.append(translated)
            }
            punctuation?.let {
                if (result.isNotEmpty()) result.append(mapZhPunctuation(it))
                if (it in SENTENCE_END_ZH) result.append(' ')
            }
            return true
        }

        for (char in source) {
            if (char in CLAUSE_PUNCTUATION_ZH) {
                if (!flush(char)) return null
            } else {
                clause.append(char)
            }
        }
        if (!flush()) return null
        val cleaned = result.toString().replace(Regex("\\s+([,.!?;:])"), "$1").replace(Regex("\\s+"), " ").trim()
        return cleaned.ifBlank { null }
    }

    fun translateEnToZh(raw: String): String? {
        val source = raw.trim()
        if (source.isEmpty() || source.length > MAX_SOURCE_CHARS) return null
        val result = StringBuilder()
        val clause = StringBuilder()

        fun flush(punctuation: Char? = null): Boolean {
            val text = clause.toString().trim()
            clause.clear()
            if (text.isNotEmpty()) {
                val translated = translateEnClause(text) ?: return false
                result.append(translated)
            }
            punctuation?.let { result.append(mapEnPunctuation(it)) }
            return true
        }

        for (char in source) {
            if (char in CLAUSE_PUNCTUATION_EN) {
                if (!flush(char)) return null
            } else {
                clause.append(char)
            }
        }
        if (!flush()) return null
        return result.toString().trim().ifBlank { null }
    }

    private fun translateZhClause(source: String): String? {
        val tokens = mutableListOf<String>()
        var index = 0
        var coveredCjk = 0
        var totalCjk = source.count(::isCjk)
        var unknownCjk = 0

        while (index < source.length) {
            val char = source[index]
            if (char.isWhitespace()) {
                index++
                continue
            }
            if (char.code < 128 && (char.isLetterOrDigit() || char in "_-./")) {
                val start = index
                index++
                while (index < source.length) {
                    val next = source[index]
                    if (next.code < 128 && (next.isLetterOrDigit() || next in "_-./")) index++ else break
                }
                tokens += source.substring(start, index)
                continue
            }

            val match = zhKeys.firstOrNull { key -> source.startsWith(key, index) }
            if (match != null) {
                val translated = zhLexicon[match].orEmpty().trim()
                coveredCjk += match.count(::isCjk)
                if (translated.isNotEmpty()) tokens += translated
                index += match.length
                continue
            }

            if (isCjk(char)) {
                unknownCjk++
            } else {
                tokens += char.toString()
            }
            index++
        }

        if (totalCjk > 0) {
            val coverage = coveredCjk.toDouble() / totalCjk.toDouble()
            if (coverage < MIN_COVERAGE || unknownCjk > MAX_UNKNOWN_UNITS) return null
        }
        return normalizeEnglish(tokens.joinToString(" "))
    }

    private fun translateEnClause(source: String): String? {
        val words = Regex("[A-Za-z0-9']+").findAll(source).map { it.value }.toList()
        if (words.isEmpty()) return null
        val lowered = words.map { it.lowercase(Locale.ROOT) }
        val out = StringBuilder()
        var index = 0
        var covered = 0
        var unknown = 0

        while (index < lowered.size) {
            var matchedWords: List<String>? = null
            var translated: String? = null
            for (keyWords in enKeys) {
                if (keyWords.size > lowered.size - index) continue
                var matches = true
                for (offset in keyWords.indices) {
                    if (lowered[index + offset] != keyWords[offset]) {
                        matches = false
                        break
                    }
                }
                if (matches) {
                    matchedWords = keyWords
                    translated = enLexicon[keyWords.joinToString(" ")]
                    break
                }
            }
            if (matchedWords != null && translated != null) {
                out.append(translated)
                covered += matchedWords.size
                index += matchedWords.size
            } else if (lowered[index].all(Char::isDigit)) {
                out.append(words[index])
                covered++
                index++
            } else {
                unknown++
                index++
            }
        }

        val coverage = covered.toDouble() / words.size.toDouble()
        if (coverage < MIN_COVERAGE || unknown > MAX_UNKNOWN_UNITS) return null
        return out.toString().trim().ifBlank { null }
    }

    private fun normalizeEnglish(raw: String): String? {
        var text = raw.replace(Regex("\\s+"), " ").trim()
        if (text.isBlank()) return null
        text = text
            .replace("I want to can ", "I want to ")
            .replace("I need to can ", "I need to ")
            .replace("please please ", "please ", ignoreCase = true)
            .replace(Regex("\\s+([,.;:!?])"), "$1")
        val chars = text.toCharArray()
        val firstLetter = chars.indexOfFirst { it.isLetter() }
        if (firstLetter >= 0 && chars[firstLetter] in 'a'..'z') chars[firstLetter] = chars[firstLetter].uppercaseChar()
        return String(chars)
    }

    private fun mapZhPunctuation(char: Char): Char = when (char) {
        '。' -> '.'
        '，' -> ','
        '！' -> '!'
        '？' -> '?'
        '；' -> ';'
        '：' -> ':'
        else -> char
    }

    private fun mapEnPunctuation(char: Char): Char = when (char) {
        '.' -> '。'
        ',' -> '，'
        '!' -> '！'
        '?' -> '？'
        ';' -> '；'
        ':' -> '：'
        else -> char
    }

    private fun isCjk(char: Char): Boolean {
        val block = Character.UnicodeBlock.of(char)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
    }

    private val CLAUSE_PUNCTUATION_ZH = setOf('。', '，', '！', '？', '；', '：', '.', ',', '!', '?', ';', ':', '\n')
    private val SENTENCE_END_ZH = setOf('。', '！', '？', '.', '!', '?', '\n')
    private val CLAUSE_PUNCTUATION_EN = setOf('.', ',', '!', '?', ';', ':', '。', '，', '！', '？', '；', '：', '\n')
    private const val MIN_COVERAGE = 0.72
    private const val MAX_UNKNOWN_UNITS = 3
    private const val MAX_SOURCE_CHARS = 600
}
