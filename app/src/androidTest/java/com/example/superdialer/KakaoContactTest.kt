package com.example.superdialer

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A contact that has KakaoTalk entries in the phone's contacts shows the 채팅 / 보이스톡 / 페이스톡 buttons; one without
 * shows none. (The entries here are made up with the media types KakaoTalk is expected to use; the app also reads the
 * label, so another naming still works.)
 */
@RunWith(AndroidJUnit4::class)
class KakaoContactTest : AppTestBase() {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val friend = "카톡친구테스트"
    private val stranger = "카톡없는테스트"

    @Before
    fun createContacts() {
        TestData.removeContact(context, friend)
        TestData.removeContact(context, stranger)
        TestData.addContact(
            context, friend, "01070003001",
            listOf(
                TestData.ExtraRow("vnd.android.cursor.item/vnd.com.kakao.talk.chat", "1", "카카오톡 채팅"),
                TestData.ExtraRow("vnd.android.cursor.item/vnd.com.kakao.talk.voice", "1", "보이스톡"),
                TestData.ExtraRow("vnd.android.cursor.item/vnd.com.kakao.talk.video", "1", "페이스톡"),
            ),
        )
        TestData.addContact(context, stranger, "01070003002")
        tab("연락처")
        waitFor(hasText(friend))
    }

    @After
    fun removeContacts() {
        TestData.removeContact(context, friend)
        TestData.removeContact(context, stranger)
    }

    @Test
    fun aKakaoFriendShowsTheThreeButtons() {
        compose.onNodeWithText(friend).performClick()
        waitFor(hasText("카카오톡 채팅"))
        compose.onNodeWithText("보이스톡").assertIsDisplayed()
        compose.onNodeWithText("페이스톡").assertIsDisplayed()
    }

    @Test
    fun tappingAButtonWithoutKakaoTalkInstalledDoesNotCrash() {
        compose.onNodeWithText(friend).performClick()
        waitFor(hasText("보이스톡"))
        compose.onNodeWithText("보이스톡").performClick()
        compose.waitForIdle()
        // still here (the emulator has no KakaoTalk: the app says so with a toast instead of failing)
        waitFor(hasText(friend).or(hasText("보이스톡")))
    }

    @Test
    fun aContactWithoutKakaoShowsNoButtons() {
        compose.onNodeWithText(stranger).performClick()
        waitFor(hasText("010-7000-3002"))
        compose.onAllNodesWithText("카카오톡 채팅").assertCountEquals(0)
    }
}
