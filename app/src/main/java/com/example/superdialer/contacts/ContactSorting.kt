package com.example.superdialer.contacts

import java.text.Collator
import java.util.Locale

/** 가나다순 정렬, 초성 인덱스, 검색 매칭. Pure functions so they can be unit tested. */
object ContactSorting {
    private const val CHOSEONG = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private val DOUBLE_TO_BASE = mapOf('ㄲ' to 'ㄱ', 'ㄸ' to 'ㄷ', 'ㅃ' to 'ㅂ', 'ㅆ' to 'ㅅ', 'ㅉ' to 'ㅈ')

    /** Initial consonant of a Hangul syllable, the jamo itself if it is one, else null. */
    fun choseong(c: Char): Char? = when (c) {
        in '가'..'힣' -> CHOSEONG[(c - '가') / 588]
        in 'ㄱ'..'ㅎ' -> c
        else -> null
    }

    /** Section header: ㄱ..ㅎ (doubles merged), A..Z, or "#". */
    fun initialOf(name: String): String {
        val c = name.trim().firstOrNull() ?: return "#"
        choseong(c)?.let { return (DOUBLE_TO_BASE[it] ?: it).toString() }
        if (c.isLetter() && Character.UnicodeScript.of(c.code) == Character.UnicodeScript.LATIN) {
            return c.uppercaseChar().toString()
        }
        return "#"
    }

    /** Hangul first, then Latin, then digits/symbols. */
    private fun bucket(name: String): Int {
        val initial = initialOf(name)
        return when {
            initial == "#" -> 2
            initial[0] in 'ㄱ'..'ㅎ' -> 0
            else -> 1
        }
    }

    private val collator: Collator = Collator.getInstance(Locale.KOREAN).apply {
        strength = Collator.PRIMARY
    }

    val comparator: Comparator<Contact> = Comparator { a, b ->
        val byBucket = bucket(a.name).compareTo(bucket(b.name))
        if (byBucket != 0) return@Comparator byBucket
        val byName = collator.compare(a.name, b.name)
        if (byName != 0) byName else a.id.compareTo(b.id)
    }

    fun choseongString(name: String): String = name.map { choseong(it) ?: it }.joinToString("")

    /** Blank query matches everything. Matches name, 초성 (ㅎㄱㄷ → 홍길동) and phone digits. */
    fun matches(contact: Contact, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        if (contact.name.contains(q, ignoreCase = true)) return true
        if (q.all { it in 'ㄱ'..'ㅎ' } && choseongString(contact.name).contains(q)) return true
        val digits = q.filter(Char::isDigit)
        if (digits.isNotEmpty() && q.all { it.isDigit() || it in "-+ " }) {
            return contact.numbers.any { n -> n.filter(Char::isDigit).contains(digits) }
        }
        return false
    }
}
