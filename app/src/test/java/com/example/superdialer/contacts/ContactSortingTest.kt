package com.example.superdialer.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSortingTest {
    private fun contact(name: String, vararg numbers: String, id: Long = name.hashCode().toLong()) =
        Contact(id, name, starred = false, numbers = numbers.toList())

    @Test fun initials() {
        assertEquals("ㄱ", ContactSorting.initialOf("김철수"))
        assertEquals("ㄱ", ContactSorting.initialOf("까치"))
        assertEquals("ㅎ", ContactSorting.initialOf("홍길동"))
        assertEquals("ㅎ", ContactSorting.initialOf("ㅎㅎ"))
        assertEquals("A", ContactSorting.initialOf("alice"))
        assertEquals("#", ContactSorting.initialOf("123"))
        assertEquals("#", ContactSorting.initialOf(""))
    }

    @Test fun sortsHangulThenLatinThenOther() {
        val names = listOf("Bob", "다람쥐", "123", "가나다", "alice", "나비", "까치")
        val sorted = names.map { contact(it) }.sortedWith(ContactSorting.comparator).map { it.name }
        assertEquals(listOf("가나다", "까치", "나비", "다람쥐", "alice", "Bob", "123"), sorted)
    }

    @Test fun matchesNameCaseInsensitive() {
        assertTrue(ContactSorting.matches(contact("Alice Kim"), "ali"))
        assertTrue(ContactSorting.matches(contact("홍길동"), "길동"))
        assertFalse(ContactSorting.matches(contact("홍길동"), "철수"))
    }

    @Test fun matchesChoseong() {
        assertTrue(ContactSorting.matches(contact("홍길동"), "ㅎㄱㄷ"))
        assertTrue(ContactSorting.matches(contact("홍길동"), "ㄱㄷ"))
        assertFalse(ContactSorting.matches(contact("홍길동"), "ㄴㄴ"))
    }

    @Test fun matchesPhoneDigits() {
        val c = contact("홍길동", "010-1234-5678")
        assertTrue(ContactSorting.matches(c, "1234"))
        assertTrue(ContactSorting.matches(c, "010-1234"))
        assertFalse(ContactSorting.matches(c, "9999"))
    }

    @Test fun blankQueryMatchesAll() {
        assertTrue(ContactSorting.matches(contact("누구든"), "  "))
    }
}
