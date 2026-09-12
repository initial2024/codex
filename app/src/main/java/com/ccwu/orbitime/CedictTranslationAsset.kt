package com.ccwu.orbitime

import android.content.Context
import java.io.FileNotFoundException
import java.util.LinkedHashMap

/**
 * Runtime reader for build-generated CC-CEDICT translation shards.
 * The CC-CEDICT data asset remains CC BY-SA 4.0 and is kept separate from code.
 */
object CedictTranslationAsset {
    private var appContext: Context? = null

    private val zhCache = object : LinkedHashMap<String, Map<String, String>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<String, String>>?): Boolean = size > 8
    }
    private val enCache = object : LinkedHashMap<String, Map<String, String>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<String, String>>?): Boolean = size > 8
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    @Synchronized
    fun exactZhToEn(raw: String): String? {
        val key = raw.trim().trimEnd('。', '！', '？', '.', '!', '?')
        if (key.isBlank()) return null
        return loadZhShard(zhShardFor(key))[key]
    }

    @Synchronized
    fun exactEnToZh(raw: String): String? {
        val key = normalizeEnglish(raw)
        if (key.isBlank()) return null
        return loadEnShard(enShardFor(key))[key]
    }

    /** Conservative longest-match composition for Chinese sentences. */
    fun composeZhToEn(raw: String): String? {
        val source = raw.trim()
        if (source.isBlank()) return null
        val pieces = mutableListOf<String>()
        var coveredChars = 0
        var translatableChars = 0
        var index = 0
        while (index < source.length) {
            val char = source[index]
            if (char.isWhitespace()) { index++; continue }
            if (char in "，。！？；：,.!?;:") {
                pieces += punctuationToEnglish(char)
                index++
                continue
            }
            translatableChars++
            var matchText: String? = null
            var matchLength = 0
            val maxLength = minOf(MAX_ZH_PHRASE_CHARS, source.length - index)
            for (length in maxLength downTo 1) {
                val phrase = source.substring(index, index + length)
                val translated = exactZhToEn(phrase)
                if (!translated.isNullOrBlank()) {
                    matchText = translated
                    matchLength = length
                    break
                }
            }
            if (matchText != null) {
                pieces += matchText
                coveredChars += matchLength
                translatableChars += matchLength - 1
                index += matchLength
            } else {
                index++
            }
        }
        if (translatableChars == 0 || coveredChars.toDouble() / translatableChars < MIN_COVERAGE) return null
        return normalizeEnglishSentence(pieces)
    }

    /** Word/phrase longest-match composition for English sentences. */
    fun composeEnToZh(raw: String): String? {
        val words = normalizeEnglish(raw).split(' ').filter(String::isNotBlank)
        if (words.isEmpty()) return null
        val pieces = mutableListOf<String>()
        var covered = 0
        var index = 0
        while (index < words.size) {
            var match: String? = null
            var span = 0
            val maxSpan = minOf(MAX_EN_PHRASE_WORDS, words.size - index)
            for (length in maxSpan downTo 1) {
                val phrase = words.subList(index, index + length).joinToString(" ")
                val translated = exactEnToZh(phrase)
                if (!translated.isNullOrBlank()) {
                    match = translated
                    span = length
                    break
                }
            }
            if (match != null) {
                pieces += match
                covered += span
                index += span
            } else {
                index++
            }
        }
        if (covered.toDouble() / words.size < MIN_COVERAGE) return null
        val text = pieces.joinToString("").trim()
        if (text.isBlank()) return null
        return if (text.lastOrNull() in setOf('。', '！', '？')) text else "$text。"
    }

    @Synchronized
    private fun loadZhShard(shard: String): Map<String, String> {
        zhCache[shard]?.let { return it }
        val loaded = load("ime/translation/zh/$shard.odict")
        zhCache[shard] = loaded
        return loaded
    }

    @Synchronized
    private fun loadEnShard(shard: String): Map<String, String> {
        enCache[shard]?.let { return it }
        val loaded = load("ime/translation/en/$shard.odict")
        enCache[shard] = loaded
        return loaded
    }

    private fun load(path: String): Map<String, String> {
        val context = appContext ?: return emptyMap()
        return try {
            val result = LinkedHashMap<String, String>()
            context.assets.open(path).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank() || line.startsWith("#")) return@forEach
                    val parts = line.split('\t', limit = 2)
                    if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                        result.putIfAbsent(parts[0], parts[1])
                    }
                }
            }
            result
        } catch (_: FileNotFoundException) {
            emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun zhShardFor(text: String): String {
        val cp = text.codePointAt(0)
        return (cp and 63).toString(16).padStart(2, '0')
    }

    private fun enShardFor(text: String): String {
        val first = text.firstOrNull()?.lowercaseChar() ?: '_'
        return if (first in 'a'..'z') first.toString() else "_"
    }

    private fun normalizeEnglish(text: String): String = text.lowercase()
        .replace(Regex("[^a-z0-9\\s'-]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun punctuationToEnglish(char: Char): String = when (char) {
        '？', '?' -> "?"
        '！', '!' -> "!"
        '；', ';' -> ";"
        '：', ':' -> ":"
        else -> "."
    }

    private fun normalizeEnglishSentence(parts: List<String>): String {
        if (parts.isEmpty()) return ""
        var result = parts.joinToString(" ")
            .replace(Regex("\\s+([?.!;:])"), "$1")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (result.isBlank()) return result
        result = result.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        if (result.lastOrNull() !in setOf('.', '!', '?', ';', ':')) result += "."
        return result
    }

    private const val MAX_ZH_PHRASE_CHARS = 12
    private const val MAX_EN_PHRASE_WORDS = 5
    private const val MIN_COVERAGE = 0.72
}
