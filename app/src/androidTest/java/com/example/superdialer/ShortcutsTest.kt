package com.example.superdialer

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** The launcher's long-press shortcuts start the app on the right tab (res/xml/shortcuts.xml sends `open_route`). */
@RunWith(AndroidJUnit4::class)
class ShortcutsTest : AppTestBase() {
    override val autoLaunch = false

    private fun shortcut(target: String) = Intent(ApplicationProvider.getApplicationContext<Context>(), MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .putExtra(MainActivity.EXTRA_OPEN_ROUTE, target)

    @Test
    fun callLogShortcutOpensTheCallLog() {
        launch(shortcut("calllog"))
        waitForText("부재중")
    }

    @Test
    fun contactsShortcutOpensTheContacts() {
        launch(shortcut("contacts"))
        waitForText("이름, 초성, 번호 검색")
    }

    @Test
    fun gamesShortcutOpensTheGameList() {
        launch(shortcut(MainActivity.SHORTCUT_GAMES))
        waitFor(hasText("Crossy Chicks"))
    }

    @Test
    fun browserShortcutOpensTheStartPage() {
        launch(shortcut(MainActivity.SHORTCUT_BROWSER))
        waitForText("주소 또는 검색어")
    }

    @Test
    fun anUnknownTargetJustOpensTheKeypad() {
        launch(shortcut("../../etc"))
        waitFor(hasTag("number_display"))
    }
}
