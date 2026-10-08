package com.example.superdialer.contacts

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactToolsTest {
    private fun c(id: Long, name: String, vararg numbers: String) = Contact(id, name, false, numbers.toList())

    @Test fun sameNumberAcrossFormatsIsADuplicate() {
        val groups = findDuplicates(listOf(c(1, "A", "010-1111-2222"), c(2, "B", "+82 10-1111-2222"), c(3, "C", "010-9999-0000")))
        assertEquals(1, groups.size)
        assertEquals(listOf(1L, 2L), groups[0].contacts.map { it.id })
        assertEquals(DuplicateReason.SameNumber, groups[0].reason)
    }

    @Test fun sameNameIgnoringCaseAndSpaces() {
        val groups = findDuplicates(listOf(c(1, "Kim Chul", "010-1"), c(2, "kimchul", "010-2"), c(3, "Park", "010-3")))
        assertEquals(1, groups.size)
        assertEquals(DuplicateReason.SameName, groups[0].reason)
    }

    @Test fun chainedMatchesFormOneGroupAndNoDuplicatesGiveNone() {
        val chain = findDuplicates(listOf(c(1, "A", "010-1"), c(2, "B", "010-1"), c(3, "B", "010-5")))
        assertEquals(1, chain.size)
        assertEquals(3, chain[0].contacts.size)
        assertEquals(0, findDuplicates(listOf(c(1, "A", "010-1"), c(2, "B", "010-2"))).size)
    }

    @Test fun groupsRoundTrip() {
        val list = listOf(ContactGroup("g1", "가족", setOf(1L, 5L)), ContactGroup("g2", "직장", emptySet()))
        assertEquals(list, ContactGroups.decode(ContactGroups.encode(list)))
        assertEquals(emptyList<ContactGroup>(), ContactGroups.decode("nope"))
    }
}
