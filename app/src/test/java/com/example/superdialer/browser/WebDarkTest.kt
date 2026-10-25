package com.example.superdialer.browser

import com.example.superdialer.settings.ThemeMode
import com.example.superdialer.settings.WebDarkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDarkTest {
    @Test fun offAndOnIgnoreTheAppTheme() {
        ThemeMode.entries.forEach { theme ->
            listOf(true, false).forEach { system ->
                assertFalse(WebDark.wanted(WebDarkMode.OFF, theme, system))
                assertTrue(WebDark.wanted(WebDarkMode.ON, theme, system))
            }
        }
    }

    @Test fun followAppUsesTheAppThemeAndThePhoneOnlyWhenTheAppFollowsIt() {
        assertTrue(WebDark.wanted(WebDarkMode.FOLLOW_APP, ThemeMode.DARK, systemDark = false))
        assertFalse(WebDark.wanted(WebDarkMode.FOLLOW_APP, ThemeMode.LIGHT, systemDark = true))
        assertTrue(WebDark.wanted(WebDarkMode.FOLLOW_APP, ThemeMode.SYSTEM, systemDark = true))
        assertFalse(WebDark.wanted(WebDarkMode.FOLLOW_APP, ThemeMode.SYSTEM, systemDark = false))
    }

    @Test fun keysRoundTripAndUnknownMeansOff() {
        WebDarkMode.entries.forEach { assertEquals(it, WebDarkMode.fromKey(it.key)) }
        assertEquals(WebDarkMode.OFF, WebDarkMode.fromKey("???"))
        assertEquals(WebDarkMode.OFF, WebDarkMode.fromKey(null))
    }
}
