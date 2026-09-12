package com.ccwu.orbitime

/**
 * Project-authored symbol keyboard and long-press map.
 * The letter-key long press layout intentionally mirrors common mobile IMEs:
 * top row -> digits, home/bottom rows -> high-frequency punctuation.
 */
object SymbolLibrary {
    data class Page(val title: String, val rows: List<List<String>>)

    val pages: List<Page> = listOf(
        Page(
            "常用",
            listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("-", "/", ":", ";", "(", ")", "¥", "&", "@", "\""),
                listOf(".", ",", "?", "!", "'", "[", "]", "{", "}", "⌫"),
                listOf("ABC", "符号", "space", "↵"),
            ),
        ),
        Page(
            "标点",
            listOf(
                listOf("~", "`", "_", "—", "…", "·", "•", "|", "\\", "⌫"),
                listOf("，", "。", "？", "！", "；", "：", "、", "…", "——", "～"),
                listOf("“", "”", "‘", "’", "《", "》", "〈", "〉", "「", "」"),
                listOf("ABC", "符号", "space", "↵"),
            ),
        ),
        Page(
            "括号",
            listOf(
                listOf("(", ")", "[", "]", "{", "}", "<", ">", "⌫"),
                listOf("（", "）", "【", "】", "〔", "〕", "［", "］", "｛", "｝"),
                listOf("〖", "〗", "〘", "〙", "〚", "〛", "「", "」", "『", "』"),
                listOf("ABC", "符号", "space", "↵"),
            ),
        ),
        Page(
            "数学",
            listOf(
                listOf("+", "−", "×", "÷", "=", "≠", "≈", "≤", "≥", "±"),
                listOf("√", "∞", "π", "∑", "∆", "∫", "∂", "%", "‰", "°"),
                listOf("²", "³", "¼", "½", "¾", "∝", "∵", "∴", "∈", "∉"),
                listOf("ABC", "符号", "space", "↵"),
            ),
        ),
        Page(
            "货币",
            listOf(
                listOf("¥", "$", "€", "£", "₩", "₽", "₹", "₿", "¢", "¤"),
                listOf("₫", "₴", "₦", "₱", "₲", "₵", "₪", "฿", "₭", "₡"),
                listOf("©", "®", "™", "℠", "№", "℃", "℉", "µ", "Ω", "⌫"),
                listOf("ABC", "符号", "space", "↵"),
            ),
        ),
        Page(
            "箭头",
            listOf(
                listOf("←", "→", "↑", "↓", "↔", "↕", "↖", "↗", "↘", "↙"),
                listOf("⇐", "⇒", "⇑", "⇓", "⇔", "➜", "➝", "➤", "➥", "➦"),
                listOf("★", "☆", "●", "○", "■", "□", "▲", "△", "▼", "▽"),
                listOf("ABC", "符号", "space", "↵"),
            ),
        ),
        Page(
            "标记",
            listOf(
                listOf("✓", "✔", "✕", "✖", "☑", "☒", "☐", "☞", "☜", "⌫"),
                listOf("※", "§", "¶", "†", "‡", "№", "#", "*", "※", "〆"),
                listOf("♠", "♥", "♦", "♣", "♪", "♫", "♩", "♬", "☀", "☾"),
                listOf("ABC", "符号", "space", "↵"),
            ),
        ),
    )

    private val longPress = mapOf(
        "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
        "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0",
        "a" to "@", "s" to "#", "d" to "$", "f" to "%", "g" to "&",
        "h" to "-", "j" to "+", "k" to "(", "l" to ")",
        "z" to "*", "x" to "\"", "c" to "'", "v" to ":", "b" to ";",
        "n" to "!", "m" to "?",
        "," to "，", "." to "…",
    )

    fun page(index: Int): Page = pages[index.mod(pages.size)]
    fun nextPage(index: Int): Int = (index + 1) % pages.size
    fun longPressFor(key: String): String? = longPress[key.lowercase()]
}
