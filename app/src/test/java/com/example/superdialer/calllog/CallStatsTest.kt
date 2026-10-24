package com.example.superdialer.calllog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallStatsTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun call(
        id: Long,
        number: String,
        type: CallType = CallType.Incoming,
        daysAgo: Long = 1,
        seconds: Long = 0,
        name: String? = null,
    ) = CallLogEntry(id, number, name, null, type, now - daysAgo * day, seconds)

    @Test fun countsByTypeAndAddsUpTalkTime() {
        val s = CallStatsCalculator.calculate(
            listOf(
                call(1, "01011112222", CallType.Incoming, seconds = 60),
                call(2, "01011112222", CallType.Outgoing, seconds = 120),
                call(3, "01033334444", CallType.Missed),
                call(4, "01055556666", CallType.Rejected),
                call(5, "01077778888", CallType.Blocked),
            ),
            StatsPeriod.All, now,
        )
        assertEquals(5, s.total)
        assertEquals(1, s.incoming)
        assertEquals(1, s.outgoing)
        assertEquals(1, s.missed)
        assertEquals(2, s.declined)
        assertEquals(180L, s.talkSeconds)
    }

    @Test fun periodCutsOffOlderCalls() {
        val entries = listOf(call(1, "0101", daysAgo = 2), call(2, "0102", daysAgo = 10), call(3, "0103", daysAgo = 40))
        assertEquals(1, CallStatsCalculator.calculate(entries, StatsPeriod.Week, now).total)
        assertEquals(2, CallStatsCalculator.calculate(entries, StatsPeriod.Month, now).total)
        assertEquals(3, CallStatsCalculator.calculate(entries, StatsPeriod.All, now).total)
    }

    @Test fun rankingGroupsOneNumberWrittenTwoWaysAndSortsByCallsThenTalkTime() {
        val entries = listOf(
            call(1, "01012345678", seconds = 10, name = "민수"),
            call(2, "+82 10-1234-5678", seconds = 20),
            call(3, "01099998888", seconds = 500, name = "영희"),
            call(4, "01099998888", seconds = 500),
            call(5, "01011110000", seconds = 5),
        )
        val top = CallStatsCalculator.calculate(entries, StatsPeriod.All, now).top
        // 민수 and 영희 both have 2 calls; 영희 talked longer.
        assertEquals(listOf("영희", "민수", null), top.map { it.name })
        assertEquals(listOf(2, 2, 1), top.map { it.calls })
        assertEquals(30L, top[1].talkSeconds)
    }

    @Test fun privateCallersAreCountedButNotRanked() {
        val s = CallStatsCalculator.calculate(listOf(call(1, ""), call(2, "")), StatsPeriod.All, now)
        assertEquals(2, s.total)
        assertEquals(emptyList<TopCaller>(), s.top)
    }

    @Test fun rankingIsLimitedAndEmptyInputGivesZeros() {
        val many = (1..9).map { call(it.toLong(), "0101000$it") }
        assertEquals(CallStatsCalculator.TOP_COUNT, CallStatsCalculator.calculate(many, StatsPeriod.All, now).top.size)
        val none = CallStatsCalculator.calculate(emptyList(), StatsPeriod.Week, now)
        assertEquals(0, none.total)
        assertEquals(0L, none.talkSeconds)
        assertNull(none.top.firstOrNull())
    }
}
