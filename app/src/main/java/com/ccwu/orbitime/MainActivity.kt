package com.ccwu.orbitime

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var modelPackManager: ModelPackManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        modelPackManager = ModelPackManager(this)
        render()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_MODEL_PACK || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        runCatching {
            val flags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (flags != 0) contentResolver.takePersistableUriPermission(uri, flags)
        }
        stageModelPack(uri)
    }

    private fun render(statusMessage: String? = null) {
        val skin = SkinManager.current(this)
        val userDictionary = UserDictionaryStore(this)
        val dictionaryStats = userDictionary.stats()
        val petRepository = PetRepository(this)
        val petProfile = petRepository.profile()
        val phraseStore = QuickPhraseStore(this)
        val contextTranslationAvailable = TranslationSettings.isContextTranslationAvailable(this)
        val contextTranslationEnabled = TranslationSettings.isContextTranslationEnabled(this)
        val isPro = ProGate.isProUnlocked(this)
        val installedPacks = if (isPro) modelPackManager.listInstalled() else emptyList()
        val scroll = ScrollView(this).apply { setBackgroundColor(skin.backgroundColor) }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
        }
        scroll.addView(container)

        container.addView(title("Orbit IME", skin))
        container.addView(paragraph("v0.23 扩大中英文领域词/真实频率数据，加入预计算下一词联想、三级模糊纠错、段落感知全文翻译和系统夜间模式。模型包管理器继续保留，但不会假装未接入的神经运行时已经可用。当前：${ProLicenseManager.licenseLabel(this)} · ${skin.name}。", skin))
        statusMessage?.let { container.addView(statusBox(it, skin)) }

        container.addView(section("输入法设置", skin))
        container.addView(paragraph("以下选择保存在本机。默认输入模式：${if (ImePreferences.inputMode(this) == ImePreferences.MODE_PINYIN) "拼音" else "English"}。", skin))
        container.addView(button("默认模式：${if (ImePreferences.inputMode(this) == ImePreferences.MODE_PINYIN) "拼音" else "English"}", skin) {
            val next = if (ImePreferences.inputMode(this) == ImePreferences.MODE_PINYIN) ImePreferences.MODE_ENGLISH else ImePreferences.MODE_PINYIN
            ImePreferences.setInputMode(this, next)
            render("默认输入模式已保存")
        })
        container.addView(button("打开输入法设置", skin) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        container.addView(button("显示输入法切换器", skin) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        container.addView(section("候选、长句、联想与模糊纠错", skin))
        container.addView(paragraph("最多 32 个中文/英文候选；连续拼音继续使用自适应 Beam + 1/2/3-gram。v0.23 加入 THUOCL 领域词、FrequencyWords 中英文真实频率层和 32 分片预计算联想资产，上屏后优先快速查下一词/短语，再由 N-gram 补充。", skin))
        container.addView(button("上屏后联想：${if (ImePreferences.associationsEnabled(this)) "开" else "关"}", skin) {
            ImePreferences.setAssociationsEnabled(this, !ImePreferences.associationsEnabled(this))
            render("联想设置已保存")
        })
        container.addView(button("模糊纠错：${fuzzyLabel()}", skin) {
            val next = when (ImePreferences.fuzzyLevel(this)) {
                ImePreferences.FUZZY_OFF -> ImePreferences.FUZZY_STANDARD
                ImePreferences.FUZZY_STANDARD -> ImePreferences.FUZZY_ENHANCED
                else -> ImePreferences.FUZZY_OFF
            }
            ImePreferences.setFuzzyLevel(this, next)
            render("模糊纠错已切换为 ${fuzzyLabel()}")
        })
        container.addView(paragraph("标准：高置信模糊音、转置、邻键和多按键；增强：再加入漏键补全、重复键折叠和第二层模糊变体。英文也使用同一强度设置。", skin))

        container.addView(section("固定短语 / 自定义短语", skin))
        container.addView(paragraph("快捷短语可以整体关闭；也可以只关闭 Orbit 内置短语，仅保留自己的中文/英文短语。", skin))
        container.addView(button("快捷短语：${if (ImePreferences.quickPhrasesEnabled(this)) "开" else "关"}", skin) {
            ImePreferences.setQuickPhrasesEnabled(this, !ImePreferences.quickPhrasesEnabled(this)); render("快捷短语设置已保存")
        })
        container.addView(button("内置短语：${if (ImePreferences.builtInPhrasesEnabled(this)) "显示" else "隐藏"}", skin) {
            ImePreferences.setBuiltInPhrasesEnabled(this, !ImePreferences.builtInPhrasesEnabled(this)); render("内置短语设置已保存")
        })
        val zhPhrase = editField("添加中文固定词/句子", skin)
        container.addView(zhPhrase)
        container.addView(button("添加中文短语", skin) {
            render(if (phraseStore.addPinyin(zhPhrase.text.toString())) "中文短语已保存" else "中文短语为空、过长或疑似敏感")
        })
        val enPhrase = editField("Add English quick phrase", skin)
        container.addView(enPhrase)
        container.addView(button("添加英文短语", skin) {
            render(if (phraseStore.addEnglish(enPhrase.text.toString())) "英文短语已保存" else "英文短语为空、过长或疑似敏感")
        })
        container.addView(paragraph("自定义中文 ${phraseStore.customPinyin().size} 条：${phraseStore.customPinyin().take(8).joinToString(" · ").ifBlank { "暂无" }}", skin))
        container.addView(paragraph("Custom English ${phraseStore.customEnglish().size}: ${phraseStore.customEnglish().take(8).joinToString(" · ").ifBlank { "none" }}", skin))
        container.addView(button("清空自定义短语", skin) { phraseStore.clearCustom(); render("自定义短语已清空，内置短语不受影响") })

        container.addView(section("选区编辑", skin))
        container.addView(paragraph("有选区时退格删除整段；空格、字母、候选、粘贴、Emoji、短语和译文直接替换选中内容；没有选区时才删除光标前一个 Unicode code point。", skin))

        container.addView(section("翻译键盘 · v0.23", skin))
        container.addView(paragraph("Free 仍提供单句本地翻译。v0.23 在精确词典之外新增动态规划长片段翻译和覆盖率判断；Pro 上下文/全文翻译会保留段落与换行，未覆盖片段保留原文而不是伪造。全文最长 ${LongFormTranslationEngine.MAX_SOURCE_CHARS} 字。", skin))
        if (contextTranslationAvailable) {
            container.addView(button("上下文翻译：${if (contextTranslationEnabled) "开" else "关"}", skin) {
                TranslationSettings.setContextTranslationEnabled(this, !contextTranslationEnabled)
                render("上下文翻译设置已保存")
            })
        } else {
            container.addView(paragraph("Free：单句翻译；Pro：上下文 + 选区全文/长文翻译。", skin))
        }
        container.addView(paragraph("第三方神经翻译包仍由 v0.22 模型包管理器安全安装。当前 APK 未捆绑 Bergamot/Marian 运行时，因此不会把模型包伪装成已可执行；本地神经 runtime 将在完成可重复 Android AAR/许可证验收后启用。", skin))

        container.addView(section("Orbit Pro", skin))
        container.addView(paragraph("Debug 版保留测试激活；Release 正式方向仍是签名许可证 token，APK 只放公钥。", skin))
        val proCode = editField("输入 Pro 激活码", skin)
        container.addView(proCode)
        container.addView(button(if (isPro) "Pro 已解锁" else "激活 Pro", skin) {
            render(ProLicenseManager.activate(this, proCode.text.toString()).message)
        })
        if (isPro) container.addView(button("退出 Pro 测试状态", skin) { ProLicenseManager.deactivate(this); render("已恢复 Free") })
        container.addView(paragraph("Pro：100,000 条个人学习上限、更高短语/剪贴板容量、Pro 宠物/装扮、上下文/长文翻译和本地模型包管理器。", skin))

        container.addView(section("Pro 本地模型包", skin))
        container.addView(paragraph("`.orbitpack` 通过系统文件选择器导入；安装前检查 manifest、LICENSE、NOTICE、SHA-256、路径安全、体积上限和免责声明，文件进入 App 私有目录。", skin))
        if (!isPro) {
            container.addView(paragraph("模型包管理器需要 Pro。基础输入与词典翻译不降级。", skin))
        } else {
            container.addView(button("导入本地 .orbitpack", skin) { launchModelPackPicker() })
            if (installedPacks.isEmpty()) {
                container.addView(paragraph("尚未安装模型包。", skin))
            } else {
                installedPacks.forEach { pack ->
                    val manifest = pack.manifest
                    container.addView(paragraph(
                        "${if (pack.enabled) "✓ " else ""}${manifest.displayName} · ${manifest.type.wireValue}/${manifest.runtime.wireValue} · ${manifest.license} · ${formatBytes(pack.installedBytes)}\n来源：${manifest.sourceUrl}\n${pack.runtimeStatus.label}",
                        skin,
                    ))
                    container.addView(button(if (pack.enabled) "停用 ${manifest.displayName}" else "设为首选 ${manifest.displayName}", skin) {
                        render(modelPackManager.setEnabled(manifest.packId, !pack.enabled).message)
                    })
                    container.addView(button("卸载 ${manifest.displayName}", skin) { confirmUninstallPack(pack) })
                }
            }
        }

        container.addView(section("模型来源参考", skin))
        container.addView(paragraph("仅提供上游来源，不代表 Orbit 自动下载或自动获得商用权；LICENSE/NOTICE 必须逐包核验。", skin))
        CuratedModelCatalog.entries.forEach { item ->
            container.addView(paragraph("${item.title} · ${item.category} · ${item.license}\n${item.commercialNote}\n${item.recommendation}", skin))
            container.addView(button("浏览器查看来源", skin) {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.sourceUrl))) }
                    .onFailure { render("无法打开浏览器：${item.sourceUrl}") }
            })
        }

        container.addView(section("表情 / 颜文字 / 贴图", skin))
        container.addView(paragraph("Unicode Emoji 17.0、项目颜文字、128 个本地宠物贴图，以及 image/png 直发 → 图片剪贴板 → Emoji 兼容链继续保留。", skin))

        container.addView(section("剪贴板", skin))
        container.addView(paragraph("Recent + Pinned 只在键盘窗口可见时监听系统剪贴板；IME 隐藏即移除监听。", skin))

        container.addView(section("可视化宠物", skin))
        val petPreview = PetAvatarV21View(this).apply { bind(petProfile, skin) }
        container.addView(petPreview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)).apply { setMargins(0, dp(4), 0, dp(10)) })
        container.addView(paragraph("当前宠物：${petProfile.petName}（${petProfile.species}） · ${petProfile.stageName} · Lv.${petProfile.level} · ${petProfile.moodLabel} · ${petProfile.equippedOutfitName ?: "无装扮"}。", skin))
        container.addView(button("今日签到", skin) { render(petRepository.checkIn().message) })
        container.addView(button("开蛋 / 随机领养", skin) { render(petRepository.adoptRandom().message) })
        container.addView(button("切换已有宠物", skin) { render(petRepository.switchToNextOwned().message) })
        container.addView(button("轮换装扮", skin) { render(petRepository.equipNextOutfit().message) })
        container.addView(button("查看宠物图鉴", skin) { render(petRepository.petCatalogLine()) })
        container.addView(button("查看装扮库", skin) { render(petRepository.outfitCatalogLine()) })
        container.addView(button(if (petProfile.displayMode == PetRepository.DISPLAY_HIDDEN) "显示键盘内宠物" else "隐藏键盘内宠物", skin) { render(petRepository.toggleHidden().message) })

        container.addView(section("个人学习库", skin))
        container.addView(paragraph("当前本地学习：${dictionaryStats.entryCount}/${dictionaryStats.maxEntries} 条，累计选择 ${dictionaryStats.totalFrequency} 次。Free 20,000；Pro 100,000。", skin))
        container.addView(button("压缩个人学习库", skin) { userDictionary.compactNow(); render("个人学习库已本地压缩") })
        container.addView(button("清空个人学习库", skin) { userDictionary.clear(); render("个人学习库已清空") })

        container.addView(section("外观 / 夜间模式", skin))
        container.addView(paragraph("当前：${SkinManager.appearanceLabel(this)}。默认跟随 Android 系统；可固定浅色、深色或 AMOLED 黑。手动选择下面的皮肤会切换为“自定义皮肤”。", skin))
        container.addView(button("显示模式：${SkinManager.appearanceLabel(this)}", skin) {
            val next = when (SkinManager.appearanceMode(this)) {
                SkinManager.APPEARANCE_SYSTEM -> SkinManager.APPEARANCE_LIGHT
                SkinManager.APPEARANCE_LIGHT -> SkinManager.APPEARANCE_DARK
                SkinManager.APPEARANCE_DARK -> SkinManager.APPEARANCE_AMOLED
                else -> SkinManager.APPEARANCE_SYSTEM
            }
            SkinManager.setAppearanceMode(this, next)
            render("显示模式已切换为 ${SkinManager.appearanceLabel(this)}")
        })

        container.addView(section("自定义皮肤", skin))
        OrbitSkins.all.forEach { option ->
            container.addView(skinButton(option, SkinManager.appearanceMode(this) == SkinManager.APPEARANCE_CUSTOM && SkinManager.selectedSkinId(this) == option.id, skin))
        }

        container.addView(section("隐私", skin))
        container.addView(paragraph("仍不申请 INTERNET、Accessibility、悬浮窗、RECORD_AUDIO 或外部存储权限。THUOCL/FrequencyWords 等数据只在构建机下载后打包，本机运行完全离线；翻译上下文仅在当前操作内存中处理。", skin))

        container.addView(paragraph("About · v0.23.0", skin))
        setContentView(scroll)
    }

    private fun fuzzyLabel(): String = when (ImePreferences.fuzzyLevel(this)) {
        ImePreferences.FUZZY_OFF -> "关闭"
        ImePreferences.FUZZY_ENHANCED -> "增强"
        else -> "标准"
    }

    private fun launchModelPackPicker() {
        if (!ProGate.isLocalModelPackManagerUnlocked(this)) {
            render("需要 Pro 才能导入本地模型包")
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_MODEL_PACK)
    }

    private fun stageModelPack(uri: Uri) {
        render("正在校验模型包 SHA-256、许可证和压缩包安全性…")
        Thread {
            val result = runCatching { modelPackManager.stage(uri) }
            runOnUiThread {
                result.onSuccess { showModelPackDisclaimer(it) }
                    .onFailure { render("模型包校验失败：${it.message ?: it.javaClass.simpleName}") }
            }
        }.start()
    }

    private fun showModelPackDisclaimer(staged: ModelPackManager.StagedPack) {
        val m = staged.manifest
        val warningText = staged.warnings.joinToString("\n") { "• $it" }.ifBlank { "• 无额外警告" }
        val message = buildString {
            append("模型：${m.displayName}\n类型：${m.type.wireValue} / ${m.runtime.wireValue}\n上游：${m.modelName}\n")
            append("来源：${m.sourceUrl}\n许可证：${m.license}\n商用声明：${m.commercialSummary}\n再分发：${m.redistribution}\n")
            append("语言：${m.languages.joinToString()}\n包体：${formatBytes(staged.packedBytes)}；解包约 ${formatBytes(staged.unpackedBytes)}；${staged.fileCount} 个校验文件\n")
            append("最低/建议内存：${m.minRamMb}/${m.recommendedRamMb} MB\n权限声明：${m.permissionSummary}\n\n警告：\n$warningText\n\n")
            append("LICENSE 摘要：\n${staged.licensePreview}\n\nNOTICE 摘要：\n${staged.noticePreview}\n\n")
            append("免责声明：第三方模型可能错误、偏见、耗电、发热或不适合高风险用途。许可证/商用/再分发义务由模型来源和权利人决定，Orbit 的完整性校验不构成法律意见。音色克隆只能用于本人声音或已获明确授权的声音。当前版本不会假装尚未接入的神经 runtime 可执行。")
        }
        var handled = false
        AlertDialog.Builder(this)
            .setTitle("安装第三方模型包？")
            .setMessage(message)
            .setNegativeButton("取消") { _, _ -> handled = true; modelPackManager.discard(staged) }
            .setPositiveButton("我已了解并安装") { _, _ -> handled = true; installStagedModelPack(staged) }
            .setOnCancelListener { if (!handled) modelPackManager.discard(staged) }
            .show()
    }

    private fun installStagedModelPack(staged: ModelPackManager.StagedPack) {
        render("正在安装 ${staged.manifest.displayName} 到 App 私有目录…")
        Thread {
            val result = modelPackManager.install(staged)
            runOnUiThread { render(result.message) }
        }.start()
    }

    private fun confirmUninstallPack(pack: ModelPackManager.InstalledPack) {
        AlertDialog.Builder(this)
            .setTitle("卸载模型包？")
            .setMessage("将删除 App 私有目录中的 ${pack.manifest.displayName}（约 ${formatBytes(pack.installedBytes)}）。")
            .setNegativeButton("取消", null)
            .setPositiveButton("卸载") { _, _ -> render(modelPackManager.uninstall(pack.manifest.packId).message) }
            .show()
    }

    private fun title(text: String, skin: OrbitSkin): TextView = TextView(this).apply {
        this.text = text; setTextColor(skin.textColor); textSize = 30f; gravity = Gravity.START; typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(14))
    }
    private fun section(text: String, skin: OrbitSkin): TextView = TextView(this).apply {
        this.text = text; setTextColor(skin.accentColor); textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(22), 0, dp(8))
    }
    private fun paragraph(text: String, skin: OrbitSkin): TextView = TextView(this).apply {
        this.text = text; setTextColor(skin.mutedTextColor); textSize = 15f; lineSpacing = dp(2).toFloat(); setPadding(0, 0, 0, dp(8))
    }
    private fun editField(hint: String, skin: OrbitSkin): EditText = EditText(this).apply {
        this.hint = hint; setHintTextColor(skin.mutedTextColor); setTextColor(skin.textColor); textSize = 15f; isSingleLine = true
        background = OrbitTheme.rounded(skin.panelColor, dp(12).toFloat(), skin.borderColor, dp(1)); setPadding(dp(12), dp(8), dp(12), dp(8))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(4), 0, dp(6)) }
    }
    private fun statusBox(text: String, skin: OrbitSkin): TextView = TextView(this).apply {
        this.text = text; setTextColor(skin.warningColor); textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER_VERTICAL
        background = OrbitTheme.rounded(skin.panelColor, dp(14).toFloat(), skin.warningColor, dp(1)); setPadding(dp(12), 0, dp(12), 0)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { setMargins(0, 0, 0, dp(10)) }
    }
    private fun skinButton(option: OrbitSkin, selected: Boolean, currentSkin: OrbitSkin): Button {
        val locked = option.isPro && !ProGate.isProUnlocked(this)
        val label = buildString { append(if (selected) "✓ " else ""); append(option.name); if (locked) append(" · Pro") }
        return button(label, currentSkin) {
            if (locked) render("${option.name} 需要 Pro") else { SkinManager.apply(this, option.id); render("已切换到 ${option.name}") }
        }.apply {
            background = OrbitTheme.rounded(if (selected) currentSkin.panelAltColor else currentSkin.panelColor, dp(14).toFloat(), if (selected) currentSkin.accentColor else currentSkin.borderColor, dp(1))
            setTextColor(if (locked) currentSkin.mutedTextColor else currentSkin.textColor)
        }
    }
    private fun button(text: String, skin: OrbitSkin, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text; setTextColor(skin.textColor); textSize = 15f
        background = OrbitTheme.rounded(skin.panelAltColor, dp(14).toFloat(), skin.accentColor, dp(1)); setOnClickListener { onClick() }
        setPadding(dp(12), dp(8), dp(12), dp(8))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(4), 0, dp(6)) }
    }
    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object { private const val REQUEST_MODEL_PACK = 2201 }
}
