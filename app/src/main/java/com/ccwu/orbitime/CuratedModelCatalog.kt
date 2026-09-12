package com.ccwu.orbitime

/**
 * Curated source references shown to Pro users.
 *
 * These are references only. Orbit v0.22 has no INTERNET permission and does not
 * download them itself. Model/runtime licenses can differ from individual voice or
 * checkpoint licenses, so every imported .orbitpack must still bundle LICENSE/NOTICE.
 */
object CuratedModelCatalog {
    data class Entry(
        val title: String,
        val category: String,
        val sourceUrl: String,
        val license: String,
        val commercialNote: String,
        val recommendation: String,
    )

    val entries = listOf(
        Entry(
            title = "Helsinki-NLP OPUS-MT zh→en",
            category = "神经翻译",
            sourceUrl = "https://huggingface.co/Helsinki-NLP/opus-mt-zh-en",
            license = "CC-BY-4.0",
            commercialNote = "允许商用，但需要满足署名等许可证义务；导入包必须附 LICENSE/NOTICE。",
            recommendation = "v0.23 中英离线神经翻译首选候选之一。",
        ),
        Entry(
            title = "Helsinki-NLP OPUS-MT en→zh",
            category = "神经翻译",
            sourceUrl = "https://huggingface.co/Helsinki-NLP/opus-mt-en-zh",
            license = "Apache-2.0",
            commercialNote = "许可证相对宽松，仍须保留许可证和通知。",
            recommendation = "v0.23 英中离线神经翻译首选候选之一。",
        ),
        Entry(
            title = "Meta NLLB-200 distilled 600M",
            category = "神经翻译 · 研究候选",
            sourceUrl = "https://huggingface.co/facebook/nllb-200-distilled-600M",
            license = "CC-BY-NC-4.0",
            commercialNote = "非商业许可证；不作为 Orbit 商业版默认模型。可供个人/研究测试，但需自行遵守许可。",
            recommendation = "多语言能力强，但商业边界不适合默认集成。",
        ),
        Entry(
            title = "k2-fsa sherpa-onnx",
            category = "ASR/TTS runtime",
            sourceUrl = "https://github.com/k2-fsa/sherpa-onnx",
            license = "Apache-2.0 runtime",
            commercialNote = "运行时代码许可证较宽松；具体 ASR/TTS 模型必须单独核对各自许可证。",
            recommendation = "v0.24 语音输入优先运行时。",
        ),
        Entry(
            title = "Piper / ONNX voices",
            category = "TTS",
            sourceUrl = "https://github.com/rhasspy/piper",
            license = "runtime/model dependent",
            commercialNote = "Piper 代码与每个 voice 的许可证必须分别核对；不能因为运行时可用就默认所有音色可商用。",
            recommendation = "v0.25 本地 TTS 候选；优先挑许可证明确的中文/英文 voice。",
        ),
    )
}
