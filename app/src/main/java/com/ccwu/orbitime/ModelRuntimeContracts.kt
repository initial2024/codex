package com.ccwu.orbitime

/**
 * Runtime contracts shared by future local model backends.
 *
 * v0.22 intentionally ships only metadata/install management. No neural runtime is
 * linked yet, so these interfaces make the boundary explicit instead of pretending
 * an imported pack can already execute.
 */
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

interface OrbitTtsProvider : OrbitModelProvider {
    fun synthesize(text: String, language: String, voiceId: String?): ByteArray?
}

interface OrbitVoiceCloneProvider : OrbitModelProvider {
    fun synthesizeWithReference(
        text: String,
        language: String,
        referencePcm16: ShortArray,
        referenceSampleRate: Int,
    ): ByteArray?
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
            label = "已安装元数据/模型文件；v0.22 暂不执行神经翻译",
            nextMilestone = "v0.23 TranslationProvider runtime",
        )
        OrbitModelPackType.ASR -> RuntimeStatus(
            executable = false,
            label = "已安装语音识别包；v0.22 不申请麦克风权限，也不启动 ASR",
            nextMilestone = "v0.24 sherpa-onnx ASR runtime",
        )
        OrbitModelPackType.TTS -> RuntimeStatus(
            executable = false,
            label = "已安装 TTS 包；v0.22 暂不执行语音合成",
            nextMilestone = "v0.25 local TTS runtime",
        )
        OrbitModelPackType.VOICE_CLONE -> RuntimeStatus(
            executable = false,
            label = "已安装实验音色包；v0.22 不执行音色克隆",
            nextMilestone = "later experimental voice-clone runtime",
        )
    }
}
