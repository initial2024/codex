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
        val scroll = ScrollView(this).apply {
            setBackgroundColor(skin.backgroundColor)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
        }
        scroll.addView(container)

        container.addView(title("Orbit IME", skin))
        container.addView(paragraph("隐私优先的本地输入法。支持英文、拼音26键、本地词库、剪贴板、翻译提示词、键盘内宠物和皮肤。当前皮肤：${skin.name}。", skin))
        statusMessage?.let {
            container.addView(statusBox(it, skin))
        }

        container.addView(section("输入法设置", skin))
        container.addView(paragraph("先在系统输入法设置中启用 Orbit IME。Android 会对所有第三方输入法显示风险提示，这是系统通用提醒。", skin))
        container.addView(button("打开输入法设置", skin) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        container.addView(button("显示输入法切换器", skin) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        })

        container.addView(section("拼音输入", skin))
        container.addView(paragraph("支持拼音26键、候选上屏、空格选首候选、本地词库排序和常用简拼联想。本地词库只记录你主动选择过的候选，不保存完整输入流。", skin))

        container.addView(section("剪贴板", skin))
        container.addView(paragraph("Orbit 只能管理自己键盘里的 Clips 面板，不能替换微信、QQ 或系统长按输入框弹出的原生菜单。复制文字后，打开 Orbit 的剪贴板面板并点击保存。", skin))

        container.addView(section("翻译", skin))
        container.addView(paragraph("免费功能是本地生成翻译提示词。Pro 离线包是本地短句包占位，不联网、不接外部翻译 API。当前状态：${if (ProGate.isOfflineTranslationPackUnlocked(this)) "已解锁" else "Pro 锁定"}。", skin))

        container.addView(section("宠物", skin))
        container.addView(paragraph("当前宠物：${petProfile.petName}，Stage ${petProfile.stage}，Lv.${petProfile.level}，${petProfile.exp} EXP，${petProfile.stars} Stars，连续签到 ${petProfile.checkInStreak} 天。宠物只在键盘内显示，不申请悬浮窗权限，不发通知，不播放声音。", skin))
        container.addView(button("今日签到", skin) {
            val result = petRepository.checkIn()
            render(result.message)
        })
        container.addView(button(if (petProfile.displayMode == PetRepository.DISPLAY_HIDDEN) "显示键盘内宠物" else "隐藏键盘内宠物", skin) {
            val result = petRepository.toggleHidden()
            render(result.message)
        })
        container.addView(button("开蛋 / 随机领养", skin) {
            val result = petRepository.adoptRandom()
            render(result.message)
        })
        container.addView(button("重置宠物本地数据", skin) {
            petRepository.clear()
            render("宠物本地数据已重置")
        })

        container.addView(section("用户词库", skin))
        container.addView(paragraph("当前本地词库：${dictionaryStats.entryCount}/${dictionaryStats.maxEntries} 条映射，累计选择 ${dictionaryStats.totalFrequency} 次。密码框、验证码、疑似密钥内容不会学习。", skin))
        container.addView(button("清空用户词库", skin) {
            userDictionary.clear()
            render("用户本地词库已清空")
        })

        container.addView(section("皮肤", skin))
        container.addView(paragraph("皮肤选择只保存在本机。重新拉起键盘后输入法界面会使用同一套皮肤。Pro Aurora 是付费占位皮肤，当前未接支付，所以默认锁定。", skin))
        OrbitSkins.all.forEach { option ->
            container.addView(skinButton(option, SkinManager.selectedSkinId(this) == option.id, skin))
        }

        container.addView(section("隐私", skin))
        container.addView(paragraph("不申请 INTERNET 权限；不上传输入内容；不接广告和 analytics；密码输入框会隐藏 Hub、宠物、翻译和剪贴板工具。", skin))

        container.addView(section("高级功能", skin))
        container.addView(paragraph("Pro 入口只做占位：更多宠物、更多装扮、更高词库/剪贴板额度和离线短句包。当前版本没有支付、广告或联网逻辑。", skin))

        container.addView(paragraph("About · v0.8.0", skin))

        setContentView(scroll)
    }

    private fun title(text: String, skin: OrbitSkin): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(skin.textColor)
            textSize = 30f
            gravity = Gravity.START
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(14))
        }
    }

    private fun section(text: String, skin: OrbitSkin): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(skin.accentColor)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(22), 0, dp(8))
        }
    }

    private fun paragraph(text: String, skin: OrbitSkin): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(skin.mutedTextColor)
            textSize = 15f
            lineSpacing = dp(2).toFloat()
            setPadding(0, 0, 0, dp(8))
        }
    }

    private fun statusBox(text: String, skin: OrbitSkin): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(skin.warningColor)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            background = OrbitTheme.rounded(skin.panelColor, dp(14).toFloat(), skin.warningColor, dp(1))
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42),
            ).apply {
                setMargins(0, 0, 0, dp(10))
            }
        }
    }

    private fun skinButton(option: OrbitSkin, selected: Boolean, currentSkin: OrbitSkin): Button {
        val locked = option.isPro && !ProGate.isProUnlocked(this)
        val label = buildString {
            append(if (selected) "✓ " else "")
            append(option.name)
            if (locked) append(" · Pro 占位")
        }
        return button(label, currentSkin) {
            if (locked) {
                render("${option.name} 是 Pro 皮肤占位。当前版本没有支付逻辑，所以暂不解锁。")
            } else {
                SkinManager.apply(this, option.id)
                render("已切换到 ${option.name}")
            }
        }.apply {
            background = OrbitTheme.rounded(
                color = if (selected) currentSkin.panelAltColor else currentSkin.panelColor,
                radiusPx = dp(14).toFloat(),
                strokeColor = if (selected) currentSkin.accentColor else currentSkin.borderColor,
                strokeWidthPx = dp(1),
            )
            setTextColor(if (locked) currentSkin.mutedTextColor else currentSkin.textColor)
        }
    }

    private fun button(text: String, skin: OrbitSkin, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(skin.textColor)
            textSize = 15f
            background = OrbitTheme.rounded(skin.panelAltColor, dp(14).toFloat(), skin.accentColor, dp(1))
            setOnClickListener { onClick() }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, dp(4), 0, dp(6))
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
