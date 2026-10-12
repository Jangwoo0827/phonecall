package com.example.superdialer

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.superdialer.browser.data.BrowserDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Adding a start-page link (saved in the real database) and opening/closing a bundled game. */
@RunWith(AndroidJUnit4::class)
class BrowserAndGamesFlowTest : AppTestBase() {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val linkTitle = "테스트링크ZZ"

    @After
    fun removeLink() = runBlocking {
        val dao = BrowserDatabase.get(context).speedDialDao()
        dao.observeAll().first().filter { it.title == linkTitle }.forEach { dao.deleteById(it.id) }
    }

    @Test
    fun addingAStartPageLinkShowsItsTile() {
        tab("웹·게임")
        waitFor(hasText("네이버"))
        compose.onNodeWithContentDescription("추가").performClick()
        waitFor(hasText("링크 추가"))

        compose.onNode(hasSetTextAction().and(hasText("이름"))).performTextInput(linkTitle)
        compose.onNode(hasSetTextAction().and(hasText("주소"))).performTextInput("example.com")
        compose.onNodeWithText("저장").performClick()

        waitFor(hasText(linkTitle))
        compose.onNodeWithText(linkTitle).assertIsDisplayed()
    }

    @Test
    fun aWrongAddressIsRejectedBeforeSaving() {
        tab("웹·게임")
        waitFor(hasText("네이버"))
        compose.onNodeWithContentDescription("추가").performClick()
        waitFor(hasText("링크 추가"))

        compose.onNode(hasSetTextAction().and(hasText("이름"))).performTextInput(linkTitle)
        compose.onNode(hasSetTextAction().and(hasText("주소"))).performTextInput("javascript:alert(1)")
        waitFor(hasText("올바른 주소를 입력하세요"))
    }

    @Test
    fun aGameOpensAndBackReturnsToTheList() {
        tab("웹·게임")
        waitForText("주소 또는 검색어")
        compose.onNodeWithText("게임").performClick()
        waitFor(hasText("스네이크"))

        compose.onNodeWithText("2048").performClick()
        // the list is gone while a game is on screen
        waitGone(hasText("스네이크"))

        pressBack()
        waitFor(hasText("스네이크"))
        compose.onNodeWithText("Crossy Chicks").assertIsDisplayed()
    }
}
