package com.example.superdialer.dialer

import com.example.superdialer.calllog.CallLogEntry
import com.example.superdialer.calllog.numberKey
import com.example.superdialer.contacts.Contact

/** A number the keypad offers to autocomplete to. [name] is set when it belongs to a contact. */
data class Suggestion(val name: String?, val number: String, val fromContact: Boolean)

/**
 * Contacts first (alphabetical order as given), then recent callers that are not saved contacts.
 * Matches when the typed digits appear anywhere in the number. Nothing for fewer than 2 digits.
 */
fun findSuggestions(
    typed: String,
    contacts: List<Contact>,
    calls: List<CallLogEntry>,
    limit: Int = 3,
): List<Suggestion> {
    val digits = numberKey(typed)
    if (digits.length < 2) return emptyList()

    val seen = HashSet<String>()
    val out = ArrayList<Suggestion>()

    fun add(s: Suggestion) {
        if (out.size < limit && seen.add(numberKey(s.number))) out += s
    }

    for (contact in contacts) {
        for (n in contact.numbers) {
            if (numberKey(n).contains(digits)) add(Suggestion(contact.name, n, fromContact = true))
        }
    }
    for (call in calls) {
        if (call.number.isNotEmpty() && numberKey(call.number).contains(digits)) {
            add(Suggestion(call.name, call.number, fromContact = call.name != null))
        }
    }
    return out
}
