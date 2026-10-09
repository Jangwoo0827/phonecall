package com.example.superdialer.messages

import org.junit.Assert.assertEquals
import org.junit.Test

class SentSmsTest {
    @Test fun roundTrip() {
        val list = listOf(SentMessage(5, "01012345678", "010-1234-5678", "안녕", SentMessage.SENT), SentMessage(9, "02123", "02-123", "b", SentMessage.FAILED))
        assertEquals(list, SentSms.decode(SentSms.encode(list)))
        assertEquals(emptyList<SentMessage>(), SentSms.decode("nope"))
    }

    @Test fun mergeIsNewestFirstAndMarksSentOnesOutgoing() {
        val system = listOf(
            SmsMessage(1, "010", "old in", 100, outgoing = false),
            SmsMessage(2, "010", "new in", 300, outgoing = false),
        )
        val merged = SentSms.merge(system, listOf(SentMessage(200, "010", "010", "mine", SentMessage.SENT)))
        assertEquals(listOf("new in", "mine", "old in"), merged.map { it.body })
        assertEquals(listOf(false, true, false), merged.map { it.outgoing })
    }

    @Test fun newestFirstDoesNotTrustTheProvidersOrder() {
        // a provider that ignores the sort order hands rows over oldest first
        val oldestFirst = (1..100).map { SmsMessage(it.toLong(), "010", "m$it", it * 1000L, outgoing = false) }
        val top = SmsRepository.newestFirst(oldestFirst, 30)
        assertEquals(30, top.size)
        assertEquals("m100", top.first().body)
        assertEquals("m71", top.last().body)
    }
}
