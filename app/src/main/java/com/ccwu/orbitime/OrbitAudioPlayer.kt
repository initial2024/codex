package com.ccwu.orbitime

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/** Local-only mono PCM-float playback for TTS/voice-clone previews. */
object OrbitAudioPlayer {
    @Volatile private var activeTrack: AudioTrack? = null

    @Synchronized
    fun stop() {
        activeTrack?.let { track ->
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.stop() }
            runCatching { track.release() }
        }
        activeTrack = null
    }

    fun play(audio: OrbitAudioResult): Result<Unit> = runCatching {
        require(audio.sampleRate in 8_000..96_000) { "invalid sample rate ${audio.sampleRate}" }
        require(audio.samples.isNotEmpty()) { "empty audio" }
        stop()
        val minBytes = AudioTrack.getMinBufferSize(
            audio.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        require(minBytes > 0) { "AudioTrack buffer configuration is unsupported" }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(audio.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBytes, 32 * 1024))
            .build()
        require(track.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack initialization failed" }
        synchronized(this) { activeTrack = track }
        try {
            track.play()
            var offset = 0
            while (offset < audio.samples.size) {
                val count = minOf(8192, audio.samples.size - offset)
                val written = track.write(audio.samples, offset, count, AudioTrack.WRITE_BLOCKING)
                require(written >= 0) { "AudioTrack write failed: $written" }
                offset += written
            }
            while (track.playState == AudioTrack.PLAYSTATE_PLAYING && track.playbackHeadPosition < audio.samples.size) {
                Thread.sleep(20)
            }
        } finally {
            synchronized(this) {
                if (activeTrack === track) activeTrack = null
            }
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }
}
