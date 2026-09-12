from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel: str, text: str) -> None:
    (ROOT / rel).write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one old snippet, found {count}")
    return text.replace(old, new, 1)


def replace_regex_once(text: str, pattern: str, replacement: str, label: str) -> str:
    compiled = re.compile(pattern, re.S)
    matches = list(compiled.finditer(text))
    if len(matches) != 1:
        raise RuntimeError(f"{label}: expected exactly one regex match, found {len(matches)}")
    return compiled.sub(replacement, text, count=1)


# -----------------------------------------------------------------------------
# app/build.gradle.kts: v0.27
# -----------------------------------------------------------------------------
rel = "app/build.gradle.kts"
text = read(rel)
text = replace_once(text, 'versionCode = 26\n        versionName = "0.26.0"', 'versionCode = 27\n        versionName = "0.27.0"', "version bump")
write(rel, text)


# -----------------------------------------------------------------------------
# PetRepository.kt: expose renderer-safe visual catalog entries.
# -----------------------------------------------------------------------------
rel = "app/src/main/java/com/ccwu/orbitime/PetRepository.kt"
text = read(rel)
text = replace_once(
    text,
    """    data class ActionResult(\n        val message: String,\n        val profile: PetProfile,\n    )\n""",
    """    data class ActionResult(\n        val message: String,\n        val profile: PetProfile,\n    )\n\n    data class PetCatalogEntry(\n        val profile: PetProfile,\n        val owned: Boolean,\n        val proOnly: Boolean,\n        val current: Boolean,\n    )\n""",
    "catalog entry data class",
)
text = replace_once(
    text,
    """    fun petCatalogLine(): String {\n        val owned = ownedPetIds()\n        return PetCatalog.all.joinToString(\" · \") { pet ->\n            val lock = if (pet.isPro && !ProGate.isProUnlocked(context)) \"🔒\" else \"\"\n            val mark = if (pet.id in owned) \"✓\" else \"\"\n            \"$mark$lock${pet.name}\"\n        }\n    }\n""",
    """    fun petCatalogLine(): String {\n        val owned = ownedPetIds()\n        return PetCatalog.all.joinToString(\" · \") { pet ->\n            val lock = if (pet.isPro && !ProGate.isProUnlocked(context)) \"🔒\" else \"\"\n            val mark = if (pet.id in owned) \"✓\" else \"\"\n            \"$mark$lock${pet.name}\"\n        }\n    }\n\n    fun petCatalogEntries(): List<PetCatalogEntry> {\n        val currentProfile = profile()\n        val owned = ownedPetIds()\n        return PetCatalog.all.map { pet ->\n            val isCurrent = pet.id == currentProfile.catalogPetId\n            val visualProfile = currentProfile.copy(\n                petId = pet.visualBaseId,\n                catalogPetId = pet.id,\n                petName = pet.name,\n                species = pet.species,\n                equippedOutfitId = if (isCurrent) currentProfile.equippedOutfitId else null,\n                catalogOutfitId = if (isCurrent) currentProfile.catalogOutfitId else null,\n                equippedOutfitName = if (isCurrent) currentProfile.equippedOutfitName else null,\n            )\n            PetCatalogEntry(\n                profile = visualProfile,\n                owned = pet.id in owned,\n                proOnly = pet.isPro,\n                current = isCurrent,\n            )\n        }\n    }\n""",
    "catalog entries method",
)
write(rel, text)


# -----------------------------------------------------------------------------
# MainActivity.kt: scroll retention, larger editors, visual pet catalog, usage help.
# -----------------------------------------------------------------------------
rel = "app/src/main/java/com/ccwu/orbitime/MainActivity.kt"
text = read(rel)
text = replace_once(
    text,
    "import android.widget.EditText\nimport android.widget.LinearLayout",
    "import android.widget.EditText\nimport android.widget.LinearLayout",
    "main imports marker",
)
text = replace_once(
    text,
    """    private lateinit var voiceReferenceStore: VoiceReferenceStore\n    private var pendingVoiceTranscript: String? = null\n""",
    """    private lateinit var voiceReferenceStore: VoiceReferenceStore\n    private var pendingVoiceTranscript: String? = null\n    private var mainScrollView: ScrollView? = null\n    private var savedScrollY: Int = 0\n""",
    "scroll fields",
)
text = replace_once(
    text,
    """    private fun render(statusMessage: String? = null) {\n        val skin = SkinManager.current(this)\n""",
    """    private fun render(statusMessage: String? = null) {\n        savedScrollY = mainScrollView?.scrollY ?: savedScrollY\n        val skin = SkinManager.current(this)\n""",
    "capture scroll position",
)
text = replace_once(
    text,
    """        val scroll = ScrollView(this).apply { setBackgroundColor(skin.backgroundColor) }\n""",
    """        val scroll = ScrollView(this).apply { setBackgroundColor(skin.backgroundColor) }\n        mainScrollView = scroll\n""",
    "remember scroll view",
)
text = replace_once(
    text,
    """        container.addView(title(\"Orbit IME\", skin))\n        container.addView(paragraph(\"v0.26 保留 v0.23 的成熟输入/领域词/联想，并完成 v0.24 本地 ASR、v0.25 本地 TTS、v0.26 ZipVoice 零样本音色克隆运行时。模型仍由 Pro 用户自行导入并逐包核验来源/许可证。当前：${ProLicenseManager.licenseLabel(this)} · ${skin.name}。\", skin))\n""",
    """        container.addView(title(\"Orbit IME\", skin))\n        container.addView(paragraph(\"v0.27 聚焦输入法可用性：保留 v0.23 成熟输入与 v0.24–v0.26 本地 ASR/TTS/ZipVoice，并把这些能力放回真实输入路径。模型仍由 Pro 用户自行导入并逐包核验来源/许可证。当前：${ProLicenseManager.licenseLabel(this)} · ${skin.name}。\", skin))\n""",
    "about version copy",
)
text = text.replace('val zhPhrase = editField("添加中文固定词/句子", skin)', 'val zhPhrase = editField("添加中文固定词/句子", skin, multiline = true)')
text = text.replace('val enPhrase = editField("Add English quick phrase", skin)', 'val enPhrase = editField("Add English quick phrase", skin, multiline = true)')
text = text.replace('val ttsText = editField("TTS 试听文本（建议短句）", skin)', 'val ttsText = editField("TTS 试听文本（建议短句）", skin, multiline = true)')
text = text.replace('val referenceText = editField("参考 WAV 中实际说出的完整原文", skin)', 'val referenceText = editField("参考 WAV 中实际说出的完整原文", skin, multiline = true)')
text = text.replace('val cloneText = editField("音色克隆试听文本", skin)', 'val cloneText = editField("音色克隆试听文本", skin, multiline = true)')
text = replace_once(
    text,
    """        container.addView(section(\"本地语音 · v0.24–v0.26\", skin))\n""",
    """        container.addView(section(\"本地语音 · v0.24–v0.26\", skin))\n        container.addView(paragraph(\"使用入口：长按输入法空格键启动/停止本地语音；选中文字后从输入法“更多 → 朗读”；ZipVoice 先在这里保存本人/已授权参考声音，再从“更多 → 音色”使用。入口会一直可见，并准确提示缺少 Pro、模型包、麦克风权限或参考声音中的哪一步。\", skin))\n""",
    "speech usage help",
)
text = replace_once(
    text,
    'container.addView(button("查看宠物图鉴", skin) { render(petRepository.petCatalogLine()) })',
    'container.addView(button("查看宠物图鉴", skin) { showPetCatalogDialog(petRepository, skin) })',
    "visual pet catalog button",
)
text = replace_once(
    text,
    """        container.addView(paragraph(\"About · v0.26.0\", skin))\n        setContentView(scroll)\n    }\n\n    private fun fuzzyLabel(): String = when (ImePreferences.fuzzyLevel(this)) {\n""",
    """        container.addView(paragraph(\"About · v0.27.0\", skin))\n        setContentView(scroll)\n        scroll.post { scroll.scrollTo(0, savedScrollY.coerceAtLeast(0)) }\n    }\n\n    private fun showPetCatalogDialog(repository: PetRepository, skin: OrbitSkin) {\n        val catalogScroll = ScrollView(this).apply { setBackgroundColor(skin.backgroundColor) }\n        val list = LinearLayout(this).apply {\n            orientation = LinearLayout.VERTICAL\n            setPadding(dp(14), dp(10), dp(14), dp(10))\n        }\n        repository.petCatalogEntries().forEach { entry ->\n            val row = LinearLayout(this).apply {\n                orientation = LinearLayout.HORIZONTAL\n                gravity = Gravity.CENTER_VERTICAL\n                background = OrbitTheme.rounded(skin.panelColor, dp(14).toFloat(), if (entry.current) skin.accentColor else skin.borderColor, dp(1))\n                setPadding(dp(8), dp(6), dp(10), dp(6))\n            }\n            row.addView(PetAvatarV21View(this).apply {\n                bind(entry.profile, skin)\n                contentDescription = entry.profile.petName\n            }, LinearLayout.LayoutParams(dp(104), dp(82)))\n            val status = when {\n                entry.current -> \"✓ 当前\"\n                entry.owned -> \"✓ 已拥有\"\n                entry.proOnly && !ProGate.isProUnlocked(this) -> \"🔒 Pro\"\n                else -> \"未解锁\"\n            }\n            row.addView(paragraph(\"${entry.profile.petName} · ${entry.profile.species}\\n$status${if (entry.proOnly) \" · Pro\" else \" · Free\"}\", skin), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))\n            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96)).apply { setMargins(0, dp(4), 0, dp(4)) })\n        }\n        catalogScroll.addView(list)\n        AlertDialog.Builder(this)\n            .setTitle(\"宠物图鉴\")\n            .setView(catalogScroll)\n            .setPositiveButton(\"关闭\", null)\n            .show()\n    }\n\n    private fun fuzzyLabel(): String = when (ImePreferences.fuzzyLevel(this)) {\n""",
    "catalog dialog and scroll restore",
)
text = replace_regex_once(
    text,
    r"    private fun editField\(hint: String, skin: OrbitSkin\): EditText = EditText\(this\)\.apply \{.*?\n    \}\n    private fun statusBox",
    """    private fun editField(hint: String, skin: OrbitSkin, multiline: Boolean = false): EditText = EditText(this).apply {\n        this.hint = hint\n        setHintTextColor(skin.mutedTextColor)\n        setTextColor(skin.textColor)\n        textSize = 16f\n        isSingleLine = !multiline\n        maxLines = if (multiline) 4 else 1\n        minHeight = dp(if (multiline) 82 else 54)\n        gravity = if (multiline) Gravity.TOP or Gravity.START else Gravity.CENTER_VERTICAL\n        background = OrbitTheme.rounded(skin.panelColor, dp(12).toFloat(), skin.borderColor, dp(1))\n        setPadding(dp(14), dp(12), dp(14), dp(12))\n        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(4), 0, dp(8)) }\n    }\n    private fun statusBox""",
    "larger edit fields",
)
write(rel, text)


# -----------------------------------------------------------------------------
# OrbitInputMethodService.kt: visible voice tools, long-press space, shift state,
# compact More bar, visual pet catalog, slightly larger key rows.
# -----------------------------------------------------------------------------
rel = "app/src/main/java/com/ccwu/orbitime/OrbitInputMethodService.kt"
text = read(rel)
text = replace_once(
    text,
    """class OrbitInputMethodService : InputMethodService() {\n    private enum class InputMode { ENGLISH, PINYIN }\n\n    private var inputMode = InputMode.PINYIN\n    private var caps = false\n""",
    """class OrbitInputMethodService : InputMethodService() {\n    private enum class InputMode { ENGLISH, PINYIN }\n    private enum class ShiftState { OFF, ONCE, LOCKED }\n\n    private var inputMode = InputMode.PINYIN\n    private var shiftState = ShiftState.OFF\n    private var lastShiftTapAt = 0L\n""",
    "shift state fields",
)
text = replace_once(
    text,
    """    private var showPet = false\n    private var showExpressions = false\n""",
    """    private var showPet = false\n    private var showPetCatalog = false\n    private var petCatalogPage = 0\n    private var showMoreTools = false\n    private var showExpressions = false\n""",
    "panel fields",
)
text = replace_once(
    text,
    """            showPet = false\n            showExpressions = false\n""",
    """            showPet = false\n            showPetCatalog = false\n            showMoreTools = false\n            showExpressions = false\n""",
    "sensitive panel reset",
)
text = replace_once(
    text,
    """            sensitiveMode -> Unit\n            showPet -> buildPetPanel(host)\n""",
    """            sensitiveMode -> Unit\n            showPetCatalog -> buildPetCatalogPanel(host)\n            showPet -> buildPetPanel(host)\n""",
    "catalog panel precedence",
)
text = replace_regex_once(
    text,
    r"    private fun buildTopBar\(parent: LinearLayout\) \{.*?\n    \}\n\n    private fun buildPetPanel",
    """    private fun buildTopBar(parent: LinearLayout) {\n        val skin = activeSkin()\n        if (sensitiveMode) {\n            parent.addView(labelBox(\"🔒 隐私模式 · 语音/朗读/剪贴板/宠物工具已隐藏\", muted = false, accent = true, warning = true))\n            return\n        }\n\n        val primaryScroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }\n        val primary = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }\n        primary.addView(chip(if (inputMode == InputMode.PINYIN) \"拼音\" else \"EN\", emphasized = true) { toggleInputMode() })\n        primary.addView(chip(if (showClips) label(\"返回\", \"Keyboard\") else label(\"剪贴板\", \"Clips\"), emphasized = showClips) {\n            if (showClips) showClips = false else {\n                commitPendingPinyin(rawFallback = true)\n                commitPendingEnglish(rawFallback = true, appendSpace = false)\n                showClips = true\n                showPet = false\n                showPetCatalog = false\n                showExpressions = false\n                clearTranslateState()\n                captureVisibleClipboard()\n            }\n            root?.let { rebuild(it) }\n        })\n        primary.addView(chip(if (showExpressions) label(\"返回\", \"Keyboard\") else label(\"表情\", \"Emoji\"), emphasized = showExpressions) {\n            if (showExpressions) showExpressions = false else {\n                commitPendingPinyin(rawFallback = true)\n                commitPendingEnglish(rawFallback = true, appendSpace = false)\n                showExpressions = true\n                showClips = false\n                showPet = false\n                showPetCatalog = false\n                clearTranslateState()\n                expressionPage = 0\n            }\n            root?.let { rebuild(it) }\n        })\n        primary.addView(chip(if (speechController.isRecording()) label(\"停止语音\", \"Stop voice\") else label(\"语音\", \"Voice\"), emphasized = speechController.isRecording() || speechController.isBusy()) {\n            handleLocalVoiceInput()\n        })\n        primary.addView(chip(if (showMoreTools) label(\"收起\", \"Less\") else label(\"更多\", \"More\"), emphasized = showMoreTools) {\n            showMoreTools = !showMoreTools\n            root?.let { rebuild(it) }\n        })\n        primaryScroller.setBackgroundColor(skin.backgroundColor)\n        primaryScroller.addView(primary)\n        parent.addView(primaryScroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))\n\n        if (!showMoreTools) return\n        val moreScroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }\n        val more = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }\n        more.addView(chip(if (showTranslate) label(\"返回\", \"Keyboard\") else label(\"翻译\", \"Translate\"), emphasized = showTranslate) {\n            if (showTranslate) clearTranslateState() else startLiveTranslateMode()\n            showExpressions = false\n            showPetCatalog = false\n            root?.let { rebuild(it) }\n        })\n        more.addView(chip(if (showPet || showPetCatalog) label(\"返回\", \"Keyboard\") else label(\"宠物\", \"Pet\"), emphasized = showPet || showPetCatalog) {\n            commitPendingPinyin(rawFallback = true)\n            commitPendingEnglish(rawFallback = true, appendSpace = false)\n            if (showPet || showPetCatalog) {\n                showPet = false\n                showPetCatalog = false\n                petPanelMessage = null\n            } else {\n                showPet = true\n                showPetCatalog = false\n                showClips = false\n                showExpressions = false\n                clearTranslateState()\n                petPanelMessage = null\n            }\n            root?.let { rebuild(it) }\n        })\n        more.addView(chip(label(\"朗读\", \"Read\")) { speakCurrentText(cloned = false) })\n        more.addView(chip(label(\"音色\", \"Clone\")) { speakCurrentText(cloned = true) })\n        more.addView(chip(label(\"切换\", \"Switch\")) { showInputMethodPickerSafely() })\n        more.addView(chip(label(\"粘贴\", \"Paste\")) { pasteClipboard(saveAfterPaste = false) })\n        moreScroller.addView(more)\n        parent.addView(moreScroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))\n    }\n\n    private fun buildPetPanel""",
    "top toolbar",
)
text = replace_once(
    text,
    'row.addView(chip("图鉴") { petPanelMessage = petRepository.petCatalogLine(); refreshDynamicHost() })',
    'row.addView(chip("图鉴") { petCatalogPage = 0; showPet = false; showPetCatalog = true; refreshDynamicHost() })',
    "IME pet catalog entry",
)
text = replace_once(
    text,
    """        row.addView(chip(\"关闭\", warning = true) { showPet = false; petPanelMessage = null; root?.let { rebuild(it) } })\n""",
    """        row.addView(chip(\"关闭\", warning = true) { showPet = false; showPetCatalog = false; petPanelMessage = null; root?.let { rebuild(it) } })\n""",
    "pet panel close",
)
text = replace_once(
    text,
    """    private fun buildExpressionPanel(parent: LinearLayout) {\n""",
    """    private fun buildPetCatalogPanel(parent: LinearLayout) {\n        val entries = petRepository.petCatalogEntries()\n        val pageSize = 4\n        val pageCount = maxOf(1, ceil(entries.size / pageSize.toDouble()).toInt())\n        petCatalogPage = petCatalogPage.coerceIn(0, pageCount - 1)\n        parent.addView(labelBox(\"宠物图鉴 · ${petCatalogPage + 1}/$pageCount\", muted = false, accent = true))\n        entries.drop(petCatalogPage * pageSize).take(pageSize).forEach { entry ->\n            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }\n            row.addView(PetAvatarV21View(this).apply {\n                bind(entry.profile, activeSkin())\n                contentDescription = entry.profile.petName\n            }, LinearLayout.LayoutParams(dp(74), dp(54)).apply { setMargins(dp(2), dp(2), dp(6), dp(2)) })\n            val state = when {\n                entry.current -> \"✓ 当前\"\n                entry.owned -> \"✓ 已拥有\"\n                entry.proOnly && !ProGate.isProUnlocked(this) -> \"🔒 Pro\"\n                else -> \"未解锁\"\n            }\n            row.addView(labelBox(\"${entry.profile.petName} · ${entry.profile.species} · $state\", muted = false, accent = entry.current), LinearLayout.LayoutParams(0, dp(34), 1f))\n            parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))\n        }\n        val nav = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }\n        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }\n        if (petCatalogPage > 0) buttons.addView(chip(\"上一页\") { petCatalogPage--; refreshDynamicHost() })\n        if (petCatalogPage + 1 < pageCount) buttons.addView(chip(\"下一页\", emphasized = true) { petCatalogPage++; refreshDynamicHost() })\n        buttons.addView(chip(\"返回宠物\") { showPetCatalog = false; showPet = true; refreshDynamicHost() })\n        buttons.addView(chip(\"关闭\", warning = true) { showPetCatalog = false; showPet = false; root?.let { rebuild(it) } })\n        nav.addView(buttons)\n        parent.addView(nav, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))\n    }\n\n    private fun buildExpressionPanel(parent: LinearLayout) {\n""",
    "visual IME pet catalog",
)
text = replace_once(
    text,
    """            parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))\n""",
    """            parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))\n""",
    "key row height",
)
text = replace_regex_once(
    text,
    r"    private fun keyView\(rawKey: String\): TextView \{.*?\n    \}\n\n    private fun keyLayoutParams",
    """    private fun keyView(rawKey: String): TextView {\n        val skin = activeSkin()\n        val shiftActive = shiftState != ShiftState.OFF\n        val display = when {\n            rawKey == \"符号\" -> SymbolLibrary.page(symbolPage).title\n            rawKey == \"⇧\" && shiftState == ShiftState.LOCKED -> \"⇪\"\n            rawKey == \"space\" && speechController.isRecording() -> \"🎙 正在听…\"\n            rawKey == \"space\" && speechController.isBusy() -> \"正在识别…\"\n            rawKey == \"space\" && showTranslate -> when {\n                inputMode == InputMode.PINYIN && pinyinBuffer.isNotEmpty() -> \"选词\"\n                inputMode == InputMode.ENGLISH && englishBuffer.isNotEmpty() -> \"select\"\n                else -> label(\"翻译输入\", \"translate\")\n            }\n            rawKey == \"space\" -> when {\n                inputMode == InputMode.PINYIN && pinyinBuffer.isNotEmpty() -> \"选词\"\n                inputMode == InputMode.ENGLISH && englishBuffer.isNotEmpty() -> \"select\"\n                else -> label(\"空格 · 长按语音\", \"space · hold voice\")\n            }\n            rawKey == \"↵\" -> if (showTranslate) label(\"译文\", \"Translate\") else label(\"回车\", \"Enter\")\n            rawKey.length == 1 && rawKey[0].isLetter() && shiftActive -> rawKey.uppercase()\n            else -> rawKey\n        }\n        val longPress = if (!symbols && rawKey != \"space\") SymbolLibrary.longPressFor(rawKey) else null\n        val visualText: CharSequence = if (longPress == null) display else {\n            SpannableString(\"$display $longPress\").apply {\n                val start = display.length + 1\n                setSpan(RelativeSizeSpan(0.48f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)\n                setSpan(SuperscriptSpan(), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)\n            }\n        }\n        val emphasized = (rawKey == \"⇧\" && shiftActive) || (rawKey == \"space\" && (speechController.isRecording() || speechController.isBusy()))\n        return TextView(this).apply {\n            text = visualText\n            OrbitTheme.label(this, sizeSp = if (rawKey == \"space\" || rawKey == \"↵\" || rawKey == \"符号\") 13f else 18f, bold = rawKey.length == 1, skin = skin)\n            background = OrbitTheme.rounded(\n                if (emphasized) skin.panelAltColor else if (isControlKey(rawKey)) skin.controlKeyColor else skin.keyColor,\n                dp(12).toFloat(),\n                if (emphasized || isControlKey(rawKey)) skin.accentColor else skin.borderColor,\n                dp(1),\n            )\n            setOnClickListener { handleKey(rawKey) }\n            when {\n                rawKey == \"space\" -> setOnLongClickListener { handleLocalVoiceInput(); true }\n                longPress != null -> setOnLongClickListener { handleLongPress(rawKey); true }\n            }\n            isClickable = true\n            isFocusable = true\n            minHeight = dp(42)\n        }\n    }\n\n    private fun keyLayoutParams""",
    "key view voice/shift",
)
text = replace_regex_once(
    text,
    r"    private fun handleKey\(rawKey: String\) \{.*?\n    \}\n\n    private fun handleLongPress",
    """    private fun handleKey(rawKey: String) {\n        when (rawKey) {\n            \"⇧\" -> handleShift()\n            \"⌫\" -> handleBackspace()\n            \"123\" -> { commitPendingForControl(); showExpressions = false; symbols = true; symbolPage = 0; root?.let { rebuild(it) } }\n            \"ABC\" -> { showExpressions = false; symbols = false; root?.let { rebuild(it) } }\n            \"符号\" -> { symbolPage = SymbolLibrary.nextPage(symbolPage); root?.let { rebuild(it) } }\n            \"space\" -> handleSpace()\n            \"↵\" -> handleEnter()\n            else -> handlePrintableKey(rawKey)\n        }\n    }\n\n    private fun handleShift() {\n        val now = System.currentTimeMillis()\n        shiftState = if (now - lastShiftTapAt <= 420L && shiftState == ShiftState.ONCE) {\n            ShiftState.LOCKED\n        } else {\n            when (shiftState) {\n                ShiftState.OFF -> ShiftState.ONCE\n                ShiftState.ONCE -> ShiftState.OFF\n                ShiftState.LOCKED -> ShiftState.OFF\n            }\n        }\n        lastShiftTapAt = now\n        root?.let { rebuild(it) }\n    }\n\n    private fun handleLongPress""",
    "shift handler",
)
text = replace_regex_once(
    text,
    r"    private fun handlePrintableKey\(rawKey: String\) \{.*?\n    \}\n\n    private fun mapPrintableText",
    """    private fun handlePrintableKey(rawKey: String) {\n        if (rawKey.length == 1 && rawKey[0].isLetter() && !symbols && shiftState != ShiftState.OFF) {\n            commitPendingForControl()\n            val upper = rawKey.uppercase()\n            if (showTranslate && translateLiveMode) {\n                longFormTranslationPreview = null\n                translateComposeText += upper\n                updateLiveTranslationPreview()\n            } else {\n                resetInternalCompositionState()\n                currentInputConnection?.commitText(upper, 1)\n                if (!sensitiveMode) petRepository.recordTypedChars(upper.length)\n            }\n            if (shiftState == ShiftState.ONCE) shiftState = ShiftState.OFF\n            root?.let { rebuild(it) }\n            return\n        }\n        if (rawKey.length == 1 && rawKey[0].isLetter() && inputMode == InputMode.PINYIN && !symbols) { appendPinyin(rawKey); return }\n        if (rawKey.length == 1 && rawKey[0].isLetter() && inputMode == InputMode.ENGLISH && !symbols) { appendEnglish(rawKey); return }\n        commitPendingForControl()\n        showExpressions = false\n        val mapped = mapPrintableText(rawKey)\n        if (showTranslate && translateLiveMode) {\n            longFormTranslationPreview = null\n            translateComposeText += mapped\n            updateLiveTranslationPreview()\n            refreshDynamicHost()\n        } else {\n            resetInternalCompositionState()\n            currentInputConnection?.commitText(mapped, 1)\n            if (!sensitiveMode) petRepository.recordTypedChars(mapped.length)\n            refreshDynamicHost()\n        }\n    }\n\n    private fun mapPrintableText""",
    "shifted printable handling",
)
text = replace_once(
    text,
    """    private fun mapPrintableText(rawKey: String): String {\n        if (inputMode == InputMode.ENGLISH && rawKey.length == 1 && rawKey[0].isLetter() && caps) return rawKey.uppercase()\n        if (inputMode == InputMode.PINYIN && !symbols) return when (rawKey) { \",\" -> \"，\"; \".\" -> \"。\"; else -> rawKey }\n        return rawKey\n    }\n""",
    """    private fun mapPrintableText(rawKey: String): String {\n        if (inputMode == InputMode.PINYIN && !symbols) return when (rawKey) { \",\" -> \"，\"; \".\" -> \"。\"; else -> rawKey }\n        return rawKey\n    }\n""",
    "remove old caps mapping",
)
text = replace_once(text, "        caps = false\n", "        shiftState = ShiftState.OFF\n        lastShiftTapAt = 0L\n", "toggle mode shift reset")
text = text.replace("        showPet = false\n        showExpressions = false\n        petPanelMessage = null\n", "        showPet = false\n        showPetCatalog = false\n        showExpressions = false\n        petPanelMessage = null\n")
if "caps" in text:
    raise RuntimeError("legacy caps state still present after v0.27 patch")
write(rel, text)

print("v0.27 usability patch applied successfully")
