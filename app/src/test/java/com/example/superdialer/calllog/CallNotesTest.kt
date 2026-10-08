package com.example.superdialer.calllog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallNotesTest {
    private val key = "01012345678"
    private val start = 1_000_000L

    @Test fun matchesAMemoWrittenDuringTheCall() {
        val note = CallNote(key, start + 30_000, "x")
        assertTrue(CallNotes.matches(note, key, start, 60))
        assertTrue(CallNotes.matches(CallNote(key, start + 60_000 + 80_000, "x"), key, start, 60))
    }

    @Test fun doesNotMatchOtherNumbersOrTimes() {
        assertFalse(CallNotes.matches(CallNote("0109999", start + 10, "x"), key, start, 60))
        assertFalse(CallNotes.matches(CallNote(key, start - 1, "x"), key, start, 60))
        assertFalse(CallNotes.matches(CallNote(key, start + 60_000 + 91_000, "x"), key, start, 60))
        assertFalse(CallNotes.matches(CallNote("", start, "x"), "", start, 60))
    }

    @Test fun roundTripAndBadInput() {
        val list = listOf(CallNote(key, 5, "한글 메모"), CallNote("02123", 9, "b"))
        assertEquals(list, CallNotes.decode(CallNotes.encode(list)))
        assertEquals(emptyList<CallNote>(), CallNotes.decode("nope"))
        assertEquals(emptyList<CallNote>(), CallNotes.decode(null))
    }
}
