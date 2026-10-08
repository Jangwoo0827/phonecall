package com.example.superdialer.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedTabsTest {
    @Test fun roundTrip() {
        val tabs = listOf(SavedTab("https://a.com/x?y=1", "A site"), SavedTab(null, ""), SavedTab("https://b.com", "한글 제목"))
        assertEquals(tabs, SavedTabs.decode(SavedTabs.encode(tabs)))
    }

    @Test fun separatorsInTitlesCannotBreakTheFormat() {
        val decoded = SavedTabs.decode(SavedTabs.encode(listOf(SavedTab("https://a.com", "bad\u0001ti\u0002tle"), SavedTab("https://b.com", "ok"))))
        assertEquals(listOf("https://a.com", "https://b.com"), decoded.map { it.url })
        assertEquals("bad ti tle", decoded[0].title)
    }

    @Test fun emptyInputGivesNoTabs() {
        assertEquals(emptyList<SavedTab>(), SavedTabs.decode(null))
        assertEquals(emptyList<SavedTab>(), SavedTabs.decode(""))
    }
}
