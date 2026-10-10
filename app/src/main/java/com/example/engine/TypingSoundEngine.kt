package com.example.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class TypingSoundProfile(val index: Int, val label: String, val shortLabel: String) {
    OFF(0, "Silent (Mute)", "Mute"),
    MECH_THOCK(1, "Mechanical Thock", "Thock"),
    CRISP_CLICK(2, "Crisp Tactile Click", "Click"),
    SOFT_ASMR(3, "Soft ASMR Tap", "ASMR");

    companion object {
        fun fromIndex(index: Int): TypingSoundProfile =
            entries.firstOrNull { it.index == index } ?: OFF
    }
}

/**
 * Zero-Latency Offline Synthesized Mechanical Keyboard Sound Engine for Content Creators.
 * Pre-generates tiny PCM 16-bit buffers in memory so creators get realistic ASMR key clicks
 * during Auto Write recording without needing any external audio files or video editing.
 * Properly manages and releases native AudioTrack resources to prevent native memory/AudioFlinger leaks.
 */
object TypingSoundEngine {

    private const val SAMPLE_RATE = 22050

    @Volatile
    private var lastPlayTimeMs = 0L

    private var thockNormalTrack: AudioTrack? = null
    private var thockEnterTrack: AudioTrack? = null
    private var clickNormalTrack: AudioTrack? = null
    private var asmrNormalTrack: AudioTrack? = null
    private var initialized = false

    @Synchronized
    private fun ensureInitialized() {
        if (initialized) return
        initialized = true
        try {
            thockNormalTrack = buildStaticTrack(
                synthesizeKeyWaveform(
                    durationMs = 14,
                    baseFreqHz = 185.0,
                    clickFreqHz = 1150.0,
                    noiseAmount = 0.32,
                    amplitude = 0.55
                )
            )
            thockEnterTrack = buildStaticTrack(
                synthesizeKeyWaveform(
                    durationMs = 18,
                    baseFreqHz = 135.0,
                    clickFreqHz = 820.0,
                    noiseAmount = 0.38,
                    amplitude = 0.68
                )
            )
            clickNormalTrack = buildStaticTrack(
                synthesizeKeyWaveform(
                    durationMs = 11,
                    baseFreqHz = 310.0,
                    clickFreqHz = 2400.0,
                    noiseAmount = 0.42,
                    amplitude = 0.50
                )
            )
            asmrNormalTrack = buildStaticTrack(
                synthesizeKeyWaveform(
                    durationMs = 13,
                    baseFreqHz = 145.0,
                    clickFreqHz = 680.0,
                    noiseAmount = 0.20,
                    amplitude = 0.36
                )
            )
        } catch (_: Throwable) {
            // Safe no-op in headless JVM / Robolectric environments
        }
    }

    @Synchronized
    fun playKeyTick(profile: TypingSoundProfile, typedChar: Char?) {
        if (profile == TypingSoundProfile.OFF) return
        val now = System.currentTimeMillis()
        // Prevent audio overlap during ultra-fast burst typing
        if (now - lastPlayTimeMs < 24L) return
        lastPlayTimeMs = now

        try {
            ensureInitialized()
            val isDeepKey = typedChar == '\n' || typedChar == ' ' || typedChar == '}' || typedChar == '>'
            val track = when (profile) {
                TypingSoundProfile.OFF -> null
                TypingSoundProfile.MECH_THOCK -> if (isDeepKey) thockEnterTrack else thockNormalTrack
                TypingSoundProfile.CRISP_CLICK -> if (isDeepKey) thockNormalTrack else clickNormalTrack
                TypingSoundProfile.SOFT_ASMR -> asmrNormalTrack
            } ?: return

            if (track.state != AudioTrack.STATE_INITIALIZED) return

            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
            track.reloadStaticData()
            track.play()
        } catch (_: Throwable) {
            // Ignore audio hardware exceptions on emulators without audio sink
        }
    }

    @Synchronized
    fun stopAll() {
        listOf(thockNormalTrack, thockEnterTrack, clickNormalTrack, asmrNormalTrack).forEach { track ->
            try {
                if (track != null &&
                    track.state == AudioTrack.STATE_INITIALIZED &&
                    track.playState == AudioTrack.PLAYSTATE_PLAYING
                ) {
                    track.stop()
                }
            } catch (_: Throwable) {
            }
        }
    }

    @Synchronized
    fun release() {
        stopAll()
        listOf(thockNormalTrack, thockEnterTrack, clickNormalTrack, asmrNormalTrack).forEach { track ->
            try {
                track?.release()
            } catch (_: Throwable) {
            }
        }
        thockNormalTrack = null
        thockEnterTrack = null
        clickNormalTrack = null
        asmrNormalTrack = null
        initialized = false
    }

    private fun synthesizeKeyWaveform(
        durationMs: Int,
        baseFreqHz: Double,
        clickFreqHz: Double,
        noiseAmount: Double,
        amplitude: Double
    ): ShortArray {
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)
        val rng = Random(42)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / numSamples.coerceAtLeast(1)
            // Fast attack + exponential decay envelope for realistic mechanical switch feel
            val attack = (i / 18.0).coerceAtMost(1.0)
            val bodyDecay = exp(-progress * 6.5)
            val transientDecay = exp(-progress * 22.0)

            val bodyWave = sin(2.0 * Math.PI * baseFreqHz * t) * bodyDecay
            val clickTransient = sin(2.0 * Math.PI * clickFreqHz * t) * transientDecay * 0.65
            val burstNoise = (rng.nextDouble(-1.0, 1.0)) * transientDecay * noiseAmount

            val combined = (bodyWave + clickTransient + burstNoise) * attack * amplitude
            val clamped = combined.coerceIn(-1.0, 1.0)
            samples[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private fun buildStaticTrack(pcmData: ShortArray): AudioTrack? {
        val byteSize = pcmData.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(byteSize.coerceAtLeast(512))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return null
        }
        val written = track.write(pcmData, 0, pcmData.size)
        if (written < 0) {
            track.release()
            return null
        }
        return track
    }
}
