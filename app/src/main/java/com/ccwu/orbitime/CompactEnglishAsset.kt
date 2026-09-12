package com.ccwu.orbitime

import android.content.Context
import java.io.FileNotFoundException

/** Reads importer-generated local English assets without networking or a database. */
class CompactEnglishAsset(private val context: Context) {
    data class Entry(
        val key: String,
        val frequency: Int,
        val candidates: List<String>,
    )

    private val shardCache = object : LinkedHashMap<String, Map<String, Entry>>(MAX_CACHED_SHARDS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<String, Entry>>?): Boolean {
            return size > MAX_CACHED_SHARDS
        }
    }
    @Volatile private var fallbackLoaded = false
    private var fallbackEntries: Map<String, Entry> = emptyMap()

    @Synchronized
    fun candidatesFor(rawInput: String, limit: Int = 12): List<String> {
        val query = EnglishDictionary.normalize(rawInput)
        if (query.isEmpty()) return emptyList()
        val entries = entriesFor(query)
        if (entries.isEmpty()) return emptyList()

        val result = mutableListOf<Pair<String, Int>>()
        entries[query]?.let { entry ->
            result += entry.key to entry.frequency
            entry.candidates.forEachIndexed { index, candidate ->
                result += candidate to (entry.frequency - index * 1000).coerceAtLeast(1)
            }
        }
        entries.asSequence()
            .filter { (key, _) -> key != query && key.startsWith(query) }
            .sortedWith(compareByDescending<Map.Entry<String, Entry>> { it.value.frequency }.thenBy { it.key })
            .take(limit * 3)
            .forEach { (key, entry) ->
                result += key to entry.frequency
                entry.candidates.take(2).forEachIndexed { index, candidate ->
                    result += candidate to (entry.frequency - index * 1000).coerceAtLeast(1)
                }
            }
        return result
            .sortedByDescending { it.second }
            .map { it.first }
            .distinct()
            .take(limit)
    }

    @Synchronized
    fun frequencyFor(rawKey: String): Int {
        val key = EnglishDictionary.normalize(rawKey)
        if (key.isEmpty()) return 0
        return entriesFor(key)[key]?.frequency ?: 0
    }

    @Synchronized
    fun hasPackagedAsset(): Boolean {
        val sharded = runCatching {
            context.assets.list("ime/english")?.any { it.endsWith(".odict") } == true
        }.getOrDefault(false)
        if (sharded) return true
        ensureFallbackLoaded()
        return fallbackEntries.isNotEmpty()
    }

    private fun entriesFor(query: String): Map<String, Entry> {
        val shard = query.firstOrNull()?.takeIf { it in 'a'..'z' }?.toString() ?: "_"
        shardCache[shard]?.let { return it }
        val sharded = loadAsset("ime/english/$shard.odict")
        if (sharded.isNotEmpty()) {
            shardCache[shard] = sharded
            return sharded
        }
        ensureFallbackLoaded()
        return fallbackEntries
    }

    private fun ensureFallbackLoaded() {
        if (fallbackLoaded) return
        fallbackEntries = loadAsset("ime/english.odict")
        fallbackLoaded = true
    }

    private fun loadAsset(path: String): Map<String, Entry> {
        return try {
            val map = LinkedHashMap<String, Entry>()
            context.assets.open(path).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trimEnd()
                    if (line.isBlank() || line.startsWith("#")) return@forEach
                    val parts = line.split('\t')
                    if (parts.size < 2) return@forEach
                    val key = EnglishDictionary.normalize(parts[0])
                    if (key.isEmpty()) return@forEach
                    val frequency = parseBase36(parts[1])
                    val candidates = parts.drop(2).filter { it.isNotBlank() }
                    map[key] = Entry(key, frequency, candidates)
                }
            }
            map
        } catch (_: FileNotFoundException) {
            emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseBase36(raw: String): Int {
        val value = runCatching { raw.trim().lowercase().toLong(36) }.getOrDefault(1L)
        return value.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    companion object {
        private const val MAX_CACHED_SHARDS = 4
    }
}
