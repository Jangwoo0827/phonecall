package com.example.superdialer.update

import com.example.superdialer.browser.matchesQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {
    @Test fun showsOnlyAfterAnUpdate() {
        assertFalse(WhatsNew.shouldShow(null, "0.5.4")) // fresh install
        assertFalse(WhatsNew.shouldShow("0.5.4", "0.5.4")) // same version
        assertTrue(WhatsNew.shouldShow("0.5.3", "0.5.4"))
        assertFalse(WhatsNew.shouldShow("0.5.3", ""))
    }

    @Test fun keepsOnlyTheChangeLines() {
        val body = """
            ## 변경 내용
            - Fix crash on fresh install
            - Add search to bookmarks

            ## 설치
            APK를 받아 열면 설치됩니다.

            ## What's Changed
            - by @someone in https://github.com/x/y/pull/1

            **Full Changelog**: https://github.com/Jangwoo0827/phonecall/compare/v0.5.2...v0.5.3
        """.trimIndent()
        assertEquals(listOf("Fix crash on fresh install", "Add search to bookmarks"), WhatsNew.cleanNotes(body))
        assertEquals(emptyList<String>(), WhatsNew.cleanNotes(""))
        // a release body without the section header falls back to its bullet lines
        assertEquals(listOf("one"), WhatsNew.cleanNotes("- one\ntext"))
    }

    @Test fun searchMatchesTitleOrAddressIgnoringCase() {
        assertTrue(matchesQuery("Naver", "https://m.naver.com", ""))
        assertTrue(matchesQuery("Naver", "https://m.naver.com", "nav"))
        assertTrue(matchesQuery("Home", "https://example.com/Docs", "docs"))
        assertFalse(matchesQuery("Naver", "https://m.naver.com", "google"))
        assertTrue(matchesQuery("네이버", "https://m.naver.com", " 네이 "))
    }
}
