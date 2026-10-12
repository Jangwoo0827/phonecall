package com.example.superdialer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.superdialer.settings.AppSettings
import com.example.superdialer.settings.TextSize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Settings: switches and choices change the saved setting, sub-screens open and close. */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest : AppTestBase() {
    private var dtmfBefore = true
    private var textSizeBefore = TextSize.NORMAL

    @Before
    fun remember() {
        dtmfBefore = AppSettings.dtmfEnabled
        textSizeBefore = AppSettings.textSize
        tab("설정")
        waitFor(hasText("계정"))
    }

    @After
    fun restore() {
        AppSettings.updateDtmfEnabled(dtmfBefore)
        AppSettings.updateTextSize(textSizeBefore)
    }

    @Test
    fun keypadToneSwitchChangesTheSetting() {
        val before = AppSettings.dtmfEnabled
        compose.onNodeWithTag("switch_dtmf").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(!before, AppSettings.dtmfEnabled)
        compose.onNodeWithTag("switch_dtmf").performClick()
        compose.waitForIdle()
        assertEquals(before, AppSettings.dtmfEnabled)
    }

    @Test
    fun textSizeChoiceChangesTheSetting() {
        compose.onNodeWithText("크게").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(TextSize.LARGE, AppSettings.textSize)
        compose.onNodeWithText("보통").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(TextSize.NORMAL, AppSettings.textSize)
    }

    @Test
    fun blockedNumbersOpensAndGoesBack() {
        compose.onNodeWithText("차단 관리").performScrollTo().performClick()
        // either the "set as default phone app first" note or the add-number field, depending on the role
        waitFor(hasText("번호 차단은", substring = true).or(hasText("차단할 번호")))
        pressBack()
        waitGone(hasText("차단할 번호").or(hasText("번호 차단은", substring = true)))
        waitFor(hasText("계정"))
    }

    @Test
    fun rejectMessagesOpensAndRestoreDefaultsIsOffered() {
        compose.onNodeWithText("거절 메시지").performScrollTo().performClick()
        waitFor(hasText("기본 문구로 되돌리기"))
        compose.onNodeWithText("기본 문구로 되돌리기").assertIsDisplayed()
        pressBack()
        waitGone(hasText("기본 문구로 되돌리기"))
        waitFor(hasText("계정"))
    }

    @Test
    fun reTappingTheSettingsTabFromASubScreenReturnsToTheTop() {
        compose.onNodeWithText("거절 메시지").performScrollTo().performClick()
        waitFor(hasText("기본 문구로 되돌리기"))
        tab("설정")
        waitGone(hasText("기본 문구로 되돌리기"))
        waitFor(hasText("계정"))
    }
}
