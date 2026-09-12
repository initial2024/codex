package com.ccwu.orbitime

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Explicit foreground microphone capture for local ASR.
 * Audio is kept only in RAM, capped at [MAX_SECONDS], and never persisted.
 */
class LocalSpeechInputController(private val context: Context) {
    data class Capture(val samples: ShortArray, val sampleRate: Int)
    data class ActionResult(val success: Boolean, val message: String)

    private val recording = AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var worker: Thread? = null
    @Volatile private var captured: ShortArray? = null
    @Volatile private var failure: String? = null

    fun hasPermission(): Boolean = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun isRecording(): Boolean = recording.get()

    @Synchronized
    fun start(): ActionResult {
        if (!hasPermission()) return ActionResult(false, "需要先在 Orbit 设置中授权麦克风")
        if (recording.get()) return ActionResult(false, "已经在录音")

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) return ActionResult(false, "设备不支持 16kHz 单声道 PCM16 录音")
        val audioRecord = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                maxOf(minBuffer, READ_CHUNK_BYTES * 2),
            )
        }.getOrElse { return ActionResult(false, "无法初始化麦克风：${it.message ?: it.javaClass.simpleName}") }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return ActionResult(false, "麦克风初始化失败")
        }

        captured = null
        failure = null
        recorder = audioRecord
        recording.set(true)
        worker = Thread({ captureLoop(audioRecord) }, "orbit-local-asr-capture").apply { start() }
        return ActionResult(true, "正在本地录音；再次点语音键停止并识别")
    }

    @Synchronized
    fun stop(): Result<Capture> {
        if (!recording.get() && captured == null) return Result.failure(IllegalStateException("当前没有录音"))
        recording.set(false)
        runCatching { recorder?.stop() }
        val thread = worker
        if (thread != null && thread !== Thread.currentThread()) runCatching { thread.join(STOP_JOIN_MS) }
        val error = failure
        val samples = captured
        cleanupRecorder()
        return when {
            error != null -> Result.failure(IllegalStateException(error))
            samples == null || samples.isEmpty() -> Result.failure(IllegalStateException("没有捕获到语音"))
            else -> Result.success(Capture(samples, SAMPLE_RATE))
        }
    }

    @Synchronized
    fun cancel() {
        recording.set(false)
        runCatching { recorder?.stop() }
        worker?.let { if (it !== Thread.currentThread()) runCatching { it.join(STOP_JOIN_MS) } }
        captured = null
        failure = null
        cleanupRecorder()
    }

    private fun captureLoop(audioRecord: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val maxSamples = SAMPLE_RATE * MAX_SECONDS
        val sink = ShortArray(maxSamples)
        val chunk = ShortArray(READ_CHUNK_SAMPLES)
        var count = 0
        try {
            audioRecord.startRecording()
            while (recording.get() && count < maxSamples) {
                val read = audioRecord.read(chunk, 0, minOf(chunk.size, maxSamples - count), AudioRecord.READ_BLOCKING)
                if (read > 0) {
                    System.arraycopy(chunk, 0, sink, count, read)
                    count += read
                } else if (read < 0) {
                    failure = "录音读取失败：$read"
                    break
                }
            }
            captured = sink.copyOf(count)
            // When MAX_SECONDS is reached, retain recording=true until the user taps
            // Stop so the full in-memory capture can still flow into ASR.
        } catch (t: Throwable) {
            failure = "录音失败：${t.message ?: t.javaClass.simpleName}"
        } finally {
            runCatching { audioRecord.stop() }
        }
    }

    private fun cleanupRecorder() {
        runCatching { recorder?.release() }
        recorder = null
        worker = null
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_SECONDS = 60
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val READ_CHUNK_SAMPLES = 2048
        private const val READ_CHUNK_BYTES = READ_CHUNK_SAMPLES * 2
        private const val STOP_JOIN_MS = 1800L
    }
}
