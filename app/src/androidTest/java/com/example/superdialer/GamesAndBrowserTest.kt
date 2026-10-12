package com.example.superdialer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** The web/games tab: the default start-page tiles and the game catalog from assets. */
@RunWith(AndroidJUnit4::class)
class GamesAndBrowserTest : AppTestBase() {

    @Test
    fun startPageShowsTheDefaultTiles() {
        tab("웹·게임")
        waitFor(hasText("네이버"))
        listOf("체크리스트", "네이버", "구글", "유튜브").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun gameListShowsEveryBundledGame() {
        tab("웹·게임")
        waitForText("주소 또는 검색어")
        // the "브라우저 / 게임" switch at the top of the tab
        compose.onNodeWithText("게임").performClick()
        waitFor(hasText("2048"))
        listOf("2048", "스네이크", "벽돌깨기", "Crossy Chicks").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }
}
