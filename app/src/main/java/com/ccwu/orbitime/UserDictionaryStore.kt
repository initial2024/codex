package com.ccwu.orbitime

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.util.Base64

/**
 * Large local personalization store.
 *
 * v0.19 moves learned words away from one giant SharedPreferences JSON string.
 * Data is kept in app-private files only:
 *
 *   files/orbit-user-dictionary/dictionary.tsv
 *   files/orbit-user-dictionary/journal.tsv
 *
 * The base file is compacted periodically while normal candidate selections only
 * append one small absolute-state journal row. The old SharedPreferences JSON is
 * migrated once and then removed.
 */
class UserDictionaryStore(private val context: Context) {
    data class Entry(
        val pinyin: String,
        val text: String,
        val frequency: Int,
        val updatedAt: Long,
    )

    data class Stats(
        val entryCount: Int,
        val totalFrequency: Int,
        val maxEntries: Int,
    )

    private val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
    private val storeDir = File(context.filesDir, STORE_DIR).apply { mkdirs() }
    private val baseFile = File(storeDir, BASE_FILE)
    private val journalFile = File(storeDir, JOURNAL_FILE)

    @Volatile private var loaded = false
    private var journalWrites = 0
    private val entriesByPinyin = LinkedHashMap<String, MutableMap<String, Entry>>()

    private val imeEngine: PinyinImeEngine by lazy(LazyThreadSafetyMode.NONE) {
        PinyinImeEngine(context.applicationContext, this)
    }
    private val associationEngine: NextAssociationEngine by lazy(LazyThreadSafetyMode.NONE) {
        NextAssociationEngine(context.applicationContext)
    }

    fun candidatesFor(
        rawInput: String,
        staticCandidates: List<String>,
        contextBeforeCursor: String? = null,
        limit: Int = MAX_CANDIDATES,
    ): List<String> {
        val query = PinyinDictionary.normalize(rawInput)
        val safeLimit = limit.coerceIn(1, MAX_CANDIDATES)
        if (query.isEmpty()) return staticCandidates.take(safeLimit)
        val engineCandidates = runCatching {
            imeEngine.candidates(query, contextBeforeCursor = contextBeforeCursor, limit = safeLimit)
        }.getOrElse { emptyList() }
        if (engineCandidates.isNotEmpty()) {
            return (engineCandidates + staticCandidates).distinct().take(safeLimit)
        }

        ensureLoaded()
        val exactUser = entriesByPinyin[query].orEmpty().values
            .sortedWith(ENTRY_ORDER).map { it.text }
        val prefixUser = entriesByPinyin.asSequence()
            .filter { (pinyin, _) -> pinyin != query && pinyin.startsWith(query) }
            .flatMap { (_, bucket) -> bucket.values.asSequence() }
            .sortedWith(ENTRY_ORDER).map { it.text }.take(safeLimit).toList()
        val containsUser = if (exactUser.isEmpty() && prefixUser.size < 6) {
            entriesByPinyin.asSequence()
                .filter { (pinyin, _) -> pinyin != query && pinyin.contains(query) }
                .flatMap { (_, bucket) -> bucket.values.asSequence() }
                .sortedWith(ENTRY_ORDER).map { it.text }.take(safeLimit).toList()
        } else emptyList()
        return (exactUser + staticCandidates + prefixUser + containsUser)
            .distinct().take(safeLimit)
    }

    fun exactCandidatesFor(
        rawInput: String,
        staticCandidates: List<String>,
        contextBeforeCursor: String? = null,
        limit: Int = MAX_CANDIDATES,
    ): List<String> {
        val query = PinyinDictionary.normalize(rawInput)
        if (query.isEmpty()) return emptyList()
        val safeLimit = limit.coerceIn(1, MAX_CANDIDATES)
        val engineCandidates = runCatching {
            imeEngine.exactCandidates(query, contextBeforeCursor = contextBeforeCursor, limit = safeLimit)
        }.getOrElse { emptyList() }
        if (engineCandidates.isNotEmpty()) return engineCandidates.distinct().take(safeLimit)
        ensureLoaded()
        return entriesByPinyin[query].orEmpty().values
            .sortedWith(ENTRY_ORDER).map { it.text }.distinct().take(safeLimit)
    }

    fun nextSuggestions(contextBeforeCursor: String?, limit: Int = DEFAULT_ASSOCIATION_LIMIT): List<String> {
        val context = contextBeforeCursor.orEmpty().trimEnd()
        if (context.isBlank()) return emptyList()
        return associationEngine.suggestions(context, limit.coerceIn(1, MAX_ASSOCIATION_LIMIT))
    }

    @Synchronized
    fun frequencyFor(rawPinyin: String, rawText: String): Int {
        val pinyin = PinyinDictionary.normalize(rawPinyin)
        val text = rawText.trim()
        if (pinyin.isEmpty() || text.isEmpty()) return 0
        ensureLoadedLocked()
        return entriesByPinyin[pinyin]?.get(text)?.frequency ?: 0
    }

    @Synchronized
    fun learnedEntriesFor(rawInput: String, limit: Int = MAX_CANDIDATES): List<Entry> {
        val query = PinyinDictionary.normalize(rawInput)
        if (query.isEmpty()) return emptyList()
        ensureLoadedLocked()
        val exact = entriesByPinyin[query].orEmpty().values
        val prefix = entriesByPinyin.asSequence()
            .filter { (pinyin, _) -> pinyin != query && pinyin.startsWith(query) }
            .flatMap { (_, bucket) -> bucket.values.asSequence() }
        return (exact.asSequence() + prefix)
            .sortedWith(ENTRY_ORDER)
            .distinctBy { it.pinyin to it.text }
            .take(limit)
            .toList()
    }

    @Synchronized
    fun learn(rawPinyin: String, rawText: String): Boolean {
        val pinyin = PinyinDictionary.normalize(rawPinyin)
        val text = rawText.trim()
        if (!canLearn(pinyin, text)) return false
        ensureLoadedLocked()

        val now = System.currentTimeMillis()
        val bucket = entriesByPinyin.getOrPut(pinyin) { LinkedHashMap() }
        val old = bucket[text]
        val entry = if (old == null) {
            Entry(pinyin, text, 1, now)
        } else {
            old.copy(frequency = (old.frequency + 1).coerceAtMost(MAX_FREQUENCY), updatedAt = now)
        }
        bucket[text] = entry
        appendJournal(entry)

        if (entryCountLocked() > maxEntries()) {
            compactLocked(forceTrim = true)
        } else if (journalWrites >= JOURNAL_COMPACT_WRITES || journalFile.length() >= JOURNAL_COMPACT_BYTES) {
            compactLocked(forceTrim = false)
        }
        imeEngine.clearCandidateCache()
        return true
    }

    @Synchronized
    fun stats(): Stats {
        ensureLoadedLocked()
        return Stats(
            entryCount = entryCountLocked(),
            totalFrequency = entriesByPinyin.values.sumOf { bucket -> bucket.values.sumOf { it.frequency } },
            maxEntries = maxEntries(),
        )
    }

    @Synchronized
    fun clear() {
        entriesByPinyin.clear()
        loaded = true
        journalWrites = 0
        baseFile.delete()
        journalFile.delete()
        legacyPrefs.edit().remove(LEGACY_KEY_ENTRIES_JSON).apply()
        imeEngine.clearCandidateCache()
    }

    @Synchronized
    fun compactNow() {
        ensureLoadedLocked()
        compactLocked(forceTrim = true)
        imeEngine.clearCandidateCache()
    }

    private fun canLearn(pinyin: String, text: String): Boolean {
        if (pinyin.length !in 1..MAX_PINYIN_LENGTH) return false
        if (text.length !in 1..MAX_TEXT_LENGTH) return false
        if (text == pinyin || !containsCjk(text)) return false
        return PrivacyGuard.isSafeToUseForPrompt(text)
    }

    private fun containsCjk(text: String): Boolean = text.any { char ->
        val block = Character.UnicodeBlock.of(char)
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
    }

    private fun maxEntries(): Int = ProGate.maxUserDictionaryItems(context)

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) { ensureLoadedLocked() }
    }

    private fun ensureLoadedLocked() {
        if (loaded) return
        entriesByPinyin.clear()
        readFileInto(baseFile)
        readFileInto(journalFile)
        journalWrites = if (journalFile.isFile()) journalFile.useLines { it.count() } else 0
        migrateLegacyLocked()
        loaded = true
        if (entryCountLocked() > maxEntries()) compactLocked(forceTrim = true)
    }

    private fun readFileInto(file: File) {
        if (!file.isFile) return
        runCatching {
            file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line -> parseLine(line)?.let { putLoaded(it) } }
            }
        }
    }

    private fun parseLine(line: String): Entry? {
        if (line.isBlank() || line.startsWith("#")) return null
        val parts = line.split('\t')
        if (parts.size < 4) return null
        val pinyin = PinyinDictionary.normalize(parts[0])
        val text = runCatching {
            String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        }.getOrNull()?.trim().orEmpty()
        val frequency = parts[2].toIntOrNull()?.coerceIn(1, MAX_FREQUENCY) ?: return null
        val updatedAt = parts[3].toLongOrNull() ?: 0L
        if (!canLearn(pinyin, text)) return null
        return Entry(pinyin, text, frequency, updatedAt)
    }

    private fun putLoaded(entry: Entry) {
        val bucket = entriesByPinyin.getOrPut(entry.pinyin) { LinkedHashMap() }
        val old = bucket[entry.text]
        if (old == null || entry.updatedAt >= old.updatedAt) bucket[entry.text] = entry
    }

    private fun appendJournal(entry: Entry) {
        storeDir.mkdirs()
        journalFile.appendText(serialize(entry) + "\n", Charsets.UTF_8)
        journalWrites++
    }

    private fun serialize(entry: Entry): String {
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(entry.text.toByteArray(Charsets.UTF_8))
        return "${entry.pinyin}\t$encoded\t${entry.frequency}\t${entry.updatedAt}"
    }

    private fun compactLocked(forceTrim: Boolean) {
        storeDir.mkdirs()
        val max = maxEntries()
        val all = entriesByPinyin.values.asSequence().flatMap { it.values.asSequence() }
            .sortedWith(ENTRY_ORDER)
            .let { sequence -> if (forceTrim) sequence.take(max).toList() else sequence.toList() }

        val temp = File(storeDir, "$BASE_FILE.tmp")
        temp.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.appendLine("#ORBIT_USER_DICTIONARY\t2")
            all.forEach { writer.appendLine(serialize(it)) }
        }
        if (baseFile.exists() && !baseFile.delete()) {
            temp.delete()
            return
        }
        if (!temp.renameTo(baseFile)) {
            temp.copyTo(baseFile, overwrite = true)
            temp.delete()
        }
        journalFile.delete()
        journalWrites = 0
        entriesByPinyin.clear()
        all.forEach { putLoaded(it) }
    }

    private fun migrateLegacyLocked() {
        val raw = legacyPrefs.getString(LEGACY_KEY_ENTRIES_JSON, null)
        if (raw.isNullOrBlank()) return
        val migrated = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val pinyin = PinyinDictionary.normalize(item.optString("pinyin"))
                    val text = item.optString("text").trim()
                    val frequency = item.optInt("frequency", 1).coerceIn(1, MAX_FREQUENCY)
                    val updatedAt = item.optLong("updatedAt", 0L)
                    if (canLearn(pinyin, text)) add(Entry(pinyin, text, frequency, updatedAt))
                }
            }
        }.getOrElse { emptyList() }
        if (migrated.isNotEmpty()) {
            migrated.forEach { putLoaded(it) }
            compactLocked(forceTrim = true)
        }
        legacyPrefs.edit().remove(LEGACY_KEY_ENTRIES_JSON).apply()
    }

    private fun entryCountLocked(): Int = entriesByPinyin.values.sumOf { it.size }

    companion object {
        private const val LEGACY_PREFS = "orbit_user_dictionary"
        private const val LEGACY_KEY_ENTRIES_JSON = "entries_json"
        private const val STORE_DIR = "orbit-user-dictionary"
        private const val BASE_FILE = "dictionary.tsv"
        private const val JOURNAL_FILE = "journal.tsv"
        private const val JOURNAL_COMPACT_WRITES = 512
        private const val JOURNAL_COMPACT_BYTES = 1_048_576L
        private const val MAX_CANDIDATES = 48
        private const val DEFAULT_ASSOCIATION_LIMIT = 48
        private const val MAX_ASSOCIATION_LIMIT = 64
        private const val MAX_FREQUENCY = 999_999
        private const val MAX_PINYIN_LENGTH = 192
        private const val MAX_TEXT_LENGTH = 128

        private val ENTRY_ORDER = compareByDescending<Entry> { it.frequency }
            .thenByDescending { it.updatedAt }
            .thenBy { it.text }
    }
}
