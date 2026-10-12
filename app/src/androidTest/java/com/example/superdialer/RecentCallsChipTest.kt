package com.example.superdialer

import android.content.ContentValues
import android.content.Context
import android.provider.CallLog
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** With nothing typed the keypad offers the most recent callers; tapping one fills in the number. */
@RunWith(AndroidJUnit4::class)
class RecentCallsChipTest : AppTestBase() {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resolver get() = context.contentResolver

    // A number nobody has: easy to find and to clean up again.
    private val number = "01077770001"
    private val shown = "010-7777-0001"

    @After
    fun removeTestCall() {
        resolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls.NUMBER} = ?", arrayOf(number))
    }

    @Test
    fun recentCallerAppearsAsAChipAndFillsTheNumber() {
        resolver.insert(CallLog.Calls.CONTENT_URI, ContentValues().apply {
            put(CallLog.Calls.NUMBER, number)
            put(CallLog.Calls.TYPE, CallLog.Calls.OUTGOING_TYPE)
            put(CallLog.Calls.DATE, System.currentTimeMillis())
            put(CallLog.Calls.DURATION, 5)
        })

        // The keypad reloads its sources when it comes back on screen.
        tab("설정")
        tab("키패드")
        waitFor(hasText(shown))
        compose.onNodeWithText(shown).assertIsDisplayed()

        compose.onNodeWithText(shown).performClick()
        compose.waitForIdle()
        assertNumberShown(shown)
    }
}
