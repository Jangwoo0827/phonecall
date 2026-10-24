package com.example.superdialer.contacts

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactMemosTest {
    @Test fun findsTheMemoUnderAnyOfTheContactsNumbers() {
        val all = mapOf("0102222" to "저녁 약속")
        assertEquals("저녁 약속", ContactMemos.find(all, listOf("0101111", "0102222")))
        assertEquals("", ContactMemos.find(all, listOf("0103333")))
        assertEquals("", ContactMemos.find(emptyMap(), emptyList()))
    }

    @Test fun storeKeepsOneMemoPerContactUnderTheFirstNumber() {
        val before = mapOf("0102222" to "옛 메모", "0109999" to "다른 사람")
        val after = ContactMemos.store(before, listOf("0101111", "0102222"), "  새 메모  ", 10)
        assertEquals(mapOf("0109999" to "다른 사람", "0101111" to "새 메모"), after)
    }

    @Test fun blankTextRemovesTheMemoOfEveryNumber() {
        val before = mapOf("0101111" to "a", "0102222" to "b", "0109999" to "c")
        assertEquals(mapOf("0109999" to "c"), ContactMemos.store(before, listOf("0101111", "0102222"), "  ", 10))
    }

    @Test fun contactWithoutUsableNumbersChangesNothing() {
        val before = mapOf("0101111" to "a")
        assertEquals(before, ContactMemos.store(before, listOf(""), "x", 10))
    }

    @Test fun theOldestMemosFallAwayPastTheLimit() {
        val before = linkedMapOf("a1" to "1", "b2" to "2")
        assertEquals(listOf("b2", "c3"), ContactMemos.store(before, listOf("c3"), "3", 2).keys.toList())
    }

    @Test fun roundTripAndBadInput() {
        val map = mapOf("0101111" to "한글 메모\n두 줄")
        assertEquals(map, ContactMemos.decode(ContactMemos.encode(map)))
        assertEquals(emptyMap<String, String>(), ContactMemos.decode("nope"))
        assertEquals(emptyMap<String, String>(), ContactMemos.decode(null))
    }
}
