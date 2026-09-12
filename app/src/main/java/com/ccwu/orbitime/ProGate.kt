package com.ccwu.orbitime

import android.content.Context

object ProGate {
    /**
     * The bundledModelsDebug package is an explicitly separate test application.
     * Its private app data does not share activation/model state with the formal
     * com.ccwu.orbitime package, so local-model features stay open there for device
     * verification. The formal package continues to use the normal license gate.
     */
    fun isProUnlocked(context: Context): Boolean =
        context.packageName.endsWith(".bundledmodels") || ProLicenseManager.isUnlocked(context)

    fun maxClipboardItems(context: Context): Int = if (isProUnlocked(context)) 10000 else 500

    fun maxTemplates(context: Context): Int = if (isProUnlocked(context)) 1000 else 200

    fun maxUserDictionaryItems(context: Context): Int = if (isProUnlocked(context)) 100000 else 20000

    fun maxOwnedPets(context: Context): Int = if (isProUnlocked(context)) 32 else 16

    fun maxOutfits(context: Context): Int = if (isProUnlocked(context)) 120 else 32

    fun isOfflineTranslationPackUnlocked(context: Context): Boolean = true

    fun isContextTranslationUnlocked(context: Context): Boolean = isProUnlocked(context)

    fun isLongFormTranslationUnlocked(context: Context): Boolean = isProUnlocked(context)

    fun isLocalModelPackManagerUnlocked(context: Context): Boolean = isProUnlocked(context)

    fun isLocalAsrUnlocked(context: Context): Boolean = isProUnlocked(context)

    fun isLocalTtsUnlocked(context: Context): Boolean = isProUnlocked(context)

    fun isVoiceCloneUnlocked(context: Context): Boolean = isProUnlocked(context)
}
