package com.example.superdialer.incall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoStatesTest {
    @org.junit.Before
    fun koreanTexts() = com.example.superdialer.i18n.TestStrings.install()

    @Test fun voiceCallsHaveNoVideo() {
        assertFalse(VideoStates.hasVideo(VideoStates.AUDIO_ONLY))
        assertFalse(VideoStates.hasVideo(VideoStates.PAUSED)) // a paused flag alone is not video
        assertEquals("음성통화", VideoStates.label(VideoStates.AUDIO_ONLY))
    }

    @Test fun directionsAreToldApart() {
        assertTrue(VideoStates.hasVideo(VideoStates.BIDIRECTIONAL))
        assertTrue(VideoStates.sendsVideo(VideoStates.BIDIRECTIONAL))
        assertTrue(VideoStates.receivesVideo(VideoStates.BIDIRECTIONAL))
        assertFalse(VideoStates.sendsVideo(VideoStates.RX))
        assertTrue(VideoStates.receivesVideo(VideoStates.RX))
        assertTrue(VideoStates.sendsVideo(VideoStates.TX))
        assertFalse(VideoStates.receivesVideo(VideoStates.TX))
    }

    @Test fun aPausedCameraIsNotSending() {
        assertFalse(VideoStates.sendsVideo(VideoStates.BIDIRECTIONAL or VideoStates.PAUSED))
    }

    @Test fun switchingTheCameraOffKeepsTheOtherSideVisible() {
        assertEquals(VideoStates.BIDIRECTIONAL, VideoStates.cameraTarget(true))
        assertEquals(VideoStates.RX, VideoStates.cameraTarget(false))
    }

    @Test fun labelsNameWhatIsGoingOn() {
        assertEquals("영상통화", VideoStates.label(VideoStates.BIDIRECTIONAL))
        assertEquals("상대 영상 보는 중", VideoStates.label(VideoStates.RX))
        assertEquals("내 영상 보내는 중", VideoStates.label(VideoStates.TX))
    }
}
