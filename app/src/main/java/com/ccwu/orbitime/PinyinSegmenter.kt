package com.ccwu.orbitime

import java.util.Locale
import kotlin.math.min

/**
 * Tone-less Hanyu-Pinyin segmenter for continuous 26-key input.
 *
 * It uses dynamic programming and keeps several best segmentations instead of
 * greedily taking the longest syllable. Explicit apostrophes/spaces are treated
 * as hard boundaries.
 */
object PinyinSegmenter {
    data class Segmentation(
        val syllables: List<String>,
        val score: Double,
        val exact: Boolean,
    ) {
        val joined: String get() = syllables.joinToString("")
    }

    private data class Path(
        val syllables: List<String>,
        val score: Double,
    )

    fun segment(rawInput: String, maxResults: Int = 6): List<Segmentation> {
        val chunks = rawInput
            .lowercase(Locale.ROOT)
            .replace('ü', 'v')
            .replace("u:", "v")
            .split(Regex("['\\s]+"))
            .map { PinyinDictionary.normalize(it) }
            .filter { it.isNotBlank() }

        if (chunks.isEmpty()) return emptyList()

        var combined = listOf(Path(emptyList(), 0.0))
        var allExact = true
        for (chunk in chunks) {
            val chunkPaths = segmentChunk(chunk, maxResults)
            if (chunkPaths.isEmpty()) {
                allExact = false
                combined = combined.map { path ->
                    Path(path.syllables + chunk, path.score - INVALID_CHUNK_PENALTY)
                }
                continue
            }
            combined = combined
                .flatMap { prefix ->
                    chunkPaths.map { suffix ->
                        Path(prefix.syllables + suffix.syllables, prefix.score + suffix.score)
                    }
                }
                .sortedByDescending { it.score }
                .distinctBy { it.syllables }
                .take(maxResults)
        }

        return combined
            .map { Segmentation(it.syllables, it.score, allExact && it.syllables.all(SYLLABLES::contains)) }
            .sortedByDescending { it.score }
            .take(maxResults)
    }

    fun isValidSyllable(raw: String): Boolean = SYLLABLES.contains(PinyinDictionary.normalize(raw))

    fun allSyllables(): Set<String> = SYLLABLES

    private fun segmentChunk(chunk: String, maxResults: Int): List<Path> {
        if (chunk.isBlank()) return emptyList()
        val n = chunk.length
        val dp = Array(n + 1) { mutableListOf<Path>() }
        dp[0].add(Path(emptyList(), 0.0))

        for (start in 0 until n) {
            val prefixes = dp[start]
            if (prefixes.isEmpty()) continue
            val maxEnd = min(n, start + MAX_SYLLABLE_LENGTH)
            for (end in (start + 1)..maxEnd) {
                val syllable = chunk.substring(start, end)
                if (!SYLLABLES.contains(syllable)) continue
                val syllableScore = syllableScore(syllable)
                prefixes.forEach { prefix ->
                    dp[end].add(Path(prefix.syllables + syllable, prefix.score + syllableScore))
                }
                trimPaths(dp[end], maxResults * 3)
            }
        }

        return dp[n]
            .sortedWith(compareByDescending<Path> { it.score }.thenBy { it.syllables.size })
            .distinctBy { it.syllables }
            .take(maxResults)
    }

    private fun syllableScore(syllable: String): Double {
        // Total character count is fixed for one query, so the negative per-syllable
        // cost prevents over-segmentation such as hao -> ha + o. Longer complete
        // syllables win unless a strong common-syllable prior supports another path.
        val commonBoost = if (COMMON_SYLLABLES.contains(syllable)) 0.22 else 0.0
        return syllable.length * 0.30 - 0.45 + commonBoost
    }

    private fun trimPaths(paths: MutableList<Path>, limit: Int) {
        if (paths.size <= limit) return
        val trimmed = paths
            .sortedByDescending { it.score }
            .distinctBy { it.syllables }
            .take(limit)
        paths.clear()
        paths.addAll(trimmed)
    }

    private val COMMON_SYLLABLES = setOf(
        "de", "le", "shi", "wo", "ni", "ta", "zai", "you", "bu", "ren", "hao", "ma", "yao", "hui", "neng", "ke", "yi",
    )

    // Standard tone-less Mandarin syllables. ü is represented by v for keyboard input.
    private val SYLLABLES: Set<String> = """
        a ai an ang ao
        ba bai ban bang bao bei ben beng bi bian biao bie bin bing bo bu
        ca cai can cang cao ce cen ceng cha chai chan chang chao che chen cheng chi chong chou chu chua chuai chuan chuang chui chun chuo ci cong cou cu cuan cui cun cuo
        da dai dan dang dao de dei den deng di dia dian diao die ding diu dong dou du duan dui dun duo
        e ei en eng er
        fa fan fang fei fen feng fo fou fu
        ga gai gan gang gao ge gei gen geng gong gou gu gua guai guan guang gui gun guo
        ha hai han hang hao he hei hen heng hong hou hu hua huai huan huang hui hun huo
        ji jia jian jiang jiao jie jin jing jiong jiu ju juan jue jun
        ka kai kan kang kao ke ken keng kong kou ku kua kuai kuan kuang kui kun kuo
        la lai lan lang lao le lei leng li lia lian liang liao lie lin ling liu lo long lou lu luan lun luo lv lve
        ma mai man mang mao me mei men meng mi mian miao mie min ming miu mo mou mu
        na nai nan nang nao ne nei nen neng ni nian niang niao nie nin ning niu nong nou nu nuan nuo nv nve
        o ou
        pa pai pan pang pao pei pen peng pi pian piao pie pin ping po pou pu
        qi qia qian qiang qiao qie qin qing qiong qiu qu quan que qun
        ran rang rao re ren reng ri rong rou ru rua ruan rui run ruo
        sa sai san sang sao se sen seng sha shai shan shang shao she shei shen sheng shi shou shu shua shuai shuan shuang shui shun shuo si song sou su suan sui sun suo
        ta tai tan tang tao te teng ti tian tiao tie ting tong tou tu tuan tui tun tuo
        wa wai wan wang wei wen weng wo wu
        xi xia xian xiang xiao xie xin xing xiong xiu xu xuan xue xun
        ya yan yang yao ye yi yin ying yo yong you yu yuan yue yun
        za zai zan zang zao ze zei zen zeng zha zhai zhan zhang zhao zhe zhei zhen zheng zhi zhong zhou zhu zhua zhuai zhuan zhuang zhui zhun zhuo zi zong zou zu zuan zui zun zuo
    """.trimIndent().split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()

    private const val MAX_SYLLABLE_LENGTH = 6
    private const val INVALID_CHUNK_PENALTY = 8.0
}
