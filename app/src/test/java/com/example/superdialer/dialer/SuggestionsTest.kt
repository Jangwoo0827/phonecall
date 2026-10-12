package com.example.superdialer.dialer

import com.example.superdialer.calllog.CallLogEntry
import com.example.superdialer.calllog.CallType
import com.example.superdialer.calllog.numberKey
import com.example.superdialer.contacts.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {
    private val contacts = listOf(
        Contact(1, "김철수", false, listOf("010-1111-2222")),
        Contact(2, "홍길동", true, listOf("010-3333-4444", "02-123-4567")),
    )
    private fun call(id: Long, number: String, name: String? = null) =
        CallLogEntry(id, number, name, null, CallType.Incoming, 0, 0)

    @Test fun needsAtLeastTwoDigits() {
        assertTrue(findSuggestions("1", contacts, emptyList()).isEmpty())
        assertTrue(findSuggestions("", contacts, emptyList()).isEmpty())
    }

    @Test fun matchesContactNumbersAnywhere() {
        val result = findSuggestions("3333", contacts, emptyList())
        assertEquals(listOf("홍길동"), result.map { it.name })
        assertEquals("010-3333-4444", result.single().number)
    }

    @Test fun everyMatchingNumberIsItsOwnSuggestion() {
        // "34" appears in both numbers of 홍길동 (3333-4444 and 02-1234-567) but not in 김철수's.
        val result = findSuggestions("34", contacts, emptyList(), limit = 10)
        assertEquals(listOf("010-3333-4444", "02-123-4567"), result.map { it.number })
    }

    @Test fun recentUnsavedCallersFollowContactsWithoutDuplicates() {
        val calls = listOf(
            call(1, "01011112222", "김철수"),          // duplicate of a contact number
            call(2, "+82 10-1111-9999"),               // unsaved
            call(3, "01011119999"),                    // same unsaved number again
        )
        val result = findSuggestions("1111", contacts, calls, limit = 10)
        assertEquals(listOf("010-1111-2222", "+82 10-1111-9999"), result.map { it.number })
        assertTrue(result[0].fromContact)
        assertEquals(false, result[1].fromContact)
    }

    @Test fun respectsLimit() {
        val many = (1..10).map { Contact(it.toLong(), "c$it", false, listOf("010-5555-000$it")) }
        assertEquals(3, findSuggestions("5555", many, emptyList()).size)
    }

    @Test fun recentCallsAreNewestFirstOnePerNumber() {
        val calls = listOf(
            CallLogEntry(1, "01011112222", "김철수", null, CallType.Incoming, 1_000, 0),
            CallLogEntry(2, "+82 10-5555-0000", null, null, CallType.Outgoing, 3_000, 0),
            CallLogEntry(3, "01055550000", null, null, CallType.Missed, 2_000, 0), // same number as #2, older
            CallLogEntry(4, "", null, null, CallType.Missed, 4_000, 0),            // private caller: no number
        )
        val result = recentCallSuggestions(calls)
        assertEquals(listOf("+82 10-5555-0000", "01011112222"), result.map { it.number })
        assertTrue(result.all { it.recent })
        assertEquals(listOf(null, "김철수"), result.map { it.name })
    }

    @Test fun recentCallsSkipStarredNumbersAndRespectLimit() {
        val calls = (1..12).map { CallLogEntry(it.toLong(), "010-0000-00%02d".format(it), null, null, CallType.Outgoing, it * 1000L, 0) }
        val skip = setOf(numberKey("010-0000-0012"))
        val result = recentCallSuggestions(calls, skip, limit = 3)
        assertEquals(listOf("010-0000-0011", "010-0000-0010", "010-0000-0009"), result.map { it.number })
    }
}
