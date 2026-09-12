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
        val personality: String,
        val visualBaseId: String = id,
    )

    data class OutfitDefinition(
        val id: String,
        val name: String,
        val slot: String,
        val isPro: Boolean,
        val visualId: String = id,
    )

    data class PetProfile(
        // petId is the renderer-safe visual id. catalogPetId is the actual owned pet id.
        val petId: String,
        val catalogPetId: String,
        val petName: String,
        val species: String,
        val stage: Int,
        val stageName: String,
        val level: Int,
        val exp: Int,
        val stars: Int,
        val checkInStreak: Int,
        val totalTypedChars: Long,
        val todayTypedChars: Int,
        val displayMode: String,
        // equippedOutfitId is renderer-safe; catalogOutfitId is the actual selection.
        val equippedOutfitId: String?,
        val catalogOutfitId: String?,
        val equippedOutfitName: String?,
        val chatUnlocked: Boolean,
        val progressPercent: Int,
        val nextStageExp: Int?,
        val moodLabel: String,
        val ownedPetCount: Int,
    )

    data class ActionResult(
        val message: String,
        val profile: PetProfile,
    )

    data class PetCatalogEntry(
        val profile: PetProfile,
        val owned: Boolean,
        val proOnly: Boolean,
        val current: Boolean,
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun profile(): PetProfile {
        ensureDefaultPet()
        val today = todayKey()
        val catalogPetId = prefs.getString(KEY_PET_ID, PetCatalog.Orbi.id) ?: PetCatalog.Orbi.id
        val definition = PetCatalog.byId(catalogPetId)
        val exp = prefs.getInt(KEY_EXP, 0).coerceAtLeast(0)
        val catalogOutfitId = prefs.getString(KEY_EQUIPPED_OUTFIT_ID, null)
        val outfit = OutfitCatalog.byId(catalogOutfitId)
        val displayMode = prefs.getString(KEY_DISPLAY_MODE, DISPLAY_KEYBOARD_ONLY) ?: DISPLAY_KEYBOARD_ONLY
        val todayTypedChars = if (prefs.getString(KEY_TODAY_TYPED_DATE, null) == today) {
            prefs.getInt(KEY_TODAY_TYPED_CHARS, 0).coerceAtLeast(0)
        } else 0
        return PetProfile(
            petId = definition.visualBaseId,
            catalogPetId = definition.id,
            petName = definition.name,
            species = definition.species,
            stage = stageFor(exp),
            stageName = stageNameFor(exp),
            level = levelFor(exp),
            exp = exp,
            stars = prefs.getInt(KEY_STARS, 0).coerceAtLeast(0),
            checkInStreak = prefs.getInt(KEY_CHECK_IN_STREAK, 0).coerceAtLeast(0),
            totalTypedChars = prefs.getLong(KEY_TOTAL_TYPED_CHARS, 0L).coerceAtLeast(0L),
            todayTypedChars = todayTypedChars,
            displayMode = displayMode,
            equippedOutfitId = outfit?.visualId,
            catalogOutfitId = outfit?.id,
            equippedOutfitName = outfit?.name,
            chatUnlocked = exp >= CHAT_UNLOCK_EXP,
            progressPercent = progressPercent(exp),
            nextStageExp = nextStageExp(exp),
            moodLabel = moodLabel(displayMode, exp, todayTypedChars),
            ownedPetCount = ownedPetIds().size,
        )
    }

    fun compactStatus(): String {
        val p = profile()
        return if (p.displayMode == DISPLAY_HIDDEN) "Pet · hidden"
        else "${p.petName} · ${p.stageName} · ${p.progressPercent}%"
    }

    fun panelLine(): String {
        val p = profile()
        return "${p.petName} · Lv.${p.level} · ${p.stageName} · ${p.exp} EXP · ${p.stars} Stars"
    }

    fun progressLine(): String {
        val p = profile()
        val next = p.nextStageExp?.let { "距下阶段 ${it - p.exp} EXP" } ?: "已进入成熟阶段"
        return "今日 ${p.todayTypedChars} 字 · 累计 ${p.totalTypedChars} 字 · $next"
    }

    fun petCatalogLine(): String {
        val owned = ownedPetIds()
        return PetCatalog.all.joinToString(" · ") { pet ->
            val lock = if (pet.isPro && !ProGate.isProUnlocked(context)) "🔒" else ""
            val mark = if (pet.id in owned) "✓" else ""
            "$mark$lock${pet.name}"
        }
    }

    fun petCatalogEntries(): List<PetCatalogEntry> {
        val currentProfile = profile()
        val owned = ownedPetIds()
        return PetCatalog.all.map { pet ->
            val isCurrent = pet.id == currentProfile.catalogPetId
            val visualProfile = currentProfile.copy(
                petId = pet.visualBaseId,
                catalogPetId = pet.id,
                petName = pet.name,
                species = pet.species,
                equippedOutfitId = if (isCurrent) currentProfile.equippedOutfitId else null,
                catalogOutfitId = if (isCurrent) currentProfile.catalogOutfitId else null,
                equippedOutfitName = if (isCurrent) currentProfile.equippedOutfitName else null,
            )
            PetCatalogEntry(
                profile = visualProfile,
                owned = pet.id in owned,
                proOnly = pet.isPro,
                current = isCurrent,
            )
        }
    }

    fun outfitCatalogLine(): String {
        val current = prefs.getString(KEY_EQUIPPED_OUTFIT_ID, null)
        val available = OutfitCatalog.all.filter { !it.isPro || ProGate.isProUnlocked(context) }
        return available.joinToString(" · ") { outfit ->
            val mark = if (outfit.id == current) "✓" else ""
            "$mark${outfit.name}"
        }.ifBlank { "暂无装扮" }
    }

    fun checkIn(): ActionResult {
        val today = todayKey()
        val last = prefs.getString(KEY_LAST_CHECK_IN_DATE, null)
        if (last == today) return ActionResult("今日已签到 · 连续 ${profile().checkInStreak} 天", profile())
        val yesterday = previousDayKey()
        val newStreak = if (last == yesterday) prefs.getInt(KEY_CHECK_IN_STREAK, 0) + 1 else 1
        val safeStreak = newStreak.coerceAtMost(9999)
        val starGain = when {
            safeStreak >= 14 -> 35
            safeStreak >= 7 -> 25
            safeStreak >= 3 -> 15
            safeStreak == 2 -> 12
            else -> 10
        }
        prefs.edit()
            .putString(KEY_LAST_CHECK_IN_DATE, today)
            .putInt(KEY_CHECK_IN_STREAK, safeStreak)
            .putInt(KEY_STARS, prefs.getInt(KEY_STARS, 0) + starGain)
            .apply()
        addExp(5)
        recordEvent(EVENT_CHECK_IN)
        return ActionResult("签到成功 +$starGain Stars · 连续 $safeStreak 天", profile())
    }

    fun recordTypedChars(count: Int) {
        if (count <= 0) return
        val today = todayKey()
        val currentToday = if (prefs.getString(KEY_TODAY_TYPED_DATE, null) == today) prefs.getInt(KEY_TODAY_TYPED_CHARS, 0) else 0
        val pending = prefs.getInt(KEY_PENDING_TYPED_CHARS, 0) + count
        val total = prefs.getLong(KEY_TOTAL_TYPED_CHARS, 0L) + count
        val expGain = pending / CHARS_PER_EXP
        val remaining = pending % CHARS_PER_EXP
        val editor = prefs.edit()
            .putString(KEY_TODAY_TYPED_DATE, today)
            .putInt(KEY_TODAY_TYPED_CHARS, currentToday + count)
            .putInt(KEY_PENDING_TYPED_CHARS, remaining)
            .putLong(KEY_TOTAL_TYPED_CHARS, total)
        if (expGain > 0) editor.putInt(KEY_EXP, min(prefs.getInt(KEY_EXP, 0) + expGain, MAX_EXP))
        editor.apply()
    }

    fun recordCandidateCommit() { addExp(1); recordEvent(EVENT_CANDIDATE) }
    fun recordClipSave() { addExp(1); recordEvent(EVENT_CLIP) }
    fun recordTranslatePrompt() { addExp(2); recordEvent(EVENT_TRANSLATE) }

    fun adoptRandom(): ActionResult {
        val pro = ProGate.isProUnlocked(context)
        val pool = PetCatalog.all.filter { !it.isPro || pro }
        val owned = ownedPetIds()
        if (owned.size >= ProGate.maxOwnedPets(context)) {
            return ActionResult("已达到当前宠物容量 ${ProGate.maxOwnedPets(context)}，先陪现有宠物成长吧", profile())
        }
        val unowned = pool.filter { it.id !in owned }
        if (unowned.isEmpty()) return ActionResult("当前可领养宠物已收集完，可切换已有宠物", profile())
        val today = todayKey()
        val dailyFree = prefs.getString(KEY_LAST_ADOPT_DATE, null) != today
        val stars = prefs.getInt(KEY_STARS, 0)
        val cost = if (dailyFree) 0 else ADOPT_COST
        if (stars < cost) return ActionResult("Stars 不足：需要 $cost，当前 $stars；明天可免费开蛋一次", profile())
        val chosen = unowned[(System.currentTimeMillis() % unowned.size).toInt()]
        owned.add(chosen.id)
        val editor = prefs.edit()
            .putStringSet(KEY_OWNED_PET_IDS, owned.toSet())
            .putString(KEY_PET_ID, chosen.id)
            .putString(KEY_DISPLAY_MODE, DISPLAY_KEYBOARD_ONLY)
        if (dailyFree) editor.putString(KEY_LAST_ADOPT_DATE, today)
        else editor.putInt(KEY_STARS, stars - cost)
        editor.apply()
        recordEvent(EVENT_ADOPT)
        return ActionResult(if (dailyFree) "今日免费开蛋：${chosen.name}" else "开蛋成功：${chosen.name} -$cost Stars", profile())
    }

    fun switchToNextOwned(): ActionResult {
        val owned = ownedPetIds()
        val ordered = PetCatalog.all.filter { it.id in owned }
        if (ordered.isEmpty()) return ActionResult("暂无宠物", profile())
        val current = prefs.getString(KEY_PET_ID, PetCatalog.Orbi.id) ?: PetCatalog.Orbi.id
        val index = ordered.indexOfFirst { it.id == current }.coerceAtLeast(0)
        val next = ordered[(index + 1) % ordered.size]
        prefs.edit().putString(KEY_PET_ID, next.id).putString(KEY_DISPLAY_MODE, DISPLAY_KEYBOARD_ONLY).apply()
        recordEvent(EVENT_SWITCH)
        return ActionResult("已切换到 ${next.name} · ${next.species}", profile())
    }

    fun toggleHidden(): ActionResult {
        val next = if (profile().displayMode == DISPLAY_HIDDEN) DISPLAY_KEYBOARD_ONLY else DISPLAY_HIDDEN
        prefs.edit().putString(KEY_DISPLAY_MODE, next).apply()
        return ActionResult(if (next == DISPLAY_HIDDEN) "宠物已隐藏" else "宠物已显示在键盘内", profile())
    }

    fun equipNextOutfit(): ActionResult {
        val available = OutfitCatalog.all.filter { !it.isPro || ProGate.isProUnlocked(context) }
        if (available.isEmpty()) return ActionResult("暂无可用装扮", profile())
        val current = prefs.getString(KEY_EQUIPPED_OUTFIT_ID, null)
        val next = if (current == null) available.first() else {
            val index = available.indexOfFirst { it.id == current }
            if (index < 0 || index == available.lastIndex) null else available[index + 1]
        }
        prefs.edit().putString(KEY_EQUIPPED_OUTFIT_ID, next?.id).apply()
        recordEvent(EVENT_OUTFIT)
        return ActionResult(next?.let { "已装备：${it.name}" } ?: "已卸下装扮", profile())
    }

    fun equipOutfit(outfitId: String?): ActionResult {
        val outfit = OutfitCatalog.byId(outfitId)
        if (outfit?.isPro == true && !ProGate.isProUnlocked(context)) return ActionResult("这是 Pro 装扮占位，当前不可用", profile())
        prefs.edit().putString(KEY_EQUIPPED_OUTFIT_ID, outfit?.id).apply()
        recordEvent(EVENT_OUTFIT)
        return ActionResult(outfit?.let { "已装备：${it.name}" } ?: "已卸下装扮", profile())
    }

    /** Small, local-only feedback shown in the pet panel. */
    fun localChatLine(): String {
        val p = profile()
        val recentEvent = if (System.currentTimeMillis() - prefs.getLong(KEY_LAST_EVENT_AT, 0L) <= FEEDBACK_TTL_MS) {
            prefs.getString(KEY_LAST_EVENT, null)
        } else null
        when (recentEvent) {
            EVENT_CANDIDATE -> return listOf("这个候选我记住了。", "选词偏好已在本机加权。", "收到，下次我会更懂你的排序。")[feedbackIndex(p, 3)]
            EVENT_CLIP -> return listOf("这条剪贴板已经收好。", "已放进本机剪贴板历史。", "剪贴板整理完成。")[feedbackIndex(p, 3)]
            EVENT_TRANSLATE -> return listOf("本地翻译完成。", "译文准备好了。", "这次翻译也算进成长值了。")[feedbackIndex(p, 3)]
            EVENT_CHECK_IN -> return listOf("签到完成，今天也在。", "连续签到继续累计。", "今天的 Stars 已到账。")[feedbackIndex(p, 3)]
            EVENT_ADOPT -> return "新伙伴加入图鉴了。"
            EVENT_SWITCH -> return "换个搭档，继续输入。"
            EVENT_OUTFIT -> return listOf("这套装扮不错。", "造型更新完成。", "新装扮已经穿上了。")[feedbackIndex(p, 3)]
        }
        if (p.displayMode == DISPLAY_HIDDEN) return "我在隐身，不会打扰你。"
        if (p.nextStageExp != null && p.nextStageExp - p.exp <= 10) return "快进化了，还差 ${p.nextStageExp - p.exp} EXP。"
        if (p.todayTypedChars >= 3000) return listOf("今天已经输入很多了，记得休息眼睛。", "高强度输入中，我会安静陪着。", "今天的输入量很高，候选学习也在积累。")[feedbackIndex(p, 3)]
        if (p.todayTypedChars >= 1000) return listOf("今天输入很多，节奏不错。", "本地学习正在慢慢贴近你的习惯。", "一千字以上了，今天挺专注。")[feedbackIndex(p, 3)]
        if (p.checkInStreak >= 14) return "连续 ${p.checkInStreak} 天，稳定得很。"
        if (p.checkInStreak >= 7) return "连续 ${p.checkInStreak} 天了，保持住。"
        return when (p.stage) {
            4 -> listOf("我已经成熟，会安静陪你输入。", "成熟阶段也会继续积累经验。", "今天想换个装扮吗？")[feedbackIndex(p, 3)]
            3 -> listOf("轮廓稳定了，继续积累文字。", "Teen 阶段，离成熟不远了。", "再多用几次候选，我还能继续成长。")[feedbackIndex(p, 3)]
            2 -> listOf("我正在成长，候选上屏也会增加经验。", "Junior 阶段，继续输入吧。", "保存剪贴板和翻译也会给一点经验。")[feedbackIndex(p, 3)]
            else -> listOf("我还在孵化，打字会让我成长。", "多输入一点，我会慢慢长大。", "今天也从一小段文字开始吧。")[feedbackIndex(p, 3)]
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
        ensureDefaultPet()
    }

    private fun recordEvent(event: String) {
        prefs.edit().putString(KEY_LAST_EVENT, event).putLong(KEY_LAST_EVENT_AT, System.currentTimeMillis()).apply()
    }

    private fun feedbackIndex(profile: PetProfile, size: Int): Int {
        if (size <= 1) return 0
        return ((profile.totalTypedChars + profile.exp + profile.checkInStreak).mod(size.toLong())).toInt()
    }

    private fun ensureDefaultPet() {
        if (!prefs.contains(KEY_PET_ID)) {
            prefs.edit()
                .putString(KEY_PET_ID, PetCatalog.Orbi.id)
                .putString(KEY_DISPLAY_MODE, DISPLAY_KEYBOARD_ONLY)
                .putStringSet(KEY_OWNED_PET_IDS, setOf(PetCatalog.Orbi.id))
                .apply()
        }
        val currentPet = prefs.getString(KEY_PET_ID, PetCatalog.Orbi.id) ?: PetCatalog.Orbi.id
        val owned = ownedPetIds()
        if (currentPet !in owned) {
            owned.add(currentPet)
            owned.add(PetCatalog.Orbi.id)
            prefs.edit().putStringSet(KEY_OWNED_PET_IDS, owned.toSet()).apply()
        }
    }

    private fun ownedPetIds(): MutableSet<String> {
        val fromPrefs = prefs.getStringSet(KEY_OWNED_PET_IDS, null)?.toMutableSet()
        if (fromPrefs != null && fromPrefs.isNotEmpty()) return fromPrefs
        return mutableSetOf(PetCatalog.Orbi.id)
    }

    private fun addExp(amount: Int) {
        if (amount <= 0) return
        prefs.edit().putInt(KEY_EXP, min(prefs.getInt(KEY_EXP, 0) + amount, MAX_EXP)).apply()
    }

    private fun stageFor(exp: Int): Int = when { exp >= 500 -> 4; exp >= 200 -> 3; exp >= 50 -> 2; else -> 1 }
    private fun stageNameFor(exp: Int): String = when (stageFor(exp)) { 1 -> "Seed"; 2 -> "Junior"; 3 -> "Teen"; else -> "Adult" }
    private fun levelFor(exp: Int): Int = (exp / 100 + 1).coerceIn(1, 99)
    private fun nextStageExp(exp: Int): Int? = when { exp < 50 -> 50; exp < 200 -> 200; exp < 500 -> 500; else -> null }

    private fun progressPercent(exp: Int): Int {
        val start = when { exp >= 500 -> 500; exp >= 200 -> 200; exp >= 50 -> 50; else -> 0 }
        val end = nextStageExp(exp) ?: 1000
        return (((exp - start).coerceAtLeast(0) * 100) / (end - start).coerceAtLeast(1)).coerceIn(0, 100)
    }

    private fun moodLabel(displayMode: String, exp: Int, todayChars: Int): String = when {
        displayMode == DISPLAY_HIDDEN -> "隐身"
        nextStageExp(exp)?.let { it - exp <= 10 } == true -> "可进化"
        todayChars >= 3000 -> "高能"
        todayChars >= 1000 -> "专注"
        todayChars >= 200 -> "活跃"
        else -> "平静"
    }

    private fun todayKey(): String = DATE.format(Date())
    private fun previousDayKey(): String = DATE.format(Date(System.currentTimeMillis() - 24L * 60L * 60L * 1000L))

    object PetCatalog {
        val Orbi = PetDefinition("orbi", "Orbi", "星轨核心", false, "理性、专注")
        val Kuro = PetDefinition("kuro", "Kuro", "数据墨滴", false, "温润、安静")
        val Pica = PetDefinition("pica", "Pica", "棱镜晶体", false, "敏锐、轻盈")
        val Voxel = PetDefinition("voxel", "Voxel", "终端像素猫", false, "机敏、极客")
        val Rune = PetDefinition("rune", "Rune", "悬浮符碑", false, "沉稳、自律")
        val Noctua = PetDefinition("noctua", "Noctua", "机械夜鸮", true, "严谨、守夜")
        val Nami = PetDefinition("nami", "Nami", "极光水母", true, "治愈、空灵")
        val Aegis = PetDefinition("aegis", "Aegis", "晶格护卫狐", true, "警觉、守护")

        // v0.19 variants reuse the eight stable renderer archetypes so every new
        // catalog pet is visible immediately without introducing fragile new drawing code.
        val Nova = PetDefinition("nova", "Nova", "星火幼龙", false, "热情、勇敢", "orbi")
        val Mochi = PetDefinition("mochi", "Mochi", "云团兔", false, "软萌、放松", "kuro")
        val Byte = PetDefinition("byte", "Byte", "机械仓鼠", false, "迅速、好奇", "voxel")
        val Lumi = PetDefinition("lumi", "Lumi", "荧光鹿灵", false, "温和、明亮", "pica")
        val Tide = PetDefinition("tide", "Tide", "潮汐鳐", false, "平静、流动", "nami")
        val Cinder = PetDefinition("cinder", "Cinder", "熔芯狐", false, "果断、炽热", "aegis")
        val Glyph = PetDefinition("glyph", "Glyph", "符文龟", false, "耐心、稳重", "rune")
        val Echo = PetDefinition("echo", "Echo", "回声夜蝠", true, "敏锐、夜行", "noctua")

        val all = listOf(
            Orbi, Kuro, Pica, Voxel, Rune, Noctua, Nami, Aegis,
            Nova, Mochi, Byte, Lumi, Tide, Cinder, Glyph, Echo,
        )

        fun byId(id: String): PetDefinition = all.firstOrNull { it.id == id } ?: Orbi
    }

    object OutfitCatalog {
        val all = listOf(
            OutfitDefinition("halo", "磁悬浮光环", "head", false),
            OutfitDefinition("monocle", "单目数据镜", "face", false),
            OutfitDefinition("study_hat", "学术帽", "head", false),
            OutfitDefinition("signal_cloak", "信号披风", "back", false),
            OutfitDefinition("bow", "全息领结", "neck", false),
            OutfitDefinition("orbit_ring", "发光轨道环", "back", true),
            OutfitDefinition("matrix_aura", "矩阵微光", "aura", true),
            OutfitDefinition("aurora_tail", "极光尾迹", "aura", true),

            OutfitDefinition("comet_halo", "彗星光环", "head", false, "halo"),
            OutfitDefinition("moon_halo", "月相光环", "head", false, "halo"),
            OutfitDefinition("data_visor", "数据护目镜", "face", false, "monocle"),
            OutfitDefinition("star_lens", "星点镜片", "face", false, "monocle"),
            OutfitDefinition("exam_hat", "备考学士帽", "head", false, "study_hat"),
            OutfitDefinition("night_cap", "夜航帽", "head", false, "study_hat"),
            OutfitDefinition("nebula_cloak", "星云披风", "back", false, "signal_cloak"),
            OutfitDefinition("pixel_cloak", "像素披风", "back", false, "signal_cloak"),
            OutfitDefinition("ribbon_bow", "流光领结", "neck", false, "bow"),
            OutfitDefinition("focus_bow", "专注领结", "neck", false, "bow"),
            OutfitDefinition("double_orbit", "双层轨道环", "back", false, "orbit_ring"),
            OutfitDefinition("saturn_ring", "土星轨道环", "back", true, "orbit_ring"),
            OutfitDefinition("pixel_aura", "像素微光", "aura", false, "matrix_aura"),
            OutfitDefinition("code_aura", "代码矩阵", "aura", true, "matrix_aura"),
            OutfitDefinition("comet_tail", "彗星尾迹", "aura", false, "aurora_tail"),
            OutfitDefinition("rainbow_tail", "虹彩尾迹", "aura", true, "aurora_tail"),
        )

        fun byId(id: String?): OutfitDefinition? = all.firstOrNull { it.id == id }
    }

    companion object {
        const val DISPLAY_HIDDEN = "hidden"
        const val DISPLAY_KEYBOARD_ONLY = "keyboard_only"

        private const val PREFS = "orbit_pet"
        private const val KEY_PET_ID = "pet_id"
        private const val KEY_OWNED_PET_IDS = "owned_pet_ids"
        private const val KEY_EXP = "exp"
        private const val KEY_STARS = "stars"
        private const val KEY_CHECK_IN_STREAK = "check_in_streak"
        private const val KEY_LAST_CHECK_IN_DATE = "last_check_in_date"
        private const val KEY_LAST_ADOPT_DATE = "last_adopt_date"
        private const val KEY_TOTAL_TYPED_CHARS = "total_typed_chars"
        private const val KEY_TODAY_TYPED_DATE = "today_typed_date"
        private const val KEY_TODAY_TYPED_CHARS = "today_typed_chars"
        private const val KEY_PENDING_TYPED_CHARS = "pending_typed_chars"
        private const val KEY_DISPLAY_MODE = "display_mode"
        private const val KEY_EQUIPPED_OUTFIT_ID = "equipped_outfit_id"
        private const val KEY_LAST_EVENT = "last_event"
        private const val KEY_LAST_EVENT_AT = "last_event_at"

        private const val EVENT_CANDIDATE = "candidate"
        private const val EVENT_CLIP = "clip"
        private const val EVENT_TRANSLATE = "translate"
        private const val EVENT_CHECK_IN = "check_in"
        private const val EVENT_ADOPT = "adopt"
        private const val EVENT_SWITCH = "switch"
        private const val EVENT_OUTFIT = "outfit"

        private const val MAX_EXP = 9999
        private const val CHAT_UNLOCK_EXP = 500
        private const val CHARS_PER_EXP = 20
        private const val ADOPT_COST = 30
        private const val FEEDBACK_TTL_MS = 30_000L
        private val DATE = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
