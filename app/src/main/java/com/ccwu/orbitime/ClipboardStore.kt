package com.ccwu.orbitime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ClipboardStore(private val context: Context) {
    data class ClipEntry(
        val content: String,
        val updatedAt: Long,
        val copyCount: Int,
    )

    private val prefs = context.getSharedPreferences("orbit_clipboard_store", Context.MODE_PRIVATE)

    fun load(): List<ClipEntry> {
        val raw = prefs.getString(KEY_CLIPS, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val content = item.optString("content", "")
                    if (content.isBlank()) continue
                    add(
                        ClipEntry(
                            content = content,
                            updatedAt = item.optLong("updatedAt", 0L),
                            copyCount = item.optInt("copyCount", 1),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(raw: CharSequence?): Boolean {
        val text = raw?.toString()?.trim() ?: return false
        if (!PrivacyGuard.isSafeToPersist(text)) return false

        val now = System.currentTimeMillis()
        val items = load().toMutableList()
        val existingIndex = items.indexOfFirst { it.content == text }

        if (existingIndex >= 0) {
            val old = items.removeAt(existingIndex)
            items.add(0, old.copy(updatedAt = now, copyCount = old.copyCount + 1))
        } else {
            items.add(0, ClipEntry(content = text, updatedAt = now, copyCount = 1))
        }

        save(items.take(ProGate.maxClipboardItems(context)))
        return true
    }

    fun clear() {
        prefs.edit().remove(KEY_CLIPS).apply()
    }

    private fun save(items: List<ClipEntry>) {
        val array = JSONArray()
        items.forEach { entry ->
            array.put(
                JSONObject()
                    .put("content", entry.content)
                    .put("updatedAt", entry.updatedAt)
                    .put("copyCount", entry.copyCount),
            )
        }
        prefs.edit().putString(KEY_CLIPS, array.toString()).apply()
    }

    companion object {
        private const val KEY_CLIPS = "clips"
    }
}
