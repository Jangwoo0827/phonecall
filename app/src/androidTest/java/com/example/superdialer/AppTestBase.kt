package com.example.superdialer

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertTextEquals
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import com.example.superdialer.settings.AppSettings
import org.junit.After
import org.junit.Before
import org.junit.Rule

/**
 * Starts [MainActivity] the way a user who has finished the first-run steps sees it: permissions granted (no system
 * dialogs), onboarding done, and no network update check popping a dialog over the screen.
 */
abstract class AppTestBase {
    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    /** Tests that start the app with their own intent (e.g. a launcher shortcut) turn this off and call [launch]. */
    protected open val autoLaunch = true

    @Before
    fun launchApp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppSettings.markOnboardingDone()
        // "checked just now": the startup update check (an internet call that can open a dialog) is skipped.
        context.getSharedPreferences("update_checker", Context.MODE_PRIVATE).edit()
            .putLong("last_check", System.currentTimeMillis()).commit()
        resetBrowserAndGameState(context)
        if (autoLaunch) launch()
    }

    /**
     * The app keeps the browser/game session alive for a while after it is left (and saves it for the next start), so
     * what one test leaves open (the games list, a game) would show up in the next one. Every test starts clean.
     */
    private fun resetBrowserAndGameState(context: Context) {
        context.getSharedPreferences("browser_session", Context.MODE_PRIVATE).edit().clear().commit()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            com.example.superdialer.browser.BrowserSession.peek()?.hubSection = 0
            com.example.superdialer.games.GameSession.close()
        }
    }

    protected fun launch(intent: Intent = Intent(ApplicationProvider.getApplicationContext<Context>(), MainActivity::class.java)) {
        scenario?.close()
        scenario = ActivityScenario.launch(intent)
        waitFor(tabMatcher("키패드"))
    }

    @After
    fun closeApp() {
        scenario?.close()
    }

    /** The bottom bar item (the same word also appears as a screen title or row text elsewhere). */
    protected fun tabMatcher(label: String): SemanticsMatcher =
        hasText(label).and(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    /**
     * The back button, as the activity sees it. (Not the accessibility "back" action: that one stopped working once
     * another test had used the shell through UiAutomation in the same run, and tests failed only in the full suite.)
     */
    protected fun pressBack() {
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    protected fun tab(label: String) {
        waitFor(tabMatcher(label))
        compose.onNode(tabMatcher(label)).performClick()
        compose.waitForIdle()
    }

    /** Waits until something matches; while the activity is being recreated there is no compose tree for a moment. */
    protected fun waitFor(matcher: SemanticsMatcher, timeoutMs: Long = 15_000) {
        try {
            compose.waitUntil(timeoutMs) {
                try {
                    compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
                } catch (e: IllegalStateException) {
                    false
                }
            }
        } catch (e: ComposeTimeoutException) {
            // What was on screen instead (adb logcat -s UiTestTree)
            runCatching { compose.onRoot().printToLog("UiTestTree") }
            throw e
        }
    }

    /** Waits until nothing matches any more (e.g. the screen that was just closed has left the composition). */
    protected fun waitGone(matcher: SemanticsMatcher, timeoutMs: Long = 15_000) {
        compose.waitUntil(timeoutMs) {
            try {
                compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()
            } catch (e: IllegalStateException) {
                false
            }
        }
    }

    protected fun waitForText(text: String, timeoutMs: Long = 15_000) = waitFor(hasText(text), timeoutMs)

    protected fun key(char: Char) = compose.onNodeWithTag("key_$char")

    protected fun display() = compose.onNodeWithTag("number_display")

    protected fun typeDigits(digits: String) = digits.forEach { key(it).performClick() }

    protected fun assertNumberShown(text: String) = display().assertTextEquals(text)

    protected fun hasTag(tag: String) = hasTestTag(tag)
}
