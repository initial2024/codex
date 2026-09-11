package com.ccwu.orbitime

import android.content.Context
import java.io.FileNotFoundException

/** Reads importer-generated ime/english.odict without networking or a database. */
class CompactEnglishAsset(private val context: Context) {
    data class Entry(
        val key: String,
        val frequency: Int,
        val candidates: List<String>,
    )

    @Volatile private var loaded = false
    private var entries: Map<String, Entry> = emptyMap()

    @Synchronized
    fun candidatesFor(rawInput: String, limit: Int = 12): List<String> {
        val query = EnglishDictionary.normalize(rawInput)
        if (query.isEmpty()) return emptyList()
        ensureLoaded()
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
            .sortedByDescending { (_, entry) -> entry.frequency }
            .take(limit * 2)
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
        ensureLoaded()
        return entries[EnglishDictionary.normalize(rawKey)]?.frequency ?: 0
    }

    @Synchronized
    fun hasPackagedAsset(): Boolean {
        ensureLoaded()
        return entries.isNotEmpty()
    }

    private fun ensureLoaded() {
        if (loaded) return
        entries = loadAsset()
        loaded = true
    }

    private fun loadAsset(): Map<String, Entry> {
        return try {
            val map = LinkedHashMap<String, Entry>()
            context.assets.open("ime/english.odict").bufferedReader(Charsets.UTF_8).useLines { lines ->
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
}
