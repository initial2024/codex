package com.ccwu.orbitime

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class PetRepository(private val context: Context) {
    data class PetDefinition(
        val id: String,
        val name: String,
        val species: String,
        val isPro: Boolean,
    )

    data class PetProfile(
        val petId: String,
        val petName: String,
        val stage: Int,
        val level: Int,
        val exp: Int,
        val stars: Int,
        val checkInStreak: Int,
        val totalTypedChars: Long,
        val displayMode: String,
        val equippedOutfitId: String?,
        val chatUnlocked: Boolean,
    )

    data class ActionResult(
        val message: String,
        val profile: PetProfile,
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun profile(): PetProfile {
        ensureDefaultPet()
        val petId = prefs.getString(KEY_PET_ID, PetCatalog.Orbi.id) ?: PetCatalog.Orbi.id
        val definition = PetCatalog.byId(petId)
        val exp = prefs.getInt(KEY_EXP, 0).coerceAtLeast(0)
        return PetProfile(
            petId = definition.id,
            petName = definition.name,
            stage = stageFor(exp),
            level = levelFor(exp),
            exp = exp,
            stars = prefs.getInt(KEY_STARS, 0).coerceAtLeast(0),
            checkInStreak = prefs.getInt(KEY_CHECK_IN_STREAK, 0).coerceAtLeast(0),
            totalTypedChars = prefs.getLong(KEY_TOTAL_TYPED_CHARS, 0L).coerceAtLeast(0L),
            displayMode = prefs.getString(KEY_DISPLAY_MODE, DISPLAY_KEYBOARD_ONLY) ?: DISPLAY_KEYBOARD_ONLY,
            equippedOutfitId = prefs.getString(KEY_EQUIPPED_OUTFIT_ID, null),
            chatUnlocked = exp >= CHAT_UNLOCK_EXP,
        )
    }

    fun compactStatus(): String {
        val p = profile()
        return if (p.displayMode == DISPLAY_HIDDEN) {
            "Pet · hidden"
        } else {
            "${p.petName} · S${p.stage} · ${progressPercent(p.exp)}%"
        }
    }

    fun panelLine(): String {
        val p = profile()
        return "${p.petName} · Lv.${p.level} · S${p.stage} · ${p.exp} EXP · ${p.stars} Stars · ${p.checkInStreak}d"
    }

    fun checkIn(): ActionResult {
        val today = todayKey()
        val last = prefs.getString(KEY_LAST_CHECK_IN_DATE, null)
        if (last == today) {
            return ActionResult("Already checked in today", profile())
        }
        val yesterday = previousDayKey()
        val newStreak = if (last == yesterday) {
            prefs.getInt(KEY_CHECK_IN_STREAK, 0) + 1
        } else {
            1
        }.coerceAtMost(9999)
        val starGain = when {
            newStreak >= 7 -> 25
            newStreak >= 3 -> 15
            newStreak == 2 -> 12
            else -> 10
        }
        prefs.edit()
            .putString(KEY_LAST_CHECK_IN_DATE, today)
            .putInt(KEY_CHECK_IN_STREAK, newStreak)
            .putInt(KEY_STARS, prefs.getInt(KEY_STARS, 0) + starGain)
            .apply()
        addExp(5)
        return ActionResult("Check-in +$starGain Stars", profile())
    }

    fun recordTypedChars(count: Int) {
        if (count <= 0 || profile().displayMode == DISPLAY_HIDDEN) return
        val pending = prefs.getInt(KEY_PENDING_TYPED_CHARS, 0) + count
        val total = prefs.getLong(KEY_TOTAL_TYPED_CHARS, 0L) + count
        val expGain = pending / CHARS_PER_EXP
        val remaining = pending % CHARS_PER_EXP
        val editor = prefs.edit()
            .putInt(KEY_PENDING_TYPED_CHARS, remaining)
            .putLong(KEY_TOTAL_TYPED_CHARS, total)
        if (expGain > 0) {
            editor.putInt(KEY_EXP, prefs.getInt(KEY_EXP, 0) + expGain)
        }
        editor.apply()
    }

    fun recordCandidateCommit() {
        addExp(1)
    }

    fun recordClipSave() {
        addExp(1)
    }

    fun recordTranslatePrompt() {
        addExp(2)
    }

    fun adoptRandom(): ActionResult {
        val pro = ProGate.isProUnlocked(context)
        val pool = PetCatalog.all.filter { !it.isPro || pro }
        val stars = prefs.getInt(KEY_STARS, 0)
        if (stars < ADOPT_COST) {
            return ActionResult("Need $ADOPT_COST Stars to adopt", profile())
        }
        val currentId = profile().petId
        val candidates = pool.filter { it.id != currentId }.ifEmpty { pool }
        val chosen = candidates[(System.currentTimeMillis() % candidates.size).toInt()]
        prefs.edit()
            .putString(KEY_PET_ID, chosen.id)
            .putInt(KEY_STARS, stars - ADOPT_COST)
            .putInt(KEY_EXP, 0)
            .putString(KEY_DISPLAY_MODE, DISPLAY_KEYBOARD_ONLY)
            .remove(KEY_EQUIPPED_OUTFIT_ID)
            .apply()
        return ActionResult("Adopted ${chosen.name}", profile())
    }

    fun toggleHidden(): ActionResult {
        val current = profile().displayMode
        val next = if (current == DISPLAY_HIDDEN) DISPLAY_KEYBOARD_ONLY else DISPLAY_HIDDEN
        prefs.edit().putString(KEY_DISPLAY_MODE, next).apply()
        return ActionResult(if (next == DISPLAY_HIDDEN) "Pet hidden" else "Pet visible in keyboard", profile())
    }

    fun equipOutfit(outfitId: String?): ActionResult {
        prefs.edit().putString(KEY_EQUIPPED_OUTFIT_ID, outfitId).apply()
        return ActionResult("Outfit updated", profile())
    }

    fun localChatLine(): String {
        val p = profile()
        if (!p.chatUnlocked) return "Grow to Adult to unlock chat."
        return when {
            p.exp >= 500 -> "I am keeping this device local."
            p.checkInStreak >= 7 -> "Seven-day focus streak."
            p.totalTypedChars >= 5000 -> "Your words are stacking up."
            else -> "I am here while you type."
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
        ensureDefaultPet()
    }

    private fun ensureDefaultPet() {
        if (!prefs.contains(KEY_PET_ID)) {
            prefs.edit()
                .putString(KEY_PET_ID, PetCatalog.Orbi.id)
                .putString(KEY_DISPLAY_MODE, DISPLAY_KEYBOARD_ONLY)
                .apply()
        }
    }

    private fun addExp(amount: Int) {
        if (amount <= 0) return
        val old = prefs.getInt(KEY_EXP, 0)
        prefs.edit().putInt(KEY_EXP, min(old + amount, MAX_EXP)).apply()
    }

    private fun stageFor(exp: Int): Int = when {
        exp >= 500 -> 4
        exp >= 200 -> 3
        exp >= 50 -> 2
        else -> 1
    }

    private fun levelFor(exp: Int): Int = (exp / 100 + 1).coerceIn(1, 99)

    private fun progressPercent(exp: Int): Int {
        val start = when {
            exp >= 500 -> 500
            exp >= 200 -> 200
            exp >= 50 -> 50
            else -> 0
        }
        val end = when {
            exp >= 500 -> 1000
            exp >= 200 -> 500
            exp >= 50 -> 200
            else -> 50
        }
        return (((exp - start).coerceAtLeast(0) * 100) / (end - start)).coerceIn(0, 100)
    }

    private fun todayKey(): String = DATE.format(Date())

    private fun previousDayKey(): String = DATE.format(Date(System.currentTimeMillis() - 24L * 60L * 60L * 1000L))

    object PetCatalog {
        val Orbi = PetDefinition("orbi", "Orbi", "orbital core", false)
        val Kuro = PetDefinition("kuro", "Kuro", "data ink drop", false)
        val Pica = PetDefinition("pica", "Pica", "prism crystal", false)
        val Voxel = PetDefinition("voxel", "Voxel", "terminal cat", false)
        val Rune = PetDefinition("rune", "Rune", "floating rune", false)
        val Noctua = PetDefinition("noctua", "Noctua", "clockwork owl", true)
        val Nami = PetDefinition("nami", "Nami", "aurora jellyfish", true)
        val Aegis = PetDefinition("aegis", "Aegis", "guardian fox", true)

        val all = listOf(Orbi, Kuro, Pica, Voxel, Rune, Noctua, Nami, Aegis)

        fun byId(id: String): PetDefinition = all.firstOrNull { it.id == id } ?: Orbi
    }

    companion object {
        const val DISPLAY_HIDDEN = "hidden"
        const val DISPLAY_KEYBOARD_ONLY = "keyboard_only"

        private const val PREFS = "orbit_pet"
        private const val KEY_PET_ID = "pet_id"
        private const val KEY_EXP = "exp"
        private const val KEY_STARS = "stars"
        private const val KEY_CHECK_IN_STREAK = "check_in_streak"
        private const val KEY_LAST_CHECK_IN_DATE = "last_check_in_date"
        private const val KEY_TOTAL_TYPED_CHARS = "total_typed_chars"
        private const val KEY_PENDING_TYPED_CHARS = "pending_typed_chars"
        private const val KEY_DISPLAY_MODE = "display_mode"
        private const val KEY_EQUIPPED_OUTFIT_ID = "equipped_outfit_id"
        private const val MAX_EXP = 9999
        private const val CHAT_UNLOCK_EXP = 500
        private const val CHARS_PER_EXP = 20
        private const val ADOPT_COST = 100
        private val DATE = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
