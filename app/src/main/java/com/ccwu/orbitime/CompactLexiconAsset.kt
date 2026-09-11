package com.ccwu.orbitime

import android.content.Context
import java.io.FileNotFoundException
import java.util.LinkedHashMap

/**
 * Runtime reader for build-time generated ORBIT_ODICT lexicon assets.
 *
 * Preferred layout:
 *   assets/ime/lexicon/a.odict ... z.odict
 *
 * Fallback layout for development:
 *   assets/ime/lexicon.odict
 *
 * Line format:
 *   pinyin<TAB>text<TAB>base36_frequency
 */
class CompactLexiconAsset(private val context: Context) {
    data class Entry(
        val pinyin: String,
        val text: String,
        val frequency: Int,
    )

    private val shardCache = object : LinkedHashMap<String, Map<String, List<Entry>>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<String, List<Entry>>>?): Boolean {
            return size > MAX_SHARDS_IN_MEMORY
        }
    }

    @Volatile
    private var fallbackLoaded = false
    private var fallbackMap: Map<String, List<Entry>> = emptyMap()

    @Synchronized
    fun exact(rawPinyin: String): List<Entry> {
        val key = PinyinDictionary.normalize(rawPinyin)
        if (key.isEmpty()) return emptyList()
        val shard = shardFor(key)
        return loadShard(shard)[key].orEmpty()
            .ifEmpty { loadFallback()[key].orEmpty() }
            .sortedByDescending { it.frequency }
    }

    @Synchronized
    fun prefix(rawPrefix: String, limit: Int = 24): List<Entry> {
        val prefix = PinyinDictionary.normalize(rawPrefix)
        if (prefix.isEmpty()) return emptyList()
        val shard = shardFor(prefix)
        val primary = loadShard(shard)
            .asSequence()
            .filter { (key, _) -> key.startsWith(prefix) }
            .flatMap { (_, values) -> values.asSequence() }
            .sortedByDescending { it.frequency }
            .take(limit)
            .toList()
        if (primary.size >= limit) return primary

        val fallback = loadFallback()
            .asSequence()
            .filter { (key, _) -> key.startsWith(prefix) }
            .flatMap { (_, values) -> values.asSequence() }
            .sortedByDescending { it.frequency }
            .take(limit - primary.size)
            .toList()
        return (primary + fallback).distinctBy { it.pinyin to it.text }.take(limit)
    }

    @Synchronized
    fun hasPackagedAssets(): Boolean {
        if (shardCache.isNotEmpty()) return true
        return runCatching {
            context.assets.open("ime/manifest.json").use { true }
        }.getOrDefault(false) || runCatching {
            context.assets.open("ime/lexicon.odict").use { true }
        }.getOrDefault(false)
    }

    @Synchronized
    private fun loadShard(shard: String): Map<String, List<Entry>> {
        shardCache[shard]?.let { return it }
        val loaded = loadFile("ime/lexicon/$shard.odict")
        shardCache[shard] = loaded
        return loaded
    }

    @Synchronized
    private fun loadFallback(): Map<String, List<Entry>> {
        if (fallbackLoaded) return fallbackMap
        fallbackMap = loadFile("ime/lexicon.odict")
        fallbackLoaded = true
        return fallbackMap
    }

    private fun loadFile(assetPath: String): Map<String, List<Entry>> {
        return try {
            val grouped = LinkedHashMap<String, MutableList<Entry>>()
            context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trimEnd()
                    if (line.isBlank() || line.startsWith("#")) return@forEach
                    val parts = line.split('\t')
                    if (parts.size < 3) return@forEach
                    val pinyin = PinyinDictionary.normalize(parts[0])
                    val text = parts[1].trim()
                    if (pinyin.isEmpty() || text.isEmpty()) return@forEach
                    val frequency = parseBase36Frequency(parts[2])
                    grouped.getOrPut(pinyin) { mutableListOf() }
                        .add(Entry(pinyin, text, frequency))
                }
            }
            grouped.mapValues { (_, values) ->
                values.distinctBy { it.text }.sortedByDescending { it.frequency }
            }
        } catch (_: FileNotFoundException) {
            emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseBase36Frequency(raw: String): Int {
        val value = runCatching { raw.trim().lowercase().toLong(36) }.getOrDefault(1L)
        return value.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun shardFor(key: String): String {
        val first = key.firstOrNull()?.lowercaseChar() ?: '_'
        return if (first in 'a'..'z') first.toString() else "_"
    }

    companion object {
        private const val MAX_SHARDS_IN_MEMORY = 6
    }
}
