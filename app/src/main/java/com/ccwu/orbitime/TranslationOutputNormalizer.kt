package com.ccwu.orbitime

/** Lightweight surface cleanup for local dictionary/DP translation output. */
object TranslationOutputNormalizer {
    fun normalize(text: String, direction: TranslatePromptBuilder.Direction): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        return when (direction) {
            TranslatePromptBuilder.Direction.ZH_TO_EN -> normalizeEnglish(trimmed)
            TranslatePromptBuilder.Direction.EN_TO_ZH -> normalizeChinese(trimmed)
        }
    }

    private fun normalizeEnglish(raw: String): String {
        var value = raw
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s+([,.;:!?])"), "$1")
            .replace(Regex("([,.;:!?])(?=[A-Za-z])"), "$1 ")
            .trim()
        val firstLetter = value.indexOfFirst { it in 'a'..'z' || it in 'A'..'Z' }
        if (firstLetter >= 0 && value[firstLetter].isLowerCase()) {
            value = value.substring(0, firstLetter) + value[firstLetter].uppercaseChar() + value.substring(firstLetter + 1)
        }
        return value
    }

    private fun normalizeChinese(raw: String): String {
        val out = StringBuilder()
        var pendingSpace = false
        raw.forEach { char ->
            if (char.isWhitespace()) {
                pendingSpace = true
                return@forEach
            }
            if (pendingSpace && out.isNotEmpty()) {
                val previous = out.last()
                // Keep spaces only when both sides are Latin/digits. Chinese text is normally compact.
                if (isAsciiWord(previous) && isAsciiWord(char)) out.append(' ')
            }
            pendingSpace = false
            out.append(
                when (char) {
                    ',' -> '，'
                    '?' -> '？'
                    '!' -> '！'
                    ';' -> '；'
                    ':' -> '：'
                    else -> char
                },
            )
        }
        return out.toString().trim()
    }

    private fun isAsciiWord(char: Char): Boolean = char.code < 128 && (char.isLetterOrDigit() || char == '\'')
}
