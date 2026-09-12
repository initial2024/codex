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
        val scroll = ScrollView(this).apply { setBackgroundColor(skin.backgroundColor) }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
        }
        scroll.addView(container)

        container.addView(title("Orbit IME", skin))
        container.addView(paragraph("隐私优先的本地输入法。v0.17 在 v0.16 长句输入、成熟词库、剪贴板和本地翻译基础上，补齐可视化宠物、Emoji/颜文字和本地宠物贴图。当前皮肤：${skin.name}。", skin))
        statusMessage?.let { container.addView(statusBox(it, skin)) }

        container.addView(section("输入法设置", skin))
        container.addView(paragraph("先在系统输入法设置中启用 Orbit IME。键盘顶部有“切换”按钮，可以打开系统输入法切换器。Android 左下角自带的小地球/切换气泡不是 Orbit 自己绘制的。", skin))
        container.addView(button("打开输入法设置", skin) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        container.addView(button("显示输入法切换器", skin) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        container.addView(section("本地输入引擎", skin))
        container.addView(paragraph("默认构建把 AOSP PinyinIME 词频数据与经过保守拼音推导的 Jieba MIT 高频中文词典合并为 .odict 分片；英文使用 ESDB/SCOWL 本地词表。候选综合词频、1/2/3-gram、拼音切分、beam search、纠错惩罚和个人本地选择频次排序。", skin))

        container.addView(section("长句拼音输入", skin))
        container.addView(paragraph("支持26键连续拼音，不要求每个词停顿上屏。长句搜索会根据输入长度自适应收窄 Beam 和分词路径，并复用查询/词组缓存，避免句子越长开销无限扩大。按键时优先只刷新候选面板。", skin))

        container.addView(section("表情 / 颜文字 / 贴图", skin))
        container.addView(paragraph("键盘顶部新增“表情/Emoji”入口。内置多分类 Emoji、手势、爱心、动物、食物、气氛、符号，以及开心/难过/生气/搞怪/喜欢等大量颜文字；支持最近使用和分页。长按文本表情可复制。", skin))
        container.addView(paragraph("“🪐 贴图”提供 8 个宠物 × 3 种情绪，共 24 个本地生成 PNG 宠物贴图。目标 App 支持 image/png 输入时直接提交图片；不支持时自动回退到对应 Emoji，不会点了无反应。贴图运行时在本机缓存生成，不联网、不申请外部存储。", skin))

        container.addView(section("快捷短语", skin))
        container.addView(paragraph("空闲状态会显示快捷短语栏。拼音模式显示中文短语，英文模式显示英文短语；若宠物未隐藏，栏内同时显示一个可点击的小宠物。", skin))

        container.addView(section("剪贴板", skin))
        container.addView(paragraph("键盘窗口可见时，Orbit 会把系统当前复制的非敏感文字加入本地“最近”历史；未固定记录约 1 小时后自动过期。长按条目可以固定/取消固定，固定项不会自动过期。支持清最近和清空全部。Orbit 不在后台采集剪贴板，也不能替换微信、QQ 或 Android 系统长按菜单。", skin))

        container.addView(section("翻译键盘", skin))
        container.addView(paragraph("点击“翻译”后进入本地翻译键盘模式。中→英时继续用拼音输入中文，候选进入翻译缓冲区，面板显示“原文 / 译文”；英→中同理。可直接点“译文上屏”，也可以用前一句、选中文本、剪贴板作为来源。覆盖不足时明确提示，不把提示词冒充译文。", skin))

        container.addView(section("可视化宠物", skin))
        val petPreview = PetAvatarView(this).apply { bind(petProfile, skin) }
        container.addView(
            petPreview,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)).apply { setMargins(0, dp(4), 0, dp(10)) },
        )
        container.addView(paragraph("当前宠物：${petProfile.petName}（${petProfile.species}），${petProfile.stageName}，Lv.${petProfile.level}，${petProfile.exp} EXP，${petProfile.stars} Stars。今日 ${petProfile.todayTypedChars} 字，累计 ${petProfile.totalTypedChars} 字，心情：${petProfile.moodLabel}，装扮：${petProfile.equippedOutfitName ?: "无"}。", skin))
        container.addView(paragraph("v0.17 不再只是文字状态：8 个宠物都有独立轮廓，成长阶段会改变体量/光环/轨道细节，已装备装扮会叠加到宠物图形上。键盘顶部“宠物/Pet”会打开大预览；未隐藏时空闲栏也有小宠物入口。", skin))
        container.addView(button("今日签到", skin) { render(petRepository.checkIn().message) })
        container.addView(button("开蛋 / 随机领养", skin) { render(petRepository.adoptRandom().message) })
        container.addView(button("切换已有宠物", skin) { render(petRepository.switchToNextOwned().message) })
        container.addView(button("轮换装扮", skin) { render(petRepository.equipNextOutfit().message) })
        container.addView(button("查看宠物图鉴", skin) { render(petRepository.petCatalogLine()) })
        container.addView(button("查看装扮库", skin) { render(petRepository.outfitCatalogLine()) })
        container.addView(button(if (petProfile.displayMode == PetRepository.DISPLAY_HIDDEN) "显示键盘内宠物" else "隐藏键盘内宠物", skin) { render(petRepository.toggleHidden().message) })
        container.addView(button("重置宠物本地数据", skin) { petRepository.clear(); render("宠物本地数据已重置") })

        container.addView(section("用户词库", skin))
        container.addView(paragraph("当前本地词库：${dictionaryStats.entryCount}/${dictionaryStats.maxEntries} 条映射，累计选择 ${dictionaryStats.totalFrequency} 次。候选上屏后只在本机记录拼音、候选文本、频次和更新时间；支持较长个人短语/句子学习，但仍不保存完整聊天历史。", skin))
        container.addView(button("清空用户词库", skin) { userDictionary.clear(); render("用户本地词库已清空") })

        container.addView(section("皮肤", skin))
        container.addView(paragraph("皮肤选择只保存在本机。宠物和贴图渲染会沿用当前皮肤的强调色。Pro Aurora 是付费占位皮肤，当前未接支付，所以默认锁定。", skin))
        OrbitSkins.all.forEach { option -> container.addView(skinButton(option, SkinManager.selectedSkinId(this) == option.id, skin)) }

        container.addView(section("隐私", skin))
        container.addView(paragraph("不申请 INTERNET 权限；不上传输入内容；不接广告和 analytics；密码输入框会隐藏 Hub、宠物、表情、翻译和剪贴板工具。宠物贴图由本机临时 ContentProvider 只读提供，Provider 不导出，只有用户主动提交图片内容时才授予目标输入框临时读取权限。", skin))

        container.addView(section("高级功能", skin))
        container.addView(paragraph("Pro 入口只做占位：更多宠物、更多装扮、更高词库/剪贴板额度和更大的离线翻译包。当前版本没有支付、广告或联网逻辑。", skin))
        container.addView(paragraph("About · v0.17.0", skin))
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

    private fun statusBox(text: String, skin: OrbitSkin): TextView = TextView(this).apply {
        this.text = text; setTextColor(skin.warningColor); textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER_VERTICAL
        background = OrbitTheme.rounded(skin.panelColor, dp(14).toFloat(), skin.warningColor, dp(1)); setPadding(dp(12), 0, dp(12), 0)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { setMargins(0, 0, 0, dp(10)) }
    }

    private fun skinButton(option: OrbitSkin, selected: Boolean, currentSkin: OrbitSkin): Button {
        val locked = option.isPro && !ProGate.isProUnlocked(this)
        val label = buildString { append(if (selected) "✓ " else ""); append(option.name); if (locked) append(" · Pro 占位") }
        return button(label, currentSkin) {
            if (locked) render("${option.name} 是 Pro 皮肤占位。当前版本没有支付逻辑，所以暂不解锁。")
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
