package com.example.superdialer.incall

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import android.telecom.VideoProfile

/**
 * Helpers around Telecom's video state bit field ([VideoProfile]): TX = we send our camera, RX = we receive theirs,
 * BIDIRECTIONAL = both, PAUSED = the camera is paused. Plain integer maths, so it is unit tested.
 */
object VideoStates {
    const val AUDIO_ONLY = VideoProfile.STATE_AUDIO_ONLY
    const val TX = VideoProfile.STATE_TX_ENABLED
    const val RX = VideoProfile.STATE_RX_ENABLED
    const val BIDIRECTIONAL = VideoProfile.STATE_BIDIRECTIONAL
    const val PAUSED = VideoProfile.STATE_PAUSED

    /** The call shows video in at least one direction. */
    fun hasVideo(state: Int): Boolean = state and BIDIRECTIONAL != 0

    /** Our camera is being sent to the other side. */
    fun sendsVideo(state: Int): Boolean = state and TX != 0 && state and PAUSED == 0

    fun receivesVideo(state: Int): Boolean = state and RX != 0

    /** What to ask for when the user switches the camera on or off during a video call (the other side stays visible). */
    fun cameraTarget(on: Boolean): Int = if (on) BIDIRECTIONAL else RX

    /** Short text for the call screen. */
    fun label(state: Int): String = when {
        !hasVideo(state) -> str(R.string.t_voice_call)
        state and BIDIRECTIONAL == BIDIRECTIONAL -> str(R.string.t_video_call)
        state and RX != 0 -> str(R.string.t_watching_their_video)
        else -> str(R.string.t_sending_your_video)
    }
}
