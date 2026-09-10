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
import android.view.inputmethod.InputMethodManager
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
    private var englishBuffer = ""
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
            clearEnglishComposition()
            clearTranslateState()
        }
        root?.let { rebuild(it) }
    }

    override fun onCreateInputView(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = OrbitTheme.keyboardBackground(activeSkin())
            setPadding(dp(8), dp(6), dp(8), dp(36))
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
            !sensitiveMode && inputMode == InputMode.PINYIN && pinyinBuffer.isNotEmpty() -> buildPinyinCandidateBar(layout)
            !sensitiveMode && inputMode == InputMode.ENGLISH && englishBuffer.isNotEmpty() -> buildEnglishCandidateBar(layout)
            !sensitiveMode && pinyinBuffer.isEmpty() && englishBuffer.isEmpty() -> buildPhraseBar(layout)
        }
        buildKeyboard(layout)
    }

    private fun buildTopBar(parent: LinearLayout) {
        val skin = activeSkin()
        if (sensitiveMode) {
            parent.addView(
                labelBox(
                    text = "🔒 隐私模式 · 工具已隐藏",
                    muted = false,
                    accent = true,
                    warning = true,
                ),
            )
            return
        }

        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(chip(if (inputMode == InputMode.PINYIN) "拼音" else "EN", emphasized = true) { toggleInputMode() })
        row.addView(chip(label(pinyin = "切换", english = "Switch")) { showInputMethodPickerSafely() })
        row.addView(chip(label(pinyin = "粘贴", english = "Paste")) { pasteClipboard(saveAfterPaste = false) })
        row.addView(chip(if (showClips) label("返回", "Keyboard") else label("剪贴板", "Clips")) {
            commitPendingEnglish(rawFallback = true, appendSpace = false)
            showClips = !showClips
            showPet = false
            if (showClips) clearTranslateState()
            root?.let { rebuild(it) }
        })
        row.addView(chip(if (showTranslate) label("返回", "Keyboard") else label("翻译", "Translate"), emphasized = showTranslate) {
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

        scroller.setBackgroundColor(skin.backgroundColor)
        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
    }

    private fun buildPetPanel(parent: LinearLayout) {
        val profile = petRepository.profile()
        val visible = profile.displayMode != PetRepository.DISPLAY_HIDDEN
        parent.addView(labelBox("宠物 · ${profile.petName} · Lv.${profile.level} · ${profile.exp} EXP", muted = false, accent = true))
        parent.addView(labelBox(if (visible) "宠物仍在设置页管理，键盘内暂时弱化。" else "宠物已隐藏，只保存在本机。", muted = true, accent = false))

        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(chip("签到", emphasized = true) {
            val result = petRepository.checkIn()
            toast(result.message)
            root?.let { rebuild(it) }
        })
        row.addView(chip(if (visible) "隐藏" else "显示") {
            val result = petRepository.toggleHidden()
            toast(result.message)
            root?.let { rebuild(it) }
        })
        row.addView(chip("关闭", warning = true) {
            showPet = false
            root?.let { rebuild(it) }
        })

        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
    }

    private fun buildTranslatePanel(parent: LinearLayout) {
        val sourceLabel = translateSourceLabel ?: label("选择来源", "choose source")
        parent.addView(labelBox("翻译 · ${translateDirection.label} · $sourceLabel · 本地", muted = false, accent = true))

        val previewText = when {
            translateDraftMode -> "草稿：${translateDraftBuffer.ifBlank { "先输入，再生成译文" }.shortLabel(44)}"
            offlineTranslationPreview != null -> "译文：${offlineTranslationPreview.orEmpty().shortLabel(64)}"
            translatePromptPreview != null -> "暂无离线译文，可插入提示词：${translatePromptPreview.orEmpty().shortLabel(40)}"
            else -> "选择来源后会优先显示本地译文；没有命中才显示提示词。"
        }
        parent.addView(labelBox(previewText, muted = translatePromptPreview == null && !translateDraftMode && offlineTranslationPreview == null, accent = offlineTranslationPreview != null))

        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        when {
            translatePromptPreview != null -> {
                offlineTranslationPreview?.let {
                    row.addView(chip("插入译文", emphasized = true) { insertOfflineTranslation() })
                    row.addView(chip("复制译文") { copyOfflineTranslation() })
                }
                if (offlineTranslationPreview == null) {
                    row.addView(chip("插入提示词") { insertTranslatePrompt() })
                    row.addView(chip("复制提示词") { copyTranslatePrompt() })
                }
                row.addView(chip("换方向") { toggleTranslateDirection(regenerate = true) })
                row.addView(chip("重选") { resetTranslateSelection() })
                row.addView(chip("取消", warning = true) {
                    clearTranslateState()
                    root?.let { rebuild(it) }
                })
            }
            translateDraftMode -> {
                row.addView(chip("生成译文", emphasized = true) { captureTranslateSource("草稿", translateDraftBuffer) })
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
                    row.addView(chip("拼音草稿") { captureTranslateSource("拼音草稿", candidatesForCurrentPinyin().firstOrNull() ?: pinyinBuffer) })
                }
                if (englishBuffer.isNotEmpty()) {
                    row.addView(chip("英文草稿") { captureTranslateSource("英文草稿", candidatesForCurrentEnglish().firstOrNull() ?: englishBuffer) })
                }
                row.addView(chip("草稿") { startTranslateDraft() })
                row.addView(chip("取消", warning = true) {
                    clearTranslateState()
                    root?.let { rebuild(it) }
                })
            }
        }

        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
    }

    private fun buildClipBar(parent: LinearLayout) {
        val clips = store.load().take(8)
        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(chip("保存当前剪贴板", emphasized = true) { saveClipboard() })
        if (clips.isEmpty()) {
            row.addView(labelBox("暂无保存内容。复制文字后点保存。", muted = true, accent = false))
        } else {
            clips.forEach { entry -> row.addView(chip(entry.content.shortLabel()) { commitDirectText(entry.content) }) }
            row.addView(chip("清空", warning = true) {
                store.clear()
                toast("剪贴板已清空")
                root?.let { rebuild(it) }
            })
        }

        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
    }

    private fun buildPinyinCandidateBar(parent: LinearLayout) {
        val candidates = candidatesForCurrentPinyin()
        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(labelBox("拼音：$pinyinBuffer", muted = false, accent = true))
        candidates.forEachIndexed { index, candidate ->
            row.addView(chip(candidate, emphasized = index == 0) { commitPinyinCandidate(candidate) })
        }
        row.addView(chip("清空", warning = true) {
            clearPinyinComposition()
            root?.let { rebuild(it) }
        })

        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
    }

    private fun buildEnglishCandidateBar(parent: LinearLayout) {
        val candidates = candidatesForCurrentEnglish()
        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(labelBox("word: $englishBuffer", muted = false, accent = true))
        candidates.forEachIndexed { index, candidate ->
            row.addView(chip(candidate, emphasized = index == 0) { commitEnglishCandidate(candidate, appendSpace = false) })
        }
        row.addView(chip("clear", warning = true) {
            clearEnglishComposition()
            root?.let { rebuild(it) }
        })

        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
    }

    private fun buildPhraseBar(parent: LinearLayout) {
        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val phrases = if (inputMode == InputMode.PINYIN) TemplateLibrary.quickPhrasesForPinyin() else TemplateLibrary.quickPhrasesForEnglish()
        phrases.forEach { phrase -> row.addView(chip(phrase.shortLabel()) { commitDirectText(phrase) }) }

        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)))
    }

    private fun buildKeyboard(parent: LinearLayout) {
        val rows = if (symbols) symbolRows() else letterRows()
        rows.forEach { rowKeys ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowKeys.forEach { key -> row.addView(keyView(key), keyLayoutParams(key)) }
            parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
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
            rawKey == "space" && translateDraftMode -> if (translateDraftBuffer.isEmpty()) "草稿" else label("空格", "space")
            rawKey == "space" -> when {
                inputMode == InputMode.PINYIN && pinyinBuffer.isNotEmpty() -> "选词"
                inputMode == InputMode.ENGLISH && englishBuffer.isNotEmpty() -> "select"
                else -> label("空格", "space")
            }
            rawKey == "↵" -> label("回车", "Enter")
            inputMode == InputMode.ENGLISH && rawKey.length == 1 && rawKey[0].isLetter() && caps -> rawKey.uppercase()
            else -> rawKey
        }

        return TextView(this).apply {
            text = display
            OrbitTheme.label(this, sizeSp = if (rawKey == "space" || rawKey == "↵") 13f else 18f, bold = rawKey.length == 1, skin = skin)
            background = OrbitTheme.rounded(
                color = if (isControlKey(rawKey)) skin.controlKeyColor else skin.keyColor,
                radiusPx = dp(12).toFloat(),
                strokeColor = if (isControlKey(rawKey)) skin.accentColor else skin.borderColor,
                strokeWidthPx = dp(1),
            )
            setOnClickListener { handleKey(rawKey) }
            isClickable = true
            isFocusable = true
            minHeight = dp(38)
        }
    }

    private fun keyLayoutParams(key: String): LinearLayout.LayoutParams {
        val weight = when (key) {
            "space" -> 4.6f
            "⇧", "⌫", "123", "ABC", "↵" -> 1.45f
            else -> 1f
        }
        return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply { setMargins(dp(3), dp(2), dp(3), dp(2)) }
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
                commitPendingEnglish(rawFallback = true, appendSpace = false)
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
        if (rawKey.length == 1 && rawKey[0].isLetter() && inputMode == InputMode.ENGLISH && !symbols) {
            appendEnglish(rawKey)
            return
        }

        commitPendingPinyin(rawFallback = true)
        commitPendingEnglish(rawFallback = true, appendSpace = false)
        val mapped = mapPrintableText(rawKey)
        currentInputConnection?.commitText(mapped, 1)
        if (!sensitiveMode) petRepository.recordTypedChars(mapped.length)
    }

    private fun mapPrintableText(rawKey: String): String {
        if (inputMode == InputMode.ENGLISH && rawKey.length == 1 && rawKey[0].isLetter() && caps) return rawKey.uppercase()
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
            if (pinyinBuffer.isEmpty()) inputConnection.finishComposingText() else inputConnection.setComposingText(pinyinBuffer, 1)
            root?.let { rebuild(it) }
            return
        }
        if (inputMode == InputMode.ENGLISH && englishBuffer.isNotEmpty()) {
            englishBuffer = englishBuffer.dropLast(1)
            if (englishBuffer.isEmpty()) inputConnection.finishComposingText() else inputConnection.setComposingText(englishBuffer, 1)
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
        if (inputMode == InputMode.ENGLISH && englishBuffer.isNotEmpty()) {
            commitPendingEnglish(rawFallback = false, appendSpace = true)
            return
        }
        currentInputConnection?.commitText(" ", 1)
        if (!sensitiveMode) petRepository.recordTypedChars(1)
    }

    private fun handleEnter() {
        commitPendingPinyin(rawFallback = true)
        commitPendingEnglish(rawFallback = true, appendSpace = false)
        sendEnterKey()
    }

    private fun appendPinyin(letter: String) {
        val normalized = PinyinDictionary.normalize(pinyinBuffer + letter)
        if (normalized.length > 64) {
            toast("拼音太长")
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

    private fun appendEnglish(letter: String) {
        val text = if (caps) letter.uppercase() else letter.lowercase()
        if (englishBuffer.length + text.length > 64) {
            toast("word too long")
            return
        }
        englishBuffer += text
        currentInputConnection?.setComposingText(englishBuffer, 1)
        showClips = false
        showTranslate = false
        showPet = false
        if (!sensitiveMode) petRepository.recordTypedChars(1)
        root?.let { rebuild(it) }
    }

    private fun commitPendingPinyin(rawFallback: Boolean) {
        if (inputMode != InputMode.PINYIN || pinyinBuffer.isEmpty()) return
        val text = if (rawFallback) exactCandidatesForCurrentPinyin().firstOrNull() ?: pinyinBuffer else candidatesForCurrentPinyin().firstOrNull() ?: pinyinBuffer
        commitPinyinCandidate(text)
    }

    private fun commitPendingEnglish(rawFallback: Boolean, appendSpace: Boolean) {
        if (inputMode != InputMode.ENGLISH || englishBuffer.isEmpty()) return
        val text = if (rawFallback) englishBuffer else candidatesForCurrentEnglish().firstOrNull() ?: englishBuffer
        commitEnglishCandidate(text, appendSpace)
    }

    private fun candidatesForCurrentPinyin(): List<String> {
        val boosted = PinyinSentenceDictionary.candidatesFor(pinyinBuffer)
        val staticCandidates = (boosted + PinyinDictionary.candidatesFor(pinyinBuffer)).distinct()
        return userDictionary.candidatesFor(rawInput = pinyinBuffer, staticCandidates = staticCandidates)
    }

    private fun exactCandidatesForCurrentPinyin(): List<String> {
        val boosted = PinyinSentenceDictionary.candidatesFor(pinyinBuffer)
        val staticCandidates = (boosted + PinyinDictionary.exactCandidatesFor(pinyinBuffer)).distinct()
        return userDictionary.exactCandidatesFor(rawInput = pinyinBuffer, staticCandidates = staticCandidates)
    }

    private fun candidatesForCurrentEnglish(): List<String> = EnglishDictionary.candidatesFor(englishBuffer)

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

    private fun commitEnglishCandidate(candidate: String, appendSpace: Boolean) {
        val inputConnection = currentInputConnection ?: return
        val text = if (appendSpace) "$candidate " else candidate
        inputConnection.commitText(text, 1)
        englishBuffer = ""
        inputConnection.finishComposingText()
        if (!sensitiveMode) petRepository.recordTypedChars(text.length)
        root?.let { rebuild(it) }
    }

    private fun clearPinyinComposition() {
        pinyinBuffer = ""
        currentInputConnection?.finishComposingText()
    }

    private fun clearEnglishComposition() {
        englishBuffer = ""
        currentInputConnection?.finishComposingText()
    }

    private fun toggleInputMode() {
        commitPendingPinyin(rawFallback = true)
        commitPendingEnglish(rawFallback = true, appendSpace = false)
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

    private fun showInputMethodPickerSafely() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        } catch (_: Exception) {
            toast("请从系统输入法按钮切换")
        }
    }

    private fun commitDirectText(text: String) {
        commitPendingPinyin(rawFallback = true)
        commitPendingEnglish(rawFallback = true, appendSpace = false)
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
        val local = OfflineTranslationPack.translateOrNull(source, translateDirection)?.translatedText
        offlineTranslationPreview = local
        translatePromptPreview = TranslatePromptBuilder.build(source, translateDirection)
        translateDraftMode = false
        translateDraftBuffer = ""
        showTranslate = true
        showClips = false
        showPet = false
        root?.let { rebuild(it) }
    }

    private fun toggleTranslateDirection(regenerate: Boolean) {
        translateDirection = if (translateDirection == TranslatePromptBuilder.Direction.ZH_TO_EN) TranslatePromptBuilder.Direction.EN_TO_ZH else TranslatePromptBuilder.Direction.ZH_TO_EN
        if (regenerate) {
            translateSourceText?.let {
                offlineTranslationPreview = OfflineTranslationPack.translateOrNull(it, translateDirection)?.translatedText
                translatePromptPreview = TranslatePromptBuilder.build(it, translateDirection)
            }
        }
        root?.let { rebuild(it) }
    }

    private fun startTranslateDraft() {
        clearPinyinComposition()
        clearEnglishComposition()
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
        toast("提示词已复制")
    }

    private fun copyOfflineTranslation() {
        val text = offlineTranslationPreview ?: return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Orbit Translation", text))
        toast("译文已复制")
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

    private fun readSelectedText(): String? = currentInputConnection?.getSelectedText(0)?.toString()

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
            toast("剪贴板为空")
            return
        }
        commitDirectText(text)
        if (saveAfterPaste && !sensitiveMode) store.add(text)
    }

    private fun saveClipboard() {
        if (sensitiveMode) {
            toast("隐私模式")
            return
        }
        val text = readClipboardText()
        if (text.isNullOrBlank()) {
            toast("剪贴板为空")
            return
        }
        if (store.add(text)) {
            petRepository.recordClipSave()
            toast("已保存到本机")
            showClips = true
            showPet = false
            clearTranslateState()
            root?.let { rebuild(it) }
        } else {
            toast("疑似敏感内容，已跳过")
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
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { onClick() }
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
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
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)).apply { setMargins(dp(3), dp(2), dp(3), dp(3)) }
        }
    }

    private fun label(pinyin: String, english: String): String = if (inputMode == InputMode.PINYIN) pinyin else english

    private fun String.shortLabel(maxLength: Int = 22): String {
        val normalized = replace("\n", " ").trim()
        return if (normalized.length <= maxLength) normalized else normalized.take(maxLength - 1) + "…"
    }

    private fun toast(message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show() else Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun activeSkin(): OrbitSkin = SkinManager.keyboardSkin(this, sensitiveMode)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
