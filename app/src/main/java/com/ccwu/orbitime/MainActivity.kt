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
        val scroll = ScrollView(this).apply {
            setBackgroundColor(skin.backgroundColor)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
        }
        scroll.addView(container)

        container.addView(title("Orbit IME v0.3", skin))
        container.addView(paragraph("隐私优先的 Orbit Hub 风格输入法。v0.3 增加本地皮肤系统；仍然不联网、不接广告、不上传输入内容。当前皮肤：${skin.name}。", skin))
        statusMessage?.let {
            container.addView(statusBox(it, skin))
        }

        container.addView(section("第一步", skin))
        container.addView(paragraph("打开系统输入法设置，启用 Orbit IME。系统会提示第三方输入法风险，这是 Android 对所有第三方输入法的通用提醒。", skin))
        container.addView(button("打开输入法设置", skin) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })

        container.addView(section("第二步", skin))
        container.addView(paragraph("启用后，点击下方按钮切换到 Orbit IME。顶部 Hub 栏可在 EN 和 拼音 模式之间切换。", skin))
        container.addView(button("显示输入法切换器", skin) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        })

        container.addView(section("v0.3 皮肤", skin))
        container.addView(paragraph("选择会保存到本机 SharedPreferences，并立即影响设置页；重新拉起键盘后输入法界面会使用同一套皮肤。Pro Aurora 是付费占位皮肤，当前未接支付，所以默认锁定。", skin))
        OrbitSkins.all.forEach { option ->
            container.addView(skinButton(option, SkinManager.selectedSkinId(this) == option.id, skin))
        }

        container.addView(section("v0.3 功能", skin))
        container.addView(paragraph("当前版本支持英文输入、拼音26键、本地静态候选、空格选首候选、候选点击上屏、本地剪贴板 Save/Clips、快捷模板插入、5 套皮肤 token 和隐私模式强化。暂不包含皮肤商城、9键、五笔、手写和用户自动词库。", skin))
        container.addView(section("隐私边界", skin))
        container.addView(paragraph("Orbit IME 不申请 INTERNET 权限；不会上传输入内容；不会在密码输入框显示 Hub；剪贴板内容只有在你主动点击 Save 时才会保存到本机。", skin))
        container.addView(section("ProGate", skin))
        container.addView(paragraph("Pro 解锁入口已预留，但 v0.3 没有支付、广告或联网逻辑。免费版默认最多保存 50 条本地剪贴板。", skin))

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
