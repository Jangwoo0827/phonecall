package com.example.superdialer.dialer

/** Formats a raw dial string (digits, *, #, leading +) for display, using Korean number rules. */
object PhoneNumberFormatter {

    fun format(raw: String): String {
        if (raw.isEmpty()) return raw
        if (raw.any { it == '*' || it == '#' }) return raw
        if (raw.startsWith("+82")) {
            val rest = raw.removePrefix("+82").removePrefix("0")
            if (rest.isEmpty()) return "+82"
            if (!rest.all(Char::isDigit)) return raw
            return "+82 " + formatDomestic("0$rest").removePrefix("0")
        }
        if (raw.startsWith("+")) return raw
        if (!raw.all(Char::isDigit)) return raw
        return formatDomestic(raw)
    }

    /** For numbers coming from the call log / contacts, which may contain spaces, dashes or parentheses. */
    fun formatLoose(raw: String): String =
        format(raw.filter { it.isDigit() || it == '+' || it == '*' || it == '#' })

    private fun formatDomestic(d: String): String = when {
        d.startsWith("02") -> withPrefix("02", d.drop(2))
        d.length <= 3 -> d
        d.startsWith("010") -> {
            val rest = d.drop(3)
            if (rest.length <= 4) "010-$rest" else "010-${rest.take(4)}-${rest.drop(4)}"
        }
        d.startsWith("0") -> withPrefix(d.take(3), d.drop(3))
        d.startsWith("1") -> if (d.length <= 4) d else "${d.take(4)}-${d.drop(4)}"
        else -> localNumber(d)
    }

    /** Area codes / 011-019 / 070 etc: 3-3-4 up to 10 digits, 3-4-4 for 11 digits. */
    private fun withPrefix(prefix: String, rest: String): String = when {
        rest.isEmpty() -> prefix
        rest.length <= 3 -> "$prefix-$rest"
        rest.length <= 7 -> "$prefix-${rest.take(3)}-${rest.drop(3)}"
        else -> "$prefix-${rest.take(4)}-${rest.drop(4)}"
    }

    private fun localNumber(d: String): String = when {
        d.length <= 4 -> d
        d.length <= 7 -> "${d.take(3)}-${d.drop(3)}"
        else -> "${d.take(4)}-${d.drop(4)}"
    }
}
