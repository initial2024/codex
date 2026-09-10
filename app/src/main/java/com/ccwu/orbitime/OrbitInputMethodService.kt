package com.ccwu.orbitime

import android.content.ClipboardManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class OrbitInputMethodService : InputMethodService() {
    private enum class InputMode {
        ENGLISH,
        PINYIN,
    }

    private var inputMode = InputMode.ENGLISH
    private var caps = false
    private var symbols = false
    private var showClips = false
    private var sensitiveMode = false
    private var pinyinBuffer = ""
    private var root: LinearLayout? = null
    private lateinit var store: ClipboardStore

    override fun onCreate() {
        super.onCreate()
        store = ClipboardStore(this)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        sensitiveMode = PrivacyGuard.isSensitiveInput(attribute)
        if (sensitiveMode) {
            showClips = false
            clearPinyinComposition()
        }
        root?.let { rebuild(it) }
    }

    override fun onCreateInputView(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(OrbitTheme.BACKGROUND)
            setPadding(dp(8), dp(8), dp(8), dp(10))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        root = layout
        rebuild(layout)
        return layout
    }

    private fun rebuild(layout: LinearLayout) {
        layout.removeAllViews()
        buildTopBar(layout)
        if (!sensitiveMode && showClips) buildClipBar(layout)
        if (!sensitiveMode && !showClips && inputMode == InputMode.PINYIN && pinyinBuffer.isNotEmpty()) {
            buildCandidateBar(layout)
        }
        if (!sensitiveMode && !showClips && pinyinBuffer.isEmpty()) buildPhraseBar(layout)
        buildKeyboard(layout)
    }

    private fun buildTopBar(parent: LinearLayout) {
        if (sensitiveMode) {
            parent.addView(
                labelBox(
                    text = "Privacy mode · Hub disabled",
                    muted = true,
                    accent = false,
                ),
            )
            return
        }

        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(chip(if (inputMode == InputMode.PINYIN) "拼音" else "EN") { toggleInputMode() })
        row.addView(chip("Paste") { pasteClipboard(saveAfterPaste = false) })
        row.addView(chip("Save") { saveClipboard() })
        row.addView(chip(if (showClips) "Keys" else "Clips") {
            showClips = !showClips
            root?.let { rebuild(it) }
        })

        TemplateLibrary.defaultActions.take(ProGate.maxTemplates(this)).forEach { action ->
            row.addView(chip(action.label) { commitDirectText(action.insertText) })
        }

        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(44),
        ))
    }

    private fun buildClipBar(parent: LinearLayout) {
        val clips = store.load().take(8)
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        if (clips.isEmpty()) {
            row.addView(labelBox("No saved clips. Tap Save after copying text.", muted = true, accent = false))
        } else {
            clips.forEach { entry ->
                row.addView(chip(entry.content.shortLabel()) { commitDirectText(entry.content) })
            }
            row.addView(chip("Clear") {
                store.clear()
                toast("Clipboard vault cleared")
                root?.let { rebuild(it) }
            })
        }

        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(42),
        ))
    }

    private fun buildCandidateBar(parent: LinearLayout) {
        val candidates = PinyinDictionary.candidatesFor(pinyinBuffer)
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(labelBox("py: $pinyinBuffer", muted = false, accent = true))
        candidates.forEach { candidate ->
            row.addView(chip(candidate) { commitPinyinCandidate(candidate) })
        }
        row.addView(chip("清空") {
            clearPinyinComposition()
            root?.let { rebuild(it) }
        })

        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(42),
        ))
    }

    private fun buildPhraseBar(parent: LinearLayout) {
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        TemplateLibrary.quickPhrases.take(5).forEach { phrase ->
            row.addView(chip(phrase.shortLabel()) { commitDirectText(phrase) })
        }

        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(40),
        ))
    }

    private fun buildKeyboard(parent: LinearLayout) {
        val rows = if (symbols) symbolRows() else letterRows()
        rows.forEach { rowKeys ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            rowKeys.forEach { key ->
                row.addView(keyView(key), keyLayoutParams(key))
            }

            parent.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48),
            ))
        }
    }

    private fun letterRows(): List<List<String>> = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"),
        listOf("123", ",", "space", ".", "↵"),
    )

    private fun symbolRows(): List<List<String>> = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("-", "/", ":", ";", "(", ")", "¥", "&", "@"),
        listOf(".", ",", "?", "!", "'", "\"", "+", "=", "⌫"),
        listOf("ABC", "#", "space", "%", "↵"),
    )

    private fun keyView(rawKey: String): TextView {
        val display = when {
            rawKey == "space" -> if (inputMode == InputMode.PINYIN && pinyinBuffer.isNotEmpty()) "选词" else "space"
            inputMode == InputMode.ENGLISH && rawKey.length == 1 && rawKey[0].isLetter() && caps -> rawKey.uppercase()
            else -> rawKey
        }

        return TextView(this).apply {
            text = display
            OrbitTheme.label(this, sizeSp = if (rawKey == "space") 13f else 18f, bold = rawKey.length == 1)
            background = OrbitTheme.rounded(
                color = if (isControlKey(rawKey)) OrbitTheme.PANEL_ALT else OrbitTheme.PANEL,
                radiusPx = dp(12).toFloat(),
                strokeColor = if (isControlKey(rawKey)) OrbitTheme.ACCENT else OrbitTheme.PANEL_ALT,
                strokeWidthPx = dp(1),
            )
            setOnClickListener { handleKey(rawKey) }
            isClickable = true
            isFocusable = true
            minHeight = dp(42)
        }
    }

    private fun keyLayoutParams(key: String): LinearLayout.LayoutParams {
        val weight = when (key) {
            "space" -> 4.4f
            "⇧", "⌫", "123", "ABC", "↵" -> 1.45f
            else -> 1f
        }
        return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }
    }

    private fun isControlKey(key: String): Boolean {
        return key == "⇧" || key == "⌫" || key == "123" || key == "ABC" || key == "↵" || key == "space"
    }

    private fun handleKey(rawKey: String) {
        when (rawKey) {
            "⇧" -> {
                if (inputMode == InputMode.ENGLISH) caps = !caps
                root?.let { rebuild(it) }
            }
            "⌫" -> handleBackspace()
            "123" -> {
                commitPendingPinyin(rawFallback = true)
                symbols = true
                root?.let { rebuild(it) }
            }
            "ABC" -> {
                symbols = false
                root?.let { rebuild(it) }
            }
            "space" -> handleSpace()
            "↵" -> handleEnter()
            else -> handlePrintableKey(rawKey)
        }
    }

    private fun handlePrintableKey(rawKey: String) {
        if (rawKey.length == 1 && rawKey[0].isLetter() && inputMode == InputMode.PINYIN && !symbols) {
            appendPinyin(rawKey)
            return
        }

        commitPendingPinyin(rawFallback = true)
        val text = if (rawKey.length == 1 && rawKey[0].isLetter() && inputMode == InputMode.ENGLISH && caps) {
            rawKey.uppercase()
        } else {
            rawKey
        }
        currentInputConnection?.commitText(text, 1)
    }

    private fun handleBackspace() {
        val inputConnection = currentInputConnection ?: return
        if (inputMode == InputMode.PINYIN && pinyinBuffer.isNotEmpty()) {
            pinyinBuffer = pinyinBuffer.dropLast(1)
            if (pinyinBuffer.isEmpty()) {
                inputConnection.finishComposingText()
            } else {
                inputConnection.setComposingText(pinyinBuffer, 1)
            }
            root?.let { rebuild(it) }
            return
        }
        inputConnection.deleteSurroundingText(1, 0)
    }

    private fun handleSpace() {
        if (inputMode == InputMode.PINYIN && pinyinBuffer.isNotEmpty()) {
            commitPendingPinyin(rawFallback = false)
            return
        }
        currentInputConnection?.commitText(" ", 1)
    }

    private fun handleEnter() {
        commitPendingPinyin(rawFallback = true)
        sendEnterKey()
    }

    private fun appendPinyin(letter: String) {
        val normalized = PinyinDictionary.normalize(pinyinBuffer + letter)
        if (normalized.length > 32) {
            toast("Pinyin buffer limit reached")
            return
        }
        pinyinBuffer = normalized
        currentInputConnection?.setComposingText(pinyinBuffer, 1)
        showClips = false
        root?.let { rebuild(it) }
    }

    private fun commitPendingPinyin(rawFallback: Boolean) {
        if (inputMode != InputMode.PINYIN || pinyinBuffer.isEmpty()) return
        val candidate = PinyinDictionary.candidatesFor(pinyinBuffer).firstOrNull()
        val text = if (rawFallback && candidate == null) pinyinBuffer else candidate ?: pinyinBuffer
        commitPinyinCandidate(text)
    }

    private fun commitPinyinCandidate(candidate: String) {
        val inputConnection = currentInputConnection ?: return
        inputConnection.commitText(candidate, 1)
        pinyinBuffer = ""
        inputConnection.finishComposingText()
        root?.let { rebuild(it) }
    }

    private fun clearPinyinComposition() {
        pinyinBuffer = ""
        currentInputConnection?.finishComposingText()
    }

    private fun toggleInputMode() {
        commitPendingPinyin(rawFallback = true)
        inputMode = if (inputMode == InputMode.PINYIN) InputMode.ENGLISH else InputMode.PINYIN
        symbols = false
        caps = false
        showClips = false
        root?.let { rebuild(it) }
    }

    private fun sendEnterKey() {
        val inputConnection = currentInputConnection ?: return
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    private fun commitDirectText(text: String) {
        commitPendingPinyin(rawFallback = true)
        currentInputConnection?.commitText(text, 1)
    }

    private fun pasteClipboard(saveAfterPaste: Boolean) {
        val text = readClipboardText()
        if (text.isNullOrBlank()) {
            toast("Clipboard is empty")
            return
        }
        commitDirectText(text)
        if (saveAfterPaste && !sensitiveMode) store.add(text)
    }

    private fun saveClipboard() {
        if (sensitiveMode) {
            toast("Privacy mode")
            return
        }
        val text = readClipboardText()
        if (text.isNullOrBlank()) {
            toast("Clipboard is empty")
            return
        }
        if (store.add(text)) {
            toast("Saved locally")
            showClips = true
            root?.let { rebuild(it) }
        } else {
            toast("Skipped sensitive or unsupported text")
        }
    }

    private fun readClipboardText(): String? {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()
    }

    private fun chip(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            OrbitTheme.label(this, sizeSp = 13f, bold = true)
            background = OrbitTheme.rounded(OrbitTheme.PANEL_ALT, dp(16).toFloat(), OrbitTheme.ACCENT, dp(1))
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener { onClick() }
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(34),
            ).apply {
                setMargins(dp(3), dp(4), dp(3), dp(4))
            }
        }
    }

    private fun labelBox(text: String, muted: Boolean, accent: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            OrbitTheme.label(this, sizeSp = 13f, muted = muted, bold = false)
            background = OrbitTheme.rounded(
                color = OrbitTheme.PANEL,
                radiusPx = dp(12).toFloat(),
                strokeColor = if (accent) OrbitTheme.ACCENT else OrbitTheme.PANEL_ALT,
                strokeWidthPx = dp(1),
            )
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(36),
            ).apply {
                setMargins(dp(3), dp(3), dp(3), dp(5))
            }
        }
    }

    private fun String.shortLabel(): String {
        val normalized = replace("\n", " ").trim()
        return if (normalized.length <= 22) normalized else normalized.take(21) + "…"
    }

    private fun toast(message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
