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
}
