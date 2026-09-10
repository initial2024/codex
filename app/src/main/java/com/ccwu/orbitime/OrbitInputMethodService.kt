package com.ccwu.orbitime

import android.content.ClipData
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
    private var showTranslate = false
    private var showPet = false
    private var sensitiveMode = false
    private var pinyinBuffer = ""
    private var translateDirection = TranslatePromptBuilder.Direction.ZH_TO_EN
    private var translateSourceText: String? = null
    private var translateSourceLabel: String? = null
    private var translatePromptPreview: String? = null
    private var offlineTranslationPreview: String? = null
    private var translateDraftMode = false
    private var translateDraftBuffer = ""
    private var root: LinearLayout? = null
    private lateinit var store: ClipboardStore
    private lateinit var userDictionary: UserDictionaryStore
    private lateinit var petRepository: PetRepository

    override fun onCreate() {
        super.onCreate()
        store = ClipboardStore(this)
        userDictionary = UserDictionaryStore(this)
        petRepository = PetRepository(this)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        sensitiveMode = PrivacyGuard.isSensitiveInput(attribute)
        if (sensitiveMode) {
            showClips = false
            showPet = false
            clearPinyinComposition()
            clearTranslateState()
        }
        root?.let { rebuild(it) }
    }

    override fun onCreateInputView(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = OrbitTheme.keyboardBackground(activeSkin())
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
        layout.background = OrbitTheme.keyboardBackground(activeSkin())
        layout.removeAllViews()
        buildTopBar(layout)
        when {
            !sensitiveMode && showPet -> buildPetPanel(layout)
            !sensitiveMode && showTranslate -> buildTranslatePanel(layout)
            !sensitiveMode && showClips -> buildClipBar(layout)
            !sensitiveMode && inputMode == InputMode.PINYIN && pinyinBuffer.isNotEmpty() -> buildCandidateBar(layout)
            !sensitiveMode && pinyinBuffer.isEmpty() -> buildPhraseBar(layout)
        }
        buildKeyboard(layout)
    }

    private fun buildTopBar(parent: LinearLayout) {
        val skin = activeSkin()
        if (sensitiveMode) {
            parent.addView(
                labelBox(
                    text = "🔒 Privacy mode · Hub disabled",
                    muted = false,
                    accent = true,
                    warning = true,
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

        row.addView(chip(if (inputMode == InputMode.PINYIN) "拼音" else "EN", emphasized = true) { toggleInputMode() })
        row.addView(chip("Paste") { pasteClipboard(saveAfterPaste = false) })
        row.addView(chip("Save") { saveClipboard() })
        row.addView(chip(if (showClips) "Keys" else "Clips") {
            showClips = !showClips
            showPet = false
            if (showClips) clearTranslateState()
            root?.let { rebuild(it) }
        })
        row.addView(chip(if (showTranslate) "Keys" else "Translate", emphasized = showTranslate) {
            if (showTranslate) {
                clearTranslateState()
            } else {
                showTranslate = true
                showClips = false
                showPet = false
                translateDraftMode = false
                translatePromptPreview = null
                offlineTranslationPreview = null
                translateSourceText = null
                translateSourceLabel = null
            }
            root?.let { rebuild(it) }
        })
        row.addView(chip(petRepository.compactStatus(), emphasized = showPet) {
            showPet = !showPet
            showClips = false
            if (showPet) clearTranslateState()
            root?.let { rebuild(it) }
        })

        TemplateLibrary.defaultActions.take(ProGate.maxTemplates(this)).forEach { action ->
            row.addView(chip(action.label) { commitDirectText(action.insertText) })
        }

        scroller.setBackgroundColor(skin.backgroundColor)
        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(44),
        ))
    }

    private fun buildPetPanel(parent: LinearLayout) {
        val profile = petRepository.profile()
        val visible = profile.displayMode != PetRepository.DISPLAY_HIDDEN
        parent.addView(labelBox("Pet · ${petRepository.panelLine()}", muted = false, accent = true))
        parent.addView(labelBox(if (visible) petRepository.localChatLine() else "Pet is hidden. It stays local and quiet.", muted = !profile.chatUnlocked, accent = profile.chatUnlocked))

        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(chip("签到", emphasized = true) {
            val result = petRepository.checkIn()
            toast(result.message)
            root?.let { rebuild(it) }
        })
        row.addView(chip("开蛋") {
            val result = petRepository.adoptRandom()
            toast(result.message)
            root?.let { rebuild(it) }
        })
        row.addView(chip(if (visible) "隐藏" else "显示") {
            val result = petRepository.toggleHidden()
            toast(result.message)
            root?.let { rebuild(it) }
        })
        row.addView(chip("聊天") {
            toast(petRepository.localChatLine())
        })
        row.addView(chip("装扮") {
            toast("Outfit slots are ready for Gemini assets")
        })
        row.addView(chip("关闭", warning = true) {
            showPet = false
            root?.let { rebuild(it) }
        })

        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(42),
        ))
    }

    private fun buildTranslatePanel(parent: LinearLayout) {
        val sourceLabel = translateSourceLabel ?: "choose source"
        parent.addView(labelBox("Translate Preview · ${translateDirection.label} · $sourceLabel · local only", muted = false, accent = true))

        val previewText = when {
            translateDraftMode -> "draft: ${translateDraftBuffer.ifBlank { "type here before confirming" }.shortLabel(44)}"
            offlineTranslationPreview != null -> "offline: ${offlineTranslationPreview.orEmpty().shortLabel(46)}"
            translatePromptPreview != null -> translatePromptPreview.orEmpty().shortLabel(56)
            else -> "Prompt Preview is free. Offline Pack is Pro and local-only."
        }
        parent.addView(labelBox(previewText, muted = translatePromptPreview == null && !translateDraftMode, accent = offlineTranslationPreview != null))

        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        when {
            translatePromptPreview != null -> {
                offlineTranslationPreview?.let {
                    row.addView(chip("插入译文", emphasized = true) { insertOfflineTranslation() })
                }
                row.addView(chip("插入Prompt") { insertTranslatePrompt() })
                row.addView(chip("复制Prompt") { copyTranslatePrompt() })
                row.addView(chip("换方向") { toggleTranslateDirection(regenerate = true) })
                row.addView(chip("重选") { resetTranslateSelection() })
                row.addView(chip("取消", warning = true) {
                    clearTranslateState()
                    root?.let { rebuild(it) }
                })
            }
            translateDraftMode -> {
                row.addView(chip("生成", emphasized = true) { captureTranslateSource("草稿", translateDraftBuffer) })
                row.addView(chip("换方向") { toggleTranslateDirection(regenerate = false) })
                row.addView(chip("清空", warning = true) {
                    translateDraftBuffer = ""
                    root?.let { rebuild(it) }
                })
                row.addView(chip("取消", warning = true) {
                    clearTranslateState()
                    root?.let { rebuild(it) }
                })
            }
            else -> {
                row.addView(chip("方向 ${translateDirection.label}", emphasized = true) { toggleTranslateDirection(regenerate = false) })
                row.addView(chip("前一句") { captureTranslateSource("前一句", readPreviousSentence()) })
                row.addView(chip("选中文本") { captureTranslateSource("选中文本", readSelectedText()) })
                row.addView(chip("剪贴板") { captureTranslateSource("剪贴板", readClipboardText()) })
                if (pinyinBuffer.isNotEmpty()) {
                    row.addView(chip("拼音草稿") { captureTranslateSource("拼音草稿", pinyinBuffer) })
                }
                row.addView(chip("草稿") { startTranslateDraft() })
                row.addView(chip(if (ProGate.isOfflineTranslationPackUnlocked(this)) "离线包ON" else "离线包Pro", warning = !ProGate.isOfflineTranslationPackUnlocked(this)) {
                    toast(if (ProGate.isOfflineTranslationPackUnlocked(this)) "Offline Pack enabled" else OfflineTranslationPack.lockedMessage())
                })
                row.addView(chip("取消", warning = true) {
                    clearTranslateState()
                    root?.let { rebuild(it) }
                })
            }
        }

        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(42),
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
            row.addView(chip("Clear", warning = true) {
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
        val candidates = candidatesForCurrentPinyin()
        val stats = userDictionary.stats()
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(labelBox("py: $pinyinBuffer · local ${stats.entryCount}", muted = false, accent = true))
        candidates.forEachIndexed { index, candidate ->
            row.addView(chip(candidate, emphasized = index == 0) { commitPinyinCandidate(candidate) })
        }
        row.addView(chip("清空", warning = true) {
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
        val skin = activeSkin()
        val display = when {
            rawKey == "space" && translateDraftMode -> if (translateDraftBuffer.isEmpty()) "draft" else "space"
            rawKey == "space" -> if (inputMode == InputMode.PINYIN && pinyinBuffer.isNotEmpty()) "选词" else "space"
            inputMode == InputMode.ENGLISH && rawKey.length == 1 && rawKey[0].isLetter() && caps -> rawKey.uppercase()
            else -> rawKey
        }

        return TextView(this).apply {
            text = display
            OrbitTheme.label(this, sizeSp = if (rawKey == "space") 13f else 18f, bold = rawKey.length == 1, skin = skin)
            background = OrbitTheme.rounded(
                color = if (isControlKey(rawKey)) skin.controlKeyColor else skin.keyColor,
                radiusPx = dp(12).toFloat(),
                strokeColor = if (isControlKey(rawKey)) skin.accentColor else skin.borderColor,
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
        if (translateDraftMode) {
            handleTranslateDraftKey(rawKey)
            return
        }

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

    private fun handleTranslateDraftKey(rawKey: String) {
        when (rawKey) {
            "⌫" -> {
                translateDraftBuffer = translateDraftBuffer.dropLast(1)
                root?.let { rebuild(it) }
            }
            "123" -> {
                symbols = true
                root?.let { rebuild(it) }
            }
            "ABC" -> {
                symbols = false
                root?.let { rebuild(it) }
            }
            "↵" -> captureTranslateSource("草稿", translateDraftBuffer)
            "⇧" -> {
                caps = !caps
                root?.let { rebuild(it) }
            }
            "space" -> appendTranslateDraft(" ")
            else -> appendTranslateDraft(mapPrintableText(rawKey))
        }
    }

    private fun appendTranslateDraft(text: String) {
        if (translateDraftBuffer.length + text.length > 1200) {
            toast(TranslatePromptBuilder.sourceTooLongMessage())
            return
        }
        translateDraftBuffer += text
        if (!sensitiveMode) petRepository.recordTypedChars(text.length)
        root?.let { rebuild(it) }
    }

    private fun handlePrintableKey(rawKey: String) {
        if (rawKey.length == 1 && rawKey[0].isLetter() && inputMode == InputMode.PINYIN && !symbols) {
            appendPinyin(rawKey)
            return
        }

        commitPendingPinyin(rawFallback = true)
        val mapped = mapPrintableText(rawKey)
        currentInputConnection?.commitText(mapped, 1)
        if (!sensitiveMode) petRepository.recordTypedChars(mapped.length)
    }

    private fun mapPrintableText(rawKey: String): String {
        if (inputMode == InputMode.ENGLISH && rawKey.length == 1 && rawKey[0].isLetter() && caps) {
            return rawKey.uppercase()
        }
        if (inputMode == InputMode.PINYIN && !symbols) {
            return when (rawKey) {
                "," -> "，"
                "." -> "。"
                else -> rawKey
            }
        }
        return rawKey
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
        if (!sensitiveMode) petRepository.recordTypedChars(1)
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
        showTranslate = false
        showPet = false
        if (!sensitiveMode) petRepository.recordTypedChars(1)
        root?.let { rebuild(it) }
    }

    private fun commitPendingPinyin(rawFallback: Boolean) {
        if (inputMode != InputMode.PINYIN || pinyinBuffer.isEmpty()) return
        val text = if (rawFallback) {
            exactCandidatesForCurrentPinyin().firstOrNull() ?: pinyinBuffer
        } else {
            candidatesForCurrentPinyin().firstOrNull() ?: pinyinBuffer
        }
        commitPinyinCandidate(text)
    }

    private fun candidatesForCurrentPinyin(): List<String> {
        return userDictionary.candidatesFor(
            rawInput = pinyinBuffer,
            staticCandidates = PinyinDictionary.candidatesFor(pinyinBuffer),
        )
    }

    private fun exactCandidatesForCurrentPinyin(): List<String> {
        return userDictionary.exactCandidatesFor(
            rawInput = pinyinBuffer,
            staticCandidates = PinyinDictionary.exactCandidatesFor(pinyinBuffer),
        )
    }

    private fun commitPinyinCandidate(candidate: String) {
        val inputConnection = currentInputConnection ?: return
        val learnedPinyin = pinyinBuffer
        inputConnection.commitText(candidate, 1)
        pinyinBuffer = ""
        inputConnection.finishComposingText()
        if (!sensitiveMode) {
            userDictionary.learn(learnedPinyin, candidate)
            petRepository.recordCandidateCommit()
            petRepository.recordTypedChars(candidate.length)
        }
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
        showPet = false
        clearTranslateState()
        root?.let { rebuild(it) }
    }

    private fun sendEnterKey() {
        val inputConnection = currentInputConnection ?: return
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    private fun commitDirectText(text: String) {
        commitPendingPinyin(rawFallback = true)
        clearTranslateState()
        currentInputConnection?.commitText(text, 1)
        if (!sensitiveMode) petRepository.recordTypedChars(text.length)
    }

    private fun captureTranslateSource(label: String, rawSource: String?) {
        val source = rawSource?.trim().orEmpty()
        if (!TranslatePromptBuilder.canUseSource(source)) {
            toast(if (source.length > 1200) TranslatePromptBuilder.sourceTooLongMessage() else TranslatePromptBuilder.unsafeSourceMessage())
            return
        }
        translateSourceText = source
        translateSourceLabel = label
        translatePromptPreview = TranslatePromptBuilder.build(source, translateDirection)
        offlineTranslationPreview = if (ProGate.isOfflineTranslationPackUnlocked(this)) {
            OfflineTranslationPack.translateOrNull(source, translateDirection)?.translatedText
        } else {
            null
        }
        translateDraftMode = false
        translateDraftBuffer = ""
        showTranslate = true
        showClips = false
        showPet = false
        root?.let { rebuild(it) }
    }

    private fun toggleTranslateDirection(regenerate: Boolean) {
        translateDirection = if (translateDirection == TranslatePromptBuilder.Direction.ZH_TO_EN) {
            TranslatePromptBuilder.Direction.EN_TO_ZH
        } else {
            TranslatePromptBuilder.Direction.ZH_TO_EN
        }
        if (regenerate) {
            translateSourceText?.let {
                translatePromptPreview = TranslatePromptBuilder.build(it, translateDirection)
                offlineTranslationPreview = if (ProGate.isOfflineTranslationPackUnlocked(this)) {
                    OfflineTranslationPack.translateOrNull(it, translateDirection)?.translatedText
                } else {
                    null
                }
            }
        }
        root?.let { rebuild(it) }
    }

    private fun startTranslateDraft() {
        clearPinyinComposition()
        translateDraftMode = true
        translateDraftBuffer = ""
        translatePromptPreview = null
        offlineTranslationPreview = null
        translateSourceText = null
        translateSourceLabel = "草稿"
        showTranslate = true
        showClips = false
        showPet = false
        symbols = false
        root?.let { rebuild(it) }
    }

    private fun resetTranslateSelection() {
        translateSourceText = null
        translateSourceLabel = null
        translatePromptPreview = null
        offlineTranslationPreview = null
        translateDraftMode = false
        translateDraftBuffer = ""
        root?.let { rebuild(it) }
    }

    private fun insertTranslatePrompt() {
        val prompt = translatePromptPreview ?: return
        clearTranslateState()
        currentInputConnection?.commitText(prompt, 1)
        if (!sensitiveMode) petRepository.recordTranslatePrompt()
        root?.let { rebuild(it) }
    }

    private fun insertOfflineTranslation() {
        val text = offlineTranslationPreview ?: return
        clearTranslateState()
        currentInputConnection?.commitText(text, 1)
        if (!sensitiveMode) {
            petRepository.recordTranslatePrompt()
            petRepository.recordTypedChars(text.length)
        }
        root?.let { rebuild(it) }
    }

    private fun copyTranslatePrompt() {
        val prompt = translatePromptPreview ?: return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Orbit Translate Prompt", prompt))
        toast("Prompt copied locally")
    }

    private fun clearTranslateState() {
        showTranslate = false
        translateDraftMode = false
        translateDraftBuffer = ""
        translateSourceText = null
        translateSourceLabel = null
        translatePromptPreview = null
        offlineTranslationPreview = null
    }

    private fun readSelectedText(): String? {
        return currentInputConnection?.getSelectedText(0)?.toString()
    }

    private fun readPreviousSentence(): String? {
        val text = currentInputConnection?.getTextBeforeCursor(240, 0)?.toString() ?: return null
        return extractLastSentence(text)
    }

    private fun extractLastSentence(raw: String): String? {
        val cleaned = raw.trim().trimEnd('。', '！', '？', '.', '!', '?', '\n', '\r', ' ', '\t')
        if (cleaned.isBlank()) return null
        val lastBreak = cleaned.indexOfLast { it == '。' || it == '！' || it == '？' || it == '.' || it == '!' || it == '?' || it == '\n' || it == '\r' }
        return cleaned.substring(lastBreak + 1).trim().ifBlank { null }
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
            petRepository.recordClipSave()
            toast("Saved locally")
            showClips = true
            showPet = false
            clearTranslateState()
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

    private fun chip(text: String, emphasized: Boolean = false, warning: Boolean = false, onClick: () -> Unit): TextView {
        val skin = activeSkin()
        return TextView(this).apply {
            this.text = text
            OrbitTheme.label(this, sizeSp = 13f, bold = true, skin = skin)
            val stroke = when {
                warning -> skin.warningColor
                emphasized -> skin.accentColor
                else -> skin.borderColor
            }
            val textColor = when {
                warning -> skin.warningColor
                emphasized -> skin.accentColor
                else -> skin.textColor
            }
            setTextColor(textColor)
            background = OrbitTheme.rounded(skin.panelAltColor, dp(16).toFloat(), stroke, dp(1))
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

    private fun labelBox(text: String, muted: Boolean, accent: Boolean, warning: Boolean = false): TextView {
        val skin = activeSkin()
        return TextView(this).apply {
            this.text = text
            OrbitTheme.label(this, sizeSp = 13f, muted = muted, bold = accent || warning, skin = skin)
            if (accent) setTextColor(skin.accentColor)
            if (warning) setTextColor(skin.warningColor)
            background = OrbitTheme.rounded(
                color = skin.panelColor,
                radiusPx = dp(12).toFloat(),
                strokeColor = when {
                    warning -> skin.warningColor
                    accent -> skin.accentColor
                    else -> skin.borderColor
                },
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

    private fun String.shortLabel(maxLength: Int = 22): String {
        val normalized = replace("\n", " ").trim()
        return if (normalized.length <= maxLength) normalized else normalized.take(maxLength - 1) + "…"
    }

    private fun toast(message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun activeSkin(): OrbitSkin = SkinManager.keyboardSkin(this, sensitiveMode)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
