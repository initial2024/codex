package com.ccwu.orbitime

/**
 * Curated upstream references shown to Pro users.
 *
 * Orbit does not download these itself. Runtime/source licenses and individual
 * checkpoint/voice licenses are distinct; every imported .orbitpack must still
 * bundle the exact LICENSE/NOTICE that applies to the packaged files.
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
            title = "k2-fsa sherpa-onnx",
            category = "Android 本地语音运行时",
            sourceUrl = "https://github.com/k2-fsa/sherpa-onnx",
            license = "Apache-2.0 runtime",
            commercialNote = "运行时代码采用宽松许可证；具体 ASR/TTS/ZipVoice checkpoint、词典和 voice 仍必须逐包核对。",
            recommendation = "Orbit v0.24–v0.26 的可执行语音主线；当前 Gradle 适配 1.13.8。",
        ),
        Entry(
            title = "k2-fsa sherpa-onnx models",
            category = "ASR 模型来源",
            sourceUrl = "https://huggingface.co/k2-fsa/sherpa-onnx-models",
            license = "checkpoint-specific",
            commercialNote = "模型集合并不意味着所有 checkpoint 使用同一许可证。制作 .orbitpack 前必须查看对应模型来源和许可。",
            recommendation = "优先选择 sherpa offline transducer，并转换/打包为 model_family=sherpa_offline_transducer。",
        ),
        Entry(
            title = "k2-fsa ZipVoice",
            category = "零样本 TTS / 音色克隆",
            sourceUrl = "https://huggingface.co/k2-fsa/ZipVoice",
            license = "checkpoint/source-specific",
            commercialNote = "Orbit 不替用户推断商用权。导入前必须把实际使用 checkpoint 的许可证、数据/声音权利说明写入 LICENSE/NOTICE。",
            recommendation = "v0.26 已提供 sherpa_zipvoice 本地适配；只允许本人或已明确授权的参考声音。",
        ),
        Entry(
            title = "Helsinki-NLP OPUS-MT zh→en",
            category = "神经翻译候选",
            sourceUrl = "https://huggingface.co/Helsinki-NLP/opus-mt-zh-en",
            license = "CC-BY-4.0",
            commercialNote = "允许商用但有署名等义务；导入包必须附准确 LICENSE/NOTICE。",
            recommendation = "来源/许可适合继续研究；当前 Orbit 尚未内置 Marian/OPUS-MT Android decoder，因此不会伪装成已可执行。",
        ),
        Entry(
            title = "Helsinki-NLP OPUS-MT en→zh",
            category = "神经翻译候选",
            sourceUrl = "https://huggingface.co/Helsinki-NLP/opus-mt-en-zh",
            license = "Apache-2.0",
            commercialNote = "许可证相对宽松，仍须保留许可证和通知，并核对模型关联数据义务。",
            recommendation = "英中神经翻译候选；当前只允许安装/审计，不虚构本地神经推理结果。",
        ),
        Entry(
            title = "Meta NLLB-200 distilled 600M",
            category = "神经翻译 · 研究候选",
            sourceUrl = "https://huggingface.co/facebook/nllb-200-distilled-600M",
            license = "CC-BY-NC-4.0",
            commercialNote = "非商业许可证；不作为 Orbit 商业 Pro 默认模型。",
            recommendation = "多语言研究/个人实验候选，生产版默认不推荐。",
        ),
        Entry(
            title = "Audio8 TTS Preview 0.6B",
            category = "TTS / Zero-shot voice clone · 实验候选",
            sourceUrl = "https://huggingface.co/Audio8/Audio8-TTS-Preview-0.6b",
            license = "Apache-2.0 (current model card)",
            commercialNote = "当前 0.6B 模型卡声明代码和权重为 Apache-2.0；实际打包仍必须携带仓库 LICENSE/NOTICE，并重新核验版本。",
            recommendation = "11 语言、44.1kHz、零样本音色克隆；使用 Transformers custom code，当前 Android APK 没有 Audio8 adapter，因此只作为实验来源。",
        ),
        Entry(
            title = "Audio8 TTS Preview 0.1B",
            category = "TTS / Voice clone · 许可受限实验候选",
            sourceUrl = "https://huggingface.co/Audio8/Audio8-TTS-Preview-0.1b",
            license = "Audio8 Community License v1.0",
            commercialNote = "当前许可证含年收入门槛等条件，不等同 Apache-2.0；商业使用必须逐条核对并在达到门槛时取得额外许可。",
            recommendation = "体积更小但许可证更复杂；不作为默认商业模型。",
        ),
    )
}
