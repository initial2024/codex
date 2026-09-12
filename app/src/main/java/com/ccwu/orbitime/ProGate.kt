package com.ccwu.orbitime

import android.content.Context

object ProGate {
    fun isProUnlocked(context: Context): Boolean = ProLicenseManager.isUnlocked(context)

    fun maxClipboardItems(context: Context): Int = if (isProUnlocked(context)) 10000 else 500

    fun maxTemplates(context: Context): Int = if (isProUnlocked(context)) 1000 else 200

    fun maxUserDictionaryItems(context: Context): Int = if (isProUnlocked(context)) 100000 else 20000

    fun maxOwnedPets(context: Context): Int = if (isProUnlocked(context)) 32 else 16

    fun maxOutfits(context: Context): Int = if (isProUnlocked(context)) 120 else 32

    fun isOfflineTranslationPackUnlocked(context: Context): Boolean = true

    fun isContextTranslationUnlocked(context: Context): Boolean = isProUnlocked(context)

    fun isLongFormTranslationUnlocked(context: Context): Boolean = isProUnlocked(context)
}
