package com.example.superdialer.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSnapshotTest {
    private val sample = SyncSnapshot(
        speedDials = listOf(SyncSnapshot.Link("네이버", "https://m.naver.com"), SyncSnapshot.Link("체크리스트", "https://x.github.io/a/")),
        bookmarks = listOf(SyncSnapshot.Link("위키", "https://ko.m.wikipedia.org")),
        gameScores = mapOf("2048" to 1080, "snake" to 12),
        dtmfEnabled = false,
        dynamicColor = true,
        themeMode = "dark",
        accent = "purple",
        rejectMessages = listOf("회의 중입니다.", "나중에 연락드릴게요."),
        checklistId = "jangwoo",
    )

    @Test fun roundTripKeepsEverything() {
        assertEquals(sample, SyncSnapshot.parse(sample.toJson().toString()))
    }

    @Test fun fingerprintIsStableAndSensitive() {
        assertEquals(sample.fingerprint(), sample.copy().fingerprint())
        assertFalse(sample.fingerprint() == sample.copy(dtmfEnabled = true).fingerprint())
        // map order must not matter
        val a = sample.copy(gameScores = linkedMapOf("a" to 1, "b" to 2))
        val b = sample.copy(gameScores = linkedMapOf("b" to 2, "a" to 1))
        assertEquals(a.fingerprint(), b.fingerprint())
    }

    @Test fun brokenOrMissingFieldsFallBackToEmpty() {
        val parsed = SyncSnapshot.parse("""{"speedDials":[{"title":"x"},{"url":"https://a.com"},5],"gameScores":{"2048":-3,"snake":4}}""")!!
        assertEquals(listOf(SyncSnapshot.Link("https://a.com", "https://a.com")), parsed.speedDials)
        assertEquals(mapOf("snake" to 4), parsed.gameScores)
        assertTrue(parsed.dtmfEnabled)
        assertNull(SyncSnapshot.parse("not json"))
    }

    @Test fun scoresTakeTheBetterOfBothDevices() {
        val merged = sample.withBestScores(mapOf("2048" to 500, "snake" to 40, "breakout" to 7))
        assertEquals(mapOf("2048" to 1080, "snake" to 40, "breakout" to 7), merged.gameScores)
    }

    @Test fun checklistIdMustLookLikeTheSitesIds() {
        assertTrue(SyncSnapshot.isValidChecklistId("jangwoo"))
        assertTrue(SyncSnapshot.isValidChecklistId("장우_1"))
        assertFalse(SyncSnapshot.isValidChecklistId("a"))
        assertFalse(SyncSnapshot.isValidChecklistId("has space"))
        assertFalse(SyncSnapshot.isValidChecklistId("x');alert(1);('"))
        assertFalse(SyncSnapshot.isValidChecklistId("A".repeat(33)))
        assertNull(SyncSnapshot.parse("""{"checklistId":"BAD ID"}""")!!.checklistId)
    }
}
