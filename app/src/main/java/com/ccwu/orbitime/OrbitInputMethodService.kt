package com.ccwu.orbitime

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
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
    private enum class ShiftState { OFF, ONCE, LOCKED }

    private var inputMode = InputMode.PINYIN
    private var shiftState = ShiftState.OFF
    private var lastShiftTapAt = 0L
    private var symbols = false
    private var symbolPage = 0
    private var showClips = false
    private var showTranslate = false
    private var showPet = false
    private var showPetCatalog = false
    private var petCatalogPage = 0
    private var showMoreTools = false
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
    private var contextTranslationBundle: ContextTranslationEngine.Bundle? = null
    private var longFormTranslationPreview: LongFormTranslationEngine.Result? = null
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
    private lateinit var quickPhraseStore: QuickPhraseStore
    private lateinit var speechController: ImeSpeechController
    private lateinit var clipboardManager: ClipboardManager
    private var clipboardListenerAttached = false
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!sensitiveMode) captureVisibleClipboard()
    }

    override fun onCreate() {
        super.onCreate()
        inputMode = if (ImePreferences.inputMode(this) == ImePreferences.MODE_ENGLISH) InputMode.ENGLISH else InputMode.PINYIN
        store = ClipboardStore(this)
        expressionStore = ExpressionStore(this)
        userDictionary = UserDictionaryStore(this)
        petRepository = PetRepository(this)
        englishImeEngine = EnglishImeEngine(this)
        quickPhraseStore = QuickPhraseStore(this)
        speechController = ImeSpeechController(this)
        CedictTranslationAsset.initialize(this)
        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    }

    override fun onDestroy() {
        detachClipboardListener()
        if (this::speechController.isInitialized) speechController.shutdown()
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
        if (this::speechController.isInitialized) speechController.cancelCapture()
        super.onWindowHidden()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        sensitiveMode = PrivacyGuard.isSensitiveInput(attribute)
        invalidatePinyinUiCache()
        if (sensitiveMode) {
            detachClipboardListener()
            if (this::speechController.isInitialized) speechController.cancelCapture()
            showClips = false
            showPet = false
            showPetCatalog = false
            showMoreTools = false
            showExpressions = false
            petPanelMessage = null
            clearPinyinComposition()
            clearEnglishComposition()
            clearTranslateState()
        }
        root?.let { rebuild(it) }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (newSelStart != newSelEnd && (pinyinBuffer.isNotEmpty() || englishBuffer.isNotEmpty())) {
            resetInternalCompositionState()
            refreshDynamicHost()
        }
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
            showPetCatalog -> buildPetCatalogPanel(host)
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
            parent.addView(labelBox("🔒 隐私模式 · 语音/朗读/剪贴板/宠物工具已隐藏", muted = false, accent = true, warning = true))
            return
        }

        val primaryScroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val primary = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        primary.addView(chip(if (inputMode == InputMode.PINYIN) "拼音" else "EN", emphasized = true) { toggleInputMode() })
        primary.addView(chip(if (showClips) label("返回", "Keyboard") else label("剪贴板", "Clips"), emphasized = showClips) {
            if (showClips) showClips = false else {
                commitPendingPinyin(rawFallback = true)
                commitPendingEnglish(rawFallback = true, appendSpace = false)
                showClips = true
                showPet = false
                showPetCatalog = false
                showExpressions = false
                clearTranslateState()
                captureVisibleClipboard()
            }
            root?.let { rebuild(it) }
        })
        primary.addView(chip(if (showExpressions) label("返回", "Keyboard") else label("表情", "Emoji"), emphasized = showExpressions) {
            if (showExpressions) showExpressions = false else {
                commitPendingPinyin(rawFallback = true)
                commitPendingEnglish(rawFallback = true, appendSpace = false)
                showExpressions = true
                showClips = false
                showPet = false
                showPetCatalog = false
                clearTranslateState()
                expressionPage = 0
            }
            root?.let { rebuild(it) }
        })
        primary.addView(chip(if (speechController.isRecording()) label("停止语音", "Stop voice") else label("语音", "Voice"), emphasized = speechController.isRecording() || speechController.isBusy()) {
            handleLocalVoiceInput()
        })
        primary.addView(chip(if (showMoreTools) label("收起", "Less") else label("更多", "More"), emphasized = showMoreTools) {
            showMoreTools = !showMoreTools
            root?.let { rebuild(it) }
        })
        primaryScroller.setBackgroundColor(skin.backgroundColor)
        primaryScroller.addView(primary)
        parent.addView(primaryScroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))

        if (!showMoreTools) return
        val moreScroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val more = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        more.addView(chip(if (showTranslate) label("返回", "Keyboard") else label("翻译", "Translate"), emphasized = showTranslate) {
            if (showTranslate) clearTranslateState() else startLiveTranslateMode()
            showExpressions = false
            showPetCatalog = false
            root?.let { rebuild(it) }
        })
        more.addView(chip(if (showPet || showPetCatalog) label("返回", "Keyboard") else label("宠物", "Pet"), emphasized = showPet || showPetCatalog) {
            commitPendingPinyin(rawFallback = true)
            commitPendingEnglish(rawFallback = true, appendSpace = false)
            if (showPet || showPetCatalog) {
                showPet = false
                showPetCatalog = false
                petPanelMessage = null
            } else {
                showPet = true
                showPetCatalog = false
                showClips = false
                showExpressions = false
                clearTranslateState()
                petPanelMessage = null
            }
            root?.let { rebuild(it) }
        })
        more.addView(chip(label("朗读", "Read")) { speakCurrentText(cloned = false) })
        more.addView(chip(label("音色", "Clone")) { speakCurrentText(cloned = true) })
        more.addView(chip(label("切换", "Switch")) { showInputMethodPickerSafely() })
        more.addView(chip(label("粘贴", "Paste")) { pasteClipboard(saveAfterPaste = false) })
        moreScroller.addView(more)
        parent.addView(moreScroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
    }

    private fun buildPetPanel(parent: LinearLayout) {
        val profile = petRepository.profile()
        val visible = profile.displayMode != PetRepository.DISPLAY_HIDDEN
        val outfit = profile.equippedOutfitName ?: "无装扮"
        val next = profile.nextStageExp?.let { "距进化 ${it - profile.exp} EXP" } ?: "成熟阶段"
        val avatar = PetAvatarV21View(this).apply {
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
        row.addView(chip("图鉴") { petCatalogPage = 0; showPet = false; showPetCatalog = true; refreshDynamicHost() })
        row.addView(chip("装扮库") { petPanelMessage = petRepository.outfitCatalogLine(); refreshDynamicHost() })
        row.addView(chip(if (visible) "隐藏" else "显示") { petPanelMessage = petRepository.toggleHidden().message; refreshDynamicHost() })
        row.addView(chip("关闭", warning = true) { showPet = false; showPetCatalog = false; petPanelMessage = null; root?.let { rebuild(it) } })
        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
    }

    private fun buildPetCatalogPanel(parent: LinearLayout) {
        val entries = petRepository.petCatalogEntries()
        val pageSize = 4
        val pageCount = maxOf(1, ceil(entries.size / pageSize.toDouble()).toInt())
        petCatalogPage = petCatalogPage.coerceIn(0, pageCount - 1)
        parent.addView(labelBox("宠物图鉴 · ${petCatalogPage + 1}/$pageCount", muted = false, accent = true))
        entries.drop(petCatalogPage * pageSize).take(pageSize).forEach { entry ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(PetAvatarV21View(this).apply {
                bind(entry.profile, activeSkin())
                contentDescription = entry.profile.petName
            }, LinearLayout.LayoutParams(dp(74), dp(54)).apply { setMargins(dp(2), dp(2), dp(6), dp(2)) })
            val state = when {
                entry.current -> "✓ 当前"
                entry.owned -> "✓ 已拥有"
                entry.proOnly && !ProGate.isProUnlocked(this) -> "🔒 Pro"
                else -> "未解锁"
            }
            row.addView(labelBox("${entry.profile.petName} · ${entry.profile.species} · $state", muted = false, accent = entry.current), LinearLayout.LayoutParams(0, dp(34), 1f))
            parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))
        }
        val nav = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (petCatalogPage > 0) buttons.addView(chip("上一页") { petCatalogPage--; refreshDynamicHost() })
        if (petCatalogPage + 1 < pageCount) buttons.addView(chip("下一页", emphasized = true) { petCatalogPage++; refreshDynamicHost() })
        buttons.addView(chip("返回宠物") { showPetCatalog = false; showPet = true; refreshDynamicHost() })
        buttons.addView(chip("关闭", warning = true) { showPetCatalog = false; showPet = false; root?.let { rebuild(it) } })
        nav.addView(buttons)
        parent.addView(nav, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
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
                        if (copyStickerImageToClipboard(sticker)) {
                            toast("图片贴图已复制，可在微信/QQ输入框长按粘贴")
                        } else {
                            clipboardManager.setPrimaryClip(ClipData.newPlainText("Orbit sticker fallback", sticker.fallbackText))
                            toast("图片复制失败，已复制备用 Emoji")
                        }
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
        resetInternalCompositionState()
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
            return
        }

        if (copyStickerImageToClipboard(sticker)) {
            expressionStore.record(sticker.fallbackText)
            petRepository.recordCandidateCommit()
            toast("目标 App 不支持 IME 图片直发；贴图已复制，请在微信/QQ输入框长按粘贴")
        } else {
            resetInternalCompositionState()
            inputConnection.commitText(sticker.fallbackText, 1)
            expressionStore.record(sticker.fallbackText)
            if (!sensitiveMode) petRepository.recordTypedChars(sticker.fallbackText.length)
            toast("图片贴图不可用，已使用 Emoji")
        }
    }

    private fun copyStickerImageToClipboard(sticker: StickerDefinition): Boolean = runCatching {
        StickerRenderer.fileFor(this, sticker)
        val uri = StickerPack.uri(this, sticker)
        val targetPackage = currentInputEditorInfo?.packageName
        if (!targetPackage.isNullOrBlank()) {
            grantUriPermission(targetPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        clipboardManager.setPrimaryClip(ClipData.newUri(contentResolver, sticker.label, uri))
        true
    }.getOrDefault(false)

    private fun buildTranslatePanel(parent: LinearLayout) {
        updateLiveTranslationPreview()
        val source = currentTranslationSource()
        val contextAvailable = TranslationSettings.isContextTranslationAvailable(this)
        val contextEnabled = TranslationSettings.isContextTranslationEnabled(this)
        val modeLabel = when {
            longFormTranslationPreview != null -> "全文"
            contextEnabled -> "上下文"
            else -> "单句"
        }
        parent.addView(labelBox("翻译键盘 · ${translateDirection.label} · 本地 · $modeLabel", muted = false, accent = true))
        parent.addView(labelBox("原文：${source.ifBlank { if (translateDirection == TranslatePromptBuilder.Direction.ZH_TO_EN) "输入中文或连续拼音" else "Type English" }.shortLabel(76)}", muted = source.isBlank(), accent = false))
        val translationLine = offlineTranslationPreview?.let { "译文：${it.shortLabel(90)}" }
            ?: if (source.isBlank()) "输入后自动显示本地译文" else OfflineTranslationPack.unavailableMessage()
        parent.addView(labelBox(translationLine, muted = offlineTranslationPreview == null, accent = offlineTranslationPreview != null))

        longFormTranslationPreview?.let { result ->
            parent.addView(labelBox("长文：${result.sourceChars} 字 · 已翻译 ${result.translatedSegments} 段 · 未覆盖 ${result.uncoveredSegments} 段", muted = result.uncoveredSegments > 0, accent = result.uncoveredSegments == 0))
        }
        val contextBundle = contextTranslationBundle
        if (contextEnabled && contextBundle != null && contextBundle.contextSentenceCount > 0 && longFormTranslationPreview == null) {
            parent.addView(labelBox("上下文参考（前 ${contextBundle.contextSentenceCount} 句）：${contextBundle.contextSourcePreview.shortLabel(96)}", muted = true, accent = false))
            if (contextBundle.contextTranslationPreview.isNotBlank()) {
                parent.addView(labelBox("上下文整段译文：${contextBundle.contextTranslationPreview.shortLabel(110)}", muted = false, accent = true))
            }
        } else if (!contextAvailable) {
            parent.addView(labelBox("Free：单句本地翻译；Pro：上下文 + 选区全文/长文翻译", muted = true, accent = false))
        }

        if (pinyinBuffer.isNotEmpty()) buildPinyinCandidateBar(parent)
        if (englishBuffer.isNotEmpty()) buildEnglishCandidateBar(parent)

        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (offlineTranslationPreview != null) row.addView(chip(if (longFormTranslationPreview != null) "替换选区" else "译文上屏", emphasized = true) { insertLiveTranslation() })
        if (source.isNotBlank() && longFormTranslationPreview == null) row.addView(chip("原文上屏") { insertLiveSource() })
        if (ProGate.isLongFormTranslationUnlocked(this)) {
            row.addView(chip("全文/长文") { prepareLongFormTranslation() })
        } else {
            row.addView(chip("全文·Pro") { toast("Pro 可对已全选/选择的文本进行本地长文翻译") })
        }
        if (contextAvailable) {
            row.addView(chip(if (contextEnabled) "上下文：开" else "上下文：关", emphasized = contextEnabled) {
                TranslationSettings.setContextTranslationEnabled(this, !contextEnabled)
                longFormTranslationPreview = null
                updateLiveTranslationPreview()
                refreshDynamicHost()
            })
        } else {
            row.addView(chip("上下文·Pro") { toast("Free 仅单句翻译；Pro 可选择开启本地上下文参考") })
        }
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
        row.addView(labelBox("拼音：${pinyinBuffer.shortLabel(32)} · ${candidates.size}候选", muted = false, accent = true))
        candidates.forEachIndexed { index, candidate -> row.addView(chip(candidate, emphasized = index == 0) { commitPinyinCandidate(candidate) }) }
        row.addView(chip("清空", warning = true) { clearPinyinComposition(); refreshDynamicHost() })
        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
    }

    private fun buildEnglishCandidateBar(parent: LinearLayout) {
        val candidates = candidatesForCurrentEnglish()
        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(labelBox("word: ${englishBuffer.shortLabel(32)} · ${candidates.size}", muted = false, accent = true))
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
            val miniPet = PetAvatarV21View(this).apply {
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

        val associations = if (inputMode == InputMode.PINYIN && ImePreferences.associationsEnabled(this)) {
            userDictionary.nextSuggestions(readCandidateContextBeforeCursor(), NEXT_SUGGESTION_LIMIT)
        } else emptyList()
        if (associations.isNotEmpty()) {
            row.addView(labelBox("联想", muted = false, accent = true))
            associations.forEach { value -> row.addView(chip(value.shortLabel()) { commitDirectText(value) }) }
        }

        val phrases = if (inputMode == InputMode.PINYIN) quickPhraseStore.phrasesForPinyin() else quickPhraseStore.phrasesForEnglish()
        phrases.asSequence().filterNot { it in associations }.take(24).forEach { phrase ->
            row.addView(chip(phrase.shortLabel()) { commitDirectText(phrase) })
        }
        if (row.childCount == 0) return
        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
    }

    private fun buildKeyboard(parent: LinearLayout) {
        val rows = if (symbols) symbolRows() else letterRows()
        rows.forEach { rowKeys ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            rowKeys.forEach { key -> row.addView(keyView(key), keyLayoutParams(key)) }
            parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
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
        val shiftActive = shiftState != ShiftState.OFF
        val display = when {
            rawKey == "符号" -> SymbolLibrary.page(symbolPage).title
            rawKey == "⇧" && shiftState == ShiftState.LOCKED -> "⇪"
            rawKey == "space" && speechController.isRecording() -> "🎙 正在听…"
            rawKey == "space" && speechController.isBusy() -> "正在识别…"
            rawKey == "space" && showTranslate -> when {
                inputMode == InputMode.PINYIN && pinyinBuffer.isNotEmpty() -> "选词"
                inputMode == InputMode.ENGLISH && englishBuffer.isNotEmpty() -> "select"
                else -> label("翻译输入", "translate")
            }
            rawKey == "space" -> when {
                inputMode == InputMode.PINYIN && pinyinBuffer.isNotEmpty() -> "选词"
                inputMode == InputMode.ENGLISH && englishBuffer.isNotEmpty() -> "select"
                else -> label("空格 · 长按语音", "space · hold voice")
            }
            rawKey == "↵" -> if (showTranslate) label("译文", "Translate") else label("回车", "Enter")
            rawKey.length == 1 && rawKey[0].isLetter() && shiftActive -> rawKey.uppercase()
            else -> rawKey
        }
        val longPress = if (!symbols && rawKey != "space") SymbolLibrary.longPressFor(rawKey) else null
        val visualText: CharSequence = if (longPress == null) display else {
            SpannableString("$display $longPress").apply {
                val start = display.length + 1
                setSpan(RelativeSizeSpan(0.48f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(SuperscriptSpan(), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val emphasized = (rawKey == "⇧" && shiftActive) || (rawKey == "space" && (speechController.isRecording() || speechController.isBusy()))
        return TextView(this).apply {
            text = visualText
            OrbitTheme.label(this, sizeSp = if (rawKey == "space" || rawKey == "↵" || rawKey == "符号") 13f else 18f, bold = rawKey.length == 1, skin = skin)
            background = OrbitTheme.rounded(
                if (emphasized) skin.panelAltColor else if (isControlKey(rawKey)) skin.controlKeyColor else skin.keyColor,
                dp(12).toFloat(),
                if (emphasized || isControlKey(rawKey)) skin.accentColor else skin.borderColor,
                dp(1),
            )
            setOnClickListener { handleKey(rawKey) }
            when {
                rawKey == "space" -> setOnLongClickListener { handleLocalVoiceInput(); true }
                longPress != null -> setOnLongClickListener { handleLongPress(rawKey); true }
            }
            isClickable = true
            isFocusable = true
            minHeight = dp(42)
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
            "⇧" -> handleShift()
            "⌫" -> handleBackspace()
            "123" -> { commitPendingForControl(); showExpressions = false; symbols = true; symbolPage = 0; root?.let { rebuild(it) } }
            "ABC" -> { showExpressions = false; symbols = false; root?.let { rebuild(it) } }
            "符号" -> { symbolPage = SymbolLibrary.nextPage(symbolPage); root?.let { rebuild(it) } }
            "space" -> handleSpace()
            "↵" -> handleEnter()
            else -> handlePrintableKey(rawKey)
        }
    }

    private fun handleShift() {
        val now = System.currentTimeMillis()
        shiftState = if (now - lastShiftTapAt <= 420L && shiftState == ShiftState.ONCE) {
            ShiftState.LOCKED
        } else {
            when (shiftState) {
                ShiftState.OFF -> ShiftState.ONCE
                ShiftState.ONCE -> ShiftState.OFF
                ShiftState.LOCKED -> ShiftState.OFF
            }
        }
        lastShiftTapAt = now
        root?.let { rebuild(it) }
    }

    private fun handleLongPress(rawKey: String) {
        val alternate = SymbolLibrary.longPressFor(rawKey) ?: return
        commitPendingForControl()
        commitAuxiliaryText(alternate)
    }

    private fun commitAuxiliaryText(text: String) {
        if (showTranslate && translateLiveMode) {
            longFormTranslationPreview = null
            translateComposeText += text
            updateLiveTranslationPreview()
        } else {
            resetInternalCompositionState()
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
        if (rawKey.length == 1 && rawKey[0].isLetter() && !symbols && inputMode == InputMode.PINYIN && shiftState != ShiftState.OFF) {
            commitPendingForControl()
            val upper = rawKey.uppercase()
            if (showTranslate && translateLiveMode) {
                longFormTranslationPreview = null
                translateComposeText += upper
                updateLiveTranslationPreview()
            } else {
                resetInternalCompositionState()
                currentInputConnection?.commitText(upper, 1)
                if (!sensitiveMode) petRepository.recordTypedChars(upper.length)
            }
            if (shiftState == ShiftState.ONCE) shiftState = ShiftState.OFF
            root?.let { rebuild(it) }
            return
        }
        if (rawKey.length == 1 && rawKey[0].isLetter() && inputMode == InputMode.PINYIN && !symbols) { appendPinyin(rawKey); return }
        if (rawKey.length == 1 && rawKey[0].isLetter() && inputMode == InputMode.ENGLISH && !symbols) { appendEnglish(rawKey); return }
        commitPendingForControl()
        showExpressions = false
        val mapped = mapPrintableText(rawKey)
        if (showTranslate && translateLiveMode) {
            longFormTranslationPreview = null
            translateComposeText += mapped
            updateLiveTranslationPreview()
            refreshDynamicHost()
        } else {
            resetInternalCompositionState()
            currentInputConnection?.commitText(mapped, 1)
            if (!sensitiveMode) petRepository.recordTypedChars(mapped.length)
            refreshDynamicHost()
        }
    }

    private fun mapPrintableText(rawKey: String): String {
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
            longFormTranslationPreview = null
            translateComposeText = translateComposeText.dropLast(1)
            updateLiveTranslationPreview()
            refreshDynamicHost()
            return
        }
        if (showExpressions) showExpressions = false
        if (hasSelectedText()) {
            inputConnection.commitText("", 1)
        } else {
            runCatching { inputConnection.deleteSurroundingTextInCodePoints(1, 0) }
                .getOrElse { inputConnection.deleteSurroundingText(1, 0) }
        }
        refreshDynamicHost()
    }

    private fun handleSpace() {
        if (inputMode == InputMode.PINYIN && pinyinBuffer.isNotEmpty()) { commitPendingPinyin(rawFallback = false); return }
        if (inputMode == InputMode.ENGLISH && englishBuffer.isNotEmpty()) { commitPendingEnglish(rawFallback = false, appendSpace = true); return }
        if (showTranslate && translateLiveMode) {
            longFormTranslationPreview = null
            if (translateDirection == TranslatePromptBuilder.Direction.EN_TO_ZH) translateComposeText += " "
            refreshDynamicHost()
            return
        }
        showExpressions = false
        resetInternalCompositionState()
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
        longFormTranslationPreview = null
        if (!sensitiveMode) petRepository.recordTypedChars(1)
        refreshDynamicHost()
    }

    private fun appendEnglish(letter: String) {
        val shifted = shiftState != ShiftState.OFF
        val consumeOneShot = shiftState == ShiftState.ONCE
        val text = if (shifted) letter.uppercase() else letter.lowercase()
        if (englishBuffer.length + text.length > MAX_ENGLISH_BUFFER) { toast("word too long"); return }
        englishBuffer += text
        currentInputConnection?.setComposingText(englishBuffer, 1)
        if (consumeOneShot) shiftState = ShiftState.OFF
        showClips = false
        showPet = false
        showPetCatalog = false
        showExpressions = false
        longFormTranslationPreview = null
        if (!sensitiveMode) petRepository.recordTypedChars(1)
        if (consumeOneShot) root?.let { rebuild(it) } else refreshDynamicHost()
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
        val raw = currentInputConnection?.getTextBeforeCursor(192, 0)?.toString() ?: return null
        val withoutComposition = if (pinyinBuffer.isNotEmpty() && raw.endsWith(pinyinBuffer)) raw.dropLast(pinyinBuffer.length) else raw
        return withoutComposition.takeLast(128)
    }

    private fun invalidatePinyinUiCache() {
        lastPinyinQuery = ""
        lastPinyinContext = ""
        lastPinyinCandidates = emptyList()
    }

    private fun candidatesForCurrentEnglish(): List<String> = englishImeEngine.candidatesFor(englishBuffer)

    private fun commitPinyinCandidate(candidate: String) {
        val inputConnection = currentInputConnection ?: return
        val learnedPinyin = pinyinBuffer
        pinyinBuffer = ""
        invalidatePinyinUiCache()
        if (showTranslate && translateLiveMode) {
            inputConnection.commitText("", 1)
            translateComposeText += candidate
            longFormTranslationPreview = null
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
            longFormTranslationPreview = null
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

    private fun resetInternalCompositionState() {
        pinyinBuffer = ""
        englishBuffer = ""
        invalidatePinyinUiCache()
    }

    private fun toggleInputMode() {
        commitPendingForControl()
        inputMode = if (inputMode == InputMode.PINYIN) InputMode.ENGLISH else InputMode.PINYIN
        ImePreferences.setInputMode(this, if (inputMode == InputMode.PINYIN) ImePreferences.MODE_PINYIN else ImePreferences.MODE_ENGLISH)
        if (showTranslate) translateDirection = if (inputMode == InputMode.PINYIN) TranslatePromptBuilder.Direction.ZH_TO_EN else TranslatePromptBuilder.Direction.EN_TO_ZH
        symbols = false
        shiftState = ShiftState.OFF
        lastShiftTapAt = 0L
        showClips = false
        showPet = false
        showPetCatalog = false
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
        val replacingSelection = hasSelectedText()
        if (replacingSelection) {
            resetInternalCompositionState()
        } else {
            commitPendingPinyin(rawFallback = true)
            commitPendingEnglish(rawFallback = true, appendSpace = false)
        }
        clearTranslateState()
        showPet = false
        showPetCatalog = false
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
        showPetCatalog = false
        showExpressions = false
        petPanelMessage = null
        longFormTranslationPreview = null
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
        if (!showTranslate || !translateLiveMode || longFormTranslationPreview != null) return
        val source = currentTranslationSource().trim()
        translateSourceText = source.ifBlank { null }
        translateSourceLabel = "翻译键盘"
        val direct = if (source.isBlank()) null else OfflineTranslationPack.translateOrNull(source, translateDirection)
        contextTranslationBundle = if (source.isNotBlank() && TranslationSettings.isContextTranslationEnabled(this)) {
            ContextTranslationEngine.translate(source, readTranslationContext().orEmpty(), translateDirection)
        } else null
        offlineTranslationPreview = contextTranslationBundle?.currentTranslation ?: direct?.translatedText
        translatePromptPreview = if (source.isBlank()) null else TranslatePromptBuilder.build(source, translateDirection)
    }

    private fun loadTranslationSource(label: String, rawSource: String?) {
        var source = rawSource?.trim().orEmpty()
        if (!ProGate.isProUnlocked(this)) {
            val single = firstSentence(source)
            if (single != source && source.isNotBlank()) toast("Free 仅翻译第一句；Pro 支持选区全文/长文翻译")
            source = single
        }
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
        longFormTranslationPreview = null
        updateLiveTranslationPreview()
        refreshDynamicHost()
    }

    private fun prepareLongFormTranslation() {
        if (!ProGate.isLongFormTranslationUnlocked(this)) {
            toast("全文/长文翻译需要 Pro")
            return
        }
        val selected = readSelectedText().orEmpty()
        if (selected.isBlank()) {
            toast("请先在目标 App 全选或选择需要翻译的文本")
            return
        }
        val source = selected.take(LongFormTranslationEngine.MAX_SOURCE_CHARS)
        val result = LongFormTranslationEngine.translate(source, translateDirection)
        if (result == null) {
            toast("所选文本无法进行本地长文翻译")
            return
        }
        resetInternalCompositionState()
        translateComposeText = source
        translateSourceText = source
        translateSourceLabel = "选区全文"
        translateLiveMode = true
        showTranslate = true
        showClips = false
        showPet = false
        showExpressions = false
        contextTranslationBundle = null
        translatePromptPreview = null
        longFormTranslationPreview = result
        offlineTranslationPreview = result.translatedText
        refreshDynamicHost()
    }

    private fun toggleTranslateDirection(regenerate: Boolean) {
        translateDirection = if (translateDirection == TranslatePromptBuilder.Direction.ZH_TO_EN) TranslatePromptBuilder.Direction.EN_TO_ZH else TranslatePromptBuilder.Direction.ZH_TO_EN
        inputMode = if (translateDirection == TranslatePromptBuilder.Direction.ZH_TO_EN) InputMode.PINYIN else InputMode.ENGLISH
        ImePreferences.setInputMode(this, if (inputMode == InputMode.PINYIN) ImePreferences.MODE_PINYIN else ImePreferences.MODE_ENGLISH)
        clearPinyinComposition()
        clearEnglishComposition()
        showExpressions = false
        longFormTranslationPreview = null
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
        contextTranslationBundle = null
        longFormTranslationPreview = null
    }

    private fun insertLiveTranslation() {
        if (longFormTranslationPreview == null) updateLiveTranslationPreview()
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
        contextTranslationBundle = null
        longFormTranslationPreview = null
    }

    private fun handleLocalVoiceInput() {
        if (sensitiveMode) { toast("隐私模式不启用语音输入"); return }
        if (pinyinBuffer.isNotEmpty()) clearPinyinComposition()
        if (englishBuffer.isNotEmpty()) clearEnglishComposition()
        speechController.toggleAsr(
            language = if (inputMode == InputMode.PINYIN) "zh" else "en",
            onPermissionRequired = {
                toast("需要麦克风授权；正在打开 Orbit 设置")
                runCatching {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(MainActivity.EXTRA_REQUEST_AUDIO, true),
                    )
                }
            },
            onState = { message -> toast(message); root?.let { rebuild(it) } },
            onText = { text ->
                resetInternalCompositionState()
                currentInputConnection?.commitText(text, 1)
                if (!sensitiveMode) {
                    petRepository.recordTypedChars(text.length)
                    petRepository.recordCandidateCommit()
                }
                refreshDynamicHost()
            },
        )
        root?.let { rebuild(it) }
    }

    private fun speakCurrentText(cloned: Boolean) {
        if (sensitiveMode) { toast("隐私模式不读取文本进行朗读"); return }
        val selected = readSelectedText()?.trim().orEmpty()
        val source = if (selected.isNotBlank()) selected else readPreviousSentence().orEmpty()
        if (source.isBlank()) { toast("请先选择文本，或把光标放在要朗读的句子后面"); return }
        val language = if (source.any { it.code > 127 }) "zh" else "en"
        val callback: (String) -> Unit = { toast(it) }
        if (cloned) speechController.speakWithClonedVoice(source, language, callback)
        else speechController.speak(source, language, callback)
    }

    private fun readSelectedText(): String? = currentInputConnection?.getSelectedText(0)?.toString()

    private fun hasSelectedText(): Boolean = !readSelectedText().isNullOrEmpty()

    private fun readPreviousSentence(): String? {
        val text = currentInputConnection?.getTextBeforeCursor(360, 0)?.toString() ?: return null
        return extractLastSentence(text)
    }

    private fun readTranslationContext(): String? = currentInputConnection?.getTextBeforeCursor(720, 0)?.toString()?.takeLast(720)

    private fun firstSentence(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        val end = trimmed.indexOfFirst { it == '。' || it == '！' || it == '？' || it == '.' || it == '!' || it == '?' || it == '\n' || it == '\r' }
        return if (end >= 0) trimmed.substring(0, end + 1).trim() else trimmed
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
        if (text.isNullOrBlank()) { toast("剪贴板没有可粘贴文字"); return }
        if (!sensitiveMode) store.capture(text)
        commitDirectText(text)
        if (saveAfterPaste && !sensitiveMode) store.capture(text)
    }

    private fun saveClipboard() {
        if (sensitiveMode) { toast("隐私模式"); return }
        val text = readClipboardText()
        if (text.isNullOrBlank()) { toast("剪贴板没有文字内容"); return }
        if (store.capture(text)) {
            petRepository.recordClipSave()
            toast("已同步到本机剪贴板历史")
            refreshDynamicHost()
        } else toast("疑似敏感内容，已跳过")
    }

    private fun readClipboardText(): String? {
        val clip = clipboardManager.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        val item = clip.getItemAt(0)
        return item.text?.toString()?.takeIf { it.isNotBlank() }
            ?: item.htmlText?.toString()?.takeIf { it.isNotBlank() }
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
        private const val NEXT_SUGGESTION_LIMIT = 24
        private const val EXPRESSION_RECENT = "recent"
        private const val EXPRESSION_UNICODE = "unicode_all"
        private const val EXPRESSION_STICKERS = "stickers"
        private const val EXPRESSIONS_PER_ROW = 12
        private const val STICKERS_PER_ROW = 8
        private const val EXPRESSIONS_PER_PAGE = 36
    }
}
