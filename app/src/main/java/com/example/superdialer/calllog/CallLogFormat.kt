package com.example.superdialer.calllog

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private fun zoned(millis: Long, zone: ZoneId) = Instant.ofEpochMilli(millis).atZone(zone)

/** "오후 3:28" */
fun formatClock(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    zoned(millis, zone).format(DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN))

/** Section header: "오늘 10. 7. 수요일", "어제 10. 6. 화요일", "10. 5. 일요일", "2025. 3. 2. 일요일". */
fun formatDayHeader(
    millis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val date = zoned(millis, zone)
    val now = zoned(nowMillis, zone)
    val days = ChronoUnit.DAYS.between(date.toLocalDate(), now.toLocalDate())
    val pattern = if (date.year == now.year) "M. d. EEEE" else "yyyy. M. d. EEEE"
    val text = date.format(DateTimeFormatter.ofPattern(pattern, Locale.KOREAN))
    return when (days) {
        0L -> "오늘 $text"
        1L -> "어제 $text"
        else -> text
    }
}

/** "10월 5일 오후 3:28", with the year when it is not the current one. */
fun formatDateTime(
    millis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val date = zoned(millis, zone)
    val sameYear = date.year == zoned(nowMillis, zone).year
    val pattern = if (sameYear) "M월 d일 a h:mm" else "yyyy년 M월 d일 a h:mm"
    return date.format(DateTimeFormatter.ofPattern(pattern, Locale.KOREAN))
}

/** 0 -> "", 45 -> "45초", 83 -> "1분 23초", 3723 -> "1시간 2분 3초". */
fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return buildList {
        if (h > 0) add("${h}시간")
        if (m > 0) add("${m}분")
        if (s > 0) add("${s}초")
    }.joinToString(" ")
}
