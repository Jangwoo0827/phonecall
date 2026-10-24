package com.example.superdialer.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FindAndDesktopTest {
    private val phone =
        "Mozilla/5.0 (Linux; Android 14; SM-S911N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36"

    @Test
    fun desktopAgentKeepsTheWebViewChromeVersionAndDropsAndroidAndMobile() {
        val ua = UserAgents.desktop(phone)
        assertTrue(ua.contains("Chrome/126.0.6478.122"))
        assertFalse(ua.contains("Android"))
        assertFalse(ua.contains("Mobile"))
        assertTrue(ua.startsWith("Mozilla/5.0 (X11; Linux x86_64)"))
    }

    @Test
    fun desktopAgentFallsBackWhenTheVersionIsMissing() {
        assertTrue(UserAgents.desktop("weird agent").contains("Chrome/120.0.0.0"))
    }

    @Test
    fun findLabelShowsPositionNoResultOrNothing() {
        assertEquals("", findLabel("", 0, 0))
        assertEquals("", findLabel("  ", 1, 3))
        assertEquals("결과 없음", findLabel("abc", 0, 0))
        assertEquals("2/7", findLabel("abc", 2, 7))
        assertEquals("1/7", findLabel("abc", 0, 7))
    }
}
