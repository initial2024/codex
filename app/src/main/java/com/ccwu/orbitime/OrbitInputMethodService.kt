package com.ccwu.orbitime

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.ceil

class OrbitInputMethodService : InputMethodService() {
    private enum class InputMode { ENGLISH, PINYIN }

    private var inputMode = InputMode.ENGLISH
    private var caps = false
    private var symbols = false
    private var symbolPage = 0
    private var showClips = false
    private var showTranslate = false
    private var showPet = false
    private var showExpressions = false
    private var sensitiveMode = false
    private var pinyinBuffer = ""
    private var englishBuffer = ""

    private var expressionCategoryId = ExpressionLibrary.categories.first().id
    private var expressionPage = 0

    private var translateDirection = TranslatePromptBuilder.Direction.ZH_TO_EN
    private var translateSourceText: String? = null
    private var translateSourceLabel: String? = null
    private var translatePromptPreview: String? = null
    private var offlineTranslationPreview: String? = null
    private var translateLiveMode = false
    private var translateComposeText = ""

    private var petPanelMessage: String? = null
    private var root: LinearLayout? = null
    private var dynamicHost: LinearLayout? = null

    private var lastPinyinQuery = ""
    private var lastPinyinContext = ""
    private var lastPinyinCandidates: List<String> = emptyList()

    private lateinit var store: ClipboardStore
    private lateinit var expressionStore: ExpressionStore
    private lateinit var userDictionary: UserDictionaryStore
    private lateinit var petRepository: PetRepository
    private lateinit var englishImeEngine: EnglishImeEngine
    private lateinit var clipboardManager: ClipboardManager
    private var clipboardListenerAttached = false
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!sensitiveMode) captureVisibleClipboard()
    }

    override fun onCreate() {
        super.onCreate()
        store = ClipboardStore(this)
        expressionStore = ExpressionStore(this)
        userDictionary = UserDictionaryStore(this)
        petRepository = PetRepository(this)
        englishImeEngine = EnglishImeEngine(this)
        CedictTranslationAsset.initialize(this)
        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    }

    override fun onDestroy() {
        detachClipboardListener()
        super.onDestroy()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (!sensitiveMode) {
            attachClipboardListener()
            captureVisibleClipboard()
        }
    }

    override fun onWindowHidden() {
        detachClipboardListener()
        super.onWindowHidden()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        sensitiveMode = PrivacyGuard.isSensitiveInput(attribute)
        invalidatePinyinUiCache()
        if (sensitiveMode) {
            detachClipboardListener()
            showClips = false
            showPet = false
            showExpressions = false
            petPanelMessage = null
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
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        root = layout
        rebuild(layout)
        return layout
    }

    private fun rebuild(layout: LinearLayout) {
        layout.background = OrbitTheme.keyboardBackground(activeSkin())
        layout.removeAllViews()
        buildTopBar(layout)
        val host = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dynamicHost = host
        layout.addView(host)
        refreshDynamicHost()
        buildKeyboard(layout)
    }

    private fun refreshDynamicHost() {
        val host = dynamicHost ?: return
        host.removeAllViews()
        when {
            sensitiveMode -> Unit
            showPet -> buildPetPanel(host)
            showExpressions -> buildExpressionPanel(host)
            showTranslate -> buildTranslatePanel(host)
            showClips -> buildClipBar(host)
            inputMode == InputMode.PINYIN && pinyinBuffer.isNotEmpty() -> buildPinyinCandidateBar(host)
            inputMode == InputMode.ENGLISH && englishBuffer.isNotEmpty() -> buildEnglishCandidateBar(host)
            pinyinBuffer.isEmpty() && englishBuffer.isEmpty() -> buildPhraseBar(host)
        }
    }

    private fun buildTopBar(parent: LinearLayout) {
        val skin = activeSkin()
        if (sensitiveMode) {
            parent.addView(labelBox("🔒 隐私模式 · 工具已隐藏", muted = false, accent = true, warning = true))
            return
        }
        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(chip(if (inputMode == InputMode.PINYIN) "拼音" else "EN", emphasized = true) { toggleInputMode() })
        row.addView(chip(label("切换", "Switch")) { showInputMethodPickerSafely() })
        row.addView(chip(label("粘贴", "Paste")) { pasteClipboard(saveAfterPaste = false) })
        row.addView(chip(if (showClips) label("返回", "Keyboard") else label("剪贴板", "Clips"), emphasized = showClips) {
            if (showClips) showClips = false else {
                commitPendingPinyin(rawFallback = true)
                commitPendingEnglish(rawFallback = true, appendSpace = false)
                showClips = true
                showPet = false
                showExpressions = false
                clearTranslateState()
                captureVisibleClipboard()
            }
            root?.let { rebuild(it) }
        })
        row.addView(chip(if (showTranslate) label("返回", "Keyboard") else label("翻译", "Translate"), emphasized = showTranslate) {
            if (showTranslate) clearTranslateState() else startLiveTranslateMode()
            showExpressions = false
            root?.let { rebuild(it) }
        })
        row.addView(chip(if (showExpressions) label("返回", "Keyboard") else label("表情", "Emoji"), emphasized = showExpressions) {
            if (showExpressions) showExpressions = false else {
                commitPendingPinyin(rawFallback = true)
                commitPendingEnglish(rawFallback = true, appendSpace = false)
                showExpressions = true
                showClips = false
                showPet = false
                clearTranslateState()
                expressionPage = 0
            }
            root?.let { rebuild(it) }
        })
        row.addView(chip(if (showPet) label("返回", "Keyboard") else label("宠物", "Pet"), emphasized = showPet) {
            commitPendingPinyin(rawFallback = true)
            commitPendingEnglish(rawFallback = true, appendSpace = false)
            if (showPet) {
                showPet = false
                petPanelMessage = null
            } else {
                showPet = true
                showClips = false
                showExpressions = false
                clearTranslateState()
                petPanelMessage = null
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
        val outfit = profile.equippedOutfitName ?: "无装扮"
        val next = profile.nextStageExp?.let { "距进化 ${it - profile.exp} EXP" } ?: "成熟阶段"
        val avatar = PetAvatarView(this).apply {
            bind(profile, activeSkin())
            contentDescription = "${profile.petName} ${profile.stageName}"
        }
        parent.addView(avatar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(112)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        parent.addView(labelBox("${profile.petName} · ${profile.species} · Lv.${profile.level} · ${profile.moodLabel} · $next · $outfit", muted = false, accent = true))
        parent.addView(labelBox("${petRepository.localChatLine()}  今日 ${profile.todayTypedChars} 字 · ${profile.stars} Stars${if (!visible) " · 当前隐藏" else ""}", muted = false, accent = false))
        petPanelMessage?.let { parent.addView(labelBox(it.shortLabel(76), muted = false, accent = true)) }

        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(chip("签到", emphasized = true) { petPanelMessage = petRepository.checkIn().message; refreshDynamicHost() })
        row.addView(chip("开蛋") { petPanelMessage = petRepository.adoptRandom().message; refreshDynamicHost() })
        row.addView(chip("切换") { petPanelMessage = petRepository.switchToNextOwned().message; refreshDynamicHost() })
        row.addView(chip("装扮") { petPanelMessage = petRepository.equipNextOutfit().message; refreshDynamicHost() })
        row.addView(chip("图鉴") { petPanelMessage = petRepository.petCatalogLine(); refreshDynamicHost() })
        row.addView(chip("装扮库") { petPanelMessage = petRepository.outfitCatalogLine(); refreshDynamicHost() })
        row.addView(chip(if (visible) "隐藏" else "显示") { petPanelMessage = petRepository.toggleHidden().message; refreshDynamicHost() })
        row.addView(chip("关闭", warning = true) { showPet = false; petPanelMessage = null; root?.let { rebuild(it) } })
        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
    }

    private fun buildExpressionPanel(parent: LinearLayout) {
        val isStickerPage = expressionCategoryId == EXPRESSION_STICKERS
        val isUnicodePage = expressionCategoryId == EXPRESSION_UNICODE
        val recent = expressionStore.recent()
        val category = if (expressionCategoryId == EXPRESSION_RECENT || isStickerPage || isUnicodePage) null else ExpressionLibrary.byId(expressionCategoryId)
        val textItems = when {
            expressionCategoryId == EXPRESSION_RECENT -> recent
            isUnicodePage -> UnicodeEmojiAsset.items(this)
            isStickerPage -> emptyList()
            else -> category?.items.orEmpty()
        }
        val stickerItems = if (isStickerPage) StickerPack.orderedFor(petRepository.profile().petId) else emptyList()
        val totalItems = if (isStickerPage) stickerItems.size else textItems.size
        val pageCount = maxOf(1, ceil(totalItems / EXPRESSIONS_PER_PAGE.toDouble()).toInt())
        if (expressionPage >= pageCount) expressionPage = pageCount - 1
        val title = when {
            expressionCategoryId == EXPRESSION_RECENT -> "最近"
            isUnicodePage -> "Unicode 全部 Emoji"
            isStickerPage -> "宠物贴图"
            else -> category?.title ?: "表情"
        }
        parent.addView(labelBox("表情 · $title · ${expressionPage + 1}/$pageCount", muted = false, accent = true))

        val categoryScroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        categoryRow.addView(chip("最近", emphasized = expressionCategoryId == EXPRESSION_RECENT) { selectExpressionCategory(EXPRESSION_RECENT) })
        categoryRow.addView(chip("😀 全部", emphasized = isUnicodePage) { selectExpressionCategory(EXPRESSION_UNICODE) })
        ExpressionLibrary.categories.forEach { item ->
            categoryRow.addView(chip(item.title, emphasized = expressionCategoryId == item.id) { selectExpressionCategory(item.id) })
        }
        categoryRow.addView(chip("🪐 贴图", emphasized = isStickerPage) { selectExpressionCategory(EXPRESSION_STICKERS) })
        categoryScroller.addView(categoryRow)
        parent.addView(categoryScroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))

        if (totalItems == 0) {
            parent.addView(labelBox(if (expressionCategoryId == EXPRESSION_RECENT) "还没有最近使用的表情。选择上方分类即可开始使用。" else "当前分类暂无内容。", muted = true, accent = false))
        } else if (isStickerPage) {
            buildStickerRows(parent, stickerItems.drop(expressionPage * EXPRESSIONS_PER_PAGE).take(EXPRESSIONS_PER_PAGE))
        } else {
            buildExpressionRows(parent, textItems.drop(expressionPage * EXPRESSIONS_PER_PAGE).take(EXPRESSIONS_PER_PAGE))
        }

        val navScroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val navRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (expressionPage > 0) navRow.addView(chip("上一页") { expressionPage--; refreshDynamicHost() })
        if (expressionPage + 1 < pageCount) navRow.addView(chip("下一页", emphasized = true) { expressionPage++; refreshDynamicHost() })
        if (expressionCategoryId == EXPRESSION_RECENT && recent.isNotEmpty()) {
            navRow.addView(chip("清最近", warning = true) { expressionStore.clear(); expressionPage = 0; refreshDynamicHost() })
        }
        navRow.addView(chip("关闭", warning = true) { showExpressions = false; root?.let { rebuild(it) } })
        navScroller.addView(navRow)
        parent.addView(navScroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
    }

    private fun buildExpressionRows(parent: LinearLayout, items: List<String>) {
        items.chunked(EXPRESSIONS_PER_ROW).forEach { chunk ->
            val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            chunk.forEach { value -> row.addView(expressionChip(value)) }
            scroller.addView(row)
            parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
        }
    }

    private fun buildStickerRows(parent: LinearLayout, items: List<StickerDefinition>) {
        items.chunked(STICKERS_PER_ROW).forEach { chunk ->
            val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            chunk.forEach { sticker ->
                val preview = StickerPreviewView(this).apply {
                    bind(sticker, activeSkin())
                    setOnClickListener { commitSticker(sticker) }
                    setOnLongClickListener {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("Orbit sticker fallback", sticker.fallbackText))
                        toast("已复制备用 Emoji")
                        true
                    }
                    isClickable = true
                    isFocusable = true
                    background = OrbitTheme.rounded(activeSkin().panelAltColor, dp(12).toFloat(), activeSkin().borderColor, dp(1))
                }
                row.addView(preview, LinearLayout.LayoutParams(dp(58), dp(58)).apply { setMargins(dp(3), dp(2), dp(3), dp(2)) })
            }
            scroller.addView(row)
            parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)))
        }
    }

    private fun expressionChip(value: String): TextView {
        val skin = activeSkin()
        val isCompactEmoji = value.codePointCount(0, value.length) <= 4 && value.length <= 12
        return TextView(this).apply {
            text = value
            OrbitTheme.label(this, sizeSp = if (isCompactEmoji) 22f else 13f, bold = isCompactEmoji, skin = skin)
            background = OrbitTheme.rounded(skin.panelAltColor, dp(12).toFloat(), skin.borderColor, dp(1))
            setPadding(dp(if (isCompactEmoji) 12 else 10), 0, dp(if (isCompactEmoji) 12 else 10), 0)
            setOnClickListener { commitExpression(value) }
            setOnLongClickListener {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("Orbit expression", value))
                toast("已复制")
                true
            }
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply { setMargins(dp(3), dp(2), dp(3), dp(2)) }
        }
    }

    private fun selectExpressionCategory(id: String) {
        expressionCategoryId = id
        expressionPage = 0
        refreshDynamicHost()
    }

    private fun commitExpression(value: String) {
        val inputConnection = currentInputConnection ?: return
        inputConnection.commitText(value, 1)
        expressionStore.record(value)
        if (!sensitiveMode) petRepository.recordTypedChars(value.length)
        if (expressionCategoryId == EXPRESSION_RECENT) refreshDynamicHost()
    }

    private fun commitSticker(sticker: StickerDefinition) {
        val inputConnection = currentInputConnection ?: return
        val mimeTypes = currentInputEditorInfo?.contentMimeTypes ?: emptyArray()
        val supportsPng = mimeTypes.any { it == OrbitStickerProvider.MIME_PNG || it == "image/*" || it == "*/*" }
        var committed = false
        if (supportsPng) {
            committed = runCatching {
                val content = InputContentInfo(
                    StickerPack.uri(this, sticker),
                    ClipDescription(sticker.label, arrayOf(OrbitStickerProvider.MIME_PNG)),
                    null,
                )
                inputConnection.commitContent(content, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
            }.getOrDefault(false)
        }
        if (committed) {
            expressionStore.record(sticker.fallbackText)
            petRepository.recordCandidateCommit()
            toast("已发送 ${sticker.label}")
        } else {
            inputConnection.commitText(sticker.fallbackText, 1)
            expressionStore.record(sticker.fallbackText)
            if (!sensitiveMode) petRepository.recordTypedChars(sticker.fallbackText.length)
            toast("当前输入框不支持图片贴图，已使用 Emoji")
        }
    }

    private fun buildTranslatePanel(parent: LinearLayout) {
        updateLiveTranslationPreview()
        val source = currentTranslationSource()
        parent.addView(labelBox("翻译键盘 · ${translateDirection.label} · 本地", muted = false, accent = true))
        parent.addView(labelBox("原文：${source.ifBlank { if (translateDirection == TranslatePromptBuilder.Direction.ZH_TO_EN) "输入中文或连续拼音" else "Type English" }.shortLabel(76)}", muted = source.isBlank(), accent = false))
        val translationLine = offlineTranslationPreview?.let { "译文：${it.shortLabel(90)}" }
            ?: if (source.isBlank()) "输入后自动显示本地译文" else OfflineTranslationPack.unavailableMessage()
        parent.addView(labelBox(translationLine, muted = offlineTranslationPreview == null, accent = offlineTranslationPreview != null))
        if (pinyinBuffer.isNotEmpty()) buildPinyinCandidateBar(parent)
        if (englishBuffer.isNotEmpty()) buildEnglishCandidateBar(parent)

        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (offlineTranslationPreview != null) row.addView(chip("译文上屏", emphasized = true) { insertLiveTranslation() })
        if (source.isNotBlank()) row.addView(chip("原文上屏") { insertLiveSource() })
        row.addView(chip("换方向") { toggleTranslateDirection(regenerate = true) })
        row.addView(chip("前一句") { loadTranslationSource("前一句", readPreviousSentence()) })
        row.addView(chip("选中文本") { loadTranslationSource("选中文本", readSelectedText()) })
        row.addView(chip("剪贴板") { loadTranslationSource("剪贴板", readClipboardText()) })
        if (source.isNotBlank() && offlineTranslationPreview == null) row.addView(chip("复制提示词") { copyTranslatePrompt() })
        row.addView(chip("清空", warning = true) { clearLiveTranslationText(); refreshDynamicHost() })
        row.addView(chip("关闭", warning = true) { clearTranslateState(); root?.let { rebuild(it) } })
        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
    }

    private fun buildClipBar(parent: LinearLayout) {
        val pinned = store.pinned()
        val recent = store.recent()
        parent.addView(labelBox("剪贴板 · 最近 1 小时 · 长按条目固定/取消固定", muted = false, accent = true))
        val actions = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        actionRow.addView(chip("同步当前", emphasized = true) { saveClipboard() })
        actionRow.addView(chip("清最近") { store.clearRecent(); refreshDynamicHost() })
        actionRow.addView(chip("清空全部", warning = true) { store.clear(); refreshDynamicHost() })
        actions.addView(actionRow)
        parent.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
        if (pinned.isNotEmpty()) buildClipEntriesRow(parent, "固定", pinned)
        if (recent.isNotEmpty()) buildClipEntriesRow(parent, "最近", recent)
        if (pinned.isEmpty() && recent.isEmpty()) parent.addView(labelBox("暂无剪贴板历史。键盘可见期间复制的非敏感文字会保存在本机。", muted = true, accent = false))
    }

    private fun buildClipEntriesRow(parent: LinearLayout, title: String, entries: List<ClipboardStore.ClipEntry>) {
        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(labelBox(title, muted = false, accent = false))
        entries.take(20).forEach { entry ->
            row.addView(chip((if (entry.pinned) "📌 " else "") + entry.content.shortLabel(28), onLongClick = {
                store.togglePin(entry.content)
                refreshDynamicHost()
            }) {
                store.markUsed(entry.content)
                commitDirectText(entry.content)
            })
        }
        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
    }

    private fun buildPinyinCandidateBar(parent: LinearLayout) {
        val candidates = candidatesForCurrentPinyin()
        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(labelBox("拼音：${pinyinBuffer.shortLabel(40)}", muted = false, accent = true))
        candidates.forEachIndexed { index, candidate -> row.addView(chip(candidate, emphasized = index == 0) { commitPinyinCandidate(candidate) }) }
        row.addView(chip("清空", warning = true) { clearPinyinComposition(); refreshDynamicHost() })
        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
    }

    private fun buildEnglishCandidateBar(parent: LinearLayout) {
        val candidates = candidatesForCurrentEnglish()
        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(labelBox("word: ${englishBuffer.shortLabel(40)}", muted = false, accent = true))
        candidates.forEachIndexed { index, candidate -> row.addView(chip(candidate, emphasized = index == 0) { commitEnglishCandidate(candidate, appendSpace = false) }) }
        row.addView(chip("clear", warning = true) { clearEnglishComposition(); refreshDynamicHost() })
        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
    }

    private fun buildPhraseBar(parent: LinearLayout) {
        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val profile = petRepository.profile()
        if (!sensitiveMode && profile.displayMode != PetRepository.DISPLAY_HIDDEN) {
            val miniPet = PetAvatarView(this).apply {
                bind(profile, activeSkin())
                contentDescription = "打开宠物 ${profile.petName}"
                setOnClickListener {
                    showPet = true
                    showClips = false
                    showExpressions = false
                    clearTranslateState()
                    root?.let { rebuild(it) }
                }
                isClickable = true
            }
            row.addView(miniPet, LinearLayout.LayoutParams(dp(64), dp(34)).apply { setMargins(dp(2), 0, dp(4), 0) })
        }
        val phrases = if (inputMode == InputMode.PINYIN) TemplateLibrary.quickPhrasesForPinyin() else TemplateLibrary.quickPhrasesForEnglish()
        phrases.forEach { phrase -> row.addView(chip(phrase.shortLabel()) { commitDirectText(phrase) }) }
        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
    }

    private fun buildKeyboard(parent: LinearLayout) {
        val rows = if (symbols) symbolRows() else letterRows()
        rows.forEach { rowKeys ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
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

    private fun symbolRows(): List<List<String>> = SymbolLibrary.page(symbolPage).rows

    private fun keyView(rawKey: String): TextView {
        val skin = activeSkin()
        val display = when {
            rawKey == "符号" -> SymbolLibrary.page(symbolPage).title
            rawKey == "space" && showTranslate -> when {
                inputMode == InputMode.PINYIN && pinyinBuffer.isNotEmpty() -> "选词"
                inputMode == InputMode.ENGLISH && englishBuffer.isNotEmpty() -> "select"
                else -> label("翻译输入", "translate")
            }
            rawKey == "space" -> when {
                inputMode == InputMode.PINYIN && pinyinBuffer.isNotEmpty() -> "选词"
                inputMode == InputMode.ENGLISH && englishBuffer.isNotEmpty() -> "select"
                else -> label("空格", "space")
            }
            rawKey == "↵" -> if (showTranslate) label("译文", "Translate") else label("回车", "Enter")
            inputMode == InputMode.ENGLISH && rawKey.length == 1 && rawKey[0].isLetter() && caps -> rawKey.uppercase()
            else -> rawKey
        }
        val longPress = if (!symbols) SymbolLibrary.longPressFor(rawKey) else null
        val visualText: CharSequence = if (longPress == null) display else {
            SpannableString("$display $longPress").apply {
                val start = display.length + 1
                setSpan(RelativeSizeSpan(0.48f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(SuperscriptSpan(), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return TextView(this).apply {
            text = visualText
            OrbitTheme.label(this, sizeSp = if (rawKey == "space" || rawKey == "↵" || rawKey == "符号") 13f else 18f, bold = rawKey.length == 1, skin = skin)
            background = OrbitTheme.rounded(if (isControlKey(rawKey)) skin.controlKeyColor else skin.keyColor, dp(12).toFloat(), if (isControlKey(rawKey)) skin.accentColor else skin.borderColor, dp(1))
            setOnClickListener { handleKey(rawKey) }
            if (longPress != null) setOnLongClickListener { handleLongPress(rawKey); true }
            isClickable = true
            isFocusable = true
            minHeight = dp(38)
        }
    }

    private fun keyLayoutParams(key: String): LinearLayout.LayoutParams {
        val weight = when (key) {
            "space" -> 4.6f
            "⇧", "⌫", "123", "ABC", "↵", "符号" -> 1.45f
            else -> 1f
        }
        return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply { setMargins(dp(3), dp(2), dp(3), dp(2)) }
    }

    private fun isControlKey(key: String): Boolean = key in setOf("⇧", "⌫", "123", "ABC", "↵", "space", "符号")

    private fun handleKey(rawKey: String) {
        when (rawKey) {
            "⇧" -> { if (inputMode == InputMode.ENGLISH) caps = !caps; root?.let { rebuild(it) } }
            "⌫" -> handleBackspace()
            "123" -> { commitPendingForControl(); showExpressions = false; symbols = true; symbolPage = 0; root?.let { rebuild(it) } }
            "ABC" -> { showExpressions = false; symbols = false; root?.let { rebuild(it) } }
            "符号" -> { symbolPage = SymbolLibrary.nextPage(symbolPage); root?.let { rebuild(it) } }
            "space" -> handleSpace()
            "↵" -> handleEnter()
            else -> handlePrintableKey(rawKey)
        }
    }

    private fun handleLongPress(rawKey: String) {
        val alternate = SymbolLibrary.longPressFor(rawKey) ?: return
        commitPendingForControl()
        commitAuxiliaryText(alternate)
    }

    private fun commitAuxiliaryText(text: String) {
        if (showTranslate && translateLiveMode) {
            translateComposeText += text
            updateLiveTranslationPreview()
        } else {
            currentInputConnection?.commitText(text, 1)
            if (!sensitiveMode) petRepository.recordTypedChars(text.length)
        }
        refreshDynamicHost()
    }

    private fun commitPendingForControl() {
        if (showTranslate) {
            if (pinyinBuffer.isNotEmpty()) commitPendingPinyin(rawFallback = false)
            if (englishBuffer.isNotEmpty()) commitPendingEnglish(rawFallback = false, appendSpace = false)
        } else {
            commitPendingPinyin(rawFallback = true)
            commitPendingEnglish(rawFallback = true, appendSpace = false)
        }
    }

    private fun handlePrintableKey(rawKey: String) {
        if (rawKey.length == 1 && rawKey[0].isLetter() && inputMode == InputMode.PINYIN && !symbols) { appendPinyin(rawKey); return }
        if (rawKey.length == 1 && rawKey[0].isLetter() && inputMode == InputMode.ENGLISH && !symbols) { appendEnglish(rawKey); return }
        commitPendingForControl()
        showExpressions = false
        val mapped = mapPrintableText(rawKey)
        if (showTranslate && translateLiveMode) {
            translateComposeText += mapped
            updateLiveTranslationPreview()
            refreshDynamicHost()
        } else {
            currentInputConnection?.commitText(mapped, 1)
            if (!sensitiveMode) petRepository.recordTypedChars(mapped.length)
            refreshDynamicHost()
        }
    }

    private fun mapPrintableText(rawKey: String): String {
        if (inputMode == InputMode.ENGLISH && rawKey.length == 1 && rawKey[0].isLetter() && caps) return rawKey.uppercase()
        if (inputMode == InputMode.PINYIN && !symbols) return when (rawKey) { "," -> "，"; "." -> "。"; else -> rawKey }
        return rawKey
    }

    private fun handleBackspace() {
        val inputConnection = currentInputConnection ?: return
        if (inputMode == InputMode.PINYIN && pinyinBuffer.isNotEmpty()) {
            pinyinBuffer = pinyinBuffer.dropLast(1)
            invalidatePinyinUiCache()
            if (pinyinBuffer.isEmpty()) inputConnection.commitText("", 1) else inputConnection.setComposingText(pinyinBuffer, 1)
            refreshDynamicHost()
            return
        }
        if (inputMode == InputMode.ENGLISH && englishBuffer.isNotEmpty()) {
            englishBuffer = englishBuffer.dropLast(1)
            if (englishBuffer.isEmpty()) inputConnection.commitText("", 1) else inputConnection.setComposingText(englishBuffer, 1)
            refreshDynamicHost()
            return
        }
        if (showTranslate && translateLiveMode && translateComposeText.isNotEmpty()) {
            translateComposeText = translateComposeText.dropLast(1)
            updateLiveTranslationPreview()
            refreshDynamicHost()
            return
        }
        if (showExpressions) showExpressions = false
        inputConnection.deleteSurroundingText(1, 0)
        refreshDynamicHost()
    }

    private fun handleSpace() {
        if (inputMode == InputMode.PINYIN && pinyinBuffer.isNotEmpty()) { commitPendingPinyin(rawFallback = false); return }
        if (inputMode == InputMode.ENGLISH && englishBuffer.isNotEmpty()) { commitPendingEnglish(rawFallback = false, appendSpace = true); return }
        if (showTranslate && translateLiveMode) {
            if (translateDirection == TranslatePromptBuilder.Direction.EN_TO_ZH) translateComposeText += " "
            refreshDynamicHost()
            return
        }
        showExpressions = false
        currentInputConnection?.commitText(" ", 1)
        if (!sensitiveMode) petRepository.recordTypedChars(1)
        refreshDynamicHost()
    }

    private fun handleEnter() {
        if (showTranslate && translateLiveMode) {
            if (pinyinBuffer.isNotEmpty()) { commitPendingPinyin(rawFallback = false); return }
            if (englishBuffer.isNotEmpty()) { commitPendingEnglish(rawFallback = false, appendSpace = false); return }
            if (offlineTranslationPreview != null) { insertLiveTranslation(); return }
            toast(OfflineTranslationPack.unavailableMessage())
            return
        }
        showExpressions = false
        commitPendingPinyin(rawFallback = true)
        commitPendingEnglish(rawFallback = true, appendSpace = false)
        sendEnterKey()
        refreshDynamicHost()
    }

    private fun appendPinyin(letter: String) {
        val normalized = PinyinDictionary.normalize(pinyinBuffer + letter)
        if (normalized.length > MAX_PINYIN_BUFFER) { toast("长句拼音已达到上限"); return }
        pinyinBuffer = normalized
        invalidatePinyinUiCache()
        currentInputConnection?.setComposingText(pinyinBuffer, 1)
        showClips = false
        showPet = false
        showExpressions = false
        if (!sensitiveMode) petRepository.recordTypedChars(1)
        refreshDynamicHost()
    }

    private fun appendEnglish(letter: String) {
        val text = if (caps) letter.uppercase() else letter.lowercase()
        if (englishBuffer.length + text.length > MAX_ENGLISH_BUFFER) { toast("word too long"); return }
        englishBuffer += text
        currentInputConnection?.setComposingText(englishBuffer, 1)
        showClips = false
        showPet = false
        showExpressions = false
        if (!sensitiveMode) petRepository.recordTypedChars(1)
        refreshDynamicHost()
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
        val context = readCandidateContextBeforeCursor().orEmpty()
        if (pinyinBuffer == lastPinyinQuery && context == lastPinyinContext && lastPinyinCandidates.isNotEmpty()) return lastPinyinCandidates
        val boosted = PinyinSentenceDictionary.candidatesFor(pinyinBuffer)
        val staticCandidates = (boosted + PinyinDictionary.candidatesFor(pinyinBuffer)).distinct()
        val result = userDictionary.candidatesFor(pinyinBuffer, staticCandidates, contextBeforeCursor = context)
        lastPinyinQuery = pinyinBuffer
        lastPinyinContext = context
        lastPinyinCandidates = result
        return result
    }

    private fun exactCandidatesForCurrentPinyin(): List<String> {
        val staticCandidates = (PinyinSentenceDictionary.exactCandidatesFor(pinyinBuffer) + PinyinDictionary.exactCandidatesFor(pinyinBuffer)).distinct()
        return userDictionary.exactCandidatesFor(pinyinBuffer, staticCandidates, contextBeforeCursor = readCandidateContextBeforeCursor())
    }

    private fun readCandidateContextBeforeCursor(): String? {
        val raw = currentInputConnection?.getTextBeforeCursor(96, 0)?.toString() ?: return null
        val withoutComposition = if (pinyinBuffer.isNotEmpty() && raw.endsWith(pinyinBuffer)) raw.dropLast(pinyinBuffer.length) else raw
        return withoutComposition.takeLast(64)
    }

    private fun invalidatePinyinUiCache() {
        lastPinyinQuery = ""
        lastPinyinContext = ""
        lastPinyinCandidates = emptyList()
    }

    private fun candidatesForCurrentEnglish(): List<String> = englishImeEngine.candidatesFor(englishBuffer)

    /** Commit candidate directly over the active composing region. Never finish raw Pinyin first. */
    private fun commitPinyinCandidate(candidate: String) {
        val inputConnection = currentInputConnection ?: return
        val learnedPinyin = pinyinBuffer
        pinyinBuffer = ""
        invalidatePinyinUiCache()
        if (showTranslate && translateLiveMode) {
            inputConnection.commitText("", 1)
            translateComposeText += candidate
            updateLiveTranslationPreview()
        } else {
            inputConnection.commitText(candidate, 1)
        }
        if (!sensitiveMode) {
            userDictionary.learn(learnedPinyin, candidate)
            petRepository.recordCandidateCommit()
            petRepository.recordTypedChars(candidate.length)
        }
        refreshDynamicHost()
    }

    private fun commitEnglishCandidate(candidate: String, appendSpace: Boolean) {
        val inputConnection = currentInputConnection ?: return
        val text = if (appendSpace) "$candidate " else candidate
        englishBuffer = ""
        if (showTranslate && translateLiveMode) {
            inputConnection.commitText("", 1)
            translateComposeText += text
            updateLiveTranslationPreview()
        } else {
            inputConnection.commitText(text, 1)
        }
        if (!sensitiveMode) petRepository.recordTypedChars(text.length)
        refreshDynamicHost()
    }

    private fun clearPinyinComposition() {
        if (pinyinBuffer.isNotEmpty()) currentInputConnection?.commitText("", 1)
        pinyinBuffer = ""
        invalidatePinyinUiCache()
    }

    private fun clearEnglishComposition() {
        if (englishBuffer.isNotEmpty()) currentInputConnection?.commitText("", 1)
        englishBuffer = ""
    }

    private fun toggleInputMode() {
        commitPendingForControl()
        inputMode = if (inputMode == InputMode.PINYIN) InputMode.ENGLISH else InputMode.PINYIN
        if (showTranslate) translateDirection = if (inputMode == InputMode.PINYIN) TranslatePromptBuilder.Direction.ZH_TO_EN else TranslatePromptBuilder.Direction.EN_TO_ZH
        symbols = false
        caps = false
        showClips = false
        showPet = false
        showExpressions = false
        petPanelMessage = null
        if (!showTranslate) clearTranslateState() else updateLiveTranslationPreview()
        root?.let { rebuild(it) }
    }

    private fun sendEnterKey() {
        val inputConnection = currentInputConnection ?: return
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    private fun showInputMethodPickerSafely() {
        try { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        catch (_: Exception) { toast("请从系统输入法按钮切换") }
    }

    private fun commitDirectText(text: String) {
        commitPendingPinyin(rawFallback = true)
        commitPendingEnglish(rawFallback = true, appendSpace = false)
        clearTranslateState()
        showPet = false
        showExpressions = false
        petPanelMessage = null
        currentInputConnection?.commitText(text, 1)
        if (!sensitiveMode) petRepository.recordTypedChars(text.length)
        root?.let { rebuild(it) }
    }

    private fun startLiveTranslateMode() {
        if (sensitiveMode) return
        val seed = when {
            inputMode == InputMode.PINYIN && pinyinBuffer.isNotEmpty() -> candidatesForCurrentPinyin().firstOrNull().orEmpty()
            inputMode == InputMode.ENGLISH && englishBuffer.isNotEmpty() -> candidatesForCurrentEnglish().firstOrNull().orEmpty()
            else -> ""
        }
        clearPinyinComposition()
        clearEnglishComposition()
        translateDirection = if (inputMode == InputMode.PINYIN) TranslatePromptBuilder.Direction.ZH_TO_EN else TranslatePromptBuilder.Direction.EN_TO_ZH
        translateComposeText = seed
        translateLiveMode = true
        showTranslate = true
        showClips = false
        showPet = false
        showExpressions = false
        petPanelMessage = null
        updateLiveTranslationPreview()
    }

    private fun currentTranslationSource(): String {
        if (!translateLiveMode) return translateSourceText.orEmpty()
        val pending = when {
            translateDirection == TranslatePromptBuilder.Direction.ZH_TO_EN && pinyinBuffer.isNotEmpty() -> candidatesForCurrentPinyin().firstOrNull().orEmpty()
            translateDirection == TranslatePromptBuilder.Direction.EN_TO_ZH && englishBuffer.isNotEmpty() -> englishBuffer
            else -> ""
        }
        return translateComposeText + pending
    }

    private fun updateLiveTranslationPreview() {
        if (!showTranslate || !translateLiveMode) return
        val source = currentTranslationSource().trim()
        translateSourceText = source.ifBlank { null }
        translateSourceLabel = "翻译键盘"
        offlineTranslationPreview = if (source.isBlank()) null else OfflineTranslationPack.translateOrNull(source, translateDirection)?.translatedText
        translatePromptPreview = if (source.isBlank()) null else TranslatePromptBuilder.build(source, translateDirection)
    }

    private fun loadTranslationSource(label: String, rawSource: String?) {
        val source = rawSource?.trim().orEmpty()
        if (!TranslatePromptBuilder.canUseSource(source)) {
            toast(if (source.length > 1200) TranslatePromptBuilder.sourceTooLongMessage() else TranslatePromptBuilder.unsafeSourceMessage())
            return
        }
        clearPinyinComposition()
        clearEnglishComposition()
        translateComposeText = source
        translateSourceLabel = label
        translateLiveMode = true
        showTranslate = true
        showClips = false
        showPet = false
        showExpressions = false
        updateLiveTranslationPreview()
        refreshDynamicHost()
    }

    private fun toggleTranslateDirection(regenerate: Boolean) {
        translateDirection = if (translateDirection == TranslatePromptBuilder.Direction.ZH_TO_EN) TranslatePromptBuilder.Direction.EN_TO_ZH else TranslatePromptBuilder.Direction.ZH_TO_EN
        inputMode = if (translateDirection == TranslatePromptBuilder.Direction.ZH_TO_EN) InputMode.PINYIN else InputMode.ENGLISH
        clearPinyinComposition()
        clearEnglishComposition()
        showExpressions = false
        if (regenerate) updateLiveTranslationPreview()
        root?.let { rebuild(it) }
    }

    private fun clearLiveTranslationText() {
        clearPinyinComposition()
        clearEnglishComposition()
        translateComposeText = ""
        translateSourceText = null
        translatePromptPreview = null
        offlineTranslationPreview = null
    }

    private fun insertLiveTranslation() {
        updateLiveTranslationPreview()
        val text = offlineTranslationPreview ?: return
        currentInputConnection?.commitText(text, 1)
        if (!sensitiveMode) {
            petRepository.recordTranslatePrompt()
            petRepository.recordTypedChars(text.length)
        }
        clearLiveTranslationText()
        refreshDynamicHost()
    }

    private fun insertLiveSource() {
        val text = currentTranslationSource().trim()
        if (text.isBlank()) return
        currentInputConnection?.commitText(text, 1)
        if (!sensitiveMode) petRepository.recordTypedChars(text.length)
        clearLiveTranslationText()
        refreshDynamicHost()
    }

    private fun copyTranslatePrompt() {
        val prompt = translatePromptPreview ?: return
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Orbit Translate Prompt", prompt))
        toast("提示词已复制")
    }

    private fun clearTranslateState() {
        showTranslate = false
        translateLiveMode = false
        translateComposeText = ""
        translateSourceText = null
        translateSourceLabel = null
        translatePromptPreview = null
        offlineTranslationPreview = null
    }

    private fun readSelectedText(): String? = currentInputConnection?.getSelectedText(0)?.toString()

    private fun readPreviousSentence(): String? {
        val text = currentInputConnection?.getTextBeforeCursor(360, 0)?.toString() ?: return null
        return extractLastSentence(text)
    }

    private fun extractLastSentence(raw: String): String? {
        val cleaned = raw.trim().trimEnd('。', '！', '？', '.', '!', '?', '\n', '\r', ' ', '\t')
        if (cleaned.isBlank()) return null
        val lastBreak = cleaned.indexOfLast { it == '。' || it == '！' || it == '？' || it == '.' || it == '!' || it == '?' || it == '\n' || it == '\r' }
        return cleaned.substring(lastBreak + 1).trim().ifBlank { null }
    }

    private fun attachClipboardListener() {
        if (clipboardListenerAttached) return
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        clipboardListenerAttached = true
    }

    private fun detachClipboardListener() {
        if (!clipboardListenerAttached || !this::clipboardManager.isInitialized) return
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        clipboardListenerAttached = false
    }

    private fun captureVisibleClipboard() {
        if (sensitiveMode || !this::clipboardManager.isInitialized) return
        readClipboardText()?.let { store.capture(it) }
        if (showClips) refreshDynamicHost()
    }

    private fun pasteClipboard(saveAfterPaste: Boolean) {
        val text = readClipboardText()
        if (text.isNullOrBlank()) { toast("剪贴板为空"); return }
        if (!sensitiveMode) store.capture(text)
        commitDirectText(text)
        if (saveAfterPaste && !sensitiveMode) store.capture(text)
    }

    private fun saveClipboard() {
        if (sensitiveMode) { toast("隐私模式"); return }
        val text = readClipboardText()
        if (text.isNullOrBlank()) { toast("剪贴板为空"); return }
        if (store.capture(text)) {
            petRepository.recordClipSave()
            toast("已同步到本机剪贴板历史")
            refreshDynamicHost()
        } else toast("疑似敏感内容，已跳过")
    }

    private fun readClipboardText(): String? {
        val clip = clipboardManager.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()
    }

    private fun chip(
        text: String,
        emphasized: Boolean = false,
        warning: Boolean = false,
        onLongClick: (() -> Unit)? = null,
        onClick: () -> Unit,
    ): TextView {
        val skin = activeSkin()
        return TextView(this).apply {
            this.text = text
            OrbitTheme.label(this, sizeSp = 13f, bold = true, skin = skin)
            val stroke = when { warning -> skin.warningColor; emphasized -> skin.accentColor; else -> skin.borderColor }
            setTextColor(when { warning -> skin.warningColor; emphasized -> skin.accentColor; else -> skin.textColor })
            background = OrbitTheme.rounded(skin.panelAltColor, dp(16).toFloat(), stroke, dp(1))
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { onClick() }
            onLongClick?.let { callback -> setOnLongClickListener { callback(); true } }
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
            background = OrbitTheme.rounded(skin.panelColor, dp(12).toFloat(), when { warning -> skin.warningColor; accent -> skin.accentColor; else -> skin.borderColor }, dp(1))
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        else Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun activeSkin(): OrbitSkin = SkinManager.keyboardSkin(this, sensitiveMode)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_PINYIN_BUFFER = 192
        private const val MAX_ENGLISH_BUFFER = 96
        private const val EXPRESSION_RECENT = "recent"
        private const val EXPRESSION_UNICODE = "unicode_all"
        private const val EXPRESSION_STICKERS = "stickers"
        private const val EXPRESSIONS_PER_ROW = 12
        private const val STICKERS_PER_ROW = 8
        private const val EXPRESSIONS_PER_PAGE = 36
    }
}
