package com.example.superdialer.dialer

import android.media.AudioManager
import android.media.ToneGenerator

/** Plays DTMF tones while a key is held. Flip [enabled] (e.g. from a future settings screen) to mute. */
class DtmfPlayer(var enabled: Boolean = DialerDefaults.DTMF_ENABLED) {

    private val generator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_DTMF, VOLUME)
    } catch (e: RuntimeException) {
        null
    }

    fun start(key: Char) {
        if (!enabled) return
        generator?.startTone(toneFor(key) ?: return)
    }

    fun stop() {
        generator?.stopTone()
    }

    fun release() {
        generator?.release()
    }

    private fun toneFor(key: Char): Int? = when (key) {
        '0' -> ToneGenerator.TONE_DTMF_0
        '1' -> ToneGenerator.TONE_DTMF_1
        '2' -> ToneGenerator.TONE_DTMF_2
        '3' -> ToneGenerator.TONE_DTMF_3
        '4' -> ToneGenerator.TONE_DTMF_4
        '5' -> ToneGenerator.TONE_DTMF_5
        '6' -> ToneGenerator.TONE_DTMF_6
        '7' -> ToneGenerator.TONE_DTMF_7
        '8' -> ToneGenerator.TONE_DTMF_8
        '9' -> ToneGenerator.TONE_DTMF_9
        '*' -> ToneGenerator.TONE_DTMF_S
        '#' -> ToneGenerator.TONE_DTMF_P
        else -> null
    }

    private companion object {
        const val VOLUME = 80
    }
}

object DialerDefaults {
    /** Default for the keypad tone; replace with a persisted setting later. */
    const val DTMF_ENABLED = true
}
