package com.ccwu.orbitime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ClipboardStore(private val context: Context) {
    data class ClipEntry(
        val content: String,
        val createdAt: Long,
        val updatedAt: Long,
        val copyCount: Int,
        val pinned: Boolean,
        val useCount: Int,
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<ClipEntry> {
        val parsed = parseRaw()
        val now = System.currentTimeMillis()
        val kept = parsed.filter { it.pinned || now - it.updatedAt <= RECENT_TTL_MS }
            .sortedWith(compareByDescending<ClipEntry> { it.pinned }.thenByDescending { it.updatedAt })
        if (kept.size != parsed.size) save(kept)
        return kept
    }

    fun pinned(): List<ClipEntry> = load().filter { it.pinned }

    fun recent(): List<ClipEntry> = load().filterNot { it.pinned }

    /** Backward-compatible explicit-save API. */
    fun add(raw: CharSequence?): Boolean = capture(raw)

    @Synchronized
    fun capture(raw: CharSequence?): Boolean {
        val text = raw?.toString()?.trim() ?: return false
        if (!PrivacyGuard.isSafeToPersist(text)) return false
        val now = System.currentTimeMillis()
        val items = load().toMutableList()
        val existingIndex = items.indexOfFirst { it.content == text }
        if (existingIndex >= 0) {
            val old = items.removeAt(existingIndex)
            items.add(
                0,
                old.copy(
                    updatedAt = now,
                    copyCount = (old.copyCount + 1).coerceAtMost(MAX_COUNTER),
                ),
            )
        } else {
            items.add(
                0,
                ClipEntry(
                    content = text,
                    createdAt = now,
                    updatedAt = now,
                    copyCount = 1,
                    pinned = false,
                    useCount = 0,
                ),
            )
        }
        save(trimToCapacity(items))
        return true
    }

    @Synchronized
    fun togglePin(content: String): Boolean {
        val items = load().toMutableList()
        val index = items.indexOfFirst { it.content == content }
        if (index < 0) return false
        val old = items[index]
        items[index] = old.copy(pinned = !old.pinned, updatedAt = System.currentTimeMillis())
        save(trimToCapacity(items))
        return items[index].pinned
    }

    @Synchronized
    fun remove(content: String): Boolean {
        val items = load().toMutableList()
        val removed = items.removeAll { it.content == content }
        if (removed) save(items)
        return removed
    }

    @Synchronized
    fun markUsed(content: String) {
        val items = load().toMutableList()
        val index = items.indexOfFirst { it.content == content }
        if (index < 0) return
        val old = items[index]
        items[index] = old.copy(
            updatedAt = System.currentTimeMillis(),
            useCount = (old.useCount + 1).coerceAtMost(MAX_COUNTER),
        )
        save(trimToCapacity(items))
    }

    @Synchronized
    fun clearRecent() {
        save(load().filter { it.pinned })
    }

    fun clear() {
        prefs.edit().remove(KEY_CLIPS).apply()
    }

    private fun parseRaw(): List<ClipEntry> {
        val raw = prefs.getString(KEY_CLIPS, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val content = item.optString("content", "").trim()
                    if (content.isBlank() || !PrivacyGuard.isSafeToPersist(content)) continue
                    val legacyUpdated = item.optLong("updatedAt", 0L).takeIf { it > 0L } ?: System.currentTimeMillis()
                    add(
                        ClipEntry(
                            content = content,
                            createdAt = item.optLong("createdAt", legacyUpdated),
                            updatedAt = legacyUpdated,
                            copyCount = item.optInt("copyCount", 1).coerceIn(1, MAX_COUNTER),
                            pinned = item.optBoolean("pinned", false),
                            useCount = item.optInt("useCount", 0).coerceIn(0, MAX_COUNTER),
                        ),
                    )
                }
            }.distinctBy { it.content }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun trimToCapacity(items: List<ClipEntry>): List<ClipEntry> {
        val maxItems = ProGate.maxClipboardItems(context)
        val pinned = items.filter { it.pinned }.sortedByDescending { it.updatedAt }
        val recent = items.filterNot { it.pinned }.sortedByDescending { it.updatedAt }
        if (pinned.size >= maxItems) return pinned.take(maxItems)
        return pinned + recent.take(maxItems - pinned.size)
    }

    private fun save(items: List<ClipEntry>) {
        val array = JSONArray()
        items.forEach { entry ->
            array.put(
                JSONObject()
                    .put("content", entry.content)
                    .put("createdAt", entry.createdAt)
                    .put("updatedAt", entry.updatedAt)
                    .put("copyCount", entry.copyCount)
                    .put("pinned", entry.pinned)
                    .put("useCount", entry.useCount),
            )
        }
        prefs.edit().putString(KEY_CLIPS, array.toString()).apply()
    }

    companion object {
        private const val PREFS = "orbit_clipboard_store"
        private const val KEY_CLIPS = "clips"
        const val RECENT_TTL_MS = 60L * 60L * 1000L
        private const val MAX_COUNTER = 999_999
    }
}
