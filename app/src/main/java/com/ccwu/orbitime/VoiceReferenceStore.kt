package com.ccwu.orbitime

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Stores one explicitly-authorized reference voice for local ZipVoice generation.
 * Only mono PCM16 WAV is accepted. The file never leaves app-private storage.
 */
class VoiceReferenceStore(private val context: Context) {
    data class Reference(
        val transcript: String,
        val samples: ShortArray,
        val sampleRate: Int,
        val durationSeconds: Float,
        val savedAt: Long,
    )

    data class SaveResult(val success: Boolean, val message: String)

    private val directory = File(context.filesDir, STORE_DIR).apply { mkdirs() }
    private val wavFile = File(directory, WAV_FILE)
    private val metaFile = File(directory, META_FILE)

    fun save(uri: Uri, transcriptRaw: String, consentConfirmed: Boolean): SaveResult {
        if (!ProGate.isProUnlocked(context)) return SaveResult(false, "需要 Pro 才能使用音色克隆参考声音")
        if (!consentConfirmed) return SaveResult(false, "必须确认这是本人声音或已获得明确授权")
        val transcript = transcriptRaw.trim()
        if (transcript.length !in 1..MAX_TRANSCRIPT_CHARS) return SaveResult(false, "参考音频对应原文不能为空或过长")

        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream(512 * 1024)
                val buffer = ByteArray(64 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_WAV_BYTES) { "参考 WAV 超过 ${MAX_WAV_BYTES / (1024 * 1024)} MB" }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            } ?: error("无法读取参考音频")
            val parsed = parsePcm16MonoWav(bytes)
            val duration = parsed.samples.size.toFloat() / parsed.sampleRate
            require(duration in MIN_SECONDS..MAX_SECONDS) { "参考声音建议 ${MIN_SECONDS.toInt()}–${MAX_SECONDS.toInt()} 秒，当前 %.1f 秒".format(duration) }

            val temp = File(directory, ".reference-${System.nanoTime()}.wav")
            temp.writeBytes(bytes)
            if (wavFile.exists()) wavFile.delete()
            require(temp.renameTo(wavFile)) { "无法保存参考 WAV" }
            metaFile.writeText(
                JSONObject()
                    .put("transcript", transcript)
                    .put("sample_rate", parsed.sampleRate)
                    .put("duration_seconds", duration.toDouble())
                    .put("saved_at", System.currentTimeMillis())
                    .put("consent_confirmed", true)
                    .toString(2),
                Charsets.UTF_8,
            )
            SaveResult(true, "参考声音已保存在本机 App 私有目录（%.1f 秒）".format(duration))
        }.getOrElse { SaveResult(false, "参考声音保存失败：${it.message ?: it.javaClass.simpleName}") }
    }

    fun load(): Reference? = runCatching {
        if (!wavFile.isFile || !metaFile.isFile) return@runCatching null
        val meta = JSONObject(metaFile.readText(Charsets.UTF_8))
        if (!meta.optBoolean("consent_confirmed", false)) return@runCatching null
        require(wavFile.length() in 1..MAX_WAV_BYTES.toLong()) { "参考 WAV 体积异常" }
        val parsed = parsePcm16MonoWav(wavFile.readBytes())
        Reference(
            transcript = meta.getString("transcript"),
            samples = parsed.samples,
            sampleRate = parsed.sampleRate,
            durationSeconds = parsed.samples.size.toFloat() / parsed.sampleRate,
            savedAt = meta.optLong("saved_at", 0L),
        )
    }.getOrNull()

    fun summary(): String = runCatching {
        if (!wavFile.isFile || !metaFile.isFile) return@runCatching "尚未保存参考声音"
        val meta = JSONObject(metaFile.readText(Charsets.UTF_8))
        if (!meta.optBoolean("consent_confirmed", false)) return@runCatching "尚未保存参考声音"
        val duration = meta.optDouble("duration_seconds", 0.0)
        val sampleRate = meta.optInt("sample_rate", 0)
        val transcriptLength = meta.optString("transcript", "").length
        "已保存参考声音 · %.1f 秒 · ${sampleRate}Hz · 对应原文 ${transcriptLength} 字".format(duration)
    }.getOrDefault("尚未保存参考声音")

    fun clear(): Boolean {
        val a = !wavFile.exists() || wavFile.delete()
        val b = !metaFile.exists() || metaFile.delete()
        return a && b
    }

    private data class ParsedWav(val samples: ShortArray, val sampleRate: Int)

    private fun parsePcm16MonoWav(bytes: ByteArray): ParsedWav {
        require(bytes.size >= 44) { "WAV 文件过短" }
        fun ascii(offset: Int, length: Int): String = bytes.copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)
        require(ascii(0, 4) == "RIFF" && ascii(8, 4) == "WAVE") { "仅支持 RIFF/WAVE" }

        var offset = 12
        var formatCode = -1
        var channels = -1
        var sampleRate = -1
        var bitsPerSample = -1
        var dataOffset = -1
        var dataSize = -1
        while (offset + 8 <= bytes.size) {
            val id = ascii(offset, 4)
            val size = littleInt(bytes, offset + 4)
            require(size >= 0 && offset + 8L + size <= bytes.size.toLong()) { "WAV chunk 损坏：$id" }
            val body = offset + 8
            when (id) {
                "fmt " -> {
                    require(size >= 16) { "WAV fmt chunk 无效" }
                    formatCode = littleShort(bytes, body).toInt() and 0xffff
                    channels = littleShort(bytes, body + 2).toInt() and 0xffff
                    sampleRate = littleInt(bytes, body + 4)
                    bitsPerSample = littleShort(bytes, body + 14).toInt() and 0xffff
                }
                "data" -> {
                    dataOffset = body
                    dataSize = size
                    break
                }
            }
            offset = body + size + (size and 1)
        }

        require(formatCode == 1) { "仅支持未压缩 PCM WAV" }
        require(channels == 1) { "仅支持单声道参考 WAV" }
        require(bitsPerSample == 16) { "仅支持 16-bit PCM WAV" }
        require(sampleRate in 8_000..96_000) { "不支持的采样率：$sampleRate" }
        require(dataOffset >= 0 && dataSize > 0 && dataSize % 2 == 0) { "WAV 没有有效 PCM data" }
        val sampleCount = dataSize / 2
        val samples = ShortArray(sampleCount)
        val buffer = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (index in 0 until sampleCount) samples[index] = buffer.short
        return ParsedWav(samples, sampleRate)
    }

    private fun littleInt(bytes: ByteArray, offset: Int): Int = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
    private fun littleShort(bytes: ByteArray, offset: Int): Short = ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short

    companion object {
        private const val STORE_DIR = "orbit-voice-reference"
        private const val WAV_FILE = "reference.wav"
        private const val META_FILE = "reference.json"
        private const val MAX_WAV_BYTES = 20 * 1024 * 1024
        private const val MAX_TRANSCRIPT_CHARS = 1200
        private const val MIN_SECONDS = 2f
        private const val MAX_SECONDS = 30f
    }
}
