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

        container.addView(title("Orbit IME v0.7", skin))
        container.addView(paragraph("隐私优先的 Orbit Hub 风格输入法。v0.7 增加键盘内宠物 MVP 和 Pro 离线翻译包占位；仍然不联网、不接广告、不上传输入内容。当前皮肤：${skin.name}。", skin))
        statusMessage?.let {
            container.addView(statusBox(it, skin))
        }

        container.addView(section("第一步", skin))
        container.addView(paragraph("打开系统输入法设置，启用 Orbit IME。系统会提示第三方输入法风险，这是 Android 对所有第三方输入法的通用提醒。", skin))
        container.addView(button("打开输入法设置", skin) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })

        container.addView(section("第二步", skin))
        container.addView(paragraph("启用后，点击下方按钮切换到 Orbit IME。顶部 Hub 栏可在 EN、拼音、Clips、Translate 和 Pet 面板之间切换。", skin))
        container.addView(button("显示输入法切换器", skin) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        })

        container.addView(section("v0.7 Keyboard Pet", skin))
        container.addView(paragraph("当前宠物：${petProfile.petName}，Stage ${petProfile.stage}，Lv.${petProfile.level}，${petProfile.exp} EXP，${petProfile.stars} Stars，连续签到 ${petProfile.checkInStreak} 天。宠物只在键盘内显示；不申请悬浮窗权限，不发通知，不播放声音，不读取完整输入流。", skin))
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

        container.addView(section("v0.7 Pro 离线翻译包", skin))
        container.addView(paragraph("Offline Translation Pack 是 Pro 高级功能。当前实现为本地短句/常用表达包，不是云翻译，不接外部翻译 API，不新增 INTERNET 权限。免费用户仍可使用 v0.4 Translate Preview 生成 Prompt。当前状态：${if (ProGate.isOfflineTranslationPackUnlocked(this)) "已解锁" else "Pro 锁定"}。", skin))

        container.addView(section("v0.5 用户本地词库", skin))
        container.addView(paragraph("当前本地词库：${dictionaryStats.entryCount}/${dictionaryStats.maxEntries} 条映射，累计选择 ${dictionaryStats.totalFrequency} 次。只有在拼音模式下点击候选或空格选首候选后，才会记录拼音→词条频次。密码框、验证码、疑似密钥内容不会学习。", skin))
        container.addView(button("清空用户词库", skin) {
            userDictionary.clear()
            render("用户本地词库已清空")
        })

        container.addView(section("v0.4 Translate Preview", skin))
        container.addView(paragraph("Translate Preview 仍然保留：本地生成 Prompt；Pro 离线包只能对少量短句给出本地译文候选。默认路线仍不包含云端翻译。", skin))

        container.addView(section("v0.3 皮肤", skin))
        container.addView(paragraph("选择会保存到本机 SharedPreferences，并立即影响设置页；重新拉起键盘后输入法界面会使用同一套皮肤。Pro Aurora 是付费占位皮肤，当前未接支付，所以默认锁定。", skin))
        OrbitSkins.all.forEach { option ->
            container.addView(skinButton(option, SkinManager.selectedSkinId(this) == option.id, skin))
        }

        container.addView(section("当前功能", skin))
        container.addView(paragraph("当前版本支持英文输入、拼音26键、本地静态候选、用户本地候选频次学习、空格选首候选、候选点击上屏、本地剪贴板 Save/Clips、Translate Preview、Pro 离线短句翻译包占位、本地皮肤、键盘内宠物和快捷模板插入。暂不包含云同步、云翻译、9键、五笔、手写、系统悬浮窗和大型分词词库。", skin))
        container.addView(section("隐私边界", skin))
        container.addView(paragraph("Orbit IME 不申请 INTERNET 权限；不会上传输入内容；不会在密码输入框显示 Hub；剪贴板内容只有在你主动点击 Save 时才会保存到本机；用户词库只记录候选选择频次；宠物只统计本地数量，不保存完整输入流。", skin))
        container.addView(section("ProGate", skin))
        container.addView(paragraph("Pro 解锁入口已预留，但 v0.7 没有支付、广告或联网逻辑。免费版默认最多保存 50 条本地剪贴板、300 条用户本地词库、1 个当前宠物；Pro 占位包含更多宠物、更多装扮、5000 条词库、5000 条剪贴板和离线翻译包。", skin))

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
