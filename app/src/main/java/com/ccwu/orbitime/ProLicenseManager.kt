package com.ccwu.orbitime

import android.content.Context
import java.security.MessageDigest

/**
 * Local Pro activation state.
 *
 * v0.21 deliberately keeps release licensing server-free. Debug builds accept a
 * strong tester code so Pro paths can be exercised now. Release builds do not
 * accept that code; production should switch to signed activation tokens whose
 * private signing key stays outside the APK/repository.
 */
object ProLicenseManager {
    data class ActivationResult(val success: Boolean, val message: String)

    private const val PREFS = "orbit_pro_gate"
    private const val KEY_UNLOCKED = "pro_unlocked"
    private const val KEY_LICENSE_TYPE = "license_type"
    private const val KEY_ACTIVATED_AT = "activated_at"

    // SHA-256 of the debug-only tester code. The clear-text code is documented
    // only for local testing and is rejected by release builds.
    private const val DEBUG_TEST_CODE_SHA256 = "8e755b08acc3c95d59ece831c8823675cc1fffe55d7f4e2a3043f8478165f152"

    fun isUnlocked(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_UNLOCKED, false)

    fun licenseLabel(context: Context): String {
        if (!isUnlocked(context)) return "Free"
        return when (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LICENSE_TYPE, "local")) {
            "debug" -> "Pro · Tester"
            "signed" -> "Pro"
            else -> "Pro · Local"
        }
    }

    fun activate(context: Context, rawCode: String): ActivationResult {
        val code = rawCode.trim().uppercase()
        if (code.isBlank()) return ActivationResult(false, "请输入激活码")

        if (BuildConfig.DEBUG && sha256(code) == DEBUG_TEST_CODE_SHA256) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_UNLOCKED, true)
                .putString(KEY_LICENSE_TYPE, "debug")
                .putLong(KEY_ACTIVATED_AT, System.currentTimeMillis())
                .apply()
            return ActivationResult(true, "Pro 测试权限已解锁")
        }

        // Production policy: do not fall back to a plain reusable invite code.
        // A future release token should be signed offline/server-side and verified
        // with an embedded public key. Keeping the private key outside the APK is
        // materially safer than shipping a shared secret in the client.
        return ActivationResult(false, if (BuildConfig.DEBUG) "激活码无效" else "当前 Release 未配置生产签名许可证")
    }

    fun deactivate(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        TranslationSettings.setContextTranslationEnabled(context, false)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
