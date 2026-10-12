package com.example.superdialer.dialer

import com.example.superdialer.calllog.CallLogEntry
import com.example.superdialer.calllog.numberKey
import com.example.superdialer.contacts.Contact

/** A number the keypad offers to autocomplete to. [name] is set when it belongs to a contact. */
data class Suggestion(val name: String?, val number: String, val fromContact: Boolean, val recent: Boolean = false)

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

/**
 * Shown on the keypad while nothing is typed: the numbers called (or that called) most recently, newest first,
 * one per number (+82/010 forms count as the same number). Private callers have no number and are skipped.
 * Numbers whose key is in [skip] (the starred contacts already shown next to them) are left out.
 */
fun recentCallSuggestions(
    calls: List<CallLogEntry>,
    skip: Set<String> = emptySet(),
    limit: Int = 8,
): List<Suggestion> {
    val seen = HashSet<String>(skip)
    val out = ArrayList<Suggestion>()
    for (call in calls.sortedByDescending { it.dateMillis }) {
        if (out.size >= limit) break
        if (call.number.isEmpty()) continue
        if (seen.add(numberKey(call.number))) {
            out += Suggestion(call.name, call.number, fromContact = call.name != null, recent = true)
        }
    }
    return out
}
