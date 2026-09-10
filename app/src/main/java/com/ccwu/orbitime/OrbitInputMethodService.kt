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
    private var caps = false
    private var symbols = false
    private var showClips = false
    private var sensitiveMode = false
    private var root: LinearLayout? = null
    private lateinit var store: ClipboardStore

    override fun onCreate() {
        super.onCreate()
        store = ClipboardStore(this)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        sensitiveMode = PrivacyGuard.isSensitiveInput(attribute)
        if (sensitiveMode) showClips = false
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
        if (!sensitiveMode && !showClips) buildPhraseBar(layout)
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

        row.addView(chip("Paste") { pasteClipboard(saveAfterPaste = false) })
        row.addView(chip("Save") { saveClipboard() })
        row.addView(chip(if (showClips) "Keys" else "Clips") {
            showClips = !showClips
            root?.let { rebuild(it) }
        })

        TemplateLibrary.defaultActions.take(ProGate.maxTemplates(this)).forEach { action ->
            row.addView(chip(action.label) { commitText(action.insertText) })
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
                row.addView(chip(entry.content.shortLabel()) { commitText(entry.content) })
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

    private fun buildPhraseBar(parent: LinearLayout) {
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        TemplateLibrary.quickPhrases.take(5).forEach { phrase ->
            row.addView(chip(phrase.shortLabel()) { commitText(phrase) })
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
            rawKey == "space" -> "space"
            rawKey.length == 1 && rawKey[0].isLetter() && caps -> rawKey.uppercase()
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
        val inputConnection = currentInputConnection ?: return
        when (rawKey) {
            "⇧" -> {
                caps = !caps
                root?.let { rebuild(it) }
            }
            "⌫" -> inputConnection.deleteSurroundingText(1, 0)
            "123" -> {
                symbols = true
                root?.let { rebuild(it) }
            }
            "ABC" -> {
                symbols = false
                root?.let { rebuild(it) }
            }
            "space" -> inputConnection.commitText(" ", 1)
            "↵" -> sendEnterKey()
            else -> {
                val text = if (rawKey.length == 1 && rawKey[0].isLetter() && caps) rawKey.uppercase() else rawKey
                inputConnection.commitText(text, 1)
            }
        }
    }

    private fun sendEnterKey() {
        val inputConnection = currentInputConnection ?: return
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun pasteClipboard(saveAfterPaste: Boolean) {
        val text = readClipboardText()
        if (text.isNullOrBlank()) {
            toast("Clipboard is empty")
            return
        }
        commitText(text)
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
                ViewGroup.LayoutParams.MATCH_PARENT,
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
