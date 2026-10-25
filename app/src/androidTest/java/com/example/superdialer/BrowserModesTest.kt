package com.example.superdialer

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.superdialer.browser.SearchEngine
import com.example.superdialer.browser.SiteDecision
import com.example.superdialer.browser.SitePermission
import com.example.superdialer.browser.SitePermissions
import com.example.superdialer.settings.AppSettings
import com.example.superdialer.settings.WebDarkMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Browser: private tabs, the search engine and web dark mode settings, and remembered site permissions. */
@RunWith(AndroidJUnit4::class)
class BrowserModesTest : AppTestBase() {
    // The restart test launches the app itself, after it has prepared the phone.
    override val autoLaunch: Boolean get() = !name.startsWith("restart")

    private val name get() = testName.methodName

    @get:org.junit.Rule(order = 2)
    val testName = org.junit.rules.TestName()

    private val engineBefore = AppSettings.searchEngine
    private val darkBefore = AppSettings.webDarkMode

    @After
    fun restore() {
        AppSettings.updateSearchEngine(engineBefore)
        AppSettings.updateWebDarkMode(darkBefore)
        SitePermissions.resetAll()
    }

    @Test
    fun aPrivateTabShowsItsBannerAndTheTabListSwitchesBetweenKinds() {
        tab("웹·게임")
        waitForText("주소 또는 검색어")
        compose.onNodeWithContentDescription("더보기").performClick()
        compose.onNodeWithTag("menu_new_private_tab").performClick()
        waitFor(hasTag("private_banner"))
        compose.onNodeWithTag("private_banner").assertIsDisplayed()

        compose.onNodeWithTag("tab_count").performClick()
        waitFor(hasTag("tabs_panel"))
        // we came from the private tab, so the private side is shown first, with its own tab
        waitForText("비공개 1")
        compose.onNodeWithText("일반 1").assertIsDisplayed()
        compose.onNodeWithText("일반 1").performClick()
        waitFor(hasTag("tab_card"))

        // closing every private tab again: the private count drops to 0
        compose.onNodeWithText("비공개 1").performClick()
        waitFor(hasText("모두 닫기"))
        compose.onNodeWithText("모두 닫기").performClick()
        waitForText("비공개 0")
        waitForText("비공개 탭이 없어요")
    }

    @Test
    fun searchEngineAndWebDarkModeChoicesAreSaved() {
        tab("설정")
        waitFor(hasText("계정"))
        compose.onNodeWithTag("search_engine_naver").performScrollTo().performClick()
        assertEquals(SearchEngine.NAVER, AppSettings.searchEngine)
        compose.onNodeWithTag("search_engine_bing").performScrollTo().performClick()
        assertEquals(SearchEngine.BING, AppSettings.searchEngine)
        compose.onNodeWithTag("web_dark_on").performScrollTo().performClick()
        assertEquals(WebDarkMode.ON, AppSettings.webDarkMode)
        compose.onNodeWithTag("web_dark_follow").performScrollTo().performClick()
        assertEquals(WebDarkMode.FOLLOW_APP, AppSettings.webDarkMode)
    }

    @Test
    fun theStartPageNamesTheChosenSearchEngine() {
        AppSettings.updateSearchEngine(SearchEngine.BING)
        tab("웹·게임")
        waitForText("주소 또는 검색어")
        compose.onNodeWithText("마이크로소프트").assertIsDisplayed()
    }

    @Test
    fun siteSettingsListTheRememberedAnswersAndCanForgetThem() {
        SitePermissions.set("https://test.example", listOf(SitePermission.Camera, SitePermission.Location), SiteDecision.Allow)
        tab("설정")
        waitFor(hasText("계정"))
        compose.onNodeWithText("사이트 권한").performScrollTo().performClick()
        waitForText("test.example")
        compose.onNodeWithText("카메라").assertIsDisplayed()
        compose.onNodeWithText("위치").assertIsDisplayed()

        compose.onNodeWithContentDescription("카메라 초기화").performClick()
        compose.waitForIdle()
        assertEquals(null, SitePermissions.decision("https://test.example", SitePermission.Camera))
        assertEquals(SiteDecision.Allow, SitePermissions.decision("https://test.example", SitePermission.Location))

        compose.onNodeWithText("모두 초기화").performClick()
        compose.onNodeWithText("초기화").performClick() // the confirmation dialog's button
        waitFor(hasTag("no_site_permissions"))
    }

    @Test
    fun restartWithLeftoverPrivateDataDoesNotCrash() {
        // An earlier run died with private tabs open: the leftovers are erased at the next start. (Once this ran the
        // erase before the view model was fully built and the app crashed on launch.)
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("browser_private", Context.MODE_PRIVATE).edit().putBoolean("dirty", true).commit()
        launch()
        tab("웹·게임")
        waitForText("주소 또는 검색어")
    }
}
