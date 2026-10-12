package com.example.superdialer

import android.content.Context
import android.provider.CallLog
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

/** The call log tab with calls the test inserts itself (incoming, missed, outgoing, and a repeated number). */
@RunWith(AndroidJUnit4::class)
class CallLogScreenTest : AppTestBase() {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val incoming = "010-7000-1001"
    private val missed = "010-7000-1002"
    private val outgoing = "010-7000-1003"
    private val repeated = "010-7000-1004"

    @Before
    fun insertCalls() {
        val now = System.currentTimeMillis()
        TestData.addCall(context, "01070001001", CallLog.Calls.INCOMING_TYPE, now - 1_000)
        TestData.addCall(context, "01070001002", CallLog.Calls.MISSED_TYPE, now - 2_000, seconds = 0)
        TestData.addCall(context, "01070001003", CallLog.Calls.OUTGOING_TYPE, now - 3_000)
        // two calls with one number right after each other collapse into a single row marked "(2)"
        TestData.addCall(context, "01070001004", CallLog.Calls.OUTGOING_TYPE, now - 4_000)
        TestData.addCall(context, "01070001004", CallLog.Calls.OUTGOING_TYPE, now - 5_000)
        tab("설정")
        tab("최근기록")
        waitFor(hasText(incoming))
    }

    @After
    fun removeCalls() {
        TestData.removeTestCalls(context)
    }

    @Test
    fun showsEveryInsertedCall() {
        listOf(incoming, missed, outgoing).forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun repeatedNumberIsGroupedWithItsCount() {
        waitFor(hasText("$repeated (2)"))
    }

    @Test
    fun missedFilterKeepsOnlyMissedCalls() {
        // the filter chips sit above the list, so the first "부재중" is the chip (rows also say it in their detail line)
        compose.onAllNodesWithText("부재중")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText(missed).assertIsDisplayed()
        compose.onAllNodesWithText(incoming).assertCountEquals(0)
        compose.onAllNodesWithText(outgoing).assertCountEquals(0)

        compose.onAllNodesWithText("전체")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText(incoming).assertIsDisplayed()
    }

    @Test
    fun searchNarrowsTheListByNumber() {
        compose.onNodeWithText("이름, 초성, 번호 검색").performTextInput("70001003")
        compose.waitForIdle()
        compose.onNodeWithText(outgoing).assertIsDisplayed()
        compose.onAllNodesWithText(incoming).assertCountEquals(0)
        compose.onAllNodesWithText(missed).assertCountEquals(0)
    }

    @Test
    fun tappingARowExpandsItsActionsWithoutCalling() {
        compose.onNodeWithText(missed).performClick()
        compose.waitForIdle()
        listOf("기록", "연락처", "메시지", "메모").forEach { label ->
            waitFor(hasText(label))
        }
        // still on the call log: nothing was dialed, no in-call screen replaced the app
        compose.onNodeWithText(missed).assertIsDisplayed()
    }
}
