package com.ccwu.orbitime

import android.text.InputType
import android.view.inputmethod.EditorInfo

object PrivacyGuard {
    fun isSensitiveInput(editorInfo: EditorInfo?): Boolean {
        if (editorInfo == null) return false

        val inputType = editorInfo.inputType
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION

        val textPassword = inputClass == InputType.TYPE_CLASS_TEXT && when (variation) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> true
            else -> false
        }

        val numberPassword = inputClass == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD

        val appRequestsNoLearning = editorInfo.imeOptions and
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0

        return textPassword || numberPassword || appRequestsNoLearning
    }

    fun isSafeToPersist(raw: CharSequence?): Boolean {
        val text = raw?.toString()?.trim() ?: return false
        if (text.length < 2 || text.length > 2000) return false
        if (looksLikeSecret(text)) return false
        if (looksLikeSingleOtp(text)) return false
        return true
    }

    private fun looksLikeSingleOtp(text: String): Boolean {
        return Regex("^\\d{4,8}$").matches(text)
    }

    private fun looksLikeSecret(text: String): Boolean {
        val secretWords = Regex(
            pattern = "(?i)(password|passwd|pwd|token|secret|api[_-]?key|bearer\\s+[a-z0-9._\\-]+|authorization:|cookie:|sessionid|refresh[_-]?token)",
        )
        if (secretWords.containsMatchIn(text)) return true

        val longDenseToken = Regex("[A-Za-z0-9_\\-]{32,}")
        return longDenseToken.containsMatchIn(text)
    }
}
