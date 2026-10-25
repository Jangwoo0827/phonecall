package com.example.superdialer.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {
    private val sample = """
        [
          {"tag_name":"v0.9.0","draft":false,"prerelease":false,"published_at":"2026-10-24T08:15:00Z",
           "body":"## 변경 내용\n- Contact memos and call statistics\n- Release waits for UI tests\n\n## 설치\nAPK를 받아 열면 설치됩니다.\n\n**Full Changelog**: https://github.com/x/y/compare/v0.8.0...v0.9.0"},
          {"tag_name":"v0.9.0-rc1","draft":false,"prerelease":true,"published_at":"2026-10-23T00:00:00Z","body":"- test build"},
          {"tag_name":"v0.8.0","draft":true,"prerelease":false,"published_at":"2026-10-20T00:00:00Z","body":"- draft"},
          {"tag_name":"v0.7.2","draft":false,"prerelease":false,"published_at":"","body":""},
          {"nope":1},
          5
        ]
    """.trimIndent()

    @Test fun keepsOnlyRealReleasesNewestFirstWithTheirChangeLines() {
        val list = ReleaseNotes.parse(sample)
        assertEquals(listOf("0.9.0", "0.7.2"), list.map { it.version })
        assertEquals("2026.10.24", list[0].date)
        assertEquals(listOf("Contact memos and call statistics", "Release waits for UI tests"), list[0].lines)
    }

    @Test fun aReleaseWithoutNotesOrDateIsStillListed() {
        val entry = ReleaseNotes.parse(sample).last()
        assertEquals("", entry.date)
        assertEquals(emptyList<String>(), entry.lines)
    }

    @Test fun brokenInputGivesAnEmptyList() {
        assertEquals(emptyList<ReleaseEntry>(), ReleaseNotes.parse("not json"))
        assertEquals(emptyList<ReleaseEntry>(), ReleaseNotes.parse(null))
        assertEquals(emptyList<ReleaseEntry>(), ReleaseNotes.parse("{}"))
    }

    @Test fun datesAreShownWithDots() {
        assertEquals("2026.01.05", ReleaseNotes.formatDate("2026-01-05T23:59:59Z"))
        assertEquals("", ReleaseNotes.formatDate("yesterday"))
        assertEquals("", ReleaseNotes.formatDate(""))
    }
}
