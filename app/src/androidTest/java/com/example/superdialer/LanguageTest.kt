package com.example.superdialer

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.superdialer.i18n.AppLanguage
import com.example.superdialer.i18n.Strings
import com.example.superdialer.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** The language setting and the two sets of texts (the UI tests themselves run in Korean, see SuperDialerTestRunner). */
@RunWith(AndroidJUnit4::class)
class LanguageTest : AppTestBase() {
    @Test
    fun theTestsRunInKorean() {
        assertEquals(AppLanguage.KOREAN, AppSettings.language)
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("취소", Strings.get(context.resources.getIdentifier("cancel", "string", context.packageName)))
    }

    @Test
    fun theSettingsOfferThePhoneKoreanAndEnglish() {
        tab("설정")
        waitFor(hasText("계정"))
        AppLanguage.entries.forEach { compose.onNodeWithTag("language_${it.key}").performScrollTo().assertIsDisplayed() }
    }

    @Test
    fun everyTextExistsInBothLanguages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun inLocale(tag: String): Context {
            val config = Configuration(context.resources.configuration)
            config.setLocale(Locale.forLanguageTag(tag))
            return context.createConfigurationContext(config)
        }
        // by name: the ids of the test app's own R are not the ids of the app under test
        fun text(ctx: Context, name: String, vararg args: Any): String {
            val id = ctx.resources.getIdentifier(name, "string", context.packageName)
            assertNotEquals("no string named $name", 0, id)
            return ctx.getString(id, *args)
        }
        val english = inLocale("en")
        val korean = inLocale("ko")
        assertEquals("Cancel", text(english, "cancel"))
        assertEquals("취소", text(korean, "cancel"))
        assertNotEquals(text(english, "t_video_call"), text(korean, "t_video_call"))
        assertEquals("7 selected", text(english, "t_selected", 7))
        assertEquals("7개 선택", text(korean, "t_selected", 7))
        assertEquals("Recents", text(english, "shortcut_calllog"))
    }


}
