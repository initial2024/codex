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
        container.addView(paragraph("隐私优先的本地输入法。v0.16 重点提升长句连续输入、成熟词库、剪贴板历史和本地翻译键盘体验。当前皮肤：${skin.name}。", skin))
        statusMessage?.let { container.addView(statusBox(it, skin)) }

        container.addView(section("输入法设置", skin))
        container.addView(paragraph("先在系统输入法设置中启用 Orbit IME。键盘顶部有“切换”按钮，可以打开系统输入法切换器。Android 左下角自带的小地球/切换气泡不是 Orbit 自己绘制的。", skin))
        container.addView(button("打开输入法设置", skin) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        container.addView(button("显示输入法切换器", skin) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        container.addView(section("本地输入引擎", skin))
        container.addView(paragraph("v0.16 默认构建会把 AOSP PinyinIME 词频数据与经过保守拼音推导的 Jieba MIT 高频中文词典合并为 .odict 分片；英文继续使用 ESDB/SCOWL 本地词表。候选综合词频、1/2/3-gram、拼音切分、beam search、纠错惩罚和个人本地选择频次排序。", skin))

        container.addView(section("长句拼音输入", skin))
        container.addView(paragraph("支持26键连续拼音，不要求每个词停顿上屏。拼音缓冲提高到长句级长度，短语搜索跨度和 beam 宽度也已扩大；按键时优先只刷新候选面板，不再每个字母重建整个键盘。示例：nihaoma、nishishei、shurufa 等会自动切分并组合句子候选。", skin))

        container.addView(section("快捷短语", skin))
        container.addView(paragraph("空闲状态会显示快捷短语栏。拼音模式显示中文短语，英文模式显示英文短语；短语库覆盖常用沟通、开发、学习和日常回复。", skin))

        container.addView(section("剪贴板", skin))
        container.addView(paragraph("键盘窗口可见时，Orbit 会把系统当前复制的非敏感文字加入本地“最近”历史；未固定记录约 1 小时后自动过期。长按条目可以固定/取消固定，固定项不会自动过期。支持清最近和清空全部。Orbit 不在后台采集剪贴板，也不能替换微信、QQ 或 Android 系统长按菜单。", skin))

        container.addView(section("翻译键盘", skin))
        container.addView(paragraph("点击“翻译”后进入本地翻译键盘模式。中→英时继续用拼音输入中文，候选先进入翻译缓冲区，面板显示“原文 / 译文”；英→中同理。可直接点“译文上屏”，也可以用前一句、选中文本、剪贴板作为来源。翻译先查精确短句库，再做本地最长短语切分；覆盖不足时会明确提示，不会把提示词冒充译文。", skin))

        container.addView(section("宠物", skin))
        container.addView(paragraph("当前宠物：${petProfile.petName}（${petProfile.species}），${petProfile.stageName}，Lv.${petProfile.level}，${petProfile.exp} EXP，${petProfile.stars} Stars。今日 ${petProfile.todayTypedChars} 字，累计 ${petProfile.totalTypedChars} 字，心情：${petProfile.moodLabel}，装扮：${petProfile.equippedOutfitName ?: "无"}。", skin))
        container.addView(paragraph("键盘顶部有“宠物/Pet”入口。打开后可签到、开蛋、切换已有宠物、轮换装扮、查看图鉴和隐藏。每天第一次开蛋免费，之后每次 30 Stars。宠物数据只保存在本机。", skin))
        container.addView(button("今日签到", skin) { render(petRepository.checkIn().message) })
        container.addView(button("开蛋 / 随机领养", skin) { render(petRepository.adoptRandom().message) })
        container.addView(button("切换已有宠物", skin) { render(petRepository.switchToNextOwned().message) })
        container.addView(button("轮换装扮", skin) { render(petRepository.equipNextOutfit().message) })
        container.addView(button("查看宠物图鉴", skin) { render(petRepository.petCatalogLine()) })
        container.addView(button("查看装扮库", skin) { render(petRepository.outfitCatalogLine()) })
        container.addView(button(if (petProfile.displayMode == PetRepository.DISPLAY_HIDDEN) "显示键盘内宠物" else "隐藏键盘内宠物", skin) { render(petRepository.toggleHidden().message) })
        container.addView(button("重置宠物本地数据", skin) { petRepository.clear(); render("宠物本地数据已重置") })

        container.addView(section("用户词库", skin))
        container.addView(paragraph("当前本地词库：${dictionaryStats.entryCount}/${dictionaryStats.maxEntries} 条映射，累计选择 ${dictionaryStats.totalFrequency} 次。候选上屏后只在本机记录拼音、候选文本、频次和更新时间；v0.16 支持更长的个人短语/句子学习，但仍不保存完整聊天历史。", skin))
        container.addView(button("清空用户词库", skin) { userDictionary.clear(); render("用户本地词库已清空") })

        container.addView(section("皮肤", skin))
        container.addView(paragraph("皮肤选择只保存在本机。重新拉起键盘后输入法界面会使用同一套皮肤。Pro Aurora 是付费占位皮肤，当前未接支付，所以默认锁定。", skin))
        OrbitSkins.all.forEach { option -> container.addView(skinButton(option, SkinManager.selectedSkinId(this) == option.id, skin)) }

        container.addView(section("隐私", skin))
        container.addView(paragraph("不申请 INTERNET 权限；不上传输入内容；不接广告和 analytics；密码输入框会隐藏 Hub、宠物、翻译和剪贴板工具。成熟词库只在构建机下载后打包进 APK，运行时输入法不联网。", skin))

        container.addView(section("高级功能", skin))
        container.addView(paragraph("Pro 入口只做占位：更多宠物、更多装扮、更高词库/剪贴板额度和更大的离线翻译包。当前版本没有支付、广告或联网逻辑。", skin))
        container.addView(paragraph("About · v0.16.0", skin))
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
