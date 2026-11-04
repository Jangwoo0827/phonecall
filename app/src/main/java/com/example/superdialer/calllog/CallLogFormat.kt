package com.example.superdialer.calllog

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import com.example.superdialer.i18n.Strings

private fun zoned(millis: Long, zone: ZoneId) = Instant.ofEpochMilli(millis).atZone(zone)

/** "오후 3:28" (Korean) / "3:28 PM" (English). Dates follow the app's language, see [Strings.locale]. */
fun formatClock(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    zoned(millis, zone).format(DateTimeFormatter.ofPattern(if (Strings.isKorean) "a h:mm" else "h:mm a", Strings.locale))

/** Section header: "오늘 10. 7. 수요일", "어제 10. 6. 화요일", "10. 5. 일요일", "2025. 3. 2. 일요일". */
fun formatDayHeader(
    millis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val date = zoned(millis, zone)
    val now = zoned(nowMillis, zone)
    val days = ChronoUnit.DAYS.between(date.toLocalDate(), now.toLocalDate())
    val sameYear = date.year == now.year
    val pattern = when {
        Strings.isKorean -> if (sameYear) "M. d. EEEE" else "yyyy. M. d. EEEE"
        else -> if (sameYear) "EEE, MMM d" else "EEE, MMM d, yyyy"
    }
    val text = date.format(DateTimeFormatter.ofPattern(pattern, Strings.locale))
    return when (days) {
        0L -> str(R.string.t_today, text)
        1L -> str(R.string.t_yesterday, text)
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
    val pattern = when {
        Strings.isKorean -> if (sameYear) "M월 d일 a h:mm" else "yyyy년 M월 d일 a h:mm"
        else -> if (sameYear) "MMM d, h:mm a" else "MMM d, yyyy, h:mm a"
    }
    return date.format(DateTimeFormatter.ofPattern(pattern, Strings.locale))
}

/** 0 -> "", 45 -> "45초", 83 -> "1분 23초", 3723 -> "1시간 2분 3초". */
fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return buildList {
        if (h > 0) add(str(R.string.t_h, h))
        if (m > 0) add(str(R.string.t_m, m))
        if (s > 0) add(str(R.string.t_s, s))
    }.joinToString(" ")
}
