package com.example.superdialer.kakao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KakaoInboxTest {
    private fun msg(room: String, text: String, time: Long, sender: String = room, outgoing: Boolean = false) =
        KakaoMessage(room, sender, text, time, outgoing)

    // --- reading a notification ---

    @Test fun aOneToOneNotificationIsTheSenderAndTheText() {
        val result = KakaoNotificationParser.parse("홍길동", "점심 먹었어?", null, emptyList(), 1_000)
        assertEquals(listOf(msg("홍길동", "점심 먹었어?", 1_000)), result)
    }

    @Test fun aGroupNotificationShowsWhoSaidIt() {
        val result = KakaoNotificationParser.parse("우리반 단톡", "철수 : 내일 시험이래", null, emptyList(), 2_000)
        assertEquals(listOf(msg("우리반 단톡", "내일 시험이래", 2_000, sender = "철수")), result)
    }

    @Test fun aMessagingStyleNotificationGivesEveryLineWithItsOwnTime() {
        val lines = listOf(
            KakaoNotificationParser.Line("철수", "안녕", 100),
            KakaoNotificationParser.Line("영희", "ㅎㅇ", 200),
            KakaoNotificationParser.Line(null, "   ", 300), // blank: dropped
        )
        val result = KakaoNotificationParser.parse("우리반 단톡", "ㅎㅇ", "우리반 단톡", lines, 999)
        assertEquals(
            listOf(msg("우리반 단톡", "안녕", 100, "철수"), msg("우리반 단톡", "ㅎㅇ", 200, "영희")),
            result,
        )
    }

    @Test fun nothingToShowGivesNothing() {
        assertTrue(KakaoNotificationParser.parse("홍길동", "  ", null, emptyList(), 1).isEmpty())
        assertTrue(KakaoNotificationParser.parse(null, null, null, emptyList(), 1).isEmpty())
    }

    @Test fun aNotificationWithoutATitleFallsBackToTheAppName() {
        assertEquals("카카오톡", KakaoNotificationParser.parse(null, "새 메시지", null, emptyList(), 1).single().room)
    }

    // --- keeping them ---

    @Test fun theSameMessageSeenAgainWithinAMinuteIsOneMessage() {
        val first = listOf(msg("a", "안녕", 1_000))
        val merged = KakaoInbox.merge(first, listOf(msg("a", "안녕", 1_000 + 30_000)), 100)
        assertEquals(1, merged.size)
        // the same words a minute later are a new message
        assertEquals(2, KakaoInbox.merge(first, listOf(msg("a", "안녕", 1_000 + 120_000)), 100).size)
    }

    @Test fun differentTextOrRoomOrDirectionAreKept() {
        val base = listOf(msg("a", "안녕", 1_000))
        assertEquals(2, KakaoInbox.merge(base, listOf(msg("a", "잘가", 1_000)), 100).size)
        assertEquals(2, KakaoInbox.merge(base, listOf(msg("b", "안녕", 1_000)), 100).size)
        assertEquals(2, KakaoInbox.merge(base, listOf(msg("a", "안녕", 1_000, outgoing = true)), 100).size)
    }

    @Test fun onlyTheNewestMessagesAreKeptAndTheyStayInTimeOrder() {
        val merged = KakaoInbox.merge(
            listOf(msg("a", "1", 3_000_000)),
            listOf(msg("a", "2", 1_000_000), msg("a", "3", 5_000_000), msg("a", "4", 7_000_000)),
            limit = 3,
        )
        assertEquals(listOf("1", "3", "4"), merged.map { it.text })
    }

    // --- rooms and storage ---

    @Test fun roomsAreNewestFirstWithTheirMessageCount() {
        val rooms = KakaoInbox.groupRooms(
            listOf(msg("a", "1", 1_000), msg("b", "2", 5_000), msg("a", "3", 9_000), msg("a", "4", 2_000)),
        )
        assertEquals(listOf("a", "b"), rooms.map { it.name })
        assertEquals(3, rooms[0].count)
        assertEquals("3", rooms[0].last.text)
    }

    @Test fun savingAndLoadingKeepsEverythingIncludingOddCharacters() {
        val list = listOf(msg("방 \"1\"", "줄1\n줄2 \\ ❤", 1_000, "철수"), msg("방 \"1\"", "답장", 2_000, "나", outgoing = true))
        assertEquals(list, KakaoInbox.decode(KakaoInbox.encode(list)))
    }

    @Test fun brokenStoredDataGivesAnEmptyList() {
        assertEquals(emptyList<KakaoMessage>(), KakaoInbox.decode("not json"))
        assertEquals(emptyList<KakaoMessage>(), KakaoInbox.decode(null))
        assertEquals(emptyList<KakaoMessage>(), KakaoInbox.decode(""))
    }
}
