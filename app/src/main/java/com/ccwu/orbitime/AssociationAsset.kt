package com.ccwu.orbitime

import android.content.Context
import java.io.FileNotFoundException
import java.util.LinkedHashMap

/**
 * Low-latency reader for v0.23 precomputed next-word/phrase associations.
 * The build pipeline shards contexts into 32 files; runtime loads only needed shards.
 */
class AssociationAsset(private val context: Context) {
    data class Suggestion(val text: String, val frequency: Int, val contextLength: Int)

    private val shardCache = object : LinkedHashMap<Int, Map<String, List<Pair<String, Int>>>>(MAX_CACHED_SHARDS, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Int, Map<String, List<Pair<String, Int>>>>?,
        ): Boolean = size > MAX_CACHED_SHARDS
    }

    @Synchronized
    fun suggestions(rawContext: String, limit: Int = 32): List<Suggestion> {
        val cjkTail = rawContext.takeLast(MAX_CONTEXT_SCAN).filter(::isCjk)
        if (cjkTail.isEmpty()) return emptyList()
        val merged = LinkedHashMap<String, Suggestion>()
        val maxWidth = minOf(MAX_CONTEXT_CHARS, cjkTail.length)
        for (width in maxWidth downTo 1) {
            val key = cjkTail.takeLast(width)
            shardFor(key)[key].orEmpty().take(limit * 2).forEach { (candidate, frequency) ->
                if (candidate.isBlank()) return@forEach
                val next = Suggestion(candidate, frequency, width)
                val old = merged[candidate]
                if (old == null || score(next) > score(old)) merged[candidate] = next
            }
        }
        return merged.values.sortedByDescending(::score).take(limit)
    }

    @Synchronized
    fun hasPackagedAsset(): Boolean = runCatching {
        context.assets.list("ime/association")?.count { it.endsWith(".odict") } == 32
    }.getOrDefault(false)

    private fun score(value: Suggestion): Long =
        value.frequency.toLong().coerceAtLeast(1L) * (1L + value.contextLength * value.contextLength)

    private fun shardFor(contextKey: String): Map<String, List<Pair<String, Int>>> {
        val shard = associationShard(contextKey)
        shardCache[shard]?.let { return it }
        return loadShard(shard).also { shardCache[shard] = it }
    }

    private fun loadShard(shard: Int): Map<String, List<Pair<String, Int>>> {
        val path = "ime/association/${shard.toString(16).padStart(2, '0')}.odict"
        return try {
            val temp = LinkedHashMap<String, MutableList<Pair<String, Int>>>()
            context.assets.open(path).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trimEnd()
                    if (line.isBlank() || line.startsWith("#")) return@forEach
                    val parts = line.split('\t')
                    if (parts.size != 3) return@forEach
                    val key = parts[0]
                    val candidate = parts[1]
                    if (key.isBlank() || candidate.isBlank()) return@forEach
                    // v0.23 writer uses compact hexadecimal counts for this dedicated asset.
                    val frequency = runCatching { parts[2].lowercase().toLong(16) }
                        .getOrDefault(1L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
                    temp.getOrPut(key) { mutableListOf() }.add(candidate to frequency)
                }
            }
            temp.mapValues { (_, values) -> values.sortedByDescending { it.second }.take(MAX_ROWS_PER_CONTEXT) }
        } catch (_: FileNotFoundException) {
            emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun associationShard(contextKey: String): Int = contextKey.sumOf { it.code }.mod(SHARD_COUNT)

    private fun isCjk(char: Char): Boolean {
        val block = Character.UnicodeBlock.of(char)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
    }

    companion object {
        private const val SHARD_COUNT = 32
        private const val MAX_CACHED_SHARDS = 6
        private const val MAX_ROWS_PER_CONTEXT = 48
        private const val MAX_CONTEXT_CHARS = 4
        private const val MAX_CONTEXT_SCAN = 24
    }
}
