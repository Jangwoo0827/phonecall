package com.example.superdialer.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ConversationRepositoryTest {
    private fun conversation(
        id: Long,
        addresses: List<String>,
        names: List<String?> = addresses.map { null },
        snippet: String = "",
        date: Long = 0,
    ) = Conversation(id, addresses, names, null, snippet, date, 1, false)

    @Test fun newestConversationComesFirstWhateverTheProvidersOrder() {
        val list = listOf(conversation(1, listOf("010"), date = 1_000), conversation(2, listOf("011"), date = 9_000), conversation(3, listOf("012"), date = 5_000))
        assertEquals(listOf(2L, 3L), ConversationRepository.newestFirst(list, 2).map { it.threadId })
    }

    @Test fun titleUsesNamesWhereKnownAndNumbersElse() {
        assertEquals("김철수", conversation(1, listOf("01011112222"), listOf("김철수")).title)
        assertEquals("01099990000", conversation(1, listOf("01099990000")).title)
        val group = conversation(1, listOf("01011112222", "01099990000"), listOf("김철수", null))
        assertEquals("김철수, 01099990000", group.title)
        assertTrue(group.isGroup)
        assertEquals("01011112222", group.number)
    }

    @Test fun searchMatchesNameNumberInAnyFormatAndMessageText() {
        val c = conversation(1, listOf("+82 10-1111-2222"), listOf("김철수"), snippet = "내일 점심 어때?")
        assertTrue(ConversationRepository.matches(c, ""))
        assertTrue(ConversationRepository.matches(c, "김철"))
        assertTrue(ConversationRepository.matches(c, "점심"))
        assertTrue(ConversationRepository.matches(c, "010-1111")) // +82 10… and 010… are the same number
        assertTrue(ConversationRepository.matches(c, "1111 2222".replace(" ", "")))
        assertFalse(ConversationRepository.matches(c, "홍길동"))
        assertFalse(ConversationRepository.matches(c, "9999"))
    }

    @Test fun timeLabelIsTheTimeTodayYesterdayThenTheDay() {
        val zone = ZoneId.of("Asia/Seoul")
        fun ms(y: Int, m: Int, d: Int, h: Int, min: Int) = LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()
        val now = ms(2026, 10, 14, 15, 0)
        assertEquals("오후 2:33", ConversationRepository.timeLabel(ms(2026, 10, 14, 14, 33), now, zone))
        assertEquals("어제", ConversationRepository.timeLabel(ms(2026, 10, 13, 23, 59), now, zone))
        assertEquals("10월 8일", ConversationRepository.timeLabel(ms(2026, 10, 8, 9, 0), now, zone))
    }
}
