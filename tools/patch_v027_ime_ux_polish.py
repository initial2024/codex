#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_between(text: str, start: str, end: str, replacement: str) -> str:
    a = text.index(start)
    b = text.index(end, a)
    return text[:a] + replacement.rstrip() + "\n\n" + text[b:]


# 1) IME UI: fewer candidates, cleaner prediction bar, persistent voice status, no dead in-IME switch action.
path = ROOT / "app/src/main/java/com/ccwu/orbitime/OrbitInputMethodService.kt"
text = path.read_text(encoding="utf-8")
text = text.replace(
    "    private var petPanelMessage: String? = null\n",
    "    private var petPanelMessage: String? = null\n    private var speechStatusMessage: String? = null\n",
    1,
)
text = text.replace(
    "        parent.addView(primaryScroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))\n\n        if (!showMoreTools) return\n",
    "        parent.addView(primaryScroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))\n\n        speechStatusMessage?.let { message ->\n            parent.addView(\n                labelBox(\"语音：${message.shortLabel(72)}\", muted = false, accent = true),\n                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)),\n            )\n        }\n\n        if (!showMoreTools) return\n",
    1,
)
text = text.replace(
    '        more.addView(chip(label("切换", "Switch")) { showInputMethodPickerSafely() })\n',
    "",
    1,
)

new_candidate_blocks = r'''    private fun buildPinyinCandidateBar(parent: LinearLayout) {
        val candidates = candidatesForCurrentPinyin()
        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (candidates.isEmpty()) {
            row.addView(labelBox("暂无候选 · ${pinyinBuffer.shortLabel(18)}", muted = true, accent = false))
        } else {
            candidates.take(CANDIDATE_UI_LIMIT).forEachIndexed { index, candidate ->
                row.addView(chip(candidate, emphasized = index == 0) { commitPinyinCandidate(candidate) })
            }
        }
        row.addView(chip("清空", warning = true) { clearPinyinComposition(); refreshDynamicHost() })
        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
    }

    private fun buildEnglishCandidateBar(parent: LinearLayout) {
        val candidates = candidatesForCurrentEnglish()
        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (candidates.isEmpty()) {
            row.addView(labelBox("no suggestion", muted = true, accent = false))
        } else {
            candidates.take(CANDIDATE_UI_LIMIT).forEachIndexed { index, candidate ->
                row.addView(chip(candidate, emphasized = index == 0) { commitEnglishCandidate(candidate, appendSpace = false) })
            }
        }
        row.addView(chip("clear", warning = true) { clearEnglishComposition(); refreshDynamicHost() })
        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
    }'''
text = replace_between(
    text,
    "    private fun buildPinyinCandidateBar(parent: LinearLayout) {",
    "    private fun buildPhraseBar(parent: LinearLayout) {",
    new_candidate_blocks,
)

new_phrase_bar = r'''    private fun buildPhraseBar(parent: LinearLayout) {
        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

        val associations = if (inputMode == InputMode.PINYIN && ImePreferences.associationsEnabled(this)) {
            userDictionary.nextSuggestions(readCandidateContextBeforeCursor(), NEXT_SUGGESTION_LIMIT)
        } else emptyList()

        if (associations.isNotEmpty()) {
            associations.take(NEXT_SUGGESTION_LIMIT).forEachIndexed { index, value ->
                row.addView(chip(value.shortLabel(), emphasized = index == 0) { commitDirectText(value) })
            }
        } else {
            val phrases = if (inputMode == InputMode.PINYIN) quickPhraseStore.phrasesForPinyin() else quickPhraseStore.phrasesForEnglish()
            phrases.take(IDLE_QUICK_PHRASE_LIMIT).forEach { phrase ->
                row.addView(chip(phrase.shortLabel()) { commitDirectText(phrase) })
            }
        }

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
            row.addView(miniPet, LinearLayout.LayoutParams(dp(56), dp(34)).apply { setMargins(dp(6), 0, dp(2), 0) })
        }

        if (row.childCount == 0) return
        scroller.addView(row)
        parent.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
    }'''
text = replace_between(
    text,
    "    private fun buildPhraseBar(parent: LinearLayout) {",
    "    private fun buildKeyboard(parent: LinearLayout) {",
    new_phrase_bar,
)

text = text.replace(
    "        val result = userDictionary.candidatesFor(pinyinBuffer, staticCandidates, contextBeforeCursor = context)\n",
    "        val result = userDictionary.candidatesFor(pinyinBuffer, staticCandidates, contextBeforeCursor = context, limit = CANDIDATE_UI_LIMIT)\n",
    1,
)
text = text.replace(
    "        return userDictionary.exactCandidatesFor(pinyinBuffer, staticCandidates, contextBeforeCursor = readCandidateContextBeforeCursor())\n",
    "        return userDictionary.exactCandidatesFor(pinyinBuffer, staticCandidates, contextBeforeCursor = readCandidateContextBeforeCursor(), limit = CANDIDATE_UI_LIMIT)\n",
    1,
)
text = text.replace(
    "    private fun candidatesForCurrentEnglish(): List<String> = englishImeEngine.candidatesFor(englishBuffer)\n",
    "    private fun candidatesForCurrentEnglish(): List<String> = englishImeEngine.candidatesFor(englishBuffer, CANDIDATE_UI_LIMIT)\n",
    1,
)

old_voice = r'''    private fun handleLocalVoiceInput() {
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
    }'''
new_voice = r'''    private fun handleLocalVoiceInput() {
        if (sensitiveMode) {
            speechStatusMessage = "隐私模式不启用语音输入"
            root?.let { rebuild(it) }
            return
        }
        if (pinyinBuffer.isNotEmpty()) clearPinyinComposition()
        if (englishBuffer.isNotEmpty()) clearEnglishComposition()
        speechStatusMessage = if (speechController.isRecording()) "正在停止录音…" else "正在检查本地语音…"
        root?.let { rebuild(it) }
        speechController.toggleAsr(
            language = if (inputMode == InputMode.PINYIN) "zh" else "en",
            onPermissionRequired = {
                speechStatusMessage = "需要麦克风权限，已打开 Orbit 设置"
                root?.let { rebuild(it) }
                runCatching {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(MainActivity.EXTRA_REQUEST_AUDIO, true),
                    )
                }.onFailure {
                    speechStatusMessage = "无法打开设置：${it.message ?: it.javaClass.simpleName}"
                    root?.let { rebuild(it) }
                }
            },
            onState = { message ->
                speechStatusMessage = if (message == "识别完成") null else message
                toast(message)
                root?.let { rebuild(it) }
            },
            onText = { text ->
                speechStatusMessage = null
                resetInternalCompositionState()
                currentInputConnection?.commitText(text, 1)
                if (!sensitiveMode) {
                    petRepository.recordTypedChars(text.length)
                    petRepository.recordCandidateCommit()
                }
                refreshDynamicHost()
            },
        )
    }'''
if old_voice not in text:
    raise SystemExit("voice handler block not found")
text = text.replace(old_voice, new_voice, 1)
text = text.replace(
    "        private const val NEXT_SUGGESTION_LIMIT = 24\n",
    "        private const val CANDIDATE_UI_LIMIT = 12\n        private const val NEXT_SUGGESTION_LIMIT = 6\n        private const val IDLE_QUICK_PHRASE_LIMIT = 8\n",
    1,
)
path.write_text(text, encoding="utf-8")


# 2) Candidate engine: let UI ask for a small candidate set instead of forcing 48 every key press.
path = ROOT / "app/src/main/java/com/ccwu/orbitime/UserDictionaryStore.kt"
text = path.read_text(encoding="utf-8")
text = text.replace(
    "        contextBeforeCursor: String? = null,\n    ): List<String> {\n        val query = PinyinDictionary.normalize(rawInput)\n        if (query.isEmpty()) return staticCandidates.take(MAX_CANDIDATES)\n        val engineCandidates = runCatching {\n            imeEngine.candidates(query, contextBeforeCursor = contextBeforeCursor, limit = MAX_CANDIDATES)\n",
    "        contextBeforeCursor: String? = null,\n        limit: Int = MAX_CANDIDATES,\n    ): List<String> {\n        val query = PinyinDictionary.normalize(rawInput)\n        val safeLimit = limit.coerceIn(1, MAX_CANDIDATES)\n        if (query.isEmpty()) return staticCandidates.take(safeLimit)\n        val engineCandidates = runCatching {\n            imeEngine.candidates(query, contextBeforeCursor = contextBeforeCursor, limit = safeLimit)\n",
    1,
)
text = text.replace("            return (engineCandidates + staticCandidates).distinct().take(MAX_CANDIDATES)\n", "            return (engineCandidates + staticCandidates).distinct().take(safeLimit)\n", 1)
text = text.replace(".sortedWith(ENTRY_ORDER).map { it.text }.take(MAX_CANDIDATES).toList()", ".sortedWith(ENTRY_ORDER).map { it.text }.take(safeLimit).toList()", 2)
text = text.replace("            .distinct().take(MAX_CANDIDATES)\n", "            .distinct().take(safeLimit)\n", 1)
text = text.replace(
    "        contextBeforeCursor: String? = null,\n    ): List<String> {\n        val query = PinyinDictionary.normalize(rawInput)\n        if (query.isEmpty()) return emptyList()\n        val engineCandidates = runCatching {\n            imeEngine.exactCandidates(query, contextBeforeCursor = contextBeforeCursor, limit = MAX_CANDIDATES)\n",
    "        contextBeforeCursor: String? = null,\n        limit: Int = MAX_CANDIDATES,\n    ): List<String> {\n        val query = PinyinDictionary.normalize(rawInput)\n        if (query.isEmpty()) return emptyList()\n        val safeLimit = limit.coerceIn(1, MAX_CANDIDATES)\n        val engineCandidates = runCatching {\n            imeEngine.exactCandidates(query, contextBeforeCursor = contextBeforeCursor, limit = safeLimit)\n",
    1,
)
text = text.replace("        if (engineCandidates.isNotEmpty()) return engineCandidates.distinct().take(MAX_CANDIDATES)\n", "        if (engineCandidates.isNotEmpty()) return engineCandidates.distinct().take(safeLimit)\n", 1)
text = text.replace("            .sortedWith(ENTRY_ORDER).map { it.text }.distinct().take(MAX_CANDIDATES)\n", "            .sortedWith(ENTRY_ORDER).map { it.text }.distinct().take(safeLimit)\n", 1)
path.write_text(text, encoding="utf-8")


# 3) Model import: first executable pack of a type becomes usable immediately after a successful install.
path = ROOT / "app/src/main/java/com/ccwu/orbitime/ModelPackManager.kt"
text = path.read_text(encoding="utf-8")
old = r'''            staged.tempFile.delete()
            val status = OrbitModelRuntimeRegistry.statusFor(inspected.manifest)
            OperationResult(
                true,
                if (status.executable) {
                    "已安装 ${inspected.manifest.displayName}。${status.label}；请在模型包列表中设为首选后使用。"
                } else {
                    "已安装 ${inspected.manifest.displayName}。${status.label}"
                },
            )'''
new = r'''            staged.tempFile.delete()
            val status = OrbitModelRuntimeRegistry.statusFor(inspected.manifest)
            val autoEnabled = status.executable && enabledPackId(inspected.manifest.type) == null
            if (autoEnabled) {
                prefs.edit().putString(enabledKey(inspected.manifest.type), inspected.manifest.packId).apply()
            }
            val testVariantNote = if (context.packageName.endsWith(".bundledmodels")) {
                " 测试版使用独立私有模型目录，与正式版需要分别导入。"
            } else ""
            OperationResult(
                true,
                if (status.executable) {
                    if (autoEnabled) {
                        "已安装 ${inspected.manifest.displayName} 并自动设为 ${inspected.manifest.type.wireValue} 首选。${status.label}。$testVariantNote"
                    } else {
                        "已安装 ${inspected.manifest.displayName}。${status.label}；当前已有首选模型，未自动切换。$testVariantNote"
                    }
                } else {
                    "已安装 ${inspected.manifest.displayName}。${status.label}。$testVariantNote"
                },
            )'''
if old not in text:
    raise SystemExit("model install result block not found")
text = text.replace(old, new, 1)
path.write_text(text, encoding="utf-8")


# 4) Speech diagnostics: distinguish not-installed / installed-but-not-enabled / non-executable packs.
path = ROOT / "app/src/main/java/com/ccwu/orbitime/ImeSpeechController.kt"
text = path.read_text(encoding="utf-8")
text = text.replace(
    '                onState("请先在 Orbit 设置中安装并启用可执行的 sherpa ASR 模型包")\n',
    '                onState(asrUnavailableMessage())\n',
    1,
)
insert = r'''
    private fun asrUnavailableMessage(): String {
        val installed = modelPacks.listInstalled().filter { it.manifest.type == OrbitModelPackType.ASR }
        if (installed.isEmpty()) return "未安装 ASR 模型包；请在 Orbit 设置中导入 .orbitpack"
        val executable = installed.filter { it.runtimeStatus.executable }
        if (executable.isEmpty()) return "已安装 ASR 模型，但当前 model_family/runtime_config 不可执行"
        return "已安装可执行 ASR 模型，但尚未设为首选；请在 Orbit 设置中启用"
    }
'''
marker = "    private fun executablePack(type: OrbitModelPackType): ModelPackManager.InstalledPack? {"
idx = text.index(marker)
text = text[:idx] + insert + "\n" + text[idx:]
path.write_text(text, encoding="utf-8")

print("patched v0.27 IME UX")
