package com.ccwu.orbitime

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(OrbitTheme.BACKGROUND)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
        }
        scroll.addView(container)

        container.addView(title("Orbit IME"))
        container.addView(paragraph("隐私优先的 Orbit Hub 风格输入法。第一版不联网、不接广告、不上传输入内容，只提供基础键盘、快捷模板和用户主动保存的本地剪贴板。"))
        container.addView(section("第一步"))
        container.addView(paragraph("打开系统输入法设置，启用 Orbit IME。系统会提示第三方输入法风险，这是 Android 对所有第三方输入法的通用提醒。"))
        container.addView(button("打开输入法设置") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })

        container.addView(section("第二步"))
        container.addView(paragraph("启用后，点击下方按钮切换到 Orbit IME。"))
        container.addView(button("显示输入法切换器") {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        })

        container.addView(section("隐私边界"))
        container.addView(paragraph("Orbit IME 不申请 INTERNET 权限；不会上传输入内容；不会在密码输入框显示 Hub；剪贴板内容只有在你主动点击 Save 时才会保存到本机。"))
        container.addView(section("ProGate"))
        container.addView(paragraph("Pro 解锁入口已预留，但第一版没有支付、广告或联网逻辑。免费版默认最多保存 50 条本地剪贴板。"))

        setContentView(scroll)
    }

    private fun title(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(OrbitTheme.TEXT)
            textSize = 30f
            gravity = Gravity.START
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(14))
        }
    }

    private fun section(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(OrbitTheme.ACCENT)
            textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(22), 0, dp(8))
        }
    }

    private fun paragraph(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(OrbitTheme.MUTED)
            textSize = 15f
            lineSpacing = dp(2).toFloat()
            setPadding(0, 0, 0, dp(8))
        }
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(OrbitTheme.TEXT)
            textSize = 15f
            background = OrbitTheme.rounded(OrbitTheme.PANEL_ALT, dp(14).toFloat(), OrbitTheme.ACCENT, dp(1))
            setOnClickListener { onClick() }
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
