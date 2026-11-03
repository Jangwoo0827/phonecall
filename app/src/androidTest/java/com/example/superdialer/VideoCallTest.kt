package com.example.superdialer

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The places where a video call can be started: next to the call button on the keypad and on each number of a contact. */
@RunWith(AndroidJUnit4::class)
class VideoCallTest : AppTestBase() {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "테스트영상통화"

    @After
    fun cleanUp() {
        TestData.removeContact(context, name)
    }

    @Test
    fun theKeypadHasAVideoCallButtonNextToTheCallButton() {
        compose.onNodeWithTag("video_call_button").assertIsDisplayed()
        compose.onNodeWithContentDescription("통화").assertIsDisplayed()
    }

    @Test
    fun aContactsNumberHasAVideoCallButton() {
        TestData.removeContact(context, name)
        TestData.addContact(context, name, "01070003001")
        tab("연락처")
        waitFor(hasText(name))
        compose.onNodeWithText(name).performClick()
        waitFor(hasText("010-7000-3001"))
        compose.onNodeWithContentDescription("영상통화").assertIsDisplayed()
        compose.onNodeWithContentDescription("발신").assertIsDisplayed()
    }
}
