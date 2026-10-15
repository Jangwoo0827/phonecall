package com.example.superdialer

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.superdialer.kakao.KakaoLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Sharing to KakaoTalk: the intent goes to its package only, and a phone without KakaoTalk is handled. */
@RunWith(AndroidJUnit4::class)
class KakaoShareTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun theShareIntentIsAddressedToKakaoTalkOnly() {
        val intent = KakaoLinks.shareIntent("제목\nhttps://example.com")
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals("제목\nhttps://example.com", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("com.kakao.talk", intent.`package`)
    }

    @Test
    fun withoutKakaoTalkInstalledSharingSaysNoInsteadOfCrashing() {
        // the emulator has no KakaoTalk (the app shows its message as a toast, which needs the main thread)
        var shared = true
        InstrumentationRegistry.getInstrumentation().runOnMainSync { shared = KakaoLinks.share(context, "https://example.com") }
        assertFalse(shared)
    }
}
