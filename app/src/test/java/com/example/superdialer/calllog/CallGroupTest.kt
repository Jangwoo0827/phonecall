package com.example.superdialer.calllog

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallGroupTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private fun at(day: Int, hour: Int) =
        ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    private var nextId = 1L
    private fun call(number: String, day: Int, hour: Int, type: CallType = CallType.Incoming, name: String? = null) =
        CallLogEntry(nextId++, number, name, null, type, at(day, hour), 0)

    @Test fun groupsConsecutiveSameNumberOnSameDay() {
        val entries = listOf(
            call("010-1111-2222", 7, 15),
            call("+821011112222", 7, 12, CallType.Missed),
            call("01033334444", 7, 10),
            call("01011112222", 7, 9),
        )
        val groups = groupCalls(entries, zone)
        assertEquals(listOf(2, 1, 1), groups.map { it.count })
        assertEquals(entries[0].id, groups[0].id)
    }

    @Test fun doesNotGroupAcrossDays() {
        val groups = groupCalls(listOf(call("01011112222", 7, 1), call("01011112222", 6, 23)), zone)
        assertEquals(2, groups.size)
    }

    @Test fun neverGroupsUnknownNumbers() {
        val groups = groupCalls(listOf(call("", 7, 15), call("", 7, 14)), zone)
        assertEquals(2, groups.size)
    }

    @Test fun filters() {
        val missed = call("1", 7, 1, CallType.Missed)
        val incoming = call("2", 7, 1, CallType.Incoming)
        val outgoing = call("3", 7, 1, CallType.Outgoing)
        assertTrue(CallFilter.All.accepts(missed))
        assertTrue(CallFilter.Missed.accepts(missed))
        assertFalse(CallFilter.Missed.accepts(incoming))
        assertTrue(CallFilter.Incoming.accepts(incoming))
        assertTrue(CallFilter.Outgoing.accepts(outgoing))
        assertFalse(CallFilter.Outgoing.accepts(missed))
    }

    @Test fun queryMatchesNameChoseongAndNumber() {
        val e = call("010-3333-4444", 7, 1, name = "홍길동")
        assertTrue(matchesCallQuery(e, "홍길"))
        assertTrue(matchesCallQuery(e, "ㅎㄱㄷ"))
        assertTrue(matchesCallQuery(e, "3333"))
        assertFalse(matchesCallQuery(e, "9999"))
        assertTrue(matchesCallQuery(e, ""))
    }
}
