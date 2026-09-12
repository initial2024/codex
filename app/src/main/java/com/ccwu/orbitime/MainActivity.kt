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
        container.addView(paragraph("隐私优先的本地输入法。v0.18 重点修复候选上屏追加原始拼音的问题，并扩大中文、英文、翻译、Emoji、符号和模糊联想数据。当前皮肤：${skin.name}。", skin))
        statusMessage?.let { container.addView(statusBox(it, skin)) }

        container.addView(section("输入法设置", skin))
        container.addView(paragraph("先在系统输入法设置中启用 Orbit IME。键盘顶部有“切换”按钮，可以打开系统输入法切换器。Android 左下角自带的小地球/切换气泡不是 Orbit 自己绘制的。", skin))
        container.addView(button("打开输入法设置", skin) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        container.addView(button("显示输入法切换器", skin) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        container.addView(section("本地输入引擎", skin))
        container.addView(paragraph("v0.18 默认构建合并 AOSP PinyinIME、Jieba 和 CC-CEDICT 中文数据；英文使用 ESDB/SCOWL en_US-large。候选综合词频、1/2/3-gram、拼音切分、Beam Search、个人学习、前缀联想以及低置信度模糊/误按纠错。", skin))

        container.addView(section("候选替换与长句", skin))
        container.addView(paragraph("中文/英文候选现在直接覆盖 Android composing 区，不再先提交原始拼音再追加候选。支持 26 键连续长句拼音，长句搜索会按输入长度自适应收窄 Beam 和分词路径，并复用查询/词组缓存。", skin))

        container.addView(section("模糊联想", skin))
        container.addView(paragraph("模糊候选会实际查询成熟本地大词库，包括 zh/z、ch/c、sh/s、n/l、f/h、r/l、u/v 等兼容变体，以及相邻键误按、相邻字母交换、重复/多按一个键和拼音前缀联想。精确候选始终比猜测候选优先。", skin))

        container.addView(section("长按与符号", skin))
        container.addView(paragraph("26 键会显示长按提示：q~p 对应 1~0，其他字母对应 @、#、$、%、&、括号、感叹号、问号等常用符号。123 键盘扩充为常用、标点、括号、数学、货币、箭头、标记 7 页，可通过“符号”键轮换。", skin))

        container.addView(section("表情 / 颜文字 / 贴图", skin))
        container.addView(paragraph("“表情/Emoji”保留项目内置分类与大量颜文字，并增加构建时打包的 Unicode 17.0 fully-qualified Emoji 全量页、最近使用和分页。长按文本表情可复制。", skin))
        container.addView(paragraph("“🪐 贴图”提供 8 个宠物 × 3 种情绪，共 24 个本地生成 PNG 宠物贴图。支持图片内容的目标 App 直接接收 PNG；不支持时自动退回对应 Emoji。", skin))

        container.addView(section("剪贴板", skin))
        container.addView(paragraph("键盘窗口可见时，Orbit 会把系统当前复制的非敏感文字加入本地“最近”历史；未固定记录约 1 小时后自动过期。长按条目可以固定/取消固定。Orbit 不在后台采集剪贴板。", skin))

        container.addView(section("翻译键盘", skin))
        container.addView(paragraph("翻译运行时仍完全离线。v0.18 在项目短句表之外接入 CC-CEDICT 分片词典：先做精确词/短语查找，再做保守最长词组拼接；覆盖不足时明确提示，不把提示词冒充译文。", skin))

        container.addView(section("可视化宠物", skin))
        val petPreview = PetAvatarView(this).apply { bind(petProfile, skin) }
        container.addView(petPreview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)).apply { setMargins(0, dp(4), 0, dp(10)) })
        container.addView(paragraph("当前宠物：${petProfile.petName}（${petProfile.species}），${petProfile.stageName}，Lv.${petProfile.level}，${petProfile.exp} EXP，${petProfile.stars} Stars。今日 ${petProfile.todayTypedChars} 字，累计 ${petProfile.totalTypedChars} 字，心情：${petProfile.moodLabel}，装扮：${petProfile.equippedOutfitName ?: "无"}。", skin))
        container.addView(paragraph("8 个宠物都有独立轮廓，成长阶段会改变体量/光环/轨道细节，装扮会实际叠加到宠物图形上。", skin))
        container.addView(button("今日签到", skin) { render(petRepository.checkIn().message) })
        container.addView(button("开蛋 / 随机领养", skin) { render(petRepository.adoptRandom().message) })
        container.addView(button("切换已有宠物", skin) { render(petRepository.switchToNextOwned().message) })
        container.addView(button("轮换装扮", skin) { render(petRepository.equipNextOutfit().message) })
        container.addView(button("查看宠物图鉴", skin) { render(petRepository.petCatalogLine()) })
        container.addView(button("查看装扮库", skin) { render(petRepository.outfitCatalogLine()) })
        container.addView(button(if (petProfile.displayMode == PetRepository.DISPLAY_HIDDEN) "显示键盘内宠物" else "隐藏键盘内宠物", skin) { render(petRepository.toggleHidden().message) })
        container.addView(button("重置宠物本地数据", skin) { petRepository.clear(); render("宠物本地数据已重置") })

        container.addView(section("用户词库", skin))
        container.addView(paragraph("当前本地词库：${dictionaryStats.entryCount}/${dictionaryStats.maxEntries} 条映射，累计选择 ${dictionaryStats.totalFrequency} 次。只保存拼音、候选文本、频次和更新时间，不保存完整聊天历史。", skin))
        container.addView(button("清空用户词库", skin) { userDictionary.clear(); render("用户本地词库已清空") })

        container.addView(section("皮肤", skin))
        container.addView(paragraph("皮肤选择只保存在本机。宠物和贴图渲染会沿用当前皮肤的强调色。Pro Aurora 是占位皮肤，当前未接支付。", skin))
        OrbitSkins.all.forEach { option -> container.addView(skinButton(option, SkinManager.selectedSkinId(this) == option.id, skin)) }

        container.addView(section("隐私", skin))
        container.addView(paragraph("不申请 INTERNET 权限；不上传输入内容；不接广告和 analytics；密码输入框会隐藏宠物、表情、翻译和剪贴板工具。大词库只在构建机下载并打包进 APK，运行时不联网。", skin))

        container.addView(section("数据许可", skin))
        container.addView(paragraph("AOSP、Jieba、ESDB/SCOWL、CC-CEDICT 和 Unicode Emoji 数据分别保留独立来源与许可证说明。CC-CEDICT 派生词典/翻译数据仍按 CC BY-SA 4.0 处理，不与应用代码许可证混淆。", skin))

        container.addView(paragraph("About · v0.18.0", skin))
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
