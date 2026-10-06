package com.example.superdialer.dialer

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNumberFormatterTest {
    private fun check(expected: String, raw: String) =
        assertEquals(expected, PhoneNumberFormatter.format(raw))

    @Test fun mobile010() {
        check("010", "010")
        check("010-1234", "0101234")
        check("010-1234-5", "01012345")
        check("010-1234-5678", "01012345678")
    }

    @Test fun mobileLegacy() {
        check("011-123-4567", "0111234567")
        check("011-1234-5678", "01112345678")
    }

    @Test fun seoul() {
        check("02", "02")
        check("02-1", "021")
        check("02-123-4567", "021234567")
        check("02-1234-5678", "0212345678")
    }

    @Test fun otherAreas() {
        check("031-123-4567", "0311234567")
        check("031-1234-5678", "03112345678")
        check("070-1234-5678", "07012345678")
    }

    @Test fun shortAndServiceNumbers() {
        check("112", "112")
        check("1588", "1588")
        check("1588-1234", "15881234")
    }

    @Test fun internationalKorea() {
        check("+82", "+82")
        check("+82 10-1234-5678", "+821012345678")
        check("+82 10-1234-5678", "+8201012345678")
        check("+82 2-1234-5678", "+82212345678")
    }

    @Test fun passthrough() {
        check("", "")
        check("*123#", "*123#")
        check("+1415", "+1415")
    }
}
