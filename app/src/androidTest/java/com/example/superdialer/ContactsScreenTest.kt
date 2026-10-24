package com.example.superdialer

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The contacts tab with a contact the test creates itself: list, search (name and initials), detail and back. */
@RunWith(AndroidJUnit4::class)
class ContactsScreenTest : AppTestBase() {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "테스트연락처"
    private val number = "01070002001"
    private val shown = "010-7000-2001"

    @Before
    fun createContact() {
        TestData.removeContact(context, name)
        TestData.addContact(context, name, number)
        tab("연락처")
        waitFor(hasText(name))
    }

    @After
    fun removeContact() {
        TestData.removeContact(context, name)
        com.example.superdialer.contacts.ContactMemos.setFor(listOf(number), "")
    }

    @Test
    fun listsTheContact() {
        compose.onNodeWithText(name).assertIsDisplayed()
    }

    @Test
    fun searchFindsItByNameByInitialsAndByNumber() {
        val search = compose.onNodeWithText("이름, 초성, 번호 검색")
        search.performTextInput("테스트")
        compose.waitForIdle()
        compose.onNodeWithText(name).assertIsDisplayed()
    }

    @Test
    fun searchByInitialConsonantsMatches() {
        compose.onNodeWithText("이름, 초성, 번호 검색").performTextInput("ㅌㅅㅌ")
        compose.waitForIdle()
        compose.onNodeWithText(name).assertIsDisplayed()
    }

    @Test
    fun searchThatMatchesNothingHidesIt() {
        compose.onNodeWithText("이름, 초성, 번호 검색").performTextInput("zzzzqqqq")
        compose.waitForIdle()
        compose.onAllNodesWithText(name).assertCountEquals(0)
    }

    @Test
    fun detailShowsTheNumberAndBackReturnsToTheList() {
        compose.onNodeWithText(name).performClick()
        waitFor(hasText(shown))
        compose.onNodeWithText("메시지").assertIsDisplayed()

        pressBack()
        waitFor(hasText("이름, 초성, 번호 검색"))
        compose.onNodeWithText(name).assertIsDisplayed()
    }

    @Test
    fun aMemoCanBeAddedToTheContactAndShowsOnItsDetail() {
        compose.onNodeWithText(name).performClick()
        waitFor(hasText(shown))
        compose.onNodeWithText("메모 추가").performClick()
        compose.onNodeWithText("메모").performTextInput("저녁 약속 잡기")
        compose.onNodeWithText("저장").performClick()
        waitFor(hasText("저녁 약속 잡기"))
        compose.onNodeWithText("눌러서 수정").assertIsDisplayed()
    }
}
