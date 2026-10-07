package com.example.superdialer.calllog

import com.example.superdialer.contacts.Contact
import com.example.superdialer.contacts.ContactSorting
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Consecutive calls with the same number on the same day, shown as one row. [entries] is newest first. */
class CallGroup(val entries: List<CallLogEntry>) {
    val latest: CallLogEntry get() = entries.first()
    val count: Int get() = entries.size
    val id: Long get() = latest.id
}

fun groupCalls(entries: List<CallLogEntry>, zone: ZoneId = ZoneId.systemDefault()): List<CallGroup> {
    val groups = ArrayList<MutableList<CallLogEntry>>()
    var lastKey: String? = null
    var lastDay: LocalDate? = null
    for (entry in entries) {
        val key = numberKey(entry.number)
        val day = Instant.ofEpochMilli(entry.dateMillis).atZone(zone).toLocalDate()
        if (groups.isNotEmpty() && key.isNotEmpty() && key == lastKey && day == lastDay) {
            groups.last().add(entry)
        } else {
            groups.add(mutableListOf(entry))
        }
        lastKey = key
        lastDay = day
    }
    return groups.map(::CallGroup)
}

enum class CallFilter(val label: String) {
    All("전체"),
    Missed("부재중"),
    Incoming("수신"),
    Outgoing("발신");

    fun accepts(entry: CallLogEntry): Boolean = when (this) {
        All -> true
        Missed -> entry.type == CallType.Missed
        Incoming -> entry.type == CallType.Incoming
        Outgoing -> entry.type == CallType.Outgoing
    }
}

/** Matches name, 초성 (ㅎㄱㄷ) and phone digits. Blank query matches everything. */
fun matchesCallQuery(entry: CallLogEntry, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    // Reuse the contact matcher: a call entry looks like a contact with one number.
    val asContact = Contact(id = entry.id, name = entry.name.orEmpty(), starred = false, numbers = listOf(entry.number))
    return ContactSorting.matches(asContact, q)
}
