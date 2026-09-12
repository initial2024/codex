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
        val contextTranslationAvailable = TranslationSettings.isContextTranslationAvailable(this)
        val contextTranslationEnabled = TranslationSettings.isContextTranslationEnabled(this)
        val scroll = ScrollView(this).apply { setBackgroundColor(skin.backgroundColor) }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
        }
        scroll.addView(container)

        container.addView(title("Orbit IME", skin))
        container.addView(paragraph("隐私优先的本地输入法。v0.20 重点加强上下文翻译、上屏后的动态联想、候选数量、成语/常见软件词汇，以及微信/QQ等不支持 IME 图片直发时的贴图兼容路径。当前皮肤：${skin.name}。", skin))
        statusMessage?.let { container.addView(statusBox(it, skin)) }

        container.addView(section("输入法设置", skin))
        container.addView(paragraph("先在系统输入法设置中启用 Orbit IME。键盘顶部“切换”按钮打开系统输入法切换器；Android 左下角系统小地球/切换气泡不是 Orbit 自己绘制的。", skin))
        container.addView(button("打开输入法设置", skin) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        container.addView(button("显示输入法切换器", skin) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        container.addView(section("成熟本地数据", skin))
        container.addView(paragraph("正常构建必须经过 AOSP PinyinIME + Jieba + CC-CEDICT 中文、ESDB/SCOWL en_US-large 英文、Unicode Emoji 17.0 和 CC-CEDICT 双语翻译索引。v0.20 额外把 CC-CEDICT 中四字词/成语做高优先级层，并合并项目维护的常见软件、平台、AI、开发工具和系统名称。", skin))

        container.addView(section("候选、长句与联想", skin))
        container.addView(paragraph("中文/英文候选直接覆盖 Android composing 区，不先提交原始拼音。支持连续长句拼音、DP 切分、自适应 Beam、1/2/3-gram、前缀联想、低置信度模糊纠错和个人本地排序。v0.20 单次中文/英文候选池扩大到最多 32 个。", skin))
        container.addView(paragraph("候选上屏以后不再直接回到固定短语栏：中文模式会读取当前光标前的短上下文，用本地 2/3-gram 和项目高置信度规则生成下一词/下一短语联想，再用固定短语做兜底。", skin))

        container.addView(section("长按与符号", skin))
        container.addView(paragraph("26 键保留长按数字/符号提示；123 符号键盘保留常用、标点、括号、数学、货币、箭头、标记 7 页。", skin))

        container.addView(section("表情 / 颜文字 / 贴图", skin))
        container.addView(paragraph("表情面板包含 Unicode 17.0 全量页、项目分类 Emoji、数百级颜文字、最近使用和分页。当前宠物目录 16 种，每种 8 个贴图状态，共 128 个本地 PNG 贴图定义。", skin))
        container.addView(paragraph("发送贴图时先尝试 Android IME 的 image/png 直发；目标 App（例如部分微信/QQ版本）不开放该接口时，会把实际 PNG URI 复制到系统剪贴板并临时授权给当前 App，提示你在输入框长按粘贴。若目标 App 连图片剪贴板也不接受，最后才退回 Emoji。", skin))

        container.addView(section("剪贴板", skin))
        container.addView(paragraph("键盘窗口可见时，Orbit 会把系统当前复制的非敏感文字加入本地 Recent；支持 Pinned、固定/取消固定、清最近和清全部。IME 隐藏后移除监听，不做后台剪贴板采集。图片贴图 URI 不会被当成文字历史保存。", skin))

        container.addView(section("翻译键盘", skin))
        container.addView(paragraph("翻译完全离线：项目高质量短句 → CC-CEDICT 精确词/短语 → CC-CEDICT 分片最长匹配 → 本地 composer → 明确 unavailable。不会把提示词冒充译文。", skin))
        if (contextTranslationAvailable) {
            container.addView(paragraph("上下文翻译：${if (contextTranslationEnabled) "已开启" else "已关闭"}。开启后只在翻译时临时读取光标前最多两句作为本地上下文参考，不持久化上下文；当前句译文仍单独上屏，避免重复发送前文。", skin))
            container.addView(button(if (contextTranslationEnabled) "关闭上下文翻译" else "开启上下文翻译", skin) {
                TranslationSettings.setContextTranslationEnabled(this, !contextTranslationEnabled)
                render(if (contextTranslationEnabled) "上下文翻译已关闭" else "上下文翻译已开启")
            })
        } else {
            container.addView(paragraph("普通用户保持单句本地翻译。上下文翻译作为 Pro 可选能力占位：当前未接支付，因此普通用户不会读取前文参与翻译。", skin))
        }

        container.addView(section("可视化宠物", skin))
        val petPreview = PetAvatarView(this).apply { bind(petProfile, skin) }
        container.addView(petPreview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)).apply { setMargins(0, dp(4), 0, dp(10)) })
        container.addView(paragraph("当前宠物：${petProfile.petName}（${petProfile.species}），${petProfile.stageName}，Lv.${petProfile.level}，${petProfile.exp} EXP，${petProfile.stars} Stars。今日 ${petProfile.todayTypedChars} 字，累计 ${petProfile.totalTypedChars} 字，心情：${petProfile.moodLabel}，装扮：${petProfile.equippedOutfitName ?: "无"}。", skin))
        container.addView(paragraph("宠物目录 16 种，新增种类复用经过验证的 8 套本地图形骨架，确保每只都能实际显示；装扮 24 套并映射到可见的帽子、眼镜、披风、领结、轨道、光效和尾迹层。宠物会对选词、剪贴板、翻译、签到、开蛋、切换和换装给短时本地反馈。", skin))
        container.addView(button("今日签到", skin) { render(petRepository.checkIn().message) })
        container.addView(button("开蛋 / 随机领养", skin) { render(petRepository.adoptRandom().message) })
        container.addView(button("切换已有宠物", skin) { render(petRepository.switchToNextOwned().message) })
        container.addView(button("轮换装扮", skin) { render(petRepository.equipNextOutfit().message) })
        container.addView(button("查看宠物图鉴", skin) { render(petRepository.petCatalogLine()) })
        container.addView(button("查看装扮库", skin) { render(petRepository.outfitCatalogLine()) })
        container.addView(button(if (petProfile.displayMode == PetRepository.DISPLAY_HIDDEN) "显示键盘内宠物" else "隐藏键盘内宠物", skin) { render(petRepository.toggleHidden().message) })
        container.addView(button("重置宠物本地数据", skin) { petRepository.clear(); render("宠物本地数据已重置") })

        container.addView(section("个人学习库", skin))
        container.addView(paragraph("当前本地学习：${dictionaryStats.entryCount}/${dictionaryStats.maxEntries} 条，累计选择 ${dictionaryStats.totalFrequency} 次。免费容量 20,000 条，Pro 占位容量 100,000 条；存储为 app 私有文件 + 增量 journal + 定期压缩。", skin))
        container.addView(paragraph("学习记录仍只包含拼音、候选文本、频次和更新时间；不保存完整聊天、目标 App、输入框身份或完整按键流。旧版个人词库会在首次读取时自动迁移。", skin))
        container.addView(button("压缩个人学习库", skin) { userDictionary.compactNow(); render("个人学习库已本地压缩") })
        container.addView(button("清空个人学习库", skin) { userDictionary.clear(); render("个人学习库已清空") })

        container.addView(section("皮肤", skin))
        container.addView(paragraph("皮肤选择只保存在本机。宠物与贴图沿用当前皮肤强调色。Pro Aurora 仍是付费占位，当前未接支付。", skin))
        OrbitSkins.all.forEach { option -> container.addView(skinButton(option, SkinManager.selectedSkinId(this) == option.id, skin)) }

        container.addView(section("隐私", skin))
        container.addView(paragraph("不申请 INTERNET 权限；不上传输入内容；不接广告和 analytics；密码输入框隐藏宠物、表情、翻译和剪贴板工具。成熟数据只在构建机下载并打包进 APK。上下文翻译开启时读取的前两句只在内存使用，不进入用户词库。", skin))

        container.addView(section("数据许可", skin))
        container.addView(paragraph("AOSP、Jieba、ESDB/SCOWL、CC-CEDICT 和 Unicode Emoji 保留独立来源与许可证说明。成语增强直接从已纳入项目的 CC-CEDICT 数据派生，不额外引入来源不清的互联网成语库；常见软件/平台名称由 Orbit 项目维护。", skin))

        container.addView(paragraph("About · v0.20.0", skin))
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
