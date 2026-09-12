package com.ccwu.orbitime

/** Shared runtime contracts for optional, user-installed local model packs. */
interface OrbitModelProvider {
    val providerId: String
    val packId: String
    val type: OrbitModelPackType
    fun isReady(): Boolean
    fun close()
}

interface OrbitTranslationProvider : OrbitModelProvider {
    fun translate(text: String, sourceLanguage: String, targetLanguage: String): String?
}

interface OrbitAsrProvider : OrbitModelProvider {
    fun transcribePcm16(samples: ShortArray, sampleRate: Int, language: String?): String?
}

data class OrbitAudioResult(
    val samples: FloatArray,
    val sampleRate: Int,
)

interface OrbitTtsProvider : OrbitModelProvider {
    fun synthesize(text: String, language: String, voiceId: String?): OrbitAudioResult?
}

interface OrbitVoiceCloneProvider : OrbitModelProvider {
    fun synthesizeWithReference(
        text: String,
        language: String,
        referencePcm16: ShortArray,
        referenceSampleRate: Int,
        referenceText: String,
    ): OrbitAudioResult?
}

object OrbitModelRuntimeRegistry {
    data class RuntimeStatus(
        val executable: Boolean,
        val label: String,
        val nextMilestone: String,
    )

    fun statusFor(manifest: OrbitModelPackManifest): RuntimeStatus = when (manifest.type) {
        OrbitModelPackType.TRANSLATION -> RuntimeStatus(
            executable = false,
            label = "模型包可安全安装；当前 APK 尚未内置通用 Marian/OPUS-MT Android 解码器，继续使用本地词典/规则翻译",
            nextMilestone = "future audited neural-translation runtime",
        )
        OrbitModelPackType.ASR -> if (manifest.runtime == OrbitModelRuntime.SHERPA_ONNX && manifest.modelFamily == "sherpa_offline_transducer") {
            RuntimeStatus(true, "可执行：sherpa-onnx 本地离线 ASR", "v0.24 local ASR")
        } else {
            RuntimeStatus(false, "已安装 ASR 包，但当前只执行 sherpa_offline_transducer 家族", "convert/package as sherpa_offline_transducer")
        }
        OrbitModelPackType.TTS -> if (
            manifest.runtime == OrbitModelRuntime.SHERPA_ONNX &&
            manifest.modelFamily in setOf("sherpa_vits", "sherpa_kokoro", "sherpa_supertonic")
        ) {
            RuntimeStatus(true, "可执行：sherpa-onnx 本地 TTS", "v0.25 local TTS")
        } else {
            RuntimeStatus(false, "已安装 TTS 包，但当前模型家族没有可执行适配器", "use a supported sherpa TTS family")
        }
        OrbitModelPackType.VOICE_CLONE -> if (
            manifest.runtime == OrbitModelRuntime.SHERPA_ONNX && manifest.modelFamily == "sherpa_zipvoice"
        ) {
            RuntimeStatus(true, "可执行：ZipVoice 本地零样本音色克隆", "v0.26 local voice clone")
        } else {
            RuntimeStatus(false, "已安装实验音色包；当前仅 ZipVoice sherpa 包可执行，Audio8 等保留为来源明确的实验候选", "future audited adapter")
        }
    }
}
