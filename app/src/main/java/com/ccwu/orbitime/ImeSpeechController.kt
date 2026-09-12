package com.ccwu.orbitime

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

class ImeSpeechController(private val context: Context) {
    private val modelPacks = ModelPackManager(context)
    private val capture = LocalSpeechInputController(context)
    private val voiceReferences = VoiceReferenceStore(context)
    private val main = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)

    fun isRecording(): Boolean = capture.isRecording()
    fun isBusy(): Boolean = busy.get()
    fun hasMicrophonePermission(): Boolean = capture.hasPermission()

    fun cancelCapture() = capture.cancel()

    fun shutdown() {
        capture.cancel()
        OrbitAudioPlayer.stop()
        busy.set(false)
    }

    fun toggleAsr(
        language: String?,
        onPermissionRequired: () -> Unit,
        onState: (String) -> Unit,
        onText: (String) -> Unit,
    ) {
        if (!ProGate.isLocalAsrUnlocked(context)) {
            onState("本地语音输入需要 Pro")
            return
        }
        if (!capture.hasPermission()) {
            onPermissionRequired()
            return
        }
        if (busy.get()) {
            onState("语音模型正在处理，请稍候")
            return
        }
        if (!capture.isRecording()) {
            val pack = executablePack(OrbitModelPackType.ASR)
            if (pack == null) {
                onState(asrUnavailableMessage())
                return
            }
            val started = capture.start()
            onState(started.message)
            return
        }

        val pack = executablePack(OrbitModelPackType.ASR)
        if (pack == null) {
            capture.cancel()
            onState("ASR 模型包已不可用")
            return
        }
        if (!busy.compareAndSet(false, true)) return
        onState("正在停止录音并本地识别…")
        Thread {
            val message = runCatching {
                val audio = capture.stop().getOrThrow()
                val provider = OrbitModelProviderFactory.createAsr(pack) ?: error("无法创建 ASR 运行时")
                try {
                    val text = provider.transcribePcm16(audio.samples, audio.sampleRate, language).orEmpty().trim()
                    if (text.isBlank()) error("没有识别到文字")
                    main.post { onText(text) }
                    "识别完成"
                } finally { provider.close() }
            }.getOrElse { "识别失败：${it.message ?: it.javaClass.simpleName}" }
            busy.set(false)
            main.post { onState(message) }
        }.start()
    }

    fun speak(textRaw: String, language: String, onState: (String) -> Unit) {
        if (!ProGate.isLocalTtsUnlocked(context)) { onState("本地 TTS 需要 Pro"); return }
        val text = textRaw.trim().take(600)
        if (text.isBlank()) { onState("没有可朗读文本"); return }
        val pack = executablePack(OrbitModelPackType.TTS)
        if (pack == null) { onState("请先安装并启用可执行的 sherpa TTS 模型包"); return }
        if (!busy.compareAndSet(false, true)) { onState("语音模型正在处理，请稍候"); return }
        onState("正在本地合成…")
        Thread {
            val message = runCatching {
                val provider = OrbitModelProviderFactory.createTts(pack) ?: error("无法创建 TTS 运行时")
                try {
                    val audio = provider.synthesize(text, language, null) ?: error("没有生成音频")
                    OrbitAudioPlayer.play(audio).getOrThrow()
                    "朗读完成"
                } finally { provider.close() }
            }.getOrElse { "朗读失败：${it.message ?: it.javaClass.simpleName}" }
            busy.set(false)
            main.post { onState(message) }
        }.start()
    }

    fun speakWithClonedVoice(textRaw: String, language: String, onState: (String) -> Unit) {
        if (!ProGate.isVoiceCloneUnlocked(context)) { onState("音色克隆需要 Pro"); return }
        val text = textRaw.trim().take(400)
        if (text.isBlank()) { onState("没有可朗读文本"); return }
        val pack = executablePack(OrbitModelPackType.VOICE_CLONE)
        if (pack == null) { onState("请先安装并启用 sherpa ZipVoice 模型包"); return }
        if (!busy.compareAndSet(false, true)) { onState("语音模型正在处理，请稍候"); return }
        onState("正在本地生成克隆语音…")
        Thread {
            val message = runCatching {
                val reference = voiceReferences.load() ?: error("请先在 Orbit 设置中保存本人/已授权参考声音")
                val provider = OrbitModelProviderFactory.createVoiceClone(pack) ?: error("无法创建 ZipVoice 运行时")
                try {
                    val audio = provider.synthesizeWithReference(
                        text = text,
                        language = language,
                        referencePcm16 = reference.samples,
                        referenceSampleRate = reference.sampleRate,
                        referenceText = reference.transcript,
                    ) ?: error("没有生成克隆音频")
                    OrbitAudioPlayer.play(audio).getOrThrow()
                    "克隆语音播放完成"
                } finally { provider.close() }
            }.getOrElse { "音色克隆失败：${it.message ?: it.javaClass.simpleName}" }
            busy.set(false)
            main.post { onState(message) }
        }.start()
    }


    private fun asrUnavailableMessage(): String {
        val installed = modelPacks.listInstalled().filter { it.manifest.type == OrbitModelPackType.ASR }
        if (installed.isEmpty()) return "未安装 ASR 模型包；请在 Orbit 设置中导入 .orbitpack"
        val executable = installed.filter { it.runtimeStatus.executable }
        if (executable.isEmpty()) return "已安装 ASR 模型，但当前 model_family/runtime_config 不可执行"
        return "已安装可执行 ASR 模型，但尚未设为首选；请在 Orbit 设置中启用"
    }

    private fun executablePack(type: OrbitModelPackType): ModelPackManager.InstalledPack? {
        val pack = modelPacks.enabledPack(type) ?: return null
        return pack.takeIf { it.enabled && it.runtimeStatus.executable }
    }
}
