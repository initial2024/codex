package com.ccwu.orbitime

import android.content.Context

object ProGate {
    fun isProUnlocked(context: Context): Boolean {
        return context.getSharedPreferences("orbit_pro_gate", Context.MODE_PRIVATE)
            .getBoolean("pro_unlocked", false)
    }

    fun maxClipboardItems(context: Context): Int = if (isProUnlocked(context)) 10000 else 500

    fun maxTemplates(context: Context): Int = if (isProUnlocked(context)) 1000 else 200

    // v0.19: personalization is a first-class local feature. The free tier is
    // deliberately large enough for long-term daily use; Pro remains only a
    // future capacity placeholder and does not gate basic learning quality.
    fun maxUserDictionaryItems(context: Context): Int = if (isProUnlocked(context)) 100000 else 20000

    fun maxOwnedPets(context: Context): Int = if (isProUnlocked(context)) 32 else 16

    fun maxOutfits(context: Context): Int = if (isProUnlocked(context)) 120 else 32

    // Basic offline translation is enabled for usability. Future Pro can still
    // unlock optional larger specialist packs without making base translation fake.
    fun isOfflineTranslationPackUnlocked(context: Context): Boolean = true
}
