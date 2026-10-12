package com.example.superdialer

import android.Manifest
import android.content.Context
import androidx.compose.ui.semantics.Role
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

    @Before
    fun launchApp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppSettings.markOnboardingDone()
        // "checked just now": the startup update check (an internet call that can open a dialog) is skipped.
        context.getSharedPreferences("update_checker", Context.MODE_PRIVATE).edit()
            .putLong("last_check", System.currentTimeMillis()).commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(tabMatcher("키패드"))
    }

    @After
    fun closeApp() {
        scenario?.close()
    }

    /** The bottom bar item (the same word also appears as a screen title or row text elsewhere). */
    protected fun tabMatcher(label: String): SemanticsMatcher =
        hasText(label).and(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    protected fun tab(label: String) {
        waitFor(tabMatcher(label))
        compose.onNode(tabMatcher(label)).performClick()
        compose.waitForIdle()
    }

    /** Waits until something matches; while the activity is being recreated there is no compose tree for a moment. */
    protected fun waitFor(matcher: SemanticsMatcher, timeoutMs: Long = 15_000) {
        compose.waitUntil(timeoutMs) {
            try {
                compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
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
