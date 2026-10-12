package com.example.superdialer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** The app starts, every bottom tab opens its screen, and the keypad takes input. Runs on an emulator or phone. */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest : AppTestBase() {

    @Test
    fun startsOnTheKeypadWithAllFiveTabs() {
        listOf("키패드", "최근기록", "연락처", "웹·게임", "설정").forEach {
            compose.onNode(tabMatcher(it)).assertIsDisplayed()
        }
        "1234567890*#".forEach { key(it).assertIsDisplayed() }
        compose.onNodeWithContentDescription("통화").assertIsDisplayed()
    }

    @Test
    fun keypadFormatsTheTypedNumberAndBackspaceEditsIt() {
        typeDigits("01099990000")
        assertNumberShown("010-9999-0000")

        compose.onNodeWithContentDescription("지우기").performClick()
        assertNumberShown("010-9999-000")

        // long press on backspace clears everything
        compose.onNodeWithContentDescription("지우기").performTouchInput { longClick() }
        compose.waitForIdle()
        assertNumberShown("")
    }

    @Test
    fun zeroLongPressTypesPlus() {
        key('0').performTouchInput { longClick() }
        compose.waitForIdle()
        assertNumberShown("+")
    }

    @Test
    fun everyTabOpensItsScreen() {
        tab("최근기록")
        waitForText("부재중") // filter chip, only on the call log

        tab("연락처")
        waitForText("이름, 초성, 번호 검색")

        tab("웹·게임")
        waitForText("주소 또는 검색어")

        tab("설정")
        waitFor(hasText("계정"))
        compose.onAllNodes(hasText("계정"))[0].assertIsDisplayed()

        tab("키패드")
        waitFor(hasTag("number_display"))
        compose.onNodeWithContentDescription("통화").assertIsDisplayed()
    }

    @Test
    fun settingsTabCanBeReopenedAfterLeavingIt() {
        tab("설정")
        waitFor(hasText("계정"))
        tab("연락처")
        waitForText("이름, 초성, 번호 검색")
        tab("설정")
        waitFor(hasText("계정"))
    }
}
