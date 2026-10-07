package com.example.superdialer.calllog

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CallLogFormatTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private fun millis(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    private val now = millis(2026, 10, 7, 15, 0)

    @Test fun durations() {
        assertEquals("", formatDuration(0))
        assertEquals("45초", formatDuration(45))
        assertEquals("1분 23초", formatDuration(83))
        assertEquals("2분", formatDuration(120))
        assertEquals("1시간 2분 3초", formatDuration(3723))
    }

    @Test fun clock() {
        assertEquals("오후 3:28", formatClock(millis(2026, 10, 7, 15, 28), zone))
        assertEquals("오전 12:05", formatClock(millis(2026, 10, 7, 0, 5), zone))
    }

    @Test fun dayHeader() {
        assertEquals("오늘 10. 7. 수요일", formatDayHeader(millis(2026, 10, 7, 9), now, zone))
        assertEquals("어제 10. 6. 화요일", formatDayHeader(millis(2026, 10, 6, 23, 59), now, zone))
        assertEquals("10. 5. 월요일", formatDayHeader(millis(2026, 10, 5), now, zone))
        assertEquals("2025. 3. 2. 일요일", formatDayHeader(millis(2025, 3, 2), now, zone))
    }

    @Test fun dateTime() {
        assertEquals("10월 5일 오후 3:28", formatDateTime(millis(2026, 10, 5, 15, 28), now, zone))
        assertEquals("2025년 3월 2일 오전 9:00", formatDateTime(millis(2025, 3, 2, 9), now, zone))
    }

    @Test fun numberKeyTreatsInternationalAndLocalAlike() {
        assertEquals("01012345678", numberKey("010-1234-5678"))
        assertEquals("01012345678", numberKey("+82 10-1234-5678"))
        assertEquals("01012345678", numberKey("+821012345678"))
        assertEquals("", numberKey(""))
    }
}
