package com.arnix.codepad.studio.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import com.arnix.codepad.studio.R

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
 * Authentic Mechanical Keyboard Sound Engine powered by Android's SoundPool.
 * Loads actual acoustic WAV recordings directly from res/raw:
 * - sound_thock.wav: Heavy, deep lubed mechanical switch bottom-out
 * - sound_thock_enter.wav: Deep spacebar / enter key resonance
 * - sound_click.wav: Crisp tactile clickbar / blue switch click
 * - sound_asmr.wav: Damped soft acoustic tap
 */
object TypingSoundEngine {

    @Volatile
    private var lastPlayTimeMs = 0L

    private var soundPool: SoundPool? = null
    private var soundThockId = 0
    private var soundThockEnterId = 0
    private var soundClickId = 0
    private var soundAsmrId = 0

    private var isLoaded = false
    private var appContext: Context? = null

    @Synchronized
    fun initialize(context: Context) {
        if (soundPool != null) return
        appContext = context.applicationContext

        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(audioAttributes)
                .build().apply {
                    soundThockId = load(context, R.raw.sound_thock, 1)
                    soundThockEnterId = load(context, R.raw.sound_thock_enter, 1)
                    soundClickId = load(context, R.raw.sound_click, 1)
                    soundAsmrId = load(context, R.raw.sound_asmr, 1)
                }
            isLoaded = true
        } catch (_: Throwable) {
            // Safe fallback for mock / test environments
        }
    }

    @Synchronized
    fun playKeyTick(profile: TypingSoundProfile, typedChar: Char?) {
        if (profile == TypingSoundProfile.OFF) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayTimeMs < 18L) return
        lastPlayTimeMs = now

        val pool = soundPool ?: run {
            appContext?.let { initialize(it) }
            soundPool
        } ?: return

        val isDeepKey = typedChar == '\n' || typedChar == ' ' || typedChar == '}' || typedChar == '>'

        val soundId = when (profile) {
            TypingSoundProfile.OFF -> 0
            TypingSoundProfile.MECH_THOCK -> if (isDeepKey) soundThockEnterId else soundThockId
            TypingSoundProfile.CRISP_CLICK -> soundClickId
            TypingSoundProfile.SOFT_ASMR -> soundAsmrId
        }

        if (soundId != 0) {
            try {
                // Subtle pitch variation (+- 4%) to make repetitive typing sound completely organic & human
                val randomRate = 0.97f + (Math.random().toFloat() * 0.06f)
                val volume = when (profile) {
                    TypingSoundProfile.MECH_THOCK -> if (isDeepKey) 1.0f else 0.95f
                    TypingSoundProfile.CRISP_CLICK -> 0.90f
                    TypingSoundProfile.SOFT_ASMR -> 0.85f
                    else -> 0.9f
                }
                pool.play(soundId, volume, volume, 1, 0, randomRate)
            } catch (_: Throwable) {
            }
        }
    }

    @Synchronized
    fun stopAll() {
        try {
            soundPool?.autoPause()
        } catch (_: Throwable) {
        }
    }

    @Synchronized
    fun release() {
        try {
            soundPool?.release()
        } catch (_: Throwable) {
        }
        soundPool = null
        soundThockId = 0
        soundThockEnterId = 0
        soundClickId = 0
        soundAsmrId = 0
        isLoaded = false
    }
}
