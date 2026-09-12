package com.ccwu.orbitime

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
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
        val scroll = ScrollView(this).apply { setBackgroundColor(skin.backgroundColor) }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
        }
        scroll.addView(container)

        container.addView(title("Orbit IME", skin))
        container.addView(paragraph("v0.21 重点修复选中文本的替换/删除，增加可记忆设置、自定义快捷短语、Pro 长文翻译入口，并重新整理宠物装扮视觉。当前：${ProLicenseManager.licenseLabel(this)} · ${skin.name}。", skin))
        statusMessage?.let { container.addView(statusBox(it, skin)) }

        container.addView(section("输入法设置", skin))
        container.addView(paragraph("所有下面的选择都保存在本机，重新打开键盘仍会保留。默认输入模式：${if (ImePreferences.inputMode(this) == ImePreferences.MODE_PINYIN) "拼音" else "English"}。", skin))
        container.addView(button("默认模式：${if (ImePreferences.inputMode(this) == ImePreferences.MODE_PINYIN) "拼音" else "English"}", skin) {
            val next = if (ImePreferences.inputMode(this) == ImePreferences.MODE_PINYIN) ImePreferences.MODE_ENGLISH else ImePreferences.MODE_PINYIN
            ImePreferences.setInputMode(this, next)
            render("默认输入模式已保存")
        })
        container.addView(button("打开输入法设置", skin) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        container.addView(button("显示输入法切换器", skin) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        container.addView(section("固定短语 / 自定义短语", skin))
        container.addView(paragraph("快捷短语可以整体关闭；也可以只关闭 Orbit 内置短语，仅保留你自己的短语。中文和英文自定义内容分别保存。", skin))
        container.addView(button("快捷短语：${if (ImePreferences.quickPhrasesEnabled(this)) "开" else "关"}", skin) {
            ImePreferences.setQuickPhrasesEnabled(this, !ImePreferences.quickPhrasesEnabled(this))
            render("快捷短语设置已保存")
        })
        container.addView(button("内置短语：${if (ImePreferences.builtInPhrasesEnabled(this)) "显示" else "隐藏"}", skin) {
            ImePreferences.setBuiltInPhrasesEnabled(this, !ImePreferences.builtInPhrasesEnabled(this))
            render("内置短语设置已保存")
        })
        container.addView(button("上屏后联想：${if (ImePreferences.associationsEnabled(this)) "开" else "关"}", skin) {
            ImePreferences.setAssociationsEnabled(this, !ImePreferences.associationsEnabled(this))
            render("联想设置已保存")
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
        container.addView(paragraph("v0.21 按 Android InputConnection 语义处理选区：有选区时退格删除整段；空格、字母、候选、粘贴、Emoji、短语和译文使用 commit/setComposing 直接替换选中内容；没有选区时才删除光标前一个 Unicode code point。", skin))

        container.addView(section("候选、长句与联想", skin))
        container.addView(paragraph("保留最多 32 个中文/英文候选、连续长句拼音、自适应 Beam、1/2/3-gram、模糊纠错、软件/平台词和四字词增强。候选上屏后的本地下一词联想可以单独关闭。", skin))

        container.addView(section("翻译键盘", skin))
        container.addView(paragraph("普通用户只提供单句本地翻译。Pro 可开启前两句上下文参考，并增加“全文/长文翻译”：先在目标 App 全选或选择一段文本，再点全文翻译，Orbit 在本机按句切分翻译，最长 ${LongFormTranslationEngine.MAX_SOURCE_CHARS} 字；未覆盖句会保留原文，不假装成功。", skin))
        if (contextTranslationAvailable) {
            container.addView(button("上下文翻译：${if (contextTranslationEnabled) "开" else "关"}", skin) {
                TranslationSettings.setContextTranslationEnabled(this, !contextTranslationEnabled)
                render("上下文翻译设置已保存")
            })
        } else {
            container.addView(paragraph("Free：单句翻译；Pro：上下文 + 选区全文/长文翻译。", skin))
        }

        container.addView(section("Orbit Pro", skin))
        container.addView(paragraph("当前方案不采用可随意转发的明文邀请码作为正式授权。Debug 版先保留本地测试激活码，方便你验证 Pro 功能；Release 正式版应切换为“签名许可证码”，APK 只放公钥，私钥永远不进仓库/安装包。", skin))
        val proCode = editField("输入 Pro 激活码", skin)
        container.addView(proCode)
        container.addView(button(if (isPro) "Pro 已解锁" else "激活 Pro", skin) {
            val result = ProLicenseManager.activate(this, proCode.text.toString())
            render(result.message)
        })
        if (isPro) container.addView(button("退出 Pro 测试状态", skin) { ProLicenseManager.deactivate(this); render("已恢复 Free") })
        container.addView(paragraph("Pro 当前解锁：100,000 条个人学习上限、更高短语/剪贴板容量、Pro 宠物/装扮、上下文翻译和选区长文翻译。基础输入质量、核心词库和单句翻译不做人为降级。", skin))

        container.addView(section("表情 / 颜文字 / 贴图", skin))
        container.addView(paragraph("保留 Unicode Emoji 17.0、项目颜文字、128 个本地宠物贴图，以及 image/png 直发 → 图片剪贴板 → Emoji 的兼容链。", skin))

        container.addView(section("剪贴板", skin))
        container.addView(paragraph("Recent + Pinned 仍只在键盘窗口可见时监听系统剪贴板；IME 隐藏即移除监听。", skin))

        container.addView(section("可视化宠物 v0.21", skin))
        val petPreview = PetAvatarV21View(this).apply { bind(petProfile, skin) }
        container.addView(petPreview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)).apply { setMargins(0, dp(4), 0, dp(10)) })
        container.addView(paragraph("当前宠物：${petProfile.petName}（${petProfile.species}） · ${petProfile.stageName} · Lv.${petProfile.level} · ${petProfile.moodLabel} · ${petProfile.equippedOutfitName ?: "无装扮"}。", skin))
        container.addView(paragraph("v0.21 参考桌宠的“角色主体 + 行为状态 + 少量可辨识装扮”思路：保留稳定宠物轮廓，移除旧的叠加式装扮绘制，改成统一比例的星环、轻量护目镜、学者帽、披肩、丝带、轨道、微光和尾迹，并加低频呼吸/漂浮动画。", skin))
        container.addView(button("今日签到", skin) { render(petRepository.checkIn().message) })
        container.addView(button("开蛋 / 随机领养", skin) { render(petRepository.adoptRandom().message) })
        container.addView(button("切换已有宠物", skin) { render(petRepository.switchToNextOwned().message) })
        container.addView(button("轮换装扮", skin) { render(petRepository.equipNextOutfit().message) })
        container.addView(button("查看宠物图鉴", skin) { render(petRepository.petCatalogLine()) })
        container.addView(button("查看装扮库", skin) { render(petRepository.outfitCatalogLine()) })
        container.addView(button(if (petProfile.displayMode == PetRepository.DISPLAY_HIDDEN) "显示键盘内宠物" else "隐藏键盘内宠物", skin) { render(petRepository.toggleHidden().message) })

        container.addView(section("个人学习库", skin))
        container.addView(paragraph("当前本地学习：${dictionaryStats.entryCount}/${dictionaryStats.maxEntries} 条，累计选择 ${dictionaryStats.totalFrequency} 次。Free 20,000；Pro 100,000；app 私有文件 + journal + 压缩。", skin))
        container.addView(button("压缩个人学习库", skin) { userDictionary.compactNow(); render("个人学习库已本地压缩") })
        container.addView(button("清空个人学习库", skin) { userDictionary.clear(); render("个人学习库已清空") })

        container.addView(section("皮肤", skin))
        OrbitSkins.all.forEach { option -> container.addView(skinButton(option, SkinManager.selectedSkinId(this) == option.id, skin)) }

        container.addView(section("隐私", skin))
        container.addView(paragraph("不申请 INTERNET、Accessibility、悬浮窗或外部存储权限；不上传输入内容。上下文和长文翻译文本只在当前操作内存中处理，不进入个人词库。", skin))

        container.addView(paragraph("About · v0.21.0", skin))
        setContentView(scroll)
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
        this.hint = hint
        setHintTextColor(skin.mutedTextColor)
        setTextColor(skin.textColor)
        textSize = 15f
        isSingleLine = true
        background = OrbitTheme.rounded(skin.panelColor, dp(12).toFloat(), skin.borderColor, dp(1))
        setPadding(dp(12), dp(8), dp(12), dp(8))
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
            if (locked) render("${option.name} 需要 Pro")
            else { SkinManager.apply(this, option.id); render("已切换到 ${option.name}") }
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
