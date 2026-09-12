package com.ccwu.orbitime

import android.content.Context
import java.io.FileNotFoundException

/** Reads the build-generated Unicode 17 fully-qualified emoji list. */
object UnicodeEmojiAsset {
    @Volatile private var cached: List<String>? = null

    fun items(context: Context): List<String> {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: load(context.applicationContext).also { cached = it }
        }
    }

    private fun load(context: Context): List<String> = try {
        context.assets.open("ime/emoji_unicode.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.asSequence()
                .map(String::trim)
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .distinct()
                .toList()
        }
    } catch (_: FileNotFoundException) {
        fallback
    } catch (_: Exception) {
        fallback
    }

    private val fallback = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "🙂", "🙃", "😉",
        "😍", "🥰", "😘", "😋", "😎", "🤩", "🥳", "😭", "😤", "😡", "🤯", "🤔",
        "👍", "👎", "👌", "✌️", "🤞", "🫶", "👏", "🙏", "💪", "👀", "❤️", "💔",
        "🔥", "✨", "⭐", "💯", "✅", "❌", "⚠️", "🎉", "🎁", "🚀", "📌", "📚",
    )
}
