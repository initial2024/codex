package com.ccwu.orbitime

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsZipVoiceModelConfig
import java.io.File

private fun ModelPackManager.InstalledPack.modelPath(key: String, fallback: String): String {
    val relative = manifest.runtimeValue(key, fallback).replace('\\', '/').trimStart('/')
    require(relative.isNotBlank() && !relative.contains("..")) { "invalid model path for $key" }
    val base = directory.canonicalFile
    val file = File(directory, relative).canonicalFile
    require(file.path == base.path || file.path.startsWith(base.path + File.separator)) { "model path escaped pack directory" }
    require(file.exists()) { "model file missing: $relative" }
    return file.absolutePath
}

private fun String.intOr(defaultValue: Int): Int = toIntOrNull()?.coerceIn(1, 8) ?: defaultValue
private fun String.floatOr(defaultValue: Float): Float = toFloatOrNull()?.coerceIn(0.25f, 3.0f) ?: defaultValue
private fun ShortArray.toFloatSamples(): FloatArray = FloatArray(size) { index -> this[index] / 32768.0f }

class SherpaAsrProvider(
    private val pack: ModelPackManager.InstalledPack,
) : OrbitAsrProvider {
    override val providerId: String = "sherpa-asr"
    override val packId: String = pack.manifest.packId
    override val type: OrbitModelPackType = OrbitModelPackType.ASR

    private val recognizer: OfflineRecognizer

    init {
        require(pack.manifest.runtime == OrbitModelRuntime.SHERPA_ONNX)
        require(pack.manifest.modelFamily == "sherpa_offline_transducer")
        val model = OfflineModelConfig(
            transducer = OfflineTransducerModelConfig(
                encoder = pack.modelPath("encoder", "model/encoder.int8.onnx"),
                decoder = pack.modelPath("decoder", "model/decoder.onnx"),
                joiner = pack.modelPath("joiner", "model/joiner.int8.onnx"),
            ),
            tokens = pack.modelPath("tokens", "model/tokens.txt"),
            numThreads = pack.manifest.runtimeValue("num_threads", "2").intOr(2),
            debug = false,
            provider = "cpu",
            modelType = "transducer",
        )
        recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                modelConfig = model,
                decodingMethod = pack.manifest.runtimeValue("decoding_method", "greedy_search"),
                maxActivePaths = pack.manifest.runtimeValue("max_active_paths", "4").intOr(4),
            ),
        )
    }

    override fun isReady(): Boolean = true

    override fun transcribePcm16(samples: ShortArray, sampleRate: Int, language: String?): String? {
        if (samples.isEmpty() || sampleRate <= 0) return null
        val stream = recognizer.createStream()
        return try {
            language?.takeIf { it.isNotBlank() }?.let { stream.setOption("language", it) }
            stream.acceptWaveform(samples.toFloatSamples(), sampleRate)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim().ifBlank { null }
        } finally {
            stream.release()
        }
    }

    override fun close() = recognizer.release()
}

class SherpaTtsProvider(
    private val pack: ModelPackManager.InstalledPack,
) : OrbitTtsProvider {
    override val providerId: String = "sherpa-tts"
    override val packId: String = pack.manifest.packId
    override val type: OrbitModelPackType = OrbitModelPackType.TTS

    private val tts: OfflineTts

    init {
        require(pack.manifest.runtime == OrbitModelRuntime.SHERPA_ONNX)
        val threads = pack.manifest.runtimeValue("num_threads", "2").intOr(2)
        val modelConfig = when (pack.manifest.modelFamily) {
            "sherpa_vits" -> OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = pack.modelPath("model", "model/model.onnx"),
                    lexicon = optionalPath("lexicon"),
                    tokens = pack.modelPath("tokens", "model/tokens.txt"),
                    dataDir = optionalPath("data_dir"),
                    lengthScale = 1.0f,
                ),
                numThreads = threads,
                debug = false,
            )
            "sherpa_kokoro" -> OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = pack.modelPath("model", "model/model.onnx"),
                    voices = pack.modelPath("voices", "model/voices.bin"),
                    tokens = pack.modelPath("tokens", "model/tokens.txt"),
                    dataDir = optionalPath("data_dir"),
                    lexicon = optionalPath("lexicon"),
                    lang = pack.manifest.runtimeValue("lang", ""),
                ),
                numThreads = threads,
                debug = false,
            )
            "sherpa_supertonic" -> OfflineTtsModelConfig(
                supertonic = OfflineTtsSupertonicModelConfig(
                    durationPredictor = pack.modelPath("duration_predictor", "model/duration_predictor.int8.onnx"),
                    textEncoder = pack.modelPath("text_encoder", "model/text_encoder.int8.onnx"),
                    vectorEstimator = pack.modelPath("vector_estimator", "model/vector_estimator.int8.onnx"),
                    vocoder = pack.modelPath("vocoder", "model/vocoder.int8.onnx"),
                    ttsJson = pack.modelPath("tts_json", "model/tts.json"),
                    unicodeIndexer = pack.modelPath("unicode_indexer", "model/unicode_indexer.bin"),
                    voiceStyle = pack.modelPath("voice_style", "model/voice.bin"),
                ),
                numThreads = threads,
                debug = false,
            )
            else -> error("unsupported sherpa TTS family: ${pack.manifest.modelFamily}")
        }
        tts = OfflineTts(config = OfflineTtsConfig(model = modelConfig))
    }

    private fun optionalPath(key: String): String {
        val value = pack.manifest.runtimeValue(key, "")
        if (value.isBlank()) return ""
        return pack.modelPath(key, value)
    }

    override fun isReady(): Boolean = true

    override fun synthesize(text: String, language: String, voiceId: String?): OrbitAudioResult? {
        if (text.isBlank()) return null
        val sid = voiceId?.toIntOrNull()?.coerceAtLeast(0) ?: pack.manifest.runtimeValue("speaker_id", "0").toIntOrNull()?.coerceAtLeast(0) ?: 0
        val speed = pack.manifest.runtimeValue("speed", "1.0").floatOr(1.0f)
        val generation = GenerationConfig(
            sid = sid,
            speed = speed,
            silenceScale = 0.2f,
            numSteps = pack.manifest.runtimeValue("num_steps", "5").toIntOrNull()?.coerceIn(1, 32) ?: 5,
            extra = if (language.isBlank()) null else mapOf("lang" to language),
        )
        val audio = tts.generateWithConfig(text, generation)
        return OrbitAudioResult(audio.samples, audio.sampleRate)
    }

    override fun close() = tts.release()
}

class SherpaZipVoiceProvider(
    private val pack: ModelPackManager.InstalledPack,
) : OrbitVoiceCloneProvider {
    override val providerId: String = "sherpa-zipvoice"
    override val packId: String = pack.manifest.packId
    override val type: OrbitModelPackType = OrbitModelPackType.VOICE_CLONE

    private val tts: OfflineTts

    init {
        require(pack.manifest.runtime == OrbitModelRuntime.SHERPA_ONNX)
        require(pack.manifest.modelFamily == "sherpa_zipvoice")
        val model = OfflineTtsModelConfig(
            zipvoice = OfflineTtsZipVoiceModelConfig(
                encoder = pack.modelPath("encoder", "model/encoder.int8.onnx"),
                decoder = pack.modelPath("decoder", "model/decoder.int8.onnx"),
                vocoder = pack.modelPath("vocoder", "model/vocoder.onnx"),
                tokens = pack.modelPath("tokens", "model/tokens.txt"),
                lexicon = pack.modelPath("lexicon", "model/lexicon.txt"),
                dataDir = pack.modelPath("data_dir", "model/espeak-ng-data"),
            ),
            numThreads = pack.manifest.runtimeValue("num_threads", "2").intOr(2),
            debug = false,
        )
        tts = OfflineTts(config = OfflineTtsConfig(model = model))
    }

    override fun isReady(): Boolean = true

    override fun synthesizeWithReference(
        text: String,
        language: String,
        referencePcm16: ShortArray,
        referenceSampleRate: Int,
        referenceText: String,
    ): OrbitAudioResult? {
        if (text.isBlank() || referenceText.isBlank() || referencePcm16.isEmpty() || referenceSampleRate <= 0) return null
        val generation = GenerationConfig(
            speed = pack.manifest.runtimeValue("speed", "1.0").floatOr(1.0f),
            referenceAudio = referencePcm16.toFloatSamples(),
            referenceSampleRate = referenceSampleRate,
            referenceText = referenceText,
            numSteps = pack.manifest.runtimeValue("num_steps", "5").toIntOrNull()?.coerceIn(1, 32) ?: 5,
            extra = if (language.isBlank()) null else mapOf("lang" to language),
        )
        val audio = tts.generateWithConfig(text, generation)
        return OrbitAudioResult(audio.samples, audio.sampleRate)
    }

    override fun close() = tts.release()
}

object OrbitModelProviderFactory {
    fun createAsr(pack: ModelPackManager.InstalledPack): OrbitAsrProvider? = runCatching {
        if (OrbitModelRuntimeRegistry.statusFor(pack.manifest).executable && pack.manifest.type == OrbitModelPackType.ASR) SherpaAsrProvider(pack) else null
    }.getOrNull()

    fun createTts(pack: ModelPackManager.InstalledPack): OrbitTtsProvider? = runCatching {
        if (OrbitModelRuntimeRegistry.statusFor(pack.manifest).executable && pack.manifest.type == OrbitModelPackType.TTS) SherpaTtsProvider(pack) else null
    }.getOrNull()

    fun createVoiceClone(pack: ModelPackManager.InstalledPack): OrbitVoiceCloneProvider? = runCatching {
        if (OrbitModelRuntimeRegistry.statusFor(pack.manifest).executable && pack.manifest.type == OrbitModelPackType.VOICE_CLONE) SherpaZipVoiceProvider(pack) else null
    }.getOrNull()
}
