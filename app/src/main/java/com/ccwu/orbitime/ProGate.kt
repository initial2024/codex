package com.ccwu.orbitime

import android.content.Context

object ProGate {
    fun isProUnlocked(context: Context): Boolean {
        return context.getSharedPreferences("orbit_pro_gate", Context.MODE_PRIVATE)
            .getBoolean("pro_unlocked", false)
    }

    fun maxClipboardItems(context: Context): Int = if (isProUnlocked(context)) 5000 else 50

    fun maxTemplates(context: Context): Int = if (isProUnlocked(context)) 200 else 20

    fun maxUserDictionaryItems(context: Context): Int = if (isProUnlocked(context)) 5000 else 300

    fun maxOwnedPets(context: Context): Int = if (isProUnlocked(context)) 12 else 1

    fun maxOutfits(context: Context): Int = if (isProUnlocked(context)) 60 else 8

    fun isOfflineTranslationPackUnlocked(context: Context): Boolean = isProUnlocked(context)
}
